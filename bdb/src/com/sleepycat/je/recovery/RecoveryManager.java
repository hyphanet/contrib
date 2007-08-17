/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryManager.java,v 1.211.2.4 2007/07/02 19:54:51 mark Exp $
 */

package com.sleepycat.je.recovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.CheckpointFileReader;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.INFileReader;
import com.sleepycat.je.log.LNFileReader;
import com.sleepycat.je.log.LastFileReader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogFileNotFoundException;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.TrackingInfo;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeLocation;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

public class RecoveryManager {
    private static final String TRACE_DUP_ROOT_REPLACE =
        "DupRootRecover:";
    private static final String TRACE_LN_REDO = "LNRedo:";
    private static final String TRACE_LN_UNDO = "LNUndo";
    private static final String TRACE_IN_REPLACE = "INRecover:";
    private static final String TRACE_ROOT_REPLACE = "RootRecover:";
    private static final String TRACE_IN_DEL_REPLAY = "INDelReplay:";
    private static final String TRACE_IN_DUPDEL_REPLAY = "INDupDelReplay:";
    private static final String TRACE_ROOT_DELETE = "RootDelete:";

    private static final int CLEAR_INCREMENT = 50;

    private EnvironmentImpl env;
    private int readBufferSize;
    private RecoveryInfo info;       // stat info
    private Set committedTxnIds;     // committed txns
    private Set abortedTxnIds;       // aborted txns
    private Map preparedTxns;        // txnid -> prepared Txn
    private Set inListRebuildDbIds;  // dbs for which we have to rebuild the
                                     // in memory IN list.

    private Level detailedTraceLevel; // level value for detailed trace msgs
    private Map fileSummaryLsns;      // file number -> LSN of FileSummaryLN
    private int inListClearCounter;   // governs intermediate IN list clearing

    /**
     * Make a recovery manager
     */
    public RecoveryManager(EnvironmentImpl env)
        throws DatabaseException {

        this.env = env;
        DbConfigManager cm = env.getConfigManager();
        readBufferSize =
            cm.getInt(EnvironmentParams.LOG_ITERATOR_READ_SIZE);
        committedTxnIds = new HashSet();
        abortedTxnIds = new HashSet();
	preparedTxns = new HashMap();
        inListRebuildDbIds = new HashSet();
        fileSummaryLsns = new HashMap();

        /* 
         * Figure out the level to use for detailed trace messages, by choosing
         * the more verbose of the recovery manager's trace setting vs the
         * general trace setting.
         */
        detailedTraceLevel =
            Tracer.parseLevel(env,
                              EnvironmentParams.JE_LOGGING_LEVEL_RECOVERY);
    }
    
    /**
     * Look for an existing log and use it to create an in memory structure for
     * accessing existing databases. The file manager and logging system are
     * only available after recovery.
     * @return RecoveryInfo statistics about the recovery process.
     */
    public RecoveryInfo recover(boolean readOnly)
        throws DatabaseException {

        info = new RecoveryInfo();

        try {
            FileManager fileManager = env.getFileManager();
	    DbConfigManager configManager = env.getConfigManager();
	    boolean forceCheckpoint =
		configManager.getBoolean
		(EnvironmentParams.ENV_RECOVERY_FORCE_CHECKPOINT);
            if (fileManager.filesExist()) {

                /* 
                 * Establish the location of the end of the log. After this, we
                 * can write to the log. No Tracer calls are allowed until
                 * after this point is established in the log.
                 */
                findEndOfLog(readOnly);
                Tracer.trace(Level.CONFIG, env,
                             "Recovery underway, found end of log");
        
                /*
                 * Establish the location of the root, the last checkpoint, and
                 * the first active LSN by finding the last checkpoint.
                 */
                findLastCheckpoint();
		env.getLogManager().setLastLsnAtRecovery
		    (fileManager.getLastUsedLsn());
                Tracer.trace(Level.CONFIG, env,
                             "Recovery checkpoint search, " +
                             info);

                /* Read in the root. */
                env.readMapTreeFromLog(info.useRootLsn);

                /* Rebuild the in memory tree from the log. */
                buildTree();
            } else {

                /*
                 * Nothing more to be done. Enable publishing of debug log
                 * messages to the database log.
                 */
                env.enableDebugLoggingToDbLog();
                Tracer.trace(Level.CONFIG, env, "Recovery w/no files.");
                env.logMapTreeRoot();

		/*
		 * Always force a checkpoint during creation.
		 */
		forceCheckpoint = true;
            }

	    if (preparedTxns.size() > 0) {
		Tracer.trace(Level.INFO, env,
			     "There are " + preparedTxns.size() +
			     " prepared but unfinished txns.");

		/* 
		 * We don't need this set any more since these are all
		 * registered with the TxnManager now.
		 */
		preparedTxns = null;
	    }

            /*
             * Open the UP database and populate the cache before the first
             * checkpoint so that the checkpoint may flush file summary
             * information.  May be disabled for unittests.
             */
            if (DbInternal.getCreateUP
                (env.getConfigManager().getEnvironmentConfig())) {
                env.getUtilizationProfile().populateCache();
            }

            /*
             * At this point, we've recovered (or there were no log files at
             * all. Write a checkpoint into the log.
             *
             * NOTE: The discussion of deltas below may be obsolete now that
             * we use dirty bits to determine what to include in a delta.
             * However, we still want to disallow deltas to flush full versions
             * after a crash.
             *
             * Don't allow deltas, because the delta-determining scheme that
             * compares child entries to the last full LSN doesn't work in
             * recovery land. New child entries may have an earlier LSN than
             * the owning BIN's last full, because of the act of splicing in
             * LNs during recovery.
             *
             * For example, suppose that during LN redo, bin 10 was split into
             * bin 10 and bin 12. That splitting causes a full log.  Then later
             * on, the redo splices LN x, which is from before the last full of
             * bin 10, into bin 10. If we checkpoint allowing deltas after
             * recovery finishes, we won't pick up the LNx diff, because that
             * LN is an earlier LSN than the split-induced full log entry of
             * bin 10.
             */
            if (!readOnly &&
		(env.getLogManager().getLastLsnAtRecovery() !=
		 info.checkpointEndLsn ||
		 forceCheckpoint)) {
            	CheckpointConfig config = new CheckpointConfig();
            	config.setForce(true);
            	config.setMinimizeRecoveryTime(true);
                env.invokeCheckpoint
                    (config,
                     false, // flushAll
                     "recovery");
            } else {
                /* Initialze intervals when there is no initial checkpoint. */
                env.getCheckpointer().initIntervals
                    (info.checkpointEndLsn, System.currentTimeMillis());
            }

        } catch (IOException e) {
            Tracer.trace(env, "RecoveryManager", "recover",
                         "Couldn't recover", e);
            throw new RecoveryException(env, "Couldn't recover: " +
                                        e.getMessage(), e);
        } finally {
	    Tracer.trace(Level.CONFIG, env, "Recovery finished: " + info);
        }

        return info;
    }

    /**
     * Find the end of the log, initialize the FileManager. While we're
     * perusing the log, return the last checkpoint LSN if we happen to see it.
     */
    private void findEndOfLog(boolean readOnly)
        throws IOException, DatabaseException {

        LastFileReader reader = new LastFileReader(env, readBufferSize);

        /* 
         * Tell the reader to iterate through the log file until we hit the end
         * of the log or an invalid entry.
         * Remember the last seen CkptEnd, and the first CkptStart with no
         * following CkptEnd.
         */
        while (reader.readNextEntry()) {
            LogEntryType type = reader.getEntryType();
            if (LogEntryType.LOG_CKPT_END.equals(type)) {
                info.checkpointEndLsn = reader.getLastLsn();
                info.partialCheckpointStartLsn = DbLsn.NULL_LSN;
            } else if (LogEntryType.LOG_CKPT_START.equals(type)) {
                if (info.partialCheckpointStartLsn == DbLsn.NULL_LSN) {
                    info.partialCheckpointStartLsn = reader.getLastLsn();
                }
            }
        }

        /*
         * The last valid lsn should point to the start of the last valid log entry,
         * while the end of the log should point to the first byte of blank space,
         * so these two should not be the same.
         */
        assert (reader.getLastValidLsn() != reader.getEndOfLog()):
            "lastUsed=" + DbLsn.getNoFormatString(reader.getLastValidLsn()) +
            " end=" + DbLsn.getNoFormatString(reader.getEndOfLog());


        /* Now truncate if necessary. */
        if (!readOnly) {
            reader.setEndOfFile();
        }

        /* Tell the fileManager where the end of the log is. */
        info.lastUsedLsn = reader.getLastValidLsn();
        info.nextAvailableLsn = reader.getEndOfLog();
        info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
        env.getFileManager().setLastPosition(info.nextAvailableLsn,
                                             info.lastUsedLsn,
                                             reader.getPrevOffset());

        /*
         * Now the logging system is initialized and can do more
         * logging. Enable publishing of debug log messages to the database
         * log.
         */
        env.enableDebugLoggingToDbLog();
    }

    /**
     * Find the last checkpoint and establish the firstActiveLsn point,
     * checkpoint start, and checkpoint end.
     */
    private void findLastCheckpoint() 
        throws IOException, DatabaseException {

        /* 
         * The checkpointLsn might have been already found when establishing
         * the end of the log.  If it was found, then partialCheckpointStartLsn
         * was also found.  If it was not found, search backwards for it now
         * and also set partialCheckpointStartLsn.
         */
        if (info.checkpointEndLsn == DbLsn.NULL_LSN) {
            
            /*
             * Search backwards though the log for a checkpoint end entry and a
             * root entry.
             */
            CheckpointFileReader searcher =
                new CheckpointFileReader(env, readBufferSize, false,
                                         info.lastUsedLsn, DbLsn.NULL_LSN,
                                         info.nextAvailableLsn);

            while (searcher.readNextEntry()) {

                /* 
                 * Continue iterating until we find a checkpoint end entry.
                 * While we're at it, remember the last root seen in case we
                 * don't find a checkpoint end entry.
                 */
                if (searcher.isCheckpointEnd()) {

                    /* 
                     * We're done, the checkpoint end will tell us where the
                     * root is.
                     */
                    info.checkpointEndLsn = searcher.getLastLsn();
                    break;
                } else if (searcher.isCheckpointStart()) {

                    /*
                     * Remember the first CkptStart following the CkptEnd.
                     */
                    info.partialCheckpointStartLsn = searcher.getLastLsn();

                } else if (searcher.isRoot()) {

                    /* 
                     * Save the last root that was found in the log in case we
                     * don't see a checkpoint.
                     */
                    if (info.useRootLsn == DbLsn.NULL_LSN) {
                        info.useRootLsn = searcher.getLastLsn();
                    }
                }
            }
            info.nRepeatIteratorReads += searcher.getNRepeatIteratorReads();
        }

        /*
         * If we haven't found a checkpoint, we'll have to recover without
         * one. At a minimium, we must have found a root.
         */
        if (info.checkpointEndLsn == DbLsn.NULL_LSN) {
            info.checkpointStartLsn = DbLsn.NULL_LSN;
            info.firstActiveLsn = DbLsn.NULL_LSN;
        } else {
            /* Read in the checkpoint entry. */
            CheckpointEnd checkpointEnd =
                (CheckpointEnd) (env.getLogManager().get
				 (info.checkpointEndLsn));
            info.checkpointEnd = checkpointEnd;
            info.checkpointStartLsn = checkpointEnd.getCheckpointStartLsn();
            info.firstActiveLsn = checkpointEnd.getFirstActiveLsn();
            if (checkpointEnd.getRootLsn() != DbLsn.NULL_LSN) {
                info.useRootLsn = checkpointEnd.getRootLsn();
            }

            /* Init the checkpointer's id sequence and FirstActiveLsn.*/
            env.getCheckpointer().setCheckpointId(checkpointEnd.getId());
            env.getCheckpointer().setFirstActiveLsn
                (checkpointEnd.getFirstActiveLsn());
        }
        if (info.useRootLsn == DbLsn.NULL_LSN) {
            throw new NoRootException
		(env,
		 "This environment's log file has no root. Since the root " +
		 "is the first entry written into a log at environment " +
		 "creation, this should only happen if the initial creation " +
		 "of the environment was never checkpointed or synced. " +
		 "Please move aside the existing log files to allow the " +
		 "creation of a new environment");
        }
    }

    /**
     * Use the log to recreate an in memory tree.
     */
    private void buildTree()
        throws IOException, DatabaseException {

        inListClearCounter = 0;

        /* 
         * Read all map database INs, find largest node id before any
         * possiblity of splits, find largest txn Id before any need for a root
         * update (which would use an AutoTxn)
         */
        int passNum = buildINs(1,
                               true,   /* mapping tree */
                               false); /* dup tree */


        /*
         * Undo all aborted map LNs. Read and remember all committed
         * transaction ids.
         */
        Tracer.trace(Level.CONFIG, env, passStartHeader(passNum) +
                     "undo map LNs");
	long start = System.currentTimeMillis();
        Set mapLNSet = new HashSet();
        mapLNSet.add(LogEntryType.LOG_MAPLN_TRANSACTIONAL);
        mapLNSet.add(LogEntryType.LOG_TXN_COMMIT);
        mapLNSet.add(LogEntryType.LOG_TXN_ABORT);
        mapLNSet.add(LogEntryType.LOG_TXN_PREPARE);
        undoLNs(info, mapLNSet);
	long end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env, passEndHeader(passNum, start, end) +
		     info.toString());
        passNum++;

        /* 
         * Replay all mapLNs, mapping tree in place now. Use the set of
         * committed txns found from the undo pass.
         */
        Tracer.trace(Level.CONFIG, env, passStartHeader(passNum) +
                     "redo map LNs");
	start = System.currentTimeMillis();
        mapLNSet.add(LogEntryType.LOG_MAPLN);
        redoLNs(info, mapLNSet);
	end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env, passEndHeader(passNum, start, end) +
		     info.toString());
        passNum++;

        /*
         * Reconstruct the internal nodes for the main level trees.
         */
        passNum = buildINs(passNum,
                           false,   /* mapping tree*/
                           false);  /* dup tree */

        /*
         * Reconstruct the internal nodes for the duplicate level trees.
         */
        passNum = buildINs(passNum, 
                           false,   /* mapping tree*/
                           true);  /* dup tree */

        /*
         * Rebuild the in memory IN list.  Once the tree is complete we can
         * invoke the evictor.  The evictor will also be invoked during the
         * undo and redo passes.
         */
        rebuildINList();
        env.invokeEvictor();

        /*
         * Undo aborted LNs. No need to collect committed txn ids again, was
         * done in the undo of all aborted MapLNs.
         */
        Tracer.trace(Level.CONFIG, env, passStartHeader(9) + "undo LNs");
	start = System.currentTimeMillis();
        Set lnSet = new HashSet();
        lnSet.add(LogEntryType.LOG_LN_TRANSACTIONAL);
        lnSet.add(LogEntryType.LOG_NAMELN_TRANSACTIONAL);
        lnSet.add(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
        lnSet.add(LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL);

        undoLNs(info, lnSet);
	end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env, passEndHeader(9, start, end) +
		     info.toString());

        /* Replay LNs. Also read non-transactional LNs. */
        Tracer.trace(Level.CONFIG, env, passStartHeader(10) + "redo LNs");
	start = System.currentTimeMillis();
        lnSet.add(LogEntryType.LOG_LN);
        lnSet.add(LogEntryType.LOG_NAMELN);
        lnSet.add(LogEntryType.LOG_DEL_DUPLN);
        lnSet.add(LogEntryType.LOG_DUPCOUNTLN);
        lnSet.add(LogEntryType.LOG_FILESUMMARYLN);
        redoLNs(info, lnSet);
	end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env, passEndHeader(10, start, end) +
		     info.toString());
    }

    /* 
     * Up to three passes for the INs of a given level
     * @param mappingTree if true, we're rebuilding the mapping tree 
     * @param dupTree if true, we're rebuilding the dup tree.
     */
    private int buildINs(int passNum, 
                         boolean mappingTree,
                         boolean dupTree)
        throws IOException, DatabaseException {

        Set targetEntries = new HashSet();
        Set deltaType = new HashSet();
        String passADesc = null;
        String passBDesc = null;
        String passCDesc = null;

        if (mappingTree) {
            passADesc = "read mapping INs";
            passBDesc = "redo mapping INs";
            passCDesc = "read mapping BINDeltas";
        } else if (dupTree) {
            passADesc = "read dup INs";
            passBDesc = "redo dup INs";
            passCDesc = "read dup BINDeltas";
        } else {
            passADesc = "read main INs";
            passBDesc = "redo main INs";
            passCDesc = "read main BINDeltas";
        }

        if (dupTree) {
            /* Duplicate trees read these entries. */
            targetEntries.add(LogEntryType.LOG_DIN);
            targetEntries.add(LogEntryType.LOG_DBIN);
            targetEntries.add(LogEntryType.LOG_IN_DUPDELETE_INFO);
            deltaType.add(LogEntryType.LOG_DUP_BIN_DELTA);
        } else {
            /* Main tree and mapping tree read these types of entries. */
            targetEntries.add(LogEntryType.LOG_IN);
            targetEntries.add(LogEntryType.LOG_BIN);
            targetEntries.add(LogEntryType.LOG_IN_DELETE_INFO);
            deltaType.add(LogEntryType.LOG_BIN_DELTA);
        }

        /*
         * Pass a: Read all INs and place into the proper location.
         */
        Tracer.trace(Level.CONFIG, env, passStartHeader(passNum) + passADesc);
        LevelRecorder recorder = new LevelRecorder();
	long start = System.currentTimeMillis();
        if (mappingTree) {
            readINsAndTrackIds(info.checkpointStartLsn, recorder);
        } else {
            int numINsSeen = readINs(info.checkpointStartLsn, 
                                     false,  // mapping tree only
                                     targetEntries,
                                     /* 
                                      * requireExactMatch -- why is it true
                                      * for dups, false for main tree?
                                      * Keeping logic the same for now.
                                      */
                                     (dupTree? true: false),  
                                     recorder);
            if (dupTree) {
                info.numDuplicateINs += numINsSeen; 
            } else {
                info.numOtherINs += numINsSeen; 
            }
        }
	long end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env, passEndHeader(passNum, start, end) +
		     info.toString());
        passNum++;

        /*
         * Pass b: Redo INs if the LevelRecorder detects a split/compression
         * was done after ckpt [#14424]
         */
        Set redoSet = recorder.getDbsWithDifferentLevels();
        if (redoSet.size() > 0) {
            Tracer.trace(Level.CONFIG, env,
                         passStartHeader(passNum) + passBDesc);
            start = System.currentTimeMillis();
            repeatReadINs(info.checkpointStartLsn, 
                          targetEntries,
                          redoSet);
            end = System.currentTimeMillis();
            Tracer.trace(Level.CONFIG, env,
                         passEndHeader(passNum, start, end) + info.toString());
            passNum++;
        }

        /*
         * Pass c: Read BIN Deltas.
         * BINDeltas must be processed after all INs so the delta is properly
         * applied to the last version. For example, suppose BINDeltas were not
         * done in a later pass, the tree is INa->BINb, and the log has
         *       INa
         *       BINDelta for BINb
         *       INa
         * the splicing in of the second INa would override the BINDelta.
         */
        Tracer.trace(Level.CONFIG, env, passStartHeader(passNum) + passCDesc);
	start = System.currentTimeMillis();
        info.numBinDeltas += readINs(info.checkpointStartLsn, 
                                     mappingTree,
                                     deltaType,
                                     true,   // requireExactMatch
                                     null);  // LevelRecorder
	end = System.currentTimeMillis();
        Tracer.trace(Level.CONFIG, env,
                     passEndHeader(passNum, start, end) + info.toString());
        passNum++;

        return passNum;
    }

    /*
     * Read every internal node and IN DeleteInfo in the mapping tree and place
     * in the in-memory tree.
     */
    private void readINsAndTrackIds(long rollForwardLsn,
                                    LevelRecorder recorder) 
        throws IOException, DatabaseException {

        INFileReader reader =
            new INFileReader(env,
                             readBufferSize,
                             rollForwardLsn,        // start lsn
                             info.nextAvailableLsn, // end lsn
                             true,   // track node and db ids
			     false,  // map db only
                             info.partialCheckpointStartLsn,
                             fileSummaryLsns);
        reader.addTargetType(LogEntryType.LOG_IN);
        reader.addTargetType(LogEntryType.LOG_BIN);
        reader.addTargetType(LogEntryType.LOG_IN_DELETE_INFO);

        /* Validate all entries in at least one full recovery pass. */
        reader.setAlwaysValidateChecksum(true);

        try {
            info.numMapINs = 0;
            DbTree dbMapTree = env.getDbMapTree();

            /* 
	     * Process every IN, INDeleteInfo, and INDupDeleteInfo in the
	     * mapping tree.
	     */
            while (reader.readNextEntry()) {
                DatabaseId dbId = reader.getDatabaseId();
                if (dbId.equals(DbTree.ID_DB_ID)) {
                    DatabaseImpl db = dbMapTree.getDb(dbId);
                    try {
                        replayOneIN(reader, db, false, recorder);
                        info.numMapINs++;
                    } finally {
                        dbMapTree.releaseDb(db);
                    }
                }
            }

            /* 
             * Update node id and database sequences. Use either the maximum of
             * the ids seen by the reader vs the ids stored in the checkpoint.
             */
            info.useMaxNodeId = reader.getMaxNodeId();
            info.useMaxDbId = reader.getMaxDbId();
            info.useMaxTxnId = reader.getMaxTxnId();
            if (info.checkpointEnd != null) {
                if (info.useMaxNodeId < info.checkpointEnd.getLastNodeId()) {
                    info.useMaxNodeId = info.checkpointEnd.getLastNodeId();
                }
                if (info.useMaxDbId < info.checkpointEnd.getLastDbId()) {
                    info.useMaxDbId = info.checkpointEnd.getLastDbId();
                }
                if (info.useMaxTxnId < info.checkpointEnd.getLastTxnId()) {
                    info.useMaxTxnId = info.checkpointEnd.getLastTxnId();
                }
            }

            Node.setLastNodeId(info.useMaxNodeId);
            env.getDbMapTree().setLastDbId(info.useMaxDbId);
            env.getTxnManager().setLastTxnId(info.useMaxTxnId);

            info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
        } catch (Exception e) {
            traceAndThrowException(reader.getLastLsn(), "readMapIns", e);
        }
    }

    /**
     * Read INs and process.
     */
    private int readINs(long rollForwardLsn,
                        boolean mapDbOnly,
                        Set targetLogEntryTypes,
                        boolean requireExactMatch,
                        LevelRecorder recorder)
        throws IOException, DatabaseException {

        // don't need to track NodeIds
        INFileReader reader =
	    new INFileReader(env,
                             readBufferSize,
                             rollForwardLsn,                 // startlsn
                             info.nextAvailableLsn,          // finish
                             false,
			     mapDbOnly,
                             info.partialCheckpointStartLsn, 
                             fileSummaryLsns);

        Iterator iter = targetLogEntryTypes.iterator();
        while (iter.hasNext()) {
            reader.addTargetType((LogEntryType) iter.next());
        }

        int numINsSeen = 0;
        try {

            /*
             * Read all non-provisional INs, and process if they don't belong
             * to the mapping tree.
             */
            DbTree dbMapTree = env.getDbMapTree();
            while (reader.readNextEntry()) {
                DatabaseId dbId = reader.getDatabaseId();
                boolean isMapDb = dbId.equals(DbTree.ID_DB_ID);
                boolean isTarget = false;

                if (mapDbOnly && isMapDb) {
                    isTarget = true;
                } else if (!mapDbOnly && !isMapDb) {
                    isTarget = true;
                }
                if (isTarget) {
                    DatabaseImpl db = dbMapTree.getDb(dbId);
                    try {
                        if (db == null) {
                            // This db has been deleted, ignore the entry.
                        } else {
                            replayOneIN(reader, db, requireExactMatch,
                                        recorder);
                            numINsSeen++;

                            /*
                             * Add any db that we encounter IN's for because
                             * they'll be part of the in-memory tree and
                             * therefore should be included in the INList
                             * rebuild.
                             */
                            inListRebuildDbIds.add(dbId);
                        }
                    } finally {
                        dbMapTree.releaseDb(db);
                    }
                }
            }

            info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
            return numINsSeen;
        } catch (Exception e) {
            traceAndThrowException(reader.getLastLsn(), "readNonMapIns", e);
            return 0;
        }
    }

    /**
     * Read INs and process.
     */
    private void repeatReadINs(long rollForwardLsn,
                               Set targetLogEntryTypes,
                               Set targetDbs)
        throws IOException, DatabaseException {

        // don't need to track NodeIds
        INFileReader reader =
	    new INFileReader(env,
                             readBufferSize,
                             rollForwardLsn,                 // startlsn
                             info.nextAvailableLsn,          // finish
                             false,
                             false,                          // mapDbOnly
                             info.partialCheckpointStartLsn, 
                             fileSummaryLsns);

        Iterator iter = targetLogEntryTypes.iterator();
        while (iter.hasNext()) {
            reader.addTargetType((LogEntryType) iter.next());
        }

        try {

            /* Read all non-provisional INs that are in the repeat set. */
            DbTree dbMapTree = env.getDbMapTree();
            while (reader.readNextEntry()) {
                DatabaseId dbId = reader.getDatabaseId();
                if (targetDbs.contains(dbId)) {
                    DatabaseImpl db = dbMapTree.getDb(dbId);
                    try {
                        if (db == null) {
                            // This db has been deleted, ignore the entry.
                        } else {
                            replayOneIN(reader, 
                                        db, 
                                        true,  // requireExactMatch,
                                        null); // level recorder
                        }
                    } finally {
                        dbMapTree.releaseDb(db);
                    }
                }
            }

            info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
        } catch (Exception e) {
            traceAndThrowException(reader.getLastLsn(), "readNonMapIns", e);
        }
    }

    /**
     * Get an IN from the reader, set its database, and fit into tree.
     */
    private void replayOneIN(INFileReader reader,
                             DatabaseImpl db,
                             boolean requireExactMatch,
                             LevelRecorder recorder)
        throws DatabaseException {
        
        if (reader.isDeleteInfo()) {
            /* Last entry is a delete, replay it. */
            replayINDelete(db,
                           reader.getDeletedNodeId(),
                           false,
                           reader.getDeletedIdKey(),
                           null,
                           reader.getLastLsn());
        } else if (reader.isDupDeleteInfo()) {
            /* Last entry is a dup delete, replay it. */
            replayINDelete(db,
                           reader.getDupDeletedNodeId(),
                           true,
                           reader.getDupDeletedMainKey(),
                           reader.getDupDeletedDupKey(),
                           reader.getLastLsn());
        } else {

            /* 
	     * Last entry is a node, replay it. Now, we should really call
	     * IN.postFetchInit, but we want to do something different from the
	     * faulting-in-a-node path, because we don't want to put the IN on
	     * the in memory list, and we don't want to search the db map tree,
	     * so we have a IN.postRecoveryInit.  Note also that we have to
	     * pass the LSN of the current log entry and also the LSN of the IN
	     * in question. The only time these differ is when the log entry is
	     * a BINDelta -- then the IN's LSN is the last full version LSN,
	     * and the log LSN is the current log entry.
             */
            IN in = reader.getIN();
            long inLsn = reader.getLsnOfIN();
            in.postRecoveryInit(db, inLsn);
            in.latch();

            /* 
             * track the levels, in case we need an extra splits vs ckpt
             * reconcilliation.
             */
            if (recorder != null) {
                recorder.record(db.getId(), in.getLevel());
            }
            replaceOrInsert(db, in, reader.getLastLsn(), inLsn,
                            requireExactMatch);
        }

        /* 
	 * Although we're careful to not place INs instantiated from the log on
	 * the IN list, we do call normal tree search methods when checking
	 * agains the active tree. The INList builds up from the faulting in of
	 * nodes this way. However, some of those nodes become obsolete as we
	 * splice in newer versions, so the INList becomes too large and can
	 * pose a problem by causing us to overflow memory bounds.  Some
	 * possible solutions are to create an environment wide recovery mode,
	 * or to put special logic into the normal faulting-in path to know
	 * that we're in recovery. Because we don't want to impact normal code
	 * paths, we're going to just periodically clear the INList here. The
	 * INList will be regenerated at the end of recovery.
         */
        if ((++inListClearCounter % CLEAR_INCREMENT) == 0) {
            env.getInMemoryINs().clear();        
        }
    }

    /**
     * Undo all aborted LNs. To do so, walk the log backwards, keeping a
     * collection of committed txns. If we see a log entry that doesn't have a
     * committed txn, undo it.
     */
    private void undoLNs(RecoveryInfo info, Set lnTypes)
        throws IOException, DatabaseException {

	long firstActiveLsn = info.firstActiveLsn;
	long lastUsedLsn = info.lastUsedLsn;
	long endOfFileLsn = info.nextAvailableLsn;
        /* Set up a reader to pick up target log entries from the log. */ 
        LNFileReader reader =
            new LNFileReader(env, readBufferSize, lastUsedLsn, 
                             false, endOfFileLsn, firstActiveLsn, null);

        Iterator iter = lnTypes.iterator();
        while (iter.hasNext()) {
            LogEntryType lnType = (LogEntryType) iter.next();
            reader.addTargetType(lnType);
        }

        Map countedFileSummaries = new HashMap(); // TxnNodeId -> file number
        Set countedAbortLsnNodes = new HashSet(); // set of TxnNodeId

        DbTree dbMapTree = env.getDbMapTree();
        TreeLocation location = new TreeLocation();
        try {

            /*
             * Iterate over the target LNs and commit records, constructing
             * tree.
             */
            while (reader.readNextEntry()) {
                if (reader.isLN()) {

                    /* Get the txnId from the log entry. */
                    Long txnId = reader.getTxnId();

                    /*
                     * If this node is not in a committed txn, examine it to
                     * see if it should be undone.
                     */
                    if (txnId != null &&
			!committedTxnIds.contains(txnId)) {

			/*
			 * Invoke the evictor to reduce memory consumption.
			 */
			env.invokeEvictor();

			LN ln = reader.getLN();
			long logLsn = reader.getLastLsn();
			long abortLsn = reader.getAbortLsn();
			boolean abortKnownDeleted =
			    reader.getAbortKnownDeleted();
			DatabaseId dbId = reader.getDatabaseId();
			DatabaseImpl db = dbMapTree.getDb(dbId);
                        try {
                            /* Database may be null if it's been deleted. */
                            if (db != null) {
                                ln.postFetchInit(db, logLsn);
                                try {
                                    undo(detailedTraceLevel,
                                         db,
                                         location,
                                         ln,
                                         reader.getKey(),
                                         reader.getDupTreeKey(),
                                         logLsn, 
                                         abortLsn,
                                         abortKnownDeleted,
                                         info,
                                         true);
                                } finally {
                                    if (location.bin != null) {
                                        location.bin.releaseLatchIfOwner();
                                    }
                                }
                                /* Undo utilization info. */
                                TxnNodeId txnNodeId =
                                    new TxnNodeId(reader.getNodeId(),
                                                  txnId.longValue());
                                undoUtilizationInfo(ln, logLsn, abortLsn,
                                                    abortKnownDeleted,
                                                    reader.getLastEntrySize(),
                                                    txnNodeId,
                                                    countedFileSummaries,
                                                    countedAbortLsnNodes);

                                /*
                                 * Add any db that we encounter LN's for
                                 * because they'll be part of the in-memory
                                 * tree and therefore should be included in the
                                 * INList rebuild.
                                 */
                                inListRebuildDbIds.add(dbId);
                            }
                        } finally {
                            dbMapTree.releaseDb(db);
                        }
		    }
                } else if (reader.isPrepare()) {

		    /* 
		     * The entry just read is a prepare record.  There should
		     * be no lock conflicts during recovery, but just in case
		     * there are, we set the locktimeout to 0.
		     */
		    long prepareId = reader.getTxnPrepareId();
		    Long prepareIdL = new Long(prepareId);
		    if (!committedTxnIds.contains(prepareIdL) &&
			!abortedTxnIds.contains(prepareIdL)) {
			TransactionConfig txnConf = new TransactionConfig();
			Txn preparedTxn = new Txn(env, txnConf, prepareId);
			preparedTxn.setLockTimeout(0);
			preparedTxns.put(prepareIdL, preparedTxn);
			preparedTxn.setPrepared(true);
			env.getTxnManager().registerXATxn
			    (reader.getTxnPrepareXid(), preparedTxn, true);
			Tracer.trace(Level.INFO, env,
				     "Found unfinished prepare record: id: " +
				     reader.getTxnPrepareId() +
				     " Xid: " + reader.getTxnPrepareXid());
		    }
                } else if (reader.isAbort()) {
		    /* The entry just read is an abort record. */
		    abortedTxnIds.add(new Long(reader.getTxnAbortId()));
		} else {
                    /* The entry just read is a commit record. */
                    committedTxnIds.add(new Long(reader.getTxnCommitId()));
                }
            }
            info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
        } catch (RuntimeException e) {
            traceAndThrowException(reader.getLastLsn(), "undoLNs", e);
        }
    }

    /**
     * Apply all committed LNs.
     * @param rollForwardLsn start redoing from this point
     * @param lnType1 targetted LN
     * @param lnType2 targetted LN
     */
    private void redoLNs(RecoveryInfo info, Set lnTypes)
        throws IOException, DatabaseException {

	long endOfFileLsn = info.nextAvailableLsn;
	long rollForwardLsn = info.checkpointStartLsn;
        /* Set up a reader to pick up target log entries from the log */ 
        LNFileReader reader =
            new LNFileReader(env, readBufferSize, rollForwardLsn,
                             true, DbLsn.NULL_LSN, endOfFileLsn, null);

        Iterator iter = lnTypes.iterator();
        while (iter.hasNext()) {
            LogEntryType lnType = (LogEntryType) iter.next();
            reader.addTargetType(lnType);
        }

        Set countedAbortLsnNodes = new HashSet(); // set of TxnNodeId

        DbTree dbMapTree = env.getDbMapTree();
        TreeLocation location = new TreeLocation();
        try {

            /* Iterate over the target LNs and construct in- memory tree. */
            while (reader.readNextEntry()) {
                if (reader.isLN()) {

                    /* Get the txnId from the log entry. */
                    Long txnId = reader.getTxnId();
                
                    /* 
                     * If this LN is in a committed txn, or if it's a
                     * non-transactional LN, redo it.
                     */
		    boolean processThisLN = false;
		    boolean lnIsCommitted = false;
		    boolean lnIsPrepared = false;
		    Txn preparedTxn = null;
		    if (txnId == null) {
			processThisLN = true;
		    } else {
			lnIsCommitted = committedTxnIds.contains(txnId);
			if (!lnIsCommitted) {
			    preparedTxn = (Txn) preparedTxns.get(txnId);
			    lnIsPrepared = preparedTxn != null;
			}
			if (lnIsCommitted || lnIsPrepared) {
			    processThisLN = true;
			}
		    }
		    if (processThisLN) {

                        /* Invoke the evictor to reduce memory consumption. */
                        env.invokeEvictor();

                        LN ln = reader.getLN();
                        DatabaseId dbId = reader.getDatabaseId();
                        DatabaseImpl db = dbMapTree.getDb(dbId);
                        try {
                            long logLsn = reader.getLastLsn();
                            long treeLsn = DbLsn.NULL_LSN;

                            /* Database may be null if it's been deleted. */
                            if (db != null) {
                                ln.postFetchInit(db, logLsn);

                                if (preparedTxn != null) {
                                    preparedTxn.addLogInfo(logLsn);

                                    /*
                                     * We're reconstructing a prepared, but not
                                     * finished, transaction.  We know that
                                     * there was a write lock on this LN since
                                     * it exists in the log under this txnId.
                                     */
                                    preparedTxn.lock
                                        (ln.getNodeId(), LockType.WRITE,
                                         false /*noWait*/, db);
                                }

                                treeLsn = redo(db,
                                               location,
                                               ln,
                                               reader.getKey(),
                                               reader.getDupTreeKey(),
                                               logLsn,
                                               info);

                                /*
                                 * Add any db that we encounter LN's for
                                 * because they'll be part of the in-memory
                                 * tree and therefore should be included in the
                                 * INList rebuild.
                                 */
                                inListRebuildDbIds.add(dbId);
                            }

                            /* Redo utilization info. */
                            TxnNodeId txnNodeId = null;
                            if (txnId != null) {
                                txnNodeId = new TxnNodeId(reader.getNodeId(),
                                                          txnId.longValue());
                            }
                            redoUtilizationInfo(logLsn, treeLsn,
                                                reader.getAbortLsn(),
                                                reader.getAbortKnownDeleted(),
                                                reader.getLastEntrySize(),
                                                reader.getKey(),
                                                ln, txnNodeId,
                                                countedAbortLsnNodes);
                        } finally {
                            dbMapTree.releaseDb(db);
                        }
                    }
                }
            }
            info.nRepeatIteratorReads += reader.getNRepeatIteratorReads();
        } catch (Exception e) {
            traceAndThrowException(reader.getLastLsn(), "redoLns", e);
        }
    }

    /**
     * Rebuild the in memory inList with INs that have been made resident by
     * the recovery process.
     */
    private void rebuildINList() 
        throws DatabaseException {

        env.getInMemoryINs().clear();               // empty out
        env.getDbMapTree().rebuildINListMapDb();    // scan map db

        /* For all the dbs that we read in recovery, scan for resident INs. */
        Iterator iter = inListRebuildDbIds.iterator();
        while (iter.hasNext()) {
            DatabaseId dbId = (DatabaseId) iter.next();
            /* We already did the map tree, don't do it again. */
            if (!dbId.equals(DbTree.ID_DB_ID)) {
                DatabaseImpl db = env.getDbMapTree().getDb(dbId);
                try {
                    if (db != null) {
                        db.getTree().rebuildINList();
                    }
                } finally {
                    env.releaseDb(db);
                }
            }
        }
    }

    /* Struct to hold a nodeId/txnId tuple */
    private static class TxnNodeId {
        long nodeId;
        long txnId;
        
        TxnNodeId(long nodeId, long txnId) {
            this.nodeId = nodeId;
            this.txnId = txnId;
        }

        /**
         * Compare two TxnNodeId objects
         */
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof TxnNodeId)) {
                return false;
            }

            return ((((TxnNodeId) obj).txnId == txnId) &&
                    (((TxnNodeId) obj).nodeId == nodeId));
        }

        public int hashCode() {
            return (int) (txnId + nodeId);
        }

        public String toString() {
            return "txnId=" + txnId + "/nodeId=" + nodeId;
        }
    }

    /*
     * Tree manipulation methods.
     */

    /**
     * Recover an internal node. If inFromLog is:
     *       - not found, insert it in the appropriate location.
     *       - if found and there is a physical match (LSNs are the same)
     *         do nothing.
     *       - if found and there is a logical match (LSNs are different,
     *         another version of this IN is in place, replace the found node
     *         with the node read from the log only if the log version's
     *         LSN is greater.
     * InFromLog should be latched upon entering this method and it will
     * not be latched upon exiting.
     *
     * @param inFromLog - the new node to put in the tree.  The identifier key
     * and node id are used to find the existing version of the node.
     * @param logLsn - the location of log entry in in the log.
     * @param inLsn LSN of this in -- may not be the same as the log LSN if
     * the current entry is a BINDelta
     * @param requireExactMatch - true if we won't place this node in the tree
     * unless we find exactly that parent. Used for BINDeltas, where we want
     * to only apply the BINDelta to that exact node.
     */
    private void replaceOrInsert(DatabaseImpl db,
                                 IN inFromLog,
                                 long logLsn,
                                 long inLsn,
                                 boolean requireExactMatch)
        throws DatabaseException {

        List trackingList = null;
	boolean inFromLogLatchReleased = false;
        try {

            /*
             * We must know a priori if this node is the root. We can't infer
             * that status from a search of the existing tree, because
             * splitting the root is done by putting a node above the old root.
             * A search downward would incorrectly place the new root below the
             * existing tree.
             */
            if (inFromLog.isRoot()) {
                if (inFromLog.containsDuplicates()) {
                    replaceOrInsertDuplicateRoot(db, (DIN) inFromLog, logLsn);
                } else {
                    replaceOrInsertRoot(db, inFromLog, logLsn);
		    inFromLogLatchReleased = true;
                }
            } else {
                /*
                 * Look for a parent. The call to getParentNode unlatches node.
                 * Then place inFromLog in the tree if appropriate.
                 */
                trackingList = new ArrayList();
                replaceOrInsertChild(db, inFromLog, logLsn, inLsn,
                                     trackingList, requireExactMatch);
		inFromLogLatchReleased = true;
            }
        } catch (Exception e) {
            String trace = printTrackList(trackingList);
            Tracer.trace(db.getDbEnvironment(), "RecoveryManager",
                         "replaceOrInsert", " lsnFromLog:" +
                         DbLsn.getNoFormatString(logLsn) + " " + trace,
                         e);
            throw new DatabaseException("lsnFromLog=" +
                                        DbLsn.getNoFormatString(logLsn), e);
        } finally {
	    if (!inFromLogLatchReleased) {
		inFromLog.releaseLatchIfOwner();
	    }
        
            assert (LatchSupport.countLatchesHeld() == 0):
                LatchSupport.latchesHeldToString() +
                "LSN = " + DbLsn.toString(logLsn) +
                " inFromLog = " + inFromLog.getNodeId();

        }
    }

    /**
     * Dump a tracking list into a string.
     */
    private String printTrackList(List trackingList) {
        if (trackingList != null) {
            StringBuffer sb = new StringBuffer();
            Iterator iter = trackingList.iterator();
            sb.append("Trace list:");
            sb.append('\n');
            while (iter.hasNext()) {
                sb.append((TrackingInfo) iter.next());
                sb.append('\n');
            }
            return sb.toString();
        } else {
            return null;
        }
    }

    /**
     * Replay an IN delete. Remove an entry from an IN to reflect a reverse
     * split.
     */
    private void replayINDelete(DatabaseImpl db,
				long nodeId,
                                boolean containsDuplicates,
				byte[] mainKey,
				byte[] dupKey,
                                long logLsn)
        throws DatabaseException {

        boolean found = false;
        boolean deleted = false;
        Tree tree = db.getTree();
        SearchResult result = new SearchResult();

        try {
            /* Search for the parent of this target node. */
            result = db.getTree().getParentINForChildIN
                (nodeId,
                 containsDuplicates,
                 false, // do not stop at dup tree root
                 mainKey,
                 dupKey,
                 false, // requireExactMatch
                 false, // updateGeneration
                 -1,    // targetLevel
                 null,  // trackingList
                 true); // doFetch

            if (result.parent == null) {
                /* It's null -- we actually deleted the root. */
                tree.withRootLatchedExclusive(new RootDeleter(tree));
                DbTree dbTree = db.getDbEnvironment().getDbMapTree();
                dbTree.modifyDbRoot(db);
                traceRootDeletion(Level.FINE, db);
                deleted = true;
            } else if (result.exactParentFound) {
                /* Exact match was found -- delete the parent entry. */
                found = true;
                deleted = result.parent.deleteEntry(result.index, false);
            }
        } finally {
            if (result.parent != null) {
                result.parent.releaseLatch();
            }

            traceINDeleteReplay
                (nodeId, logLsn, found, deleted, result.index,
                 containsDuplicates);
        }
    }

    /*
     * RootDeleter lets us clear the rootIN within the root latch.
     */
    private static class RootDeleter implements WithRootLatched {
        Tree tree;
        RootDeleter(Tree tree) {
            this.tree = tree;
        }

        /**
         * @return true if the in-memory root was replaced.
         */
        public IN doWork(ChildReference root) 
            throws DatabaseException {

            tree.setRoot(null, false);
            return null;
        }
    }

    /**
     * If the root of this tree is null, use this IN from the log as a root.
     * Note that we should really also check the LSN of the mapLN, because
     * perhaps the root is null because it's been deleted. However, the replay
     * of all the LNs will end up adjusting the tree correctly.
     *
     * If there is a root, check if this IN is a different LSN and if so,
     * replace it.
     */
    private void replaceOrInsertRoot(DatabaseImpl db, IN inFromLog, long lsn)
        throws DatabaseException {

        boolean success = true;
        Tree tree = db.getTree();
        RootUpdater rootUpdater = new RootUpdater(tree, inFromLog, lsn);
        try {
            /* Run the root updater while the root latch is held. */
            tree.withRootLatchedExclusive(rootUpdater);

            /* Update the mapLN if necessary */
            if (rootUpdater.updateDone()) {
                EnvironmentImpl env = db.getDbEnvironment();
                env.getDbMapTree().modifyDbRoot(db);
            } 
        } catch (Exception e) {
            success = false;
            throw new DatabaseException("lsnFromLog=" +
                                        DbLsn.getNoFormatString(lsn),
                                        e);
        } finally {
            trace(detailedTraceLevel,
                  db, TRACE_ROOT_REPLACE, success, inFromLog,
                  lsn, 
                  null,
                  true,
                  rootUpdater.getReplaced(),
                  rootUpdater.getInserted(),
                  rootUpdater.getOriginalLsn(),
		  DbLsn.NULL_LSN,
		  -1);
        }
    }

    /*
     * RootUpdater lets us replace the tree root within the tree root latch.
     */
    private static class RootUpdater implements WithRootLatched {
        private Tree tree;
        private IN inFromLog;
        private long lsn = DbLsn.NULL_LSN;
        private boolean inserted = false;
        private boolean replaced = false;
        private long originalLsn = DbLsn.NULL_LSN;

        RootUpdater(Tree tree, IN inFromLog, long lsn) {
            this.tree = tree;
            this.inFromLog = inFromLog;
            this.lsn = lsn;
        }

        public IN doWork(ChildReference root) 
            throws DatabaseException {

            ChildReference newRoot =
		tree.makeRootChildReference(inFromLog, new byte[0], lsn);
            inFromLog.releaseLatch();

            if (root == null) {
                tree.setRoot(newRoot, false);
                inserted = true;
            } else {
                originalLsn = root.getLsn(); // for debugLog

                /* 
                 * The current in-memory root IN is older than the root IN from
                 * the log.
                 */
                if (DbLsn.compareTo(originalLsn, lsn) < 0) {
                    tree.setRoot(newRoot, false);
                    replaced = true;
                }
            }
            return null;
        }

        boolean updateDone() {
            return inserted || replaced;
        }

        boolean getInserted() {
            return inserted;
        }

        boolean getReplaced() {
            return replaced;
        }

        long getOriginalLsn() {
            return originalLsn;
        }
    }

    /**
     * Recover this root of a duplicate tree.
     */
    private void replaceOrInsertDuplicateRoot(DatabaseImpl db,
                                              DIN inFromLog,
                                              long lsn)
        throws DatabaseException {

        boolean found = true;
        boolean inserted = false;
        boolean replaced = false;
        long originalLsn = DbLsn.NULL_LSN;

        byte[] mainTreeKey = inFromLog.getMainTreeKey();
        IN parent = null;
        int index = -1;
        boolean success = false;
        try {

            /*
             * Allow splits since the parent BIN of this DIN may be full.
             * [#13435]
             */
            parent = db.getTree().searchSplitsAllowed
                (mainTreeKey, -1, true /*updateGeneration*/);
            assert parent instanceof BIN;

	    ChildReference newRef =
		new ChildReference(inFromLog, mainTreeKey, lsn);
	    index = parent.insertEntry1(newRef);
	    if ((index >= 0 &&
		 (index & IN.EXACT_MATCH) != 0)) {

		index &= ~IN.EXACT_MATCH;

		/* 
		 * Replace whatever's at this entry, whether it's an LN or an
		 * earlier root DIN as long as one of the following is true:
		 * 
		 * - the entry is known deleted
		 * - or the LSN is earlier than the one we've just read from
		 *     the log.
		 */
		if (parent.isEntryKnownDeleted(index)) {
		    /* Be sure to clear the known deleted bit. */
		    parent.setEntry(index, inFromLog, mainTreeKey,
				    lsn, (byte) 0);
		    replaced = true;
		} else {
		    originalLsn = parent.getLsn(index);
		    if (DbLsn.compareTo(originalLsn, lsn) < 0) {
			parent.setEntry(index, inFromLog, mainTreeKey, lsn,
					parent.getState(index));
			replaced = true;
		    }
		}
	    } else {
		found = false;
	    }
	    success = true;
        } finally {
            if (parent != null) {
                parent.releaseLatch();
            }
            trace(detailedTraceLevel,
                  db,
                  TRACE_DUP_ROOT_REPLACE, success, inFromLog,
                  lsn, parent, found,
                  replaced, inserted, originalLsn, DbLsn.NULL_LSN, index);
        }
    }

    private void replaceOrInsertChild(DatabaseImpl db,
                                      IN inFromLog,
                                      long logLsn,
                                      long inLsn,
                                      List trackingList,
                                      boolean requireExactMatch)
        throws DatabaseException {

        boolean inserted = false;
        boolean replaced = false;
        long originalLsn = DbLsn.NULL_LSN;
        boolean success = false;
        SearchResult result = new SearchResult();
        try {
            result = db.getTree().getParentINForChildIN
                (inFromLog,
                 requireExactMatch,
                 false, // updateGeneration
                 -1,    // targetLevel
                 trackingList);

            /*
             * Does inFromLog exist in this parent?
             *
             * 1. No possible parent -- skip this child. It's represented
             *    by a parent that's later in the log.
             * 2. No match, but a possible parent: don't insert, all nodes
             *    are logged in such a way that they must have a possible
             *    parent (#13501)
             * 3. physical match: (LSNs same) this LSN is already in place, 
             *                    do nothing.
             * 4. logical match: another version of this IN is in place.
             *                   Replace child with inFromLog if inFromLog's
             *                   LSN is greater.
             */
            if (result.parent == null) {
                return;  // case 1, no possible parent.
            }

            /* Get the key that will locate inFromLog in this parent. */
            if (result.index >= 0) {
                if (result.parent.getLsn(result.index) == logLsn) {
                    /* case 3: do nothing */

                } else {

                    /* 
                     * Not an exact physical match, now need to look at child.
                     */
                    if (result.exactParentFound) {
                        originalLsn = result.parent.getLsn(result.index);
                        
                        /* case 4: It's a logical match, replace */
                        if (DbLsn.compareTo(originalLsn, logLsn) < 0) {

                            /* 
			     * It's a logical match, replace. Put the child
			     * node reference into the parent, as well as the
			     * true LSN of the IN. (If this entry is a
			     * BINDelta, the node has been updated with all the
			     * deltas, but the LSN we want to put in should be
			     * the last full LSN, not the LSN of the BINDelta)
                             */
                            result.parent.updateEntry(result.index,
                                                      inFromLog,
                                                      inLsn);
                            replaced = true;
                        }
                    }
                    /* else case 2 */
                }
            } 
            /* else case 2 */

            success = true;
        } finally {
            if (result.parent != null) {
                result.parent.releaseLatch();
            }

            trace(detailedTraceLevel, db, 
                  TRACE_IN_REPLACE, success, inFromLog, 
                  logLsn, result.parent,
                  result.exactParentFound, replaced, inserted, 
                  originalLsn, DbLsn.NULL_LSN, result.index);
        }
    }

    /**
     * Redo a committed LN for recovery. 
     *
     * <pre>
     * log LN found  | logLSN > LSN | LN is deleted | action
     *   in tree     | in tree      |               |
     * --------------+--------------+---------------+------------------------
     *     Y         |    N         |    n/a        | no action
     * --------------+--------------+---------------+------------------------
     *     Y         |    Y         |     N         | replace w/log LSN
     * --------------+--------------+---------------+------------------------
     *     Y         |    Y         |     Y         | replace w/log LSN, put
     *               |              |               | on compressor queue
     * --------------+--------------+---------------+------------------------
     *     N         |    n/a       |     N         | insert into tree
     * --------------+--------------+---------------+------------------------
     *     N         |    n/a       |     Y         | no action
     * --------------+--------------+---------------+------------------------
     *
     * </pre>
     *
     * @param location holds state about the search in the tree. Passed
     *  in from the recovery manager to reduce objection creation overhead.
     * @param lnFromLog - the new node to put in the tree. 
     * @param mainKey is the key that navigates us through the main tree
     * @param dupTreeKey is the key that navigates us through the duplicate
     * tree 
     * @param logLsn is the LSN from the just-read log entry
     * @param info is a recovery stats object.
     * @return the LSN found in the tree, or NULL_LSN if not found.
     */
    private long redo(DatabaseImpl db,
		      TreeLocation location,
		      LN lnFromLog,
		      byte[] mainKey,
		      byte[] dupKey,
		      long logLsn,
		      RecoveryInfo info)
        throws DatabaseException {

        boolean found = false;
        boolean replaced = false;
        boolean inserted = false;
        boolean success = false;
        try {

            /*
             *  Find the BIN which is the parent of this LN.
             */
            location.reset();
            found = db.getTree().getParentBINForChildLN
                (location, mainKey, dupKey, lnFromLog,
                 true,  // splitsAllowed
                 false, // findDeletedEntries
                 true,  // searchDupTree
                 true); // updateGeneration

            if (!found && (location.bin == null)) {

                /* 
                 * There is no possible parent for this LN. This tree was
                 * probably compressed away.
                 */
                success = true;
                return DbLsn.NULL_LSN;
            }

            /*
             * Now we're at the parent for this LN, whether BIN, DBIN or DIN
             */
            if (lnFromLog.containsDuplicates()) {
                if (found) {

		    /* 
		     * This is a dupCountLN. It's ok if there's no DIN parent
		     * for it. [#11307].
		     */
		    DIN duplicateRoot = (DIN)
			location.bin.fetchTarget(location.index);
		    if (DbLsn.compareTo(logLsn, location.childLsn) >= 0) {
			/* DupCountLN needs replacing. */
			duplicateRoot.latch();
			duplicateRoot.updateDupCountLNRefAndNullTarget(logLsn);
			duplicateRoot.releaseLatch();
		    }
                }
            } else {
                if (found) {

                    /* 
                     * This LN is in the tree. See if it needs replacing.
                     */
                    info.lnFound++;

                    if (DbLsn.compareTo(logLsn, location.childLsn) > 0) { 
                        info.lnReplaced++;
                        replaced = true;

                        /* 
			 * Be sure to make the target null. We don't want this
			 * new LN resident, it will make recovery start
			 * dragging in the whole tree and will consume too much
			 * memory.
                         */
                        location.bin.updateEntry(location.index,
                                                 null,
                                                 logLsn);
                    }

                    /* 
                     * If the entry in the tree is deleted, put it on the
                     * compressor queue.  Set KnownDeleted to prevent fetching
                     * a cleaned LN.
                     */
                    if (DbLsn.compareTo(logLsn, location.childLsn) >= 0 &&
                        lnFromLog.isDeleted()) {
                        location.bin.setKnownDeletedLeaveTarget
                            (location.index);
                        byte[] deletedKey = location.bin.containsDuplicates() ?
                            dupKey : mainKey;

                        /* 
                         * In the case of SR 8984, the LN has no data and 
                         * therefore no valid delete key. Don't compress.
                         */
                        if (deletedKey != null) {
                            db.getDbEnvironment().addToCompressorQueue
                                (location.bin, 
                                 new Key(deletedKey),
                                 false); // don't wakeup compressor
                        }
                    }

                } else {
                    /*
                     * This LN is not in the tree. If it's not deleted, insert
                     * it.
                     */
                    info.lnNotFound++;
                    if (!lnFromLog.isDeleted()) {
                        info.lnInserted++;
                        inserted = true;
                        boolean insertOk =
			    insertRecovery(db, location, logLsn);
                        assert insertOk;
                    }
                }
            }
            success = true;
            return found ? location.childLsn : DbLsn.NULL_LSN;
        } finally {
            if (location.bin != null) {
                location.bin.releaseLatchIfOwner();
            }
            trace(detailedTraceLevel, db,
                  TRACE_LN_REDO, success, lnFromLog,
                  logLsn, location.bin, found,
                  replaced, inserted,
                  location.childLsn, DbLsn.NULL_LSN, location.index);
        }
    }

    /**
     * Undo the changes to this node. Here are the rules that govern the action
     * taken.
     *
     * <pre>
     *
     * found LN in  | abortLsn is | logLsn ==       | action taken
     *    tree      | null        | LSN in tree     | by undo
     * -------------+-------------+----------------------------------------
     *      Y       |     N       |      Y          | replace w/abort LSN
     * ------------ +-------------+-----------------+-----------------------
     *      Y       |     Y       |      Y          | remove from tree
     * ------------ +-------------+-----------------+-----------------------
     *      Y       |     N/A     |      N          | no action
     * ------------ +-------------+-----------------+-----------------------
     *      N       |     N/A     |    N/A          | no action (*)
     * (*) If this key is not present in the tree, this record doesn't
     * reflect the IN state of the tree and this log entry is not applicable.
     *
     * </pre>
     * @param location holds state about the search in the tree. Passed
     *  in from the recovery manager to reduce objection creation overhead.
     * @param lnFromLog - the new node to put in the tree. 
     * @param mainKey is the key that navigates us through the main tree
     * @param dupTreeKey is the key that navigates us through the duplicate
     *                   tree 
     * @param logLsn is the LSN from the just-read log entry
     * @param abortLsn gives us the location of the original version of the
     *                 node
     * @param info is a recovery stats object.
     */
    public static void undo(Level traceLevel,
                            DatabaseImpl db,
                            TreeLocation location,
                            LN lnFromLog,
                            byte[] mainKey,
                            byte[] dupKey,
                            long logLsn,
                            long abortLsn,
                            boolean abortKnownDeleted,
                            RecoveryInfo info,
                            boolean splitsAllowed)
        throws DatabaseException {

        boolean found = false;
        boolean replaced = false;
        boolean success = false;

        try {

            /*
             * Find the BIN which is the parent of this LN.
             */
            location.reset();
            found = db.getTree().getParentBINForChildLN
                (location, mainKey, dupKey, lnFromLog, splitsAllowed,
                 true,  // findDeletedEntries
                 false, // searchDupTree
                 true); // updateGeneration

            /*
             * Now we're at the rightful parent, whether BIN or DBIN.
             */
            if (lnFromLog.containsDuplicates()) {

                /* 
		 * This is a dupCountLN. It's ok if there's no DIN parent
		 * for it. [#11307].
		 */
                if (found) {
		    DIN duplicateRoot = (DIN)
			location.bin.fetchTarget(location.index);
                    duplicateRoot.latch();
                    try {
                        if (DbLsn.compareTo(logLsn, location.childLsn) == 0) {
                            /* DupCountLN needs replacing. */
                            duplicateRoot.
                                updateDupCountLNRefAndNullTarget(abortLsn);
                            replaced = true;
                        }
                    } finally {
                        duplicateRoot.releaseLatch();
                    }
		}
            } else {
                if (found) {
                    /* This LN is in the tree. See if it needs replacing. */
                    if (info != null) {
                        info.lnFound++;
                    }
		    boolean updateEntry =
			DbLsn.compareTo(logLsn, location.childLsn) == 0;
		    if (updateEntry) {
			if (abortLsn == DbLsn.NULL_LSN) {

			    /*
			     * To undo a node that was created by this txn,
			     * remove it.  If this entry is deleted, put it on
                             * the compressor queue.  Set KnownDeleted to
                             * prevent fetching a cleaned LN.
			     */
			    location.bin.
				setKnownDeletedLeaveTarget(location.index);
                            byte[] deletedKey =
                                location.bin.containsDuplicates() ?
                                dupKey : mainKey;
			    db.getDbEnvironment().addToCompressorQueue
				(location.bin,
                                 new Key(deletedKey),
                                 false); // don't wakeup compressor
                            
			} else {

			    /*
			     * Apply the log record by updating the in memory
			     * tree slot to contain the abort LSN and abort
			     * Known Deleted flag.
			     */
			    if (info != null) {
				info.lnReplaced++;
			    }
			    replaced = true;
			    location.bin.updateEntry(location.index,
						     null,
						     abortLsn);
			    if (abortKnownDeleted) {
				location.bin.setKnownDeleted(location.index);
			    } else {
				location.bin.clearKnownDeleted(location.index);
			    }
			}

                        /*
                         * We must clear the PendingDeleted flag for
                         * non-deleted entries.  Clear it unconditionally,
                         * since KnownDeleted will be set above for a deleted
                         * entry. [#12885]
                         */
                        location.bin.clearPendingDeleted(location.index);
		    }

                } else {

                    /* 
                     * This LN is not in the tree.  Just make a note of it.
                     */
                    if (info != null) {
                        info.lnNotFound++;
                    }
                }
            }

            success = true;
        } finally {
            /* 
             * Note that undo relies on the caller to unlatch the bin.  Not
             * ideal, done in order to support abort processing.
             */
            trace(traceLevel, db, TRACE_LN_UNDO, success, lnFromLog,
		  logLsn, location.bin, found, replaced, false,
		  location.childLsn, abortLsn, location.index);
        }
    }

    /**
     * Inserts a LN into the tree for recovery redo processing.  In this
     * case, we know we don't have to lock when checking child LNs for deleted
     * status (there can be no other thread running on this tree) and we don't
     * have to log the new entry. (it's in the log already)
     *
     * @param db
     * @param location this embodies the parent bin, the index, the key that
     * represents this entry in the bin.
     * @param logLsn LSN of this current ln
     * @param key to use when creating a new ChildReference object.
     * @return true if LN was inserted, false if it was a duplicate
     * duplicate or if an attempt was made to insert a duplicate when
     * allowDuplicates was false.
     */
    private static boolean insertRecovery(DatabaseImpl db,
                                          TreeLocation location,
                                          long logLsn) 
        throws DatabaseException {
        
        /* Make a child reference as a candidate for insertion. */
        ChildReference newLNRef =
	    new ChildReference(null, location.lnKey, logLsn);

        BIN parentBIN = location.bin;
        int entryIndex = parentBIN.insertEntry1(newLNRef);

        if ((entryIndex & IN.INSERT_SUCCESS) == 0) {

            /* 
	     * Entry may have been a duplicate. Insertion was not successful.
	     */
            entryIndex &= ~IN.EXACT_MATCH;

            boolean canOverwrite = false;
            if (parentBIN.isEntryKnownDeleted(entryIndex)) {
                canOverwrite = true;
            } else {

                /*
                 * Read the LN that's in this slot to check for deleted
                 * status.  No need to lock, since this is recovery.  If
                 * fetchTarget returns null, a deleted LN was cleaned.
                 */
                LN currentLN = (LN) parentBIN.fetchTarget(entryIndex);

                if (currentLN == null || currentLN.isDeleted()) {
                    canOverwrite = true;
                }

                /* 
		 * Evict the target again manually, to reduce memory
		 * consumption while the evictor is not running.
                 */
		parentBIN.updateEntry(entryIndex, null);
            }

            if (canOverwrite) {
                parentBIN.updateEntry(entryIndex, null, logLsn,
                                      location.lnKey);
                parentBIN.clearKnownDeleted(entryIndex);
                location.index = entryIndex;
                return true;
            } else {
                return false;
            }
        }
        location.index = entryIndex & ~IN.INSERT_SUCCESS;
        return true;
    }

    /**
     * Update file utilization info during redo.
     */
    private void redoUtilizationInfo(long logLsn,
				     long treeLsn,
                                     long abortLsn,
				     boolean abortKnownDeleted,
                                     int logEntrySize,
                                     byte[] key,
                                     LN ln,
				     TxnNodeId txnNodeId,
                                     Set countedAbortLsnNodes)
        throws DatabaseException {

        UtilizationTracker tracker = env.getUtilizationTracker();

        /*
         * If the LN is marked deleted and its LSN follows the FileSummaryLN
         * for its file, count it as obsolete.
         */
        if (ln.isDeleted()) {
            Long logFileNum = new Long(DbLsn.getFileNumber(logLsn));
            long fileSummaryLsn =
		DbLsn.longToLsn((Long) fileSummaryLsns.get(logFileNum));
            int cmpFsLsnToLogLsn =
		(fileSummaryLsn != DbLsn.NULL_LSN) ?
		DbLsn.compareTo(fileSummaryLsn, logLsn) : -1;
            if (cmpFsLsnToLogLsn < 0) {
                tracker.countObsoleteNode(logLsn, null, logEntrySize);
            }
        }

        /* Was the LN found in the tree? */
        if (treeLsn != DbLsn.NULL_LSN) {
            int cmpLogLsnToTreeLsn = DbLsn.compareTo(logLsn, treeLsn);

            /*
             * If the oldLsn and newLsn differ and the newLsn follows the
             * FileSummaryLN for the file of the oldLsn, count the oldLsn as
             * obsolete.
             */
            if (cmpLogLsnToTreeLsn != 0) {
                long newLsn = (cmpLogLsnToTreeLsn < 0) ? treeLsn : logLsn;
                long oldLsn = (cmpLogLsnToTreeLsn > 0) ? treeLsn : logLsn;
                Long oldLsnFile = new Long(DbLsn.getFileNumber(oldLsn));
		long oldFsLsn = 
		    DbLsn.longToLsn((Long) fileSummaryLsns.get(oldLsnFile));
                int cmpOldFsLsnToNewLsn =
		    (oldFsLsn != DbLsn.NULL_LSN) ?
		    DbLsn.compareTo(oldFsLsn, newLsn) : -1;
                if (cmpOldFsLsnToNewLsn < 0) {
                    /* Fetch the LN only if necessary and so configured. */
                    int oldSize = 0;
                    if (oldLsn == logLsn) {
                        oldSize = logEntrySize;
                    } else if (env.getCleaner().getFetchObsoleteSize()) {
                        try {
                            LN oldLn = (LN) env.getLogManager().get(oldLsn);
                            oldSize = oldLn.getLastLoggedSize();
                        } catch (LogFileNotFoundException e) {
                            /* Ignore errors if the file was cleaned. */
                        }
                    }
                    tracker.countObsoleteNode(oldLsn, null, oldSize);
                }
            }

            /*
             * If the logLsn is equal to or precedes the treeLsn and the entry
             * has an abortLsn that was not previously deleted, consider the
             * set of entries for the given node.  If the logLsn is the first
             * in the set that follows the FileSummaryLN of the abortLsn, count
             * the abortLsn as obsolete.
             */
            if (cmpLogLsnToTreeLsn <= 0 &&
                abortLsn != DbLsn.NULL_LSN &&
                !abortKnownDeleted &&
                !countedAbortLsnNodes.contains(txnNodeId)) {
                /* We have not counted this abortLsn yet. */
                Long abortFileNum = new Long(DbLsn.getFileNumber(abortLsn));
		long abortFsLsn = 
		    DbLsn.longToLsn((Long) fileSummaryLsns.get(abortFileNum));
                int cmpAbortFsLsnToLogLsn =
		    (abortFsLsn != DbLsn.NULL_LSN) ?
		    DbLsn.compareTo(abortFsLsn, logLsn) : -1;
                if (cmpAbortFsLsnToLogLsn < 0) {

                    /*
                     * logLsn follows the FileSummaryLN of the abortLsn.  The
                     * abortLsn is only an approximation of the prior LSN, so
                     * use inexact counting.  Since this is relatively rare,
                     * a zero entry size (use average size) is acceptable.
                     */
                    tracker.countObsoleteNodeInexact(abortLsn, null, 0);

                    /* Don't count this abortLsn (this node) again. */
                    countedAbortLsnNodes.add(txnNodeId);
                }
            }
        }
    }

    /**
     * Update file utilization info during recovery undo (not abort undo).
     */
    private void undoUtilizationInfo(LN ln,
				     long logLsn,
				     long abortLsn,
                                     boolean abortKnownDeleted,
                                     int logEntrySize,
                                     TxnNodeId txnNodeId,
                                     Map countedFileSummaries,
                                     Set countedAbortLsnNodes) {

        UtilizationTracker tracker = env.getUtilizationTracker();

        /* Compare the fileSummaryLsn to the logLsn. */
        Long logFileNum = new Long(DbLsn.getFileNumber(logLsn));
        long fileSummaryLsn = 
            DbLsn.longToLsn((Long) fileSummaryLsns.get(logFileNum));
        int cmpFsLsnToLogLsn = (fileSummaryLsn != DbLsn.NULL_LSN) ?
            DbLsn.compareTo(fileSummaryLsn, logLsn) : -1;

        /*
         * Count the logLsn as obsolete if it follows the FileSummaryLN for the
         * file of its Lsn.
         */
        if (cmpFsLsnToLogLsn < 0) {
            tracker.countObsoleteNode(logLsn, null, logEntrySize);
        }

        /*
         * Consider the latest LSN for the given node that precedes the
         * FileSummaryLN for the file of its LSN.  Count this LSN as obsolete
         * if it is not a deleted LN.
         */
        if (cmpFsLsnToLogLsn > 0) {
            Long countedFile = (Long) countedFileSummaries.get(txnNodeId);
            if (countedFile == null ||
                countedFile.longValue() > logFileNum.longValue()) {

                /*
                 * We encountered a new file number and the FsLsn follows the
                 * logLsn.
                 */
                if (!ln.isDeleted()) {
                    tracker.countObsoleteNode(logLsn, null, logEntrySize);
                }
                /* Don't count this file again. */
                countedFileSummaries.put(txnNodeId, logFileNum);
            }
        }
    }

    /**
     * Concoct a header for the recovery pass trace info.
     */
    private String passStartHeader(int passNum) {
        return "Recovery Pass " + passNum + " start: ";
    }

    /**
     * Concoct a header for the recovery pass trace info.
     */
    private String passEndHeader(int passNum, long start, long end) {
        return "Recovery Pass " + passNum + " end (" +
            (end-start) + "): ";
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled. This is used to
     * construct verbose trace messages for individual log entry processing.
     */
    private static void trace(Level level,
                              DatabaseImpl database,
                              String debugType,
                              boolean success,
                              Node node,
                              long logLsn,
                              IN parent,
                              boolean found,
                              boolean replaced,
                              boolean inserted,
                              long replacedLsn,
			      long abortLsn,
			      int index) {
        Logger logger = database.getDbEnvironment().getLogger();
        Level useLevel= level;
        if (!success) {
            useLevel = Level.SEVERE;
        }
        if (logger.isLoggable(useLevel)) {
            StringBuffer sb = new StringBuffer();
            sb.append(debugType);
            sb.append(" success=").append(success);
            sb.append(" node=");
            sb.append(node.getNodeId());
            sb.append(" lsn=");
            sb.append(DbLsn.getNoFormatString(logLsn));
            if (parent != null) {
                sb.append(" parent=").append(parent.getNodeId());
            }
            sb.append(" found=");
            sb.append(found);
            sb.append(" replaced=");
            sb.append(replaced);
            sb.append(" inserted=");
            sb.append(inserted);
            if (replacedLsn != DbLsn.NULL_LSN) {
                sb.append(" replacedLsn=");
                sb.append(DbLsn.getNoFormatString(replacedLsn));
            }
            if (abortLsn != DbLsn.NULL_LSN) {
                sb.append(" abortLsn=");
                sb.append(DbLsn.getNoFormatString(abortLsn));
            }
            sb.append(" index=").append(index);
            logger.log(useLevel, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceINDeleteReplay(long nodeId,
                                     long logLsn,
                                     boolean found, 
                                     boolean deleted,
                                     int index,
				     boolean isDuplicate) {
        Logger logger = env.getLogger();
        if (logger.isLoggable(detailedTraceLevel)) {
            StringBuffer sb = new StringBuffer();
            sb.append((isDuplicate) ?
		      TRACE_IN_DUPDEL_REPLAY :
		      TRACE_IN_DEL_REPLAY);
            sb.append(" node=").append(nodeId);
            sb.append(" lsn=").append(DbLsn.getNoFormatString(logLsn));
            sb.append(" found=").append(found);
            sb.append(" deleted=").append(deleted);
            sb.append(" index=").append(index);
            logger.log(detailedTraceLevel, sb.toString());
        }
    }

    private void traceAndThrowException(long badLsn,
					String method,
					Exception originalException) 
        throws DatabaseException {
        String badLsnString = DbLsn.getNoFormatString(badLsn);
        Tracer.trace(env,
                     "RecoveryManager",
                     method,
                     "last LSN = " + badLsnString,
                     originalException);
        throw new DatabaseException("last LSN=" + badLsnString, 
                                    originalException);
    }

    /**
     * Log trace information about root deletions, called by INCompressor and
     * recovery. 
     */
    public static void traceRootDeletion(Level level, DatabaseImpl database) {
        Logger logger = database.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_ROOT_DELETE);
            sb.append(" Dbid=").append(database.getId());
            logger.log(level, sb.toString());
        }
    }
}
