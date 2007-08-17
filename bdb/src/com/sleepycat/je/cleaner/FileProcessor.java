/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileProcessor.java,v 1.17.2.6 2007/07/02 19:54:48 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.CleanerFileReader;
import com.sleepycat.je.log.LogFileNotFoundException;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeLocation;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.utilint.DaemonThread;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * Reads all entries in a log file and either determines them to be obsolete or
 * marks them for migration.  LNs are marked for migration by setting the BIN
 * entry MIGRATE flag.  INs are marked for migration by setting the dirty flag.
 *
 * May be invoked explicitly by calling doClean, or woken up if used as a
 * daemon thread.
 */
class FileProcessor extends DaemonThread {

    /**
     * The number of LN log entries after we process pending LNs.  If we do
     * this too seldom, the pending LN queue may grow large, and it isn't
     * budgeted memory.  If we process it too often, we will repeatedly request
     * a non-blocking lock for the same locked node.
     */
    private static final int PROCESS_PENDING_EVERY_N_LNS = 100;

    /**
     * Whether to prohibit BINDeltas for a BIN that is fetched by the cleaner.
     * The theory is that when fetching a BIN during cleaning we normally
     * expect that the BIN will be evicted soon, and a delta during checkpoint
     * would be wasted.  However, this does not take into account use of the
     * BIN by the application after fetching; the BIN could become hot and then
     * deltas may be profitable.  To be safe we currently allow deltas when
     * fetching.
     */
    private static final boolean PROHIBIT_DELTAS_WHEN_FETCHING = false;

    private static final boolean DEBUG_TRACING = false;

    private EnvironmentImpl env;
    private Cleaner cleaner;
    private FileSelector fileSelector;
    private UtilizationProfile profile;

    /* Log version for the target file. */
    private int fileLogVersion; 

    /* Per Run counters. Reset before each file is processed. */
    private int nINsObsoleteThisRun = 0;
    private int nINsCleanedThisRun = 0;
    private int nINsDeadThisRun = 0;
    private int nINsMigratedThisRun = 0;
    private int nLNsObsoleteThisRun = 0;
    private int nLNsCleanedThisRun = 0;
    private int nLNsDeadThisRun = 0;
    private int nLNsLockedThisRun = 0;
    private int nLNsMigratedThisRun = 0;
    private int nLNsMarkedThisRun = 0;
    private int nLNQueueHitsThisRun = 0;
    private int nEntriesReadThisRun;
    private long nRepeatIteratorReadsThisRun;

    FileProcessor(String name,
                  EnvironmentImpl env,
                  Cleaner cleaner,
                  UtilizationProfile profile,
                  FileSelector fileSelector) {
        super(0, name, env);
        this.env = env;
        this.cleaner = cleaner;
        this.fileSelector = fileSelector;
        this.profile = profile;
    }

    public void clearEnv() {
        env = null;
        cleaner = null;
        fileSelector = null;
        profile = null;
    }

    /**
     * Adds a sentinal object to the work queue to force onWakeup to be
     * called immediately after setting je.env.runCleaner=true.  We want to
     * process any backlog immediately.
     */
    void addSentinalWorkObject() {
        try {
            workQueueLatch.acquire();
            workQueue.add(new Object());
            workQueueLatch.release();
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Return the number of retries when a deadlock exception occurs.
     */
    protected int nDeadlockRetries()
        throws DatabaseException {

        return cleaner.nDeadlockRetries;
    }

    /**
     * Cleaner doesn't have a work queue so just throw an exception if it's
     * ever called.
     */
    public void addToQueue(Object o)
        throws DatabaseException {

        throw new DatabaseException
            ("Cleaner.addToQueue should never be called.");
    }

    /**
     * Activates the cleaner.  Is normally called when je.cleaner.byteInterval
     * bytes are written to the log.
     */
    public void onWakeup()
        throws DatabaseException {

        doClean(true,   // invokedFromDaemon
                true,   // cleanMultipleFiles
                false); // forceCleaning

        /* Remove the sentinal -- see addSentinalWorkObject. */
        workQueueLatch.acquire();
        workQueue.clear();
        workQueueLatch.release();
    }

    /**
     * Cleans selected files and returns the number of files cleaned.  May be
     * called by the daemon thread or programatically.
     *
     * @param invokedFromDaemon currently has no effect.
     *
     * @param cleanMultipleFiles is true to clean until we're under budget,
     * or false to clean at most one file.
     *
     * @param forceCleaning is true to clean even if we're not under the
     * utilization threshold.
     *
     * @return the number of files cleaned, not including files cleaned
     * unsuccessfully.
     */
    public synchronized int doClean(boolean invokedFromDaemon,
                                    boolean cleanMultipleFiles,
                                    boolean forceCleaning) 
        throws DatabaseException {

        if (env.isClosed()) {
            return 0;
        }

        /* Clean until no more files are selected.  */
        int nOriginalLogFiles = profile.getNumberOfFiles();
        int nFilesCleaned = 0;
        while (true) {

            /* Don't clean forever. */
            if (nFilesCleaned >= nOriginalLogFiles) {
                break;
            }

            /* Stop if the daemon is paused or the environment is closing. */
            if ((invokedFromDaemon && isPaused()) || env.isClosing()) {
                break;
            }

            /*
             * Process pending LNs and then attempt to delete all cleaned files
             * that are safe to delete.  Pending LNs can prevent file deletion.
             */
            cleaner.processPending();
            cleaner.deleteSafeToDeleteFiles();

            /*
             * Select the next file for cleaning and update the Cleaner's
             * read-only file collections.
             */
            boolean needLowUtilizationSet =
                cleaner.clusterResident || cleaner.clusterAll;

            Long fileNum = fileSelector.selectFileForCleaning
                (profile, forceCleaning, needLowUtilizationSet,
                 cleaner.maxBatchFiles);

            cleaner.updateReadOnlyFileCollections();

            /*
             * If no file was selected, the total utilization is under the
             * threshold and we can stop cleaning.
             */
            if (fileNum == null) {
                break;
            }

            /*
             * Clean the selected file.
             */
            resetPerRunCounters();
            boolean finished = false;
            boolean fileDeleted = false;
            long fileNumValue = fileNum.longValue();
            int runId = ++cleaner.nCleanerRuns;
            try {

                String traceMsg =
                    "CleanerRun " + runId +
                    " on file 0x" + Long.toHexString(fileNumValue) + 
                    " begins backlog=" + cleaner.nBacklogFiles;
                Tracer.trace(Level.INFO, env, traceMsg);
                if (DEBUG_TRACING) {
                    System.out.println("\n" + traceMsg);
                }

                /* Clean all log entries in the file. */
                Set deferredWriteDbs = new HashSet(); 
                if (processFile(fileNum, deferredWriteDbs)) {
                    /* File is fully processed, update status information. */
                    fileSelector.addCleanedFile(fileNum, deferredWriteDbs);
                    nFilesCleaned += 1;
                    accumulatePerRunCounters();
                    finished = true;
                }
            } catch (LogFileNotFoundException e) {

                /*
                 * File was deleted.  Although it is possible that the file was
                 * deleted externally it is much more likely that the file was
                 * deleted normally after being cleaned earlier (this was
                 * observed prior to JE 3.2.29), and that we are mistakedly
                 * processing the file repeatedly.  Since the file does not
                 * exist, ignore the error so that the cleaner will continue.
                 * The tracing below will indicate that the file was deleted.
                 * Remove the file completely from the FileSelector and
                 * UtilizationProfile so that we don't repeatedly attempt to
                 * process it. [#15528]
                 */
                fileDeleted = true;
                profile.removeFile(fileNum);
                fileSelector.removeAllFileReferences(fileNum);
            } catch (IOException e) {
                Tracer.trace(env, "Cleaner", "doClean", "", e);
                throw new DatabaseException(e);
            } finally {
                if (!finished && !fileDeleted) {
                    fileSelector.putBackFileForCleaning(fileNum);
                }
                String traceMsg =
                    "CleanerRun " + runId + 
                    " on file 0x" + Long.toHexString(fileNumValue) + 
                    " invokedFromDaemon=" + invokedFromDaemon +
                    " finished=" + finished +
                    " fileDeleted=" + fileDeleted +
                    " nEntriesRead=" + nEntriesReadThisRun +
                    " nINsObsolete=" + nINsObsoleteThisRun +
                    " nINsCleaned=" + nINsCleanedThisRun +
                    " nINsDead=" + nINsDeadThisRun +
                    " nINsMigrated=" + nINsMigratedThisRun +
                    " nLNsObsolete=" + nLNsObsoleteThisRun +
                    " nLNsCleaned=" + nLNsCleanedThisRun +
                    " nLNsDead=" + nLNsDeadThisRun +
                    " nLNsMigrated=" + nLNsMigratedThisRun +
                    " nLNsMarked=" + nLNsMarkedThisRun +
                    " nLNQueueHits=" + nLNQueueHitsThisRun +
                    " nLNsLocked=" + nLNsLockedThisRun;
                Tracer.trace(Level.SEVERE, env, traceMsg);
                if (DEBUG_TRACING) {
                    System.out.println("\n" + traceMsg);
                }
            }

            /* If we should only clean one file, stop now. */
            if (!cleanMultipleFiles) {
                break;
            }
        }

        return nFilesCleaned;
    }

    /**
     * Process all log entries in the given file.
     * 
     * Note that we check for obsolete entries using the active TFS
     * (TrackedFileSummary) for a file while it is being processed, and we
     * prohibit flushing (eviction) of that offset information until file
     * processing is complete.  An entry could become obsolete because: 1-
     * normal application activity deletes or updates the entry, 2- proactive
     * migration migrates the entry before we process it, or 3- if trackDetail
     * is false.  However, checking the TFS is expensive if it has many
     * entries, because we perform a linear search.  There is a tradeoff
     * between the cost of the TFS lookup and its benefit, which is to avoid a
     * tree search if the entry is obsolete.  Note that many more lookups for
     * non-obsolete entries than obsolete entries will typically be done.  In
     * spite of that we check the tracked summary to avoid the situation where
     * eviction does proactive migration, and evicts a BIN that is very soon
     * afterward fetched during cleaning.
     *
     * @param fileNum the file being cleaned.
     * @param deferredWriteDbs the set of databaseIds for deferredWrite 
     * databases which need a sync before a cleaned file can be safely deleted.
     * @return false if we aborted file processing because the environment is
     * being closed.
     */
    private boolean processFile(Long fileNum, Set deferredWriteDbs)
        throws DatabaseException, IOException {

        /* Get the current obsolete offsets for this file. */
        PackedOffsets obsoleteOffsets = new PackedOffsets();
        TrackedFileSummary tfs =
            profile.getObsoleteDetail(fileNum,
                                      obsoleteOffsets, 
                                      true /* logUpdate */);
        PackedOffsets.Iterator obsoleteIter = obsoleteOffsets.iterator();
        long nextObsolete = -1;

        /* Keep in local variables because they are mutable properties. */
        final int readBufferSize = cleaner.readBufferSize;
        int lookAheadCacheSize = cleaner.lookAheadCacheSize;

        /*
         * Add the overhead of this method to the budget.  Two read buffers are
         * allocated by the file reader. The log size of the offsets happens to
         * be the same as the memory overhead.
         */
        int adjustMem = (2 * readBufferSize) +
                        obsoleteOffsets.getLogSize() +
                        lookAheadCacheSize;
        MemoryBudget budget = env.getMemoryBudget();
        budget.updateMiscMemoryUsage(adjustMem);

        /* Evict after updating the budget. */
        if (Cleaner.DO_CRITICAL_EVICTION) {
            env.getEvictor().doCriticalEviction(true); // backgroundIO
        }

        /*
         * We keep a look ahead cache of non-obsolete LNs.  When we lookup a
         * BIN in processLN, we also process any other LNs in that BIN that are
         * in the cache.  This can reduce the number of tree lookups.
         */
        LookAheadCache lookAheadCache = new LookAheadCache(lookAheadCacheSize);

        /*
         * For obsolete entries we must check for pending deleted DBs.  To
         * avoid the overhead of DbTree.getDb on every entry we keep a set of
         * all DB IDs encountered and do the check once per DB at the end.
         */
        Set checkPendingDbSet = new HashSet();

        /*
         * Use local caching to reduce DbTree.getDb overhead.  Do not call
         * releaseDb after getDb with the dbCache, since the entire dbCache
         * will be released at the end of thie method.
         */
        Map dbCache = new HashMap();
        DbTree dbMapTree = env.getDbMapTree();

        try {
            /* Create the file reader. */
            CleanerFileReader reader = new CleanerFileReader
                (env, readBufferSize, DbLsn.NULL_LSN, fileNum);
            /* Validate all entries before ever deleting a file. */
            reader.setAlwaysValidateChecksum(true);

            TreeLocation location = new TreeLocation();

            int nProcessedLNs = 0;
            while (reader.readNextEntry()) {
                cleaner.nEntriesRead += 1;
                long logLsn = reader.getLastLsn();
                long fileOffset = DbLsn.getFileOffset(logLsn);
                boolean isLN = reader.isLN();
                boolean isIN = reader.isIN();
                boolean isRoot = reader.isRoot();
                boolean isFileHeader = reader.isFileHeader();
                boolean isObsolete = false;

                if (reader.isFileHeader()) {
                    fileLogVersion = reader.getFileHeader().getLogVersion();
                }

                /* Stop if the daemon is shut down. */
                if (env.isClosing()) {
                    return false;
                }

                /* Update background reads. */
                int nReads = reader.getAndResetNReads();
                if (nReads > 0) {
                    env.updateBackgroundReads(nReads);
                }

                /* Sleep if background read/write limit was exceeded. */
                env.sleepAfterBackgroundIO();

                /* Check for a known obsolete node. */
                while (nextObsolete < fileOffset && obsoleteIter.hasNext()) {
                    nextObsolete = obsoleteIter.next();
                }
                if (nextObsolete == fileOffset) {
                    isObsolete = true;
                }
                
                /* Check for the entry type next because it is very cheap. */
                if (!isObsolete &&
                    !isLN &&
                    !isIN &&
                    !isRoot) {
                    /* Consider all entries we do not process as obsolete. */
                    isObsolete = true;
                }

                /* 
                 * SR 14583: In JE 2.0 and later we can assume that all 
                 * deleted LNs are obsolete. Either the delete committed and
                 * the BIN parent is marked with a pending deleted bit, or the
                 * delete rolled back, in which case there is no reference
                 * to this entry. JE 1.7.1 and earlier require a tree lookup
                 * because deleted LNs may still be reachable through their BIN
                 * parents. 
                 */
                if (!isObsolete &&
                    isLN &&
                    reader.getLN().isDeleted() &&
                    fileLogVersion > 2) {
                    /* Deleted LNs are always obsolete. */
                    isObsolete = true;
                }

                /* Check the current tracker last, as it is more expensive. */
                if (!isObsolete &&
                    tfs != null &&
                    tfs.containsObsoleteOffset(fileOffset)) {
                    isObsolete = true;
                }

                /* Skip known obsolete nodes. */
                if (isObsolete) {
                    /* Count obsolete stats. */
                    if (isLN) {
                        nLNsObsoleteThisRun++;
                    } else if (isIN) {
                        nINsObsoleteThisRun++;
                    }
                    /* Must update the pending DB set for obsolete entries. */
                    DatabaseId dbId = reader.getDatabaseId();
                    if (dbId != null) {
                        checkPendingDbSet.add(dbId);
                    }
                    continue;
                }

                /* Evict before processing each entry. */
                if (Cleaner.DO_CRITICAL_EVICTION) {
                    env.getEvictor().doCriticalEviction(true); // backgroundIO
                }

                /* The entry is not known to be obsolete -- process it now. */
                if (isLN) {

                    LN targetLN = reader.getLN();
                    DatabaseId dbId = reader.getDatabaseId();
                    byte[] key = reader.getKey();
                    byte[] dupKey = reader.getDupTreeKey();

                    lookAheadCache.add
                        (new Long(DbLsn.getFileOffset(logLsn)),
                         new LNInfo(targetLN, dbId, key, dupKey));

                    if (lookAheadCache.isFull()) {
                        processLN(fileNum, location, lookAheadCache,
                                  dbCache, deferredWriteDbs);
                    }

                    /*
                     * Process pending LNs before proceeding in order to
                     * prevent the pending list from growing too large.
                     */
                    nProcessedLNs += 1;
                    if (nProcessedLNs % PROCESS_PENDING_EVERY_N_LNS == 0) {
                        cleaner.processPending();
                    }

                } else if (isIN) {

                    IN targetIN = reader.getIN();
                    DatabaseId dbId = reader.getDatabaseId();
                    DatabaseImpl db = dbMapTree.getDb
                        (dbId, cleaner.lockTimeout, dbCache);
                    targetIN.setDatabase(db);
                    processIN(targetIN, db, logLsn, deferredWriteDbs);
                    
                } else if (isRoot) {
                    
                    env.rewriteMapTreeRoot(logLsn);
                } else {
                    assert false;
                }
            }

            /* Process remaining queued LNs. */
            while (!lookAheadCache.isEmpty()) {
                if (Cleaner.DO_CRITICAL_EVICTION) {
                    env.getEvictor().doCriticalEviction(true); // backgroundIO
                }
                processLN(fileNum, location, lookAheadCache, dbCache,
                          deferredWriteDbs);
                /* Sleep if background read/write limit was exceeded. */
                env.sleepAfterBackgroundIO();
            }

            /* Update the pending DB set. */
            for (Iterator i = checkPendingDbSet.iterator(); i.hasNext();) {
                DatabaseId dbId = (DatabaseId) i.next();
                DatabaseImpl db = dbMapTree.getDb
                    (dbId, cleaner.lockTimeout, dbCache);
                cleaner.addPendingDB(db);
            }

            /* Update reader stats. */
            nEntriesReadThisRun = reader.getNumRead();
            nRepeatIteratorReadsThisRun = reader.getNRepeatIteratorReads();

        } finally {
            /* Subtract the overhead of this method from the budget. */
            budget.updateMiscMemoryUsage(0 - adjustMem);

            /* Release all cached DBs. */
            dbMapTree.releaseDbs(dbCache);

            /* Allow flushing of TFS when cleaning is complete. */
            if (tfs != null) {
                tfs.setAllowFlush(true);
            }
        }

        return true;
    }

    /**
     * Processes the first LN in the look ahead cache and removes it from the
     * cache.  While the BIN is latched, look through the BIN for other LNs in
     * the cache; if any match, process them to avoid a tree search later.
     */
    private void processLN(Long fileNum,
                           TreeLocation location,
                           LookAheadCache lookAheadCache,
                           Map dbCache,
                           Set deferredWriteDbs)
        throws DatabaseException {

        nLNsCleanedThisRun++;

        /* Get the first LN from the queue. */
        Long offset = lookAheadCache.nextOffset();
        LNInfo info = lookAheadCache.remove(offset);

        LN ln = info.getLN();
        byte[] key = info.getKey();
        byte[] dupKey = info.getDupKey();

        long logLsn = DbLsn.makeLsn
            (fileNum.longValue(), offset.longValue());

        /*
         * Do not call releaseDb after this getDb, since the entire dbCache
         * will be released later.
         */
        DatabaseImpl db = env.getDbMapTree().getDb
            (info.getDbId(), cleaner.lockTimeout, dbCache);

        /* Status variables are used to generate debug tracing info. */
        boolean processedHere = true; // The LN was cleaned here.
        boolean obsolete = false;     // The LN is no longer in use.
        boolean completed = false;    // This method completed.

        BIN bin = null;
        DIN parentDIN = null;      // for DupCountLNs
        try {

            /* 
             * If the DB is gone, this LN is obsolete.  If delete cleanup is in
             * progress, put the DB into the DB pending set; this LN will be
             * declared deleted after the delete cleanup is finished.
             */
            if (db == null || db.isDeleted()) {
                cleaner.addPendingDB(db);
                nLNsDeadThisRun++;
                obsolete = true;
                completed = true;
                return;
            }

            Tree tree = db.getTree();
            assert tree != null;

            /*
	     * Search down to the bottom most level for the parent of this LN.
	     */
            boolean parentFound = tree.getParentBINForChildLN
                (location, key, dupKey, ln,
                 false,  // splitsAllowed
                 true,   // findDeletedEntries
                 false,  // searchDupTree
                 Cleaner.UPDATE_GENERATION);
            bin = location.bin;
            int index = location.index;

            if (!parentFound) {
                nLNsDeadThisRun++;
		obsolete = true;
                completed = true;
		return;
            }

            /*
	     * Now we're at the parent for this LN, whether BIN, DBIN or DIN.
	     * If knownDeleted, LN is deleted and can be purged.
	     */
	    if (bin.isEntryKnownDeleted(index)) {
		nLNsDeadThisRun++;
		obsolete = true;
		completed = true;
                return;
	    }

            /*
             * Determine whether the parent is the current BIN, or in the case
             * of a DupCountLN, a DIN.  Get the tree LSN in either case.
             */
            boolean isDupCountLN = ln.containsDuplicates();
            long treeLsn;
	    if (isDupCountLN) {
		parentDIN = (DIN) bin.fetchTarget(index);
		parentDIN.latch(Cleaner.UPDATE_GENERATION);
                ChildReference dclRef = parentDIN.getDupCountLNRef();
                treeLsn = dclRef.getLsn();
	    } else {
                treeLsn = bin.getLsn(index);
	    }

            /* Process this LN that was found in the tree. */
            processedHere = false;
            processFoundLN(info, logLsn, treeLsn, bin, index, parentDIN);
            completed = true;

            /*
             * For all other non-deleted LNs in this BIN, lookup their LSN
             * in the LN queue and process any matches.
             */
            if (!isDupCountLN) {
                for (int i = 0; i < bin.getNEntries(); i += 1) {
                    long binLsn = bin.getLsn(i);
                    if (i != index &&
                        !bin.isEntryKnownDeleted(i) &&
                        !bin.isEntryPendingDeleted(i) &&
                        DbLsn.getFileNumber(binLsn) == fileNum.longValue()) {

                        Long myOffset = new Long(DbLsn.getFileOffset(binLsn));
                        LNInfo myInfo = lookAheadCache.remove(myOffset);

                        if (myInfo != null) {
                            nLNQueueHitsThisRun++;
                            nLNsCleanedThisRun++;
                            processFoundLN
                                (myInfo, binLsn, binLsn, bin, i, null);
                        }
                    }
                }
            }
            return;

        } finally {
            noteDbsRequiringSync(db, deferredWriteDbs);

            if (parentDIN != null) {
                parentDIN.releaseLatchIfOwner();
            }

            if (bin != null) {
                bin.releaseLatchIfOwner();
            }

            if (processedHere) {
                cleaner.trace
                    (cleaner.detailedTraceLevel, Cleaner.CLEAN_LN, ln, logLsn,
                     completed, obsolete, false /*migrated*/);
            }
        }
    }

    /**
     * Processes an LN that was found in the tree.  Lock the LN's node ID and
     * then set the entry's MIGRATE flag if the LSN of the LN log entry is the
     * active LSN in the tree.
     *
     * @param info identifies the LN log entry.
     *
     * @param logLsn is the LSN of the log entry.
     *
     * @param treeLsn is the LSN found in the tree.
     *
     * @param bin is the BIN found in the tree; is latched on method entry and
     * exit.
     *
     * @param index is the BIN index found in the tree.
     *
     * @param parentDIN is non-null for a DupCountLN only; if non-null, is
     * latched on method entry and exit.
     */
    private void processFoundLN(LNInfo info,
                                long logLsn,
                                long treeLsn,
                                BIN bin,
                                int index,
                                DIN parentDIN)
        throws DatabaseException {

        LN ln = info.getLN();
        byte[] key = info.getKey();
        byte[] dupKey = info.getDupKey();

        DatabaseImpl db = bin.getDatabase();
        boolean isDupCountLN = parentDIN != null;

        /* Status variables are used to generate debug tracing info. */
        boolean obsolete = false;  // The LN is no longer in use.
        boolean migrated = false;  // The LN was in use and is migrated.
        boolean lockDenied = false;// The LN lock was denied.
        boolean completed = false; // This method completed.

        long nodeId = ln.getNodeId();
        BasicLocker locker = null;
        try {
            Tree tree = db.getTree();
            assert tree != null;

            /*
             * If the tree and log LSNs are equal, then we can be fairly
             * certain that the log entry is current; in that case, it is
             * wasteful to lock the LN here -- it is better to lock only once
             * during lazy migration.  But if the tree and log LSNs differ, it
             * is likely that another thread has updated or deleted the LN and
             * the log LSN is now obsolete; in this case we can avoid dirtying
             * the BIN by checking for obsoleteness here, which requires
             * locking.  The latter case can occur frequently if trackDetail is
             * false.
             * 
             * 1. If the LSN in the tree and in the log are the same, we will
             * attempt to migrate it.
             * 
             * 2. If the LSN in the tree is < the LSN in the log, the log entry
             * is obsolete, because this LN has been rolled back to a previous
             * version by a txn that aborted.
             * 
             * 3. If the LSN in the tree is > the LSN in the log, the log entry
             * is obsolete, because the LN was advanced forward by some
             * now-committed txn.
             *
             * 4. If the LSN in the tree is a null LSN, the log entry is
             * obsolete. A slot can only have a null LSN if the record has
             * never been written to disk in a deferred write database, and
             * in that case the log entry must be for a past, deleted version
             * of that record.
             */
            if (ln.isDeleted() &&
                (treeLsn == logLsn) &&
                fileLogVersion <= 2) {

                /*
                 * SR 14583: After JE 2.0, deleted LNs are never found in the
                 * tree, since we can assume they're obsolete and correctly
                 * marked as such in the obsolete offset tracking. JE 1.7.1 and
                 * earlier did not use the pending deleted bit, so deleted LNs
                 * may still be reachable through their BIN parents.
                 */
                obsolete = true;
                nLNsDeadThisRun++;
                bin.setPendingDeleted(index);
            } else if (treeLsn == DbLsn.NULL_LSN) {

                /*
                 * Case 4: The LN in the tree is a never-written LN for a 
                 * deferred-write db, so the LN in the file is obsolete.
                 */
                obsolete = true;
            } else if (treeLsn != logLsn) {

                /*
                 * Check to see whether the LN being migrated is locked
                 * elsewhere.  Do that by attempting to lock it.  We can hold
                 * the latch on the BIN (and DIN) since we always attempt to
                 * acquire a non-blocking read lock.  Holding the latch ensures
                 * that the INs won't change underneath us because of splits or
                 * eviction.
                 */
                locker = new BasicLocker(env);
                LockResult lockRet = locker.nonBlockingLock
                    (nodeId, LockType.READ, db);
                if (lockRet.getLockGrant() == LockGrantType.DENIED) {

                    /* 
                     * LN is currently locked by another Locker, so we can't
                     * assume anything about the value of the LSN in the bin.
                     */
                    nLNsLockedThisRun++;
                    lockDenied = true;
                } else {
                    /* The LN is obsolete and can be purged. */
                    nLNsDeadThisRun++;
                    obsolete = true;
                }
            }

            if (!obsolete && !lockDenied) {

                /*
                 * Set the migrate flag and dirty the parent IN.  The evictor
                 * or checkpointer will migrate the LN later.
                 *
                 * Then set the target node so it does not have to be fetched
                 * when it is migrated, if the tree and log LSNs are equal and
                 * the target is not resident.  We must call postFetchInit to
                 * initialize MapLNs that have not been fully initialized yet
                 * [#13191].
                 */
                if (isDupCountLN) {
                    ChildReference dclRef = parentDIN.getDupCountLNRef();
                    dclRef.setMigrate(true);
                    parentDIN.setDirty(true);

                    if (treeLsn == logLsn && dclRef.getTarget() == null) {
                        ln.postFetchInit(db, logLsn);
                        parentDIN.updateDupCountLN(ln);
                    }
                } else {
                    bin.setMigrate(index, true);
                    bin.setDirty(true);

                    if (treeLsn == logLsn && bin.getTarget(index) == null) {
                        ln.postFetchInit(db, logLsn);
                        bin.updateEntry(index, ln);
                    }

                    /*
                     * If the generation is zero, we fetched this BIN just for
                     * cleaning.
                     */
                    if (PROHIBIT_DELTAS_WHEN_FETCHING &&
                        bin.getGeneration() == 0) {
                        bin.setProhibitNextDelta();
                    }

                    /*
                     * Update the generation so that the BIN is not evicted
                     * immediately.  This allows the cleaner to fill in as many
                     * entries as possible before eviction, as to-be-cleaned
                     * files are processed.
                     */
                    bin.setGeneration();
                }

                nLNsMarkedThisRun++;
                migrated = true;
            }
            completed = true;
        } finally {
            if (locker != null) {
                locker.operationEnd();
            }

            /*
             * If a write lock is held, it is likely that the log LSN will
             * become obsolete.  It is more efficient to process this via the
             * pending list than to set the MIGRATE flag, dirty the BIN, and
             * cause the BIN to be logged unnecessarily.
             */
            if (completed && lockDenied) {
                fileSelector.addPendingLN(ln, db.getId(), key, dupKey);
            }

            cleaner.trace
                (cleaner.detailedTraceLevel, Cleaner.CLEAN_LN, ln, logLsn,
                 completed, obsolete, migrated);
        }
    }

    /**
     * If an IN is still in use in the in-memory tree, dirty it. The checkpoint
     * invoked at the end of the cleaning run will end up rewriting it.
     */
    private void processIN(IN inClone,
                           DatabaseImpl db,
                           long logLsn,
                           Set deferredWriteDbs)
        throws DatabaseException {

        boolean obsolete = false;
        boolean dirtied = false;
        boolean completed = false;

        try {
            nINsCleanedThisRun++;

            /* 
             * If the DB is gone, this LN is obsolete.  If delete cleanup is in
             * progress, put the DB into the DB pending set; this LN will be
             * declared deleted after the delete cleanup is finished.
             */
            if (db == null || db.isDeleted()) {
                cleaner.addPendingDB(db);
                nINsDeadThisRun++;
                obsolete = true;
                completed = true;
                return;
            }

            Tree tree = db.getTree();
            assert tree != null;

            IN inInTree = findINInTree(tree, db, inClone, logLsn);

            if (inInTree == null) {
                /* IN is no longer in the tree.  Do nothing. */
                nINsDeadThisRun++;
                obsolete = true;
            } else {

                /* 
                 * IN is still in the tree.  Dirty it.  Checkpoint or eviction
                 * will write it out.  Prohibit the next delta, since the
                 * original version must be made obsolete.
                 */
                nINsMigratedThisRun++;
                inInTree.setDirty(true);
                inInTree.setProhibitNextDelta();
                inInTree.releaseLatch();
                dirtied = true;
            }
            
            completed = true;
        } finally {
            noteDbsRequiringSync(db, deferredWriteDbs);

            cleaner.trace
                (cleaner.detailedTraceLevel, Cleaner.CLEAN_IN, inClone, logLsn,
                 completed, obsolete, dirtied);
        }
    }

    /**
     * Given a clone of an IN that has been taken out of the log, try to find
     * it in the tree and verify that it is the current one in the log.
     * Returns the node in the tree if it is found and it is current re: LSN's.
     * Otherwise returns null if the clone is not found in the tree or it's not
     * the latest version.  Caller is responsible for unlatching the returned
     * IN.
     */
    private IN findINInTree(Tree tree,
                            DatabaseImpl db,
                            IN inClone, 
                            long logLsn)
        throws DatabaseException {

        /* Check if inClone is the root. */
        if (inClone.isDbRoot()) {
            IN rootIN = isRoot(tree, db, inClone, logLsn);
            if (rootIN == null) {

                /*
                 * inClone is a root, but no longer in use. Return now, because
                 * a call to tree.getParentNode will return something
                 * unexpected since it will try to find a parent.
                 */
                return null;  
            } else {
                return rootIN;
            }
        }       

        /* It's not the root.  Can we find it, and if so, is it current? */
        inClone.latch(Cleaner.UPDATE_GENERATION);
        SearchResult result = null;
        try {

            result = tree.getParentINForChildIN
                (inClone,
                 true,   // requireExactMatch
                 Cleaner.UPDATE_GENERATION,
                 inClone.getLevel(),
                 null);  // trackingList

            if (!result.exactParentFound) {
                return null;
            }
        
            long treeLsn = result.parent.getLsn(result.index);

            /* 
             * The IN in the tree is a never-written IN for a DW db so the IN
	     * in the file is obsolete. [#15588]
             */
            if (treeLsn == DbLsn.NULL_LSN) {
		return null;
            }
 
            int compareVal = DbLsn.compareTo(treeLsn, logLsn);
            
            if (compareVal > 0) {
                /* Log entry is obsolete. */
                return null;
            } else {

                /*
                 * Log entry is same or newer than what's in the tree.  Dirty
                 * the IN and let checkpoint write it out.
                 */
                IN in;
                if (compareVal == 0) {
                    /* We can reuse the log entry if the LSNs are equal. */
                    in = (IN) result.parent.getTarget(result.index);
                    if (in == null) {
                        in = inClone;
                        in.postFetchInit(db, logLsn);
                        result.parent.updateEntry(result.index, in);
                    }
                } else {
                    in = (IN) result.parent.fetchTarget(result.index);
                }
                in.latch(Cleaner.UPDATE_GENERATION);
                return in;
            }
        } finally {
            if ((result != null) && (result.exactParentFound)) {
                result.parent.releaseLatch();
            }
        }
    }

    /*
     * When we process a target log entry for a deferred write db, we may
     * need to sync the db at the next checkpoint. 
     * Cases are:
     *  IN found in the tree: 
     *      The IN is dirtied and must be written out at the next ckpt.
     *  IN not found in the tree:
     *      This log entry is not in use by the in-memory tree, but a later
     *      recovery has the possibility of reverting to the last synced
     *      version. To prevent that, we have to sync the database before
     *      deleting the file.
     *  LN found in tree:
     *      It will be migrated, need to be synced.
     *  LN not found in tree:
     *      Like not-found IN, need to be sure that the database is 
     *      sufficiently synced.
     * Note that if nothing in the db is actually dirty (LN and IN are not
     * found) there's no harm done, there will be no sync and no extra
     * processing.
     */
    private void noteDbsRequiringSync(DatabaseImpl db,
                                          Set deferredWriteDbs) {
        if ((db != null) && (!db.isDeleted()) && db.isDeferredWrite()) {
            deferredWriteDbs.add(db.getId());
        }
    }

    /**
     * Get the current root in the tree, or null if the inClone
     * is the current root.
     */
    private static class RootDoWork implements WithRootLatched {
        private DatabaseImpl db;
        private IN inClone;
        private long logLsn;

        RootDoWork(DatabaseImpl db, IN inClone, long logLsn) {
            this.db = db;
            this.inClone = inClone;
            this.logLsn = logLsn;
        }

        public IN doWork(ChildReference root)
            throws DatabaseException {

            if (root == null ||
                (root.getLsn() == DbLsn.NULL_LSN) || // deferred write root
		(root.fetchTarget(db, null).getNodeId() !=
                 inClone.getNodeId())) {
                return null;
            }

            /*
             * A root LSN less than the log LSN must be an artifact of when we
             * didn't properly propagate the logging of the rootIN up to the
             * root ChildReference.  We still do this for compatibility with
             * old log versions but may be able to remove it in the future.
             */
            if (DbLsn.compareTo(root.getLsn(), logLsn) <= 0) {
                IN rootIN = (IN) root.fetchTarget(db, null);
                rootIN.latch(Cleaner.UPDATE_GENERATION);
                return rootIN;
            } else {
                return null;
            }
        }
    }

    /**
     * Check if the cloned IN is the same node as the root in tree.  Return the
     * real root if it is, null otherwise.  If non-null is returned, the
     * returned IN (the root) is latched -- caller is responsible for
     * unlatching it.
     */
    private IN isRoot(Tree tree, DatabaseImpl db, IN inClone, long lsn)
        throws DatabaseException {

        RootDoWork rdw = new RootDoWork(db, inClone, lsn);
        return tree.withRootLatchedShared(rdw);
    }

    /**
     * Reset per-run counters.
     */
    private void resetPerRunCounters() {
        nINsObsoleteThisRun = 0;
        nINsCleanedThisRun = 0;
        nINsDeadThisRun = 0;
        nINsMigratedThisRun = 0;
        nLNsObsoleteThisRun = 0;
        nLNsCleanedThisRun = 0;
        nLNsDeadThisRun = 0;
        nLNsMigratedThisRun = 0;
        nLNsMarkedThisRun = 0;
        nLNQueueHitsThisRun = 0;
        nLNsLockedThisRun = 0;
        nEntriesReadThisRun = 0;
        nRepeatIteratorReadsThisRun = 0;
    }

    /**
     * Add per-run counters to total counters.
     */
    private void accumulatePerRunCounters() {
        cleaner.nINsObsolete +=         nINsObsoleteThisRun;
        cleaner.nINsCleaned +=          nINsCleanedThisRun;
        cleaner.nINsDead +=             nINsDeadThisRun;
        cleaner.nINsMigrated +=         nINsMigratedThisRun;
        cleaner.nLNsObsolete +=         nLNsObsoleteThisRun;
        cleaner.nLNsCleaned +=          nLNsCleanedThisRun;
        cleaner.nLNsDead +=             nLNsDeadThisRun;
        cleaner.nLNsMigrated +=         nLNsMigratedThisRun;
        cleaner.nLNsMarked +=           nLNsMarkedThisRun;
        cleaner.nLNQueueHits +=         nLNQueueHitsThisRun;
        cleaner.nLNsLocked +=           nLNsLockedThisRun;
        cleaner.nRepeatIteratorReads += nRepeatIteratorReadsThisRun;
    }

    /**
     * A cache of LNInfo by LSN offset.  Used to hold a set of LNs that are
     * to be processed.  Keeps track of memory used, and when full (over
     * budget) the next offset should be queried and removed.
     */
    private static class LookAheadCache {

        private SortedMap map;
        private int maxMem;
        private int usedMem;

        LookAheadCache(int lookAheadCacheSize) {
            map = new TreeMap();
            maxMem = lookAheadCacheSize;
            usedMem = MemoryBudget.TREEMAP_OVERHEAD;
        }

        boolean isEmpty() {
            return map.isEmpty();
        }

        boolean isFull() {
            return usedMem >= maxMem;
        }

        Long nextOffset() {
            return (Long) map.firstKey();
        }

        void add(Long lsnOffset, LNInfo info) {
            map.put(lsnOffset, info);
            usedMem += info.getMemorySize();
            usedMem += MemoryBudget.TREEMAP_ENTRY_OVERHEAD;
        }

        LNInfo remove(Long offset) {
            LNInfo info = (LNInfo) map.remove(offset);
            if (info != null) {
                usedMem -= info.getMemorySize();
                usedMem -= MemoryBudget.TREEMAP_ENTRY_OVERHEAD;
            }
            return info;
        }
    }
}
