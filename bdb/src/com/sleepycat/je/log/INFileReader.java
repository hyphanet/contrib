/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INFileReader.java,v 1.52.2.3 2007/08/06 16:00:20 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.INContainingEntry;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.log.entry.NodeLogEntry;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.INDeleteInfo;
import com.sleepycat.je.tree.INDupDeleteInfo;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * INFileReader supports recovery by scanning log files during the IN rebuild
 * pass. It looks for internal nodes (all types), segregated by whether they
 * belong to the main tree or the duplicate trees.
 *
 * <p>This file reader can also be run in tracking mode to keep track of the
 * maximum node id, database id and txn id seen so those sequences can be
 * updated properly at recovery.  In this mode it also performs utilization
 * counting.  It is only run once in tracking mode per recovery, in the
 * first phase of recovery.</p>
 */
public class INFileReader extends FileReader {

    /* Information about the last entry seen. */
    private boolean lastEntryWasDelete;
    private boolean lastEntryWasDupDelete;
    private LogEntryType fromLogType;
    private boolean isProvisional;

    /* 
     * targetEntryMap maps DbLogEntryTypes to log entries. We use this
     * collection to find the right LogEntry instance to read in the
     * current entry.
     */
    private Map targetEntryMap;
    private LogEntry targetLogEntry;

    /*
     * For tracking non-target log entries.
     * Note that dbIdTrackingEntry and txnIdTrackingEntry do not overlap with
     * targetLogEntry, since the former are LNs and the latter are INs.
     * But nodeTrackingEntry and inTrackingEntry can overlap with the others,
     * and we only load one of them when they do overlap.
     */
    private Map dbIdTrackingMap;
    private LNLogEntry dbIdTrackingEntry;
    private Map txnIdTrackingMap;
    private LNLogEntry txnIdTrackingEntry;
    private Map otherNodeTrackingMap;
    private NodeLogEntry nodeTrackingEntry;
    private INLogEntry inTrackingEntry;
    private LNLogEntry fsTrackingEntry;

    /*
     * If trackIds is true, peruse all node entries for the maximum
     * node id, check all MapLNs for the maximum db id, and check all
     * LNs for the maximum txn id
     */
    private boolean trackIds;
    private long maxNodeId;
    private int maxDbId;
    private long maxTxnId;
    private boolean mapDbOnly;

    /* Used for utilization tracking. */
    private long partialCkptStart;
    private UtilizationTracker tracker;
    private Map fileSummaryLsns;

    /**
     * Create this reader to start at a given LSN.
     */
    public INFileReader(EnvironmentImpl env,
                        int readBufferSize, 
                        long startLsn,
                        long finishLsn,
                        boolean trackIds,
                        boolean mapDbOnly,
                        long partialCkptStart,
                        Map fileSummaryLsns)
        throws IOException, DatabaseException {

        super(env, readBufferSize, true, startLsn, null,
              DbLsn.NULL_LSN, finishLsn);

        this.trackIds = trackIds;
        this.mapDbOnly = mapDbOnly;
        targetEntryMap = new HashMap();

        if (trackIds) {
            maxNodeId = 0;
            maxDbId = 0;
            tracker = env.getUtilizationTracker();
            this.partialCkptStart = partialCkptStart;
            this.fileSummaryLsns = fileSummaryLsns;
            fsTrackingEntry = (LNLogEntry)
                LogEntryType.LOG_FILESUMMARYLN.getNewLogEntry();

            dbIdTrackingMap = new HashMap();
            txnIdTrackingMap = new HashMap();
            otherNodeTrackingMap = new HashMap();

            dbIdTrackingMap.put(LogEntryType.LOG_MAPLN_TRANSACTIONAL,
                                LogEntryType.LOG_MAPLN_TRANSACTIONAL.
                                getNewLogEntry());
            dbIdTrackingMap.put(LogEntryType.LOG_MAPLN,
                                LogEntryType.LOG_MAPLN.getNewLogEntry());
            txnIdTrackingMap.put(LogEntryType.LOG_LN_TRANSACTIONAL,
                                 LogEntryType.LOG_LN_TRANSACTIONAL.
                                 getNewLogEntry());
            txnIdTrackingMap.put(LogEntryType.LOG_MAPLN_TRANSACTIONAL,
                                 LogEntryType.LOG_MAPLN_TRANSACTIONAL.
                                 getNewLogEntry());
            txnIdTrackingMap.put(LogEntryType.LOG_NAMELN_TRANSACTIONAL,
                                 LogEntryType.LOG_NAMELN_TRANSACTIONAL.
                                 getNewLogEntry());
            txnIdTrackingMap.put(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL,
                                 LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL.
                                 getNewLogEntry());
            txnIdTrackingMap.put(LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL,
                                 LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL.
                                 getNewLogEntry());
        }
    }

    /**
     * Configure this reader to target this kind of entry.
     */
    public void addTargetType(LogEntryType entryType)
        throws DatabaseException {

        targetEntryMap.put(entryType, entryType.getNewLogEntry());
    }

    /** 
     * If we're tracking node, database and txn ids, we want to see all node
     * log entries. If not, we only want to see IN entries.
     * @return true if this is an IN entry.
     */
    protected boolean isTargetEntry(byte entryTypeNum,
                                    byte entryTypeVersion)
        throws DatabaseException {

        lastEntryWasDelete = false;
        lastEntryWasDupDelete = false;
        targetLogEntry = null;
        dbIdTrackingEntry = null;
        txnIdTrackingEntry = null;
        nodeTrackingEntry = null;
        inTrackingEntry = null;
        fsTrackingEntry = null;
        isProvisional = LogEntryType.isEntryProvisional(entryTypeVersion);

        /* Get the log entry type instance we need to read the entry. */
        fromLogType = LogEntryType.findType(entryTypeNum, entryTypeVersion);
        LogEntry possibleTarget = (LogEntry) targetEntryMap.get(fromLogType);

        /*
         * If the entry is provisional, we won't be reading it in its entirety;
         * otherwise, we try to establish targetLogEntry.
         */
        if (!isProvisional) {
            targetLogEntry = possibleTarget;
        }

        /* Was the log entry an IN deletion? */
        if (LogEntryType.LOG_IN_DELETE_INFO.equals(fromLogType)) {
            lastEntryWasDelete = true;
        }

        if (LogEntryType.LOG_IN_DUPDELETE_INFO.equals(fromLogType)) {
            lastEntryWasDupDelete = true;
        }

        if (trackIds) {

            /*
             * Check if it's a db or txn id tracking entry.  Note that these
             * entries do not overlap with targetLogEntry.
             */
            if (!isProvisional) {
                dbIdTrackingEntry = (LNLogEntry)
                    dbIdTrackingMap.get(fromLogType);
                txnIdTrackingEntry = (LNLogEntry)
                    txnIdTrackingMap.get(fromLogType);
            }

            /*
             * Determine nodeTrackingEntry, inTrackingEntry, fsTrackingEntry.
             * Note that these entries do overlap with targetLogEntry.
             */
            if (fromLogType.isNodeType()) {
                if (possibleTarget != null) {
                    nodeTrackingEntry = (NodeLogEntry) possibleTarget;
                } else if (dbIdTrackingEntry != null) {
                    nodeTrackingEntry = dbIdTrackingEntry;
                } else if (txnIdTrackingEntry != null) {
                    nodeTrackingEntry = txnIdTrackingEntry;
                } else {
                    nodeTrackingEntry = (NodeLogEntry)
                        otherNodeTrackingMap.get(fromLogType);
                    if (nodeTrackingEntry == null) {
                        nodeTrackingEntry = (NodeLogEntry)
                            fromLogType.getNewLogEntry();
                        otherNodeTrackingMap.put(fromLogType,
                                                 nodeTrackingEntry);
                    }
                }
                if (nodeTrackingEntry instanceof INLogEntry) {
                    inTrackingEntry = (INLogEntry) nodeTrackingEntry;
                }
                if (LogEntryType.LOG_FILESUMMARYLN.equals(fromLogType)) {
                    fsTrackingEntry = (LNLogEntry) nodeTrackingEntry;
                }
            }

            /*
             * Count all entries except for the file header as new.
             * UtilizationTracker does not count the file header.
             */
            if (!LogEntryType.LOG_FILE_HEADER.equals(fromLogType)) {
                tracker.countNewLogEntry(getLastLsn(), fromLogType,
                                         currentEntryHeader.getSize() +
                                         currentEntryHeader.getItemSize());
            }

            /*
             * Return true if this entry should be passed on to processEntry.
             * If we're tracking ids, return if this is a targeted entry
             * or if it's any kind of tracked entry or node.
             */
            return (targetLogEntry != null) ||
                (dbIdTrackingEntry != null) ||
                (txnIdTrackingEntry != null) ||
                (nodeTrackingEntry != null);
        } else {

            /*
             * Return true if this entry should be passed on to processEntry.
             * If we're not tracking ids, only return true if it's a targeted
             * entry.
             */
            return (targetLogEntry != null);
        }
    }

    /**
     * This reader looks at all nodes for the max node id and database id. It
     * only returns non-provisional INs and IN delete entries.
     */
    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        boolean useEntry = false;
        boolean entryLoaded = false;

        /* If this is a targetted entry, read the entire log entry. */
        if (targetLogEntry != null) {
            readEntry(targetLogEntry, entryBuffer, true); // readFullItem
            DatabaseId dbId = getDatabaseId();
            boolean isMapDb = dbId.equals(DbTree.ID_DB_ID);
            useEntry = (!mapDbOnly || isMapDb);
            entryLoaded = true;
        }
        
        /* Do a partial load during tracking if necessary. */
        if (trackIds) {

            /*
             * Do partial load of db and txn id tracking entries if necessary.
             * Note that these entries do not overlap with targetLogEntry.
             *
             * We're doing a full load for now, since LNLogEntry does not read
             * the db and txn id in a partial load, only the node id.
             */
            LNLogEntry lnEntry = null;
            if (dbIdTrackingEntry != null) {
                /* This entry has a db id */
                lnEntry = dbIdTrackingEntry;
                readEntry(lnEntry, entryBuffer, true); // readFullItem
                entryLoaded = true;
                MapLN mapLN = (MapLN) lnEntry.getMainItem();
                int dbId = mapLN.getDatabase().getId().getId();
                if (dbId > maxDbId) {
                    maxDbId = dbId;
                }
            }
            if (txnIdTrackingEntry != null) {
                /* This entry has a txn id */
                if (lnEntry == null) {
                    lnEntry = txnIdTrackingEntry;
                    readEntry(lnEntry, entryBuffer, true ); // readFullItem
                    entryLoaded = true;
                }
                long txnId = lnEntry.getTxnId().longValue();
                if (txnId > maxTxnId) {
                    maxTxnId = txnId;
                }
            }

            /*
             * Perform utilization counting under trackIds to prevent
             * double-counting.
             */
            if (fsTrackingEntry != null) {

                /* Must do full load to get key from file summary LN. */
                if (!entryLoaded) {
                    readEntry(nodeTrackingEntry, entryBuffer,
                              true); // readFullItem
                    entryLoaded = true;
                }

                /*
                 * When a FileSummaryLN is encountered, reset the tracked
                 * summary for that file to replay what happens when a
                 * FileSummaryLN log entry is written.
                 */
                byte[] keyBytes = fsTrackingEntry.getKey();
                FileSummaryLN fsln =
                    (FileSummaryLN) fsTrackingEntry.getMainItem();
                long fileNum = fsln.getFileNumber(keyBytes);
                TrackedFileSummary trackedLN = tracker.getTrackedFile(fileNum);
                if (trackedLN != null) {
                    trackedLN.reset();
                }

                /* Save the LSN of the FileSummaryLN for use by undo/redo. */
                fileSummaryLsns.put(new Long(fileNum), new Long(getLastLsn()));

                /*
                 * SR 10395: Do not cache the file summary in the
                 * UtilizationProfile here, since it may be for a deleted log
                 * file.
                 */
            }

            /*
             * Do partial load of nodeTrackingEntry (and inTrackingEntry) if
             * not already loaded.  We only need the node id.
             */
            if (nodeTrackingEntry != null) {
                if (!entryLoaded) {
                    readEntry(nodeTrackingEntry, entryBuffer,
                              false ); // readFullItem
                    entryLoaded = true;
                }
                /* Keep track of the largest node id seen. */
                long nodeId = nodeTrackingEntry.getNodeId();
                maxNodeId = (nodeId > maxNodeId) ? nodeId: maxNodeId;
            }

            if (inTrackingEntry != null) {
                assert entryLoaded : "All nodes should have been loaded";

                /*
                 * Count the obsolete LSN of the previous version, if available
                 * and if not already counted.  Use inexact counting for two
                 * reasons: 1) we don't always have the full LSN because
                 * earlier log versions only had the file number, and 2) we
                 * can't guarantee obsoleteness for provisional INs.
                 */
                long oldLsn = inTrackingEntry.getObsoleteLsn();
                if (oldLsn != DbLsn.NULL_LSN) {
                    long newLsn = getLastLsn();
                    if (!isObsoleteLsnAlreadyCounted(oldLsn, newLsn)) {
                        tracker.countObsoleteNodeInexact
                            (oldLsn, fromLogType, 0);
                    }
                }

                /*
                 * Count a provisional IN as obsolete if it follows
                 * partialCkptStart.  It cannot have been already counted,
                 * because provisional INs are not normally counted as
                 * obsolete; they are only considered obsolete when they are
                 * part of a partial checkpoint.
                 *
                 * Depending on the exact point at which the checkpoint was
                 * aborted, this technique is not always accurate; therefore
                 * inexact counting must be used.
                 */
                if (isProvisional && partialCkptStart != DbLsn.NULL_LSN) {
                    oldLsn = getLastLsn();
                    if (DbLsn.compareTo(partialCkptStart, oldLsn) < 0) {
                        tracker.countObsoleteNodeInexact
                            (oldLsn, fromLogType, 0);
                    }
                }
            }
        }

        /* Return true if this entry should be processed */
        return useEntry;
    }

    /**
     * Returns whether a given obsolete LSN has already been counted in the
     * utilization profile.  If true is returned, it should not be counted
     * again, to prevent double-counting.
     */
    private boolean isObsoleteLsnAlreadyCounted(long oldLsn, long newLsn) {

        /* If the file summary follows the new LSN, it was already counted. */
        Long fileNum = new Long(DbLsn.getFileNumber(oldLsn));
        long fileSummaryLsn =
            DbLsn.longToLsn((Long) fileSummaryLsns.get(fileNum));
        int cmpFsLsnToNewLsn = (fileSummaryLsn != DbLsn.NULL_LSN) ?
            DbLsn.compareTo(fileSummaryLsn, newLsn) : -1;
        return (cmpFsLsnToNewLsn >= 0);
    }

    /**
     * Get the last IN seen by the reader.
     */
    public IN getIN() 
        throws DatabaseException {
                
        return ((INContainingEntry) targetLogEntry).getIN(envImpl);
    }

    /**
     * Get the last databaseId seen by the reader.
     */
    public DatabaseId getDatabaseId() {
        if (lastEntryWasDelete) {
            return ((INDeleteInfo) targetLogEntry.getMainItem()).
                getDatabaseId();
        } else if (lastEntryWasDupDelete) {
            return ((INDupDeleteInfo) targetLogEntry.getMainItem()).
                getDatabaseId();
        } else {
            return ((INContainingEntry) targetLogEntry).getDbId();
        }
    }

    /**
     * Get the maximum node id seen by the reader.
     */
    public long getMaxNodeId() {
        return maxNodeId;
    }

    /**
     * Get the maximum db id seen by the reader.
     */
    public int getMaxDbId() {
        return maxDbId;
    }

    /**
     * Get the maximum txn id seen by the reader.
     */
    public long getMaxTxnId() {
        return maxTxnId;
    }

    /**
     * @return true if the last entry was a delete info entry.
     */
    public boolean isDeleteInfo() {
        return lastEntryWasDelete;
    }
    
    /**
     * @return true if the last entry was a dup delete info entry.
     */
    public boolean isDupDeleteInfo() {
        return lastEntryWasDupDelete;
    }
    
    /**
     * Get the deleted node id stored in the last delete info log entry.
     */
    public long getDeletedNodeId() {
        return ((INDeleteInfo)
                targetLogEntry.getMainItem()).getDeletedNodeId();
    }

    /**
     * Get the deleted id key stored in the last delete info log entry.
     */
    public byte[] getDeletedIdKey() {
        return ((INDeleteInfo)
                targetLogEntry.getMainItem()).getDeletedIdKey();
    }

    /**
     * Get the deleted node id stored in the last delete info log entry.
     */
    public long getDupDeletedNodeId() {
        return ((INDupDeleteInfo)
                targetLogEntry.getMainItem()).getDeletedNodeId();
    }

    /**
     * Get the deleted main key stored in the last delete info log entry.
     */
    public byte[] getDupDeletedMainKey() {
        return ((INDupDeleteInfo)
                targetLogEntry.getMainItem()).getDeletedMainKey();
    }

    /**
     * Get the deleted main key stored in the last delete info log entry.
     */
    public byte[] getDupDeletedDupKey() {
        return ((INDupDeleteInfo)
                targetLogEntry.getMainItem()).getDeletedDupKey();
    }

    /**
     * Get the LSN that should represent this IN. For most INs, it's the LSN
     * that was just read. For BINDelta entries, it's the LSN of the last
     * full version.
     */
    public long getLsnOfIN() {
        return ((INContainingEntry) targetLogEntry).getLsnOfIN(getLastLsn());
    }
}
