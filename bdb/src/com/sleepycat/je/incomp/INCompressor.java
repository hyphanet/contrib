/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INCompressor.java,v 1.125.2.5 2007/07/02 19:54:50 mark Exp $
 */

package com.sleepycat.je.incomp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINReference;
import com.sleepycat.je.tree.CursorsExistException;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.NodeNotEmptyException;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.Tree.SearchType;
import com.sleepycat.je.utilint.DaemonThread;
import com.sleepycat.je.utilint.PropUtil;
import com.sleepycat.je.utilint.Tracer;

/**
 * The IN Compressor.  JE compression consist of removing delete entries from
 * BINs, and pruning empty IN/BINs from the tree. Compression is carried out by
 * either a daemon thread or lazily by operations (namely checkpointing and
 * eviction) that are writing INS.
 */
public class INCompressor extends DaemonThread {
    private static final String TRACE_COMPRESS = "INCompress:";
    private static final boolean DEBUG = false;

    private EnvironmentImpl env;
    private long lockTimeout;

    /* stats */
    private int splitBins = 0;
    private int dbClosedBins = 0;
    private int cursorsBins = 0;
    private int nonEmptyBins = 0;
    private int processedBins = 0;
    
    /* per-run stats */
    private int splitBinsThisRun = 0;
    private int dbClosedBinsThisRun = 0;
    private int cursorsBinsThisRun = 0;
    private int nonEmptyBinsThisRun = 0;
    private int processedBinsThisRun = 0;

    /* 
     * The following stats are not kept per run, because they're set by
     * multiple threads doing lazy compression. They are debugging aids; it
     * didn't seem like a good idea to add synchronization to the general path.
     */
    private int lazyProcessed = 0;
    private int lazyEmpty = 0;
    private int lazySplit = 0;
    private int wokenUp = 0;

    /* 
     * Store logical references to BINs that have deleted entries and are
     * candidates for compaction.
     */
    private Map binRefQueue;
    private Object binRefQueueSync;

    public INCompressor(EnvironmentImpl env, long waitTime, String name)
	throws DatabaseException {

	super(waitTime, name, env);
        this.env = env;
	lockTimeout = PropUtil.microsToMillis(
            env.getConfigManager().getLong
                (EnvironmentParams.COMPRESSOR_LOCK_TIMEOUT));
        binRefQueue = new HashMap();
        binRefQueueSync = new Object();
    }

    synchronized public void clearEnv() {
	env = null;
    }

    public synchronized void verifyCursors()
	throws DatabaseException {

	/*
	 * Environment may have been closed.  If so, then our job here is done.
	 */
	if (env.isClosed()) {
	    return;
	}

	/* 
	 * Use a snapshot to verify the cursors.  This way we don't have to
	 * hold a latch while verify takes locks.
	 */
	List queueSnapshot = null;
	synchronized (binRefQueueSync) {
	    queueSnapshot = new ArrayList(binRefQueue.values());
        }

        /*
         * Use local caching to reduce DbTree.getDb overhead.  Do not call
         * releaseDb after each getDb, since the entire dbCache will be
         * released at the end.
         */
        DbTree dbTree = env.getDbMapTree();
        Map dbCache = new HashMap();
        try {
            Iterator it = queueSnapshot.iterator();
            while (it.hasNext()) {
                BINReference binRef = (BINReference) it.next();
                DatabaseImpl db = dbTree.getDb
                    (binRef.getDatabaseId(), lockTimeout, dbCache);
                BIN bin = searchForBIN(db, binRef);
                if (bin != null) {
                    bin.verifyCursors();
                    bin.releaseLatch();
                }
            }
        } finally {
            dbTree.releaseDbs(dbCache);
        }
    }

    /**
     * The default daemon work queue is not used because we need a map, not a
     * set.
     */
    public void addToQueue(Object o)
	throws DatabaseException {

        throw new DatabaseException
            ("INCompressor.addToQueue should never be called.");
    }

    public int getBinRefQueueSize()
        throws DatabaseException {

        int size = 0;
        synchronized (binRefQueueSync) {
            size = binRefQueue.size();
        }

        return size;
    }

    /*
     * There are multiple flavors of the addBin*ToQueue methods. All allow
     * the caller to specify whether the daemon should be notified. Currently
     * no callers proactively notify, and we rely on lazy compression and
     * the daemon timebased wakeup to process the queue.
     */

    /**
     * Adds the BIN and deleted Key to the queue if the BIN is not already in
     * the queue, or adds the deleted key to an existing entry if one exists.
     */
    public void addBinKeyToQueue(BIN bin, Key deletedKey, boolean doWakeup)
	throws DatabaseException {

        synchronized (binRefQueueSync) {
            addBinKeyToQueueAlreadyLatched(bin, deletedKey);
        }
        if (doWakeup) {
            wakeup();
        }
    }

    /**
     * Adds the BINReference to the queue if the BIN is not already in the
     * queue, or adds the deleted keys to an existing entry if one exists.
     */
    public void addBinRefToQueue(BINReference binRef, boolean doWakeup)
	throws DatabaseException {

        synchronized (binRefQueueSync) {
            addBinRefToQueueAlreadyLatched(binRef);
        }

        if (doWakeup) {
            wakeup();
        }
    }

    /**
     * Adds an entire collection of BINReferences to the queue at once.  Use
     * this to avoid latching for each add.
     */
    public void addMultipleBinRefsToQueue(Collection binRefs,
                                          boolean doWakeup)
	throws DatabaseException {

	synchronized (binRefQueueSync) {
            Iterator it = binRefs.iterator();
            while (it.hasNext()) {
                BINReference binRef = (BINReference) it.next();
                addBinRefToQueueAlreadyLatched(binRef);
            }
        }
        
        if (doWakeup) {
            wakeup();
        }
    }

    /**
     * Adds the BINReference with the latch held.
     */
    private void addBinRefToQueueAlreadyLatched(BINReference binRef) {

        Long node = new Long(binRef.getNodeId());
        BINReference existingRef = (BINReference) binRefQueue.get(node);
        if (existingRef != null) {
            existingRef.addDeletedKeys(binRef);
        } else {
            binRefQueue.put(node, binRef);
        }
    }

    /**
     * Adds the BIN and deleted Key with the latch held.
     */
    private void addBinKeyToQueueAlreadyLatched(BIN bin, Key deletedKey) {

        Long node = new Long(bin.getNodeId());
        BINReference existingRef = (BINReference) binRefQueue.get(node);
        if (existingRef != null) {
            if (deletedKey != null) {
                existingRef.addDeletedKey(deletedKey);
            }
        } else {
            BINReference binRef = bin.createReference();
            if (deletedKey != null) {
                binRef.addDeletedKey(deletedKey);
            }
            binRefQueue.put(node, binRef);
        }
    }

    public boolean exists(long nodeId) {
        Long node = new Long(nodeId);
        synchronized (binRefQueueSync) {
            return (binRefQueue.get(node) != null);
	}
    }

    /* 
     * Return a bin reference for this node if it exists and has a set of
     * deletable keys.
     */
    private BINReference removeCompressibleBinReference(long nodeId) {
        Long node = new Long(nodeId);
        BINReference foundRef = null;
        synchronized (binRefQueueSync) {
            BINReference target = (BINReference) binRefQueue.remove(node);
            if (target != null) {
                if (target.deletedKeysExist()) {
                    foundRef = target;
                } else {

                    /* 
                     * This is an entry that needs to be pruned. Put it back
                     * to be dealt with by the daemon. 
                     */
                    binRefQueue.put(node, target);
                }
            }
        }
        return foundRef;
    }

    /**
     * Return stats
     */
    public void loadStats(StatsConfig config, EnvironmentStats stat) 
        throws DatabaseException {

        stat.setSplitBins(splitBins);
        stat.setDbClosedBins(dbClosedBins);
        stat.setCursorsBins(cursorsBins);
        stat.setNonEmptyBins(nonEmptyBins);
        stat.setProcessedBins(processedBins);
        stat.setInCompQueueSize(getBinRefQueueSize());

        if (DEBUG) {
            System.out.println("lazyProcessed = " + lazyProcessed);
            System.out.println("lazyEmpty = " + lazyEmpty);
            System.out.println("lazySplit = " + lazySplit);
            System.out.println("wokenUp=" + wokenUp);
        }

        if (config.getClear()) {
            splitBins = 0;
            dbClosedBins = 0;
            cursorsBins = 0;
            nonEmptyBins = 0;
            processedBins = 0;
            lazyProcessed = 0;
            lazyEmpty = 0;
            lazySplit = 0;
            wokenUp = 0;
        }
    }

    /**
     * Return the number of retries when a deadlock exception occurs.
     */
    protected int nDeadlockRetries()
        throws DatabaseException {

        return env.getConfigManager().getInt
            (EnvironmentParams.COMPRESSOR_RETRY);
    }

    public synchronized void onWakeup()
	throws DatabaseException {

        if (env.isClosed()) {
            return;
        } 
        wokenUp++;
        doCompress();
    }

    /**
     * The real work to doing a compress. This may be called by the compressor
     * thread or programatically.
     */
    public synchronized void doCompress() 
        throws DatabaseException {

	if (!isRunnable()) {
	    return;
	}

	/* 
         * Make a snapshot of the current work queue so the compressor thread
         * can safely iterate over the queue. Note that this impacts lazy
         * compression, because it lazy compressors will not see BINReferences
         * that have been moved to the snapshot.
         */
        Map queueSnapshot = null;
        int binQueueSize = 0;
        synchronized (binRefQueueSync) {
            binQueueSize = binRefQueue.size();
            if (binQueueSize > 0) {
                queueSnapshot = binRefQueue;
                binRefQueue = new HashMap();
            }
        }

        /* There is work to be done. */
        if (binQueueSize > 0) {
            resetPerRunCounters();
            Tracer.trace(Level.FINE, env,
                         "InCompress.doCompress called, queue size: " +
                         binQueueSize);
            assert LatchSupport.countLatchesHeld() == 0;
            
            /*
             * Compressed entries must be counted as obsoleted.  A separate
             * tracker is used to accumulate tracked obsolete info so it can be
             * added in a single call under the log write latch.  We log the
             * info for deleted subtrees immediately because we don't process
             * deleted IN entries during recovery; this reduces the chance of
             * lost info.
             */
            UtilizationTracker tracker = new UtilizationTracker(env);

            /* Use local caching to reduce DbTree.getDb overhead. */
            Map dbCache = new HashMap();

            DbTree dbTree = env.getDbMapTree();
            BINSearch binSearch = new BINSearch();
            try {
                Iterator it = queueSnapshot.values().iterator();
                while (it.hasNext()) {
                    if (env.isClosed()) {
                        return;
                    }

                    BINReference binRef = (BINReference) it.next();
                    if (!findDBAndBIN(binSearch, binRef, dbTree, dbCache)) {

                        /* 
                         * Either the db is closed, or the BIN doesn't
                         * exist. Don't process this BINReference.
                         */
                        continue;
                    }
                    
                    if (binRef.deletedKeysExist()) {
                        /* Compress deleted slots. */
                        boolean requeued = compressBin
                            (binSearch.db, binSearch.bin, binRef, tracker);

                        if (!requeued) {

                            /* 
                             * This BINReference was fully processed, but there
			     * may still be deleted slots. If there are still
			     * deleted keys in the binref, they were relocated
			     * by a split.
                             */
                            checkForRelocatedSlots
                                (binSearch.db, binRef, tracker);
                        }
                    } else {

                        /* 
                         * An empty BINReference on the queue was put there by
                         * a lazy compressor to indicate that we should try to
                         * prune an empty BIN.
                         */
                        BIN foundBin = binSearch.bin;

                        byte[] idKey = foundBin.getIdentifierKey();
                        boolean isDBIN = foundBin.containsDuplicates();
                        byte[] dupKey = null;
                        if (isDBIN) {
                            dupKey = ((DBIN) foundBin).getDupKey();
                        }

                        /* 
                         * Release the bin latch taken by the initial
                         * search. Pruning starts from the top of the tree
                         * and requires that no latches are held.
                         */
                        foundBin.releaseLatch();
                        
                        pruneBIN(binSearch.db,  binRef, idKey, isDBIN,
                                 dupKey, tracker);
                    }
                }

                /*
                 * Count obsolete nodes and write out modified file summaries
                 * for recovery.  All latches must have been released.
                 */
                TrackedFileSummary[] summaries = tracker.getTrackedFiles();
                if (summaries.length > 0) {
                    env.getUtilizationProfile().countAndLogSummaries
                        (summaries);
                }

            } finally {
                dbTree.releaseDbs(dbCache);
                assert LatchSupport.countLatchesHeld() == 0;
                accumulatePerRunCounters();
            }
        }
    }

    /**
     * Compresses a single BIN and then deletes the BIN if it is empty.
     * @param bin is latched when this method is called, and unlatched when it
     * returns.
     * @return true if the BINReference was requeued by this method.
     */
    private boolean compressBin(DatabaseImpl db,
                                BIN bin,
                                BINReference binRef,
                                UtilizationTracker tracker)
        throws DatabaseException {

        /* Safe to get identifier keys; bin is latched. */
        boolean empty = false;
        boolean requeued = false;
        byte[] idKey = bin.getIdentifierKey();
        byte[] dupKey = null;
        boolean isDBIN = bin.containsDuplicates();
            
        try {
            int nCursors = bin.nCursors();
            if (nCursors > 0) {

                /* 
                 * There are cursors pointing to the BIN, so try again later.
                 */
                addBinRefToQueue(binRef, false);
                requeued = true;
                cursorsBinsThisRun++;
            } else {
                requeued = bin.compress(binRef, true /* canFetch */, tracker);
                if (!requeued) {

                    /* 
                     * Only check for emptiness if this BINRef is in play and
                     * not on the queue.
                     */
                    empty = (bin.getNEntries() == 0);

                    if (empty) {

                        /* 
                         * While we have the BIN latched, prepare a dup key if
                         * needed for navigating the tree while pruning.
                         */
                        if (isDBIN) {
                            dupKey = ((DBIN) bin).getDupKey();
                        }
                    }
                }
            }
        } finally {
            bin.releaseLatch();
        }

        /* Prune if the bin is empty and there has been no requeuing. */
        if (empty) {
            requeued = pruneBIN(db, binRef, idKey, isDBIN, dupKey, tracker);
        }

        return requeued;
    }

    /**
     * If the target BIN is empty, attempt to remove the empty branch of the 
     * tree.
     * @return true if the pruning was unable to proceed and the BINReference
     * was requeued.
     */
    private boolean pruneBIN(DatabaseImpl dbImpl,
                             BINReference binRef,
                             byte[] idKey,
                             boolean containsDups,
                             byte[] dupKey,
                             UtilizationTracker tracker)
        throws DatabaseException {

        boolean requeued = false;
        try {
            Tree tree = dbImpl.getTree();
            
            if (containsDups) {
                tree.deleteDup(idKey, dupKey, tracker);
            } else {
                tree.delete(idKey, tracker);
            }
            processedBinsThisRun++;
        } catch (NodeNotEmptyException NNEE) {

            /* 
             * Something was added to the node since the point when the
             * deletion occurred; we can't prune, and we can throw away this
             * BINReference.
             */
             nonEmptyBinsThisRun++;
        } catch (CursorsExistException e) {

            /* 
             * If there are cursors in the way of the delete, retry later. 
             * For example, When we delete a BIN or DBIN, we're guaranteed that
             * there are no cursors at that node. (otherwise, we wouldn't be
             * able to remove all the entries. However, there's the possibility
             * that the BIN that is the parent of the duplicate tree has
             * resident cursors, and in that case, we would not be able to
             * remove the whole duplicate tree and DIN root. In that case, we'd
             * requeue.
             */
            addBinRefToQueue(binRef, false);
            cursorsBinsThisRun++;
            requeued = true;
        }
        return requeued;
    }
    
    /*
     * When we do not requeue the BINRef but there are deleted keys remaining,
     * those keys were not found in the BIN and therefore must have been moved
     * to another BIN during a split.
     */
    private void checkForRelocatedSlots(DatabaseImpl db,
                                        BINReference binRef,
                                        UtilizationTracker tracker) 
        throws DatabaseException {

        Iterator iter = binRef.getDeletedKeyIterator();
        if (iter != null) {

            /* mainKey is only used for dups. */
            byte[] mainKey = binRef.getKey();
            boolean isDup = (binRef.getData() != null);

            while (iter.hasNext()) {
                Key key = (Key) iter.next();

                /*
                 * Lookup the BIN for each deleted key, and compress that BIN
                 * separately.
                 */
                BIN splitBin = isDup ?
                    searchForBIN(db, mainKey, key.getKey()) :
                    searchForBIN(db, key.getKey(), null);
                if (splitBin != null) {
                    BINReference splitBinRef = splitBin.createReference();
                    splitBinRef.addDeletedKey(key);
		    compressBin(db, splitBin, splitBinRef, tracker);
                }
            }
        }
    }

    private boolean isRunnable()
	throws DatabaseException {

	return true;
    }

    /**
     * Search the tree for the BIN or DBIN that corresponds to this
     * BINReference.
     * 
     * @param binRef the BINReference that indicates the bin we want.
     * @return the BIN or DBIN that corresponds to this BINReference. The
     * node is latched upon return. Returns null if the BIN can't be found.
     */
    public BIN searchForBIN(DatabaseImpl db, BINReference binRef)
        throws DatabaseException {

        return searchForBIN(db, binRef.getKey(), binRef.getData());
    }

    private BIN searchForBIN(DatabaseImpl db, byte[] mainKey, byte[] dupKey)
        throws DatabaseException {

        /* Search for this IN */
        Tree tree = db.getTree();
        IN in = tree.search
            (mainKey, SearchType.NORMAL, -1, null, false /*updateGeneration*/);

        /* Couldn't find a BIN, return null */
        if (in == null) {
            return null;
        }

        /* This is not a duplicate, we're done. */
        if (dupKey == null) {
            return (BIN) in;
        }

        /* We need to descend down into a duplicate tree. */
        DIN duplicateRoot = null;
        DBIN duplicateBin = null;
        BIN bin = (BIN) in;
        try {
            int index = bin.findEntry(mainKey, false, true);
            if (index >= 0) {
                Node node = null;
		if (!bin.isEntryKnownDeleted(index)) {
                    /* If fetchTarget returns null, a deleted LN was cleaned. */
                    node = bin.fetchTarget(index);
                }
                if (node == null) {
		    bin.releaseLatch();
		    return null;
		}
                if (node.containsDuplicates()) {
                    /* It's a duplicate tree. */
                    duplicateRoot = (DIN) node;
                    duplicateRoot.latch();
                    bin.releaseLatch();
                    duplicateBin = (DBIN) tree.searchSubTree
                        (duplicateRoot, dupKey, SearchType.NORMAL, -1, null,
                         false /*updateGeneration*/);

                    return duplicateBin;
                } else {
                    /* We haven't migrated to a duplicate tree yet.
                     * XXX, isn't this taken care of above? */
                    return bin;
                }
            } else {
                bin.releaseLatch();
                return null;
            }
        } catch (DatabaseException DBE) {
            if (bin != null) {
                bin.releaseLatchIfOwner();
            }
            if (duplicateRoot != null) {
                duplicateRoot.releaseLatchIfOwner();
            }

	    /* 
	     * FindBugs whines about Redundent comparison to null below, but
	     * for stylistic purposes we'll leave it in.
	     */
            if (duplicateBin != null) { 
                duplicateBin.releaseLatchIfOwner();
            }
            throw DBE;
        }
    }

    /**
     * Reset per-run counters.
     */
    private void resetPerRunCounters() {
	splitBinsThisRun = 0;
	dbClosedBinsThisRun = 0;
	cursorsBinsThisRun = 0;
	nonEmptyBinsThisRun = 0;
	processedBinsThisRun = 0;
    }

    private void accumulatePerRunCounters() {
	splitBins += splitBinsThisRun;
	dbClosedBins += dbClosedBinsThisRun;
	cursorsBins += cursorsBinsThisRun;
	nonEmptyBins += nonEmptyBinsThisRun;
	processedBins += processedBinsThisRun;
    }

    /**
     * Lazily compress a single BIN. Do not do any pruning. The target IN
     * should be latched when we enter, and it will be remain latched.
     */
    public void lazyCompress(IN in, UtilizationTracker tracker) 
        throws DatabaseException {

        if (!in.isCompressible()) {
            return;
        }

        assert in.isLatchOwnerForWrite();
        
        /* BIN is latched. */
        BIN bin = (BIN) in;
        int nCursors = bin.nCursors();
        if (nCursors > 0) {
            /* Cursor prohibit compression. */
            return;
        } else {
            BINReference binRef = 
                removeCompressibleBinReference(bin.getNodeId());
            if ((binRef == null) || (!binRef.deletedKeysExist())) {
                return;
            } else {

                boolean requeued =
                    bin.compress(binRef, false /* canFetch */, tracker);
                lazyProcessed++;

                /* 
                 * If this wasn't requeued, but there were deleted keys
                 * remaining, requeue, so the daemon can handle this.  Either
                 * we must have shuffled some items because of a split, or a
                 * child was not resident and we couldn't process that entry.
                 */
                if (!requeued && binRef.deletedKeysExist()) {
                    addBinRefToQueue(binRef, false);
                    lazySplit++;
                } else {
                    if (bin.getNEntries() == 0) {
                        addBinRefToQueue(binRef, false);
                        lazyEmpty++;
                    }
                }
            }
        }
    }

    /* 
     * Find the db and bin for a BINReference.
     * @return true if the db is open and the target bin is found.
     */
    private boolean findDBAndBIN(BINSearch binSearch, 
                                 BINReference binRef,
                                 DbTree dbTree,
                                 Map dbCache) 
        throws DatabaseException {

        /*
         * Find the database.  Do not call releaseDb after this getDb, since
         * the entire dbCache will be released later.
         */
        binSearch.db = dbTree.getDb
            (binRef.getDatabaseId(), lockTimeout, dbCache);
        if ((binSearch.db == null) ||(binSearch.db.isDeleted())) {  
          /* The db was deleted. Ignore this BIN Ref. */
            dbClosedBinsThisRun++;
            return false;
        }

        /* Perform eviction before each operation. */
        env.getEvictor().doCriticalEviction(true); // backgroundIO

        /* Find the BIN. */
        binSearch.bin = searchForBIN(binSearch.db, binRef);
        if ((binSearch.bin == null) ||
            binSearch.bin.getNodeId() != binRef.getNodeId()) {
            /* The BIN may have been split. */
            if (binSearch.bin != null) {
                binSearch.bin.releaseLatch();
            }
            splitBinsThisRun++;
            return false;
        }
        
        return true;
    }

    /* Struct to return multiple values from findDBAndBIN. */
    private static class BINSearch {
        public DatabaseImpl db;
        public BIN bin;
    }
}
