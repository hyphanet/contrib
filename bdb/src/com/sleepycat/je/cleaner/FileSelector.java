/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileSelector.java,v 1.15.2.2 2007/05/31 21:55:32 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.tree.LN;

/**
 * Keeps track of the status of files for which cleaning is in progres.
 */
public class FileSelector {

    /*
     * Each file for which cleaning is in progress is in one of the following
     * collections.  Files numbers migrate from one collection to another as
     * their status changes, in order:
     *
     * toBeCleaned -> beingCleaned -> cleaned ->
     * checkpointed -> fullyProcessed -> safeToDelete
     *
     * Access to these collections is synchronized to guarantee that the status
     * is atomically updated.
     */

    /*
     * A file is initially to-be-cleaned when it is selected as part of a batch
     * of files that, when deleted, will bring total utilization down to the
     * minimum configured value.  All files in this collection will be cleaned
     * in lowest-cost-to-clean order.  For two files of equal cost to clean,
     * the lower numbered (oldest) files is selected; this is why the set is
     * sorted.
     */
    private SortedSet toBeCleanedFiles;

    /*
     * When a file is selected for processing by FileProcessor from the
     * to-be-cleaned list, it is added to this processing set.  This
     * distinction is used to prevent a file from being processed by more than
     * one thread.
     */
    private Set beingCleanedFiles;

    /*
     * A file moves to the cleaned set when all log entries have been read and
     * processed.  However, entries needing migration will be marked with the
     * BIN entry MIGRATE flag, entries that could not be locked will be in the
     * pending LN set, and the DBs that were pending deletion will be in the
     * pending DB set.
     *
     * After processing a file, we may identify a set of deferred write dbs
     * which need a sync before we can safely delete the file. The
     * cleanedDeferredWriteDbs is the collecton of DatabaseIds for those dw
     * dbs, and goes lockstep with the cleanedFiles. It's implemented as a
     * list rather than a set, because a DW db may show up multiple times
     * for different files, unlike the files in the cleanedFiles set.
     * For example, suppose 
     *  - file 20 is cleaned and adds DW dbs 1,3
     *  - file 25 is cleaned and adds DW dbs 1,5
     *  - a checkpoint starts, and copies dw db 1,3,5
     *  - file 30 is cleaned, and adds DW dbs 1,5
     * When the ckpt returns and removes the proper entries from the list, 
     * it must make sure that dbs 1,5 stay on the list, because that sync 
     * must happen for file 30.
     */
    private Set cleanedFiles;
    private LinkedList cleanedDeferredWriteDbs;

    /*
     * A file moves to the checkpointed set at the end of a checkpoint if it
     * was in the cleaned set at the beginning of the checkpoint.  Because all
     * dirty BINs are flushed during the checkpoints, no files in this set
     * will have entries with the MIGRATE flag set.  However, some entries may
     * be in the pending LN set and some DBs may be in the pending DB set.
     */
    private Set checkpointedFiles;

    /*
     * A file is moved from the checkpointed set to the fully-processed set
     * when the pending LN/DB sets become empty.  Since a pending LN was not
     * locked successfully, we don't know its original file.  But we do know
     * that when no pending LNs are present for any file, all log entries in
     * checkpointed files are either obsolete or have been migrated.  Note,
     * however, that the parent BINs of the migrated entries may not have been
     * logged yet.
     * 
     * No special handling is required to coordinate syncing of deferred write
     * databases for pending, deferred write LNs. Note that although DW
     * databases are non-txnal, their LNs may be pended because of lock
     * collisions.  Any required syncing falls out naturally because dbs are
     * entered into the cleanedDeferredWriteDbs list when a member LN is
     * successfully processed and removed from the pendingLN set, whether that
     * happens on the first pass of processing the file, or on future passes
     * when the LN is removed from the pending set.
     */
    private Set fullyProcessedFiles;

    /*
     * A file moves to the safe-to-delete set at the end of a checkpoint if it
     * was in the fully-processed set at the beginning of the checkpoint.  All
     * parent BINs of migrated entries have now been logged, and any
     * deferred write db syncs have been executed and the files are
     * safe to delete.
     */
    private Set safeToDeleteFiles;

    /*
     * Pending LNs are stored in a map of {NodeID -> LNInfo}.  These are LNs
     * that could not be locked, either during processing or during migration.
     */
    private Map pendingLNs;

    /*
     * For processed entries with DBs that are pending deletion, we consider
     * them to be obsolete but we store their DatabaseIds in a set.  Until the
     * DB deletion is complete, we can't delete the log files containing those
     * entries.
     */
    private Set pendingDBs;

    /*
     * If during a checkpoint there are no pending LNs or DBs added, we can
     * move cleaned files to safe-delete files at the end of the checkpoint.
     * This is an optimization that allows deleting files more quickly when
     * possible. In particular this impacts the checkpoint during environment
     * close, since no user operations are active during that checkpoint; this
     * optimization allows us to delete all cleaned files after the final
     * checkpoint.
     */
    private boolean anyPendingDuringCheckpoint;

    /*
     * As a side effect of file selection a set of low utilization files is
     * determined.  This set is guaranteed to be non-null and read-only, so no
     * synchronization is needed when accessing it.
     */
    private Set lowUtilizationFiles;

    FileSelector() {
        toBeCleanedFiles = new TreeSet();
        cleanedFiles = new HashSet();
        cleanedDeferredWriteDbs = new LinkedList();
        checkpointedFiles = new HashSet();
        fullyProcessedFiles = new HashSet();
        safeToDeleteFiles = new HashSet();
        pendingLNs = new HashMap();
        pendingDBs = new HashSet();
        lowUtilizationFiles = Collections.EMPTY_SET;
        beingCleanedFiles = new HashSet();
    }

    /**
     * Returns the best file that qualifies for cleaning, or null if no file
     * qualifies.  This method is not thread safe and should only be called
     * from the cleaner thread.
     *
     * @param forceCleaning is true to always select a file, even if its
     * utilization is above the minimum utilization threshold.
     *
     * @param calcLowUtilizationFiles whether to recalculate the set of files
     * that are below the minimum utilization threshold.
     *
     * @param maxBatchFiles is the maximum number of files to be selected at
     * one time, or zero if there is no limit.
     *
     * @return the next file to be cleaned, or null if no file needs cleaning.
     */
    Long selectFileForCleaning(UtilizationProfile profile,
                               boolean forceCleaning,
                               boolean calcLowUtilizationFiles,
                               int maxBatchFiles)
        throws DatabaseException {

        Set newLowUtilizationFiles = calcLowUtilizationFiles ?
            (new HashSet()) : null;

        /*
         * Add files until we reach the theoretical minimum utilization
         * threshold.
         */
        while (true) {

            if (maxBatchFiles > 0) {
                synchronized (this) {
                    if (toBeCleanedFiles.size() >= maxBatchFiles) {
                        break;
                    }
                }
            }

            Long fileNum = profile.getBestFileForCleaning
                (this, forceCleaning, newLowUtilizationFiles);

            if (fileNum == null) {
                break;
            }

            synchronized (this) {
                toBeCleanedFiles.add(fileNum);
            }
        }

        /* Update the read-only set. */
        if (newLowUtilizationFiles != null) {
            lowUtilizationFiles = newLowUtilizationFiles;
        }

        /*
         * Select the cheapest file to clean from a copy of the to-be-cleaned
         * set.  Then move the file from the to-be-cleaned set to the
         * being-cleaned set.
         */
        SortedSet availableFiles;
        synchronized (this) {
            availableFiles = new TreeSet(toBeCleanedFiles);
        }
        Long file = profile.getCheapestFileToClean(availableFiles);
        if (file != null) {
            synchronized (this) {
                toBeCleanedFiles.remove(file);
                beingCleanedFiles.add(file);
            }
        }
        return file;
    }

    /**
     * Returns whether the file is in any stage of the cleaning process.
     */
    synchronized boolean isFileCleaningInProgress(Long file) {
        return toBeCleanedFiles.contains(file) ||
            beingCleanedFiles.contains(file) ||
            cleanedFiles.contains(file) ||
            checkpointedFiles.contains(file) ||
            fullyProcessedFiles.contains(file) ||
            safeToDeleteFiles.contains(file);
    }

    /**
     * Removes all references to a file.
     */
    synchronized void removeAllFileReferences(Long file) {
        toBeCleanedFiles.remove(file);
        beingCleanedFiles.remove(file);
        cleanedFiles.remove(file);
        checkpointedFiles.remove(file);
        fullyProcessedFiles.remove(file);
        safeToDeleteFiles.remove(file);
    }

    /**
     * When file cleaning is aborted, move the file back from the being-cleaned
     * set to the to-be-cleaned set.
     */
    synchronized void putBackFileForCleaning(Long fileNum) {
        toBeCleanedFiles.add(fileNum);
        beingCleanedFiles.remove(fileNum);
    }

    /**
     * When cleaning is complete, move the file from the being-cleaned set to
     * the cleaned set.
     */
    synchronized void addCleanedFile(Long fileNum, Set deferredWriteDbs) {
        cleanedFiles.add(fileNum);
        cleanedDeferredWriteDbs.addAll(deferredWriteDbs);
        beingCleanedFiles.remove(fileNum);
    }

    /**
     * Returns a read-only set of low utilization files that can be accessed
     * without synchronization.
     */
    Set getLowUtilizationFiles() {
        /* This set is read-only, so there is no need to make a copy. */
        return lowUtilizationFiles;
    }

    /**
     * Returns a read-only copy of to-be-cleaned and being-cleaned files that
     * can be accessed without synchronization.
     */
    synchronized Set getMustBeCleanedFiles() {
        Set set = new HashSet(toBeCleanedFiles);
        set.addAll(beingCleanedFiles);
        return set;
    }

    /**
     * Returns the number of files waiting to-be-cleaned.
     */
    synchronized int getBacklog() {
        return toBeCleanedFiles.size();
    }

    /**
     * Returns a copy of the cleaned and fully-processed files at the time a
     * checkpoint starts.
     */
    synchronized CheckpointStartCleanerState getFilesAtCheckpointStart() {

        anyPendingDuringCheckpoint = !pendingLNs.isEmpty() ||
            !pendingDBs.isEmpty();

        CheckpointStartCleanerState info = 
            new CheckpointStartCleanerState(cleanedFiles,
                                            fullyProcessedFiles,
                                            cleanedDeferredWriteDbs);
        return info;
    }

    /**
     * When a checkpoint is complete, move the previously cleaned and
     * fully-processed files to the checkpointed and safe-to-delete sets.
     * Also take the dbs that have been synced through this checkpoint off
     * their place at the top of the deferredWriteDb list
     */
    synchronized void updateFilesAtCheckpointEnd(
                     CheckpointStartCleanerState info) {

        if (!info.isEmpty()) {

            Set previouslyCleanedFiles = info.getCleanedFiles();
            if (previouslyCleanedFiles != null) {
                if (anyPendingDuringCheckpoint) {
                    checkpointedFiles.addAll(previouslyCleanedFiles);
                } else {
                    safeToDeleteFiles.addAll(previouslyCleanedFiles);
                }
                cleanedFiles.removeAll(previouslyCleanedFiles);
            }

            Set previouslyProcessedFiles =
                info.getFullyProcessedFiles();
            if (previouslyProcessedFiles != null) {
                safeToDeleteFiles.addAll(previouslyProcessedFiles);
                fullyProcessedFiles.removeAll(previouslyProcessedFiles);
            }

            int previousSize = cleanedDeferredWriteDbs.size();
            int numDbsSyncedByCheckpoint =
                info.getDeferredWriteDbsSize();
            for (int i = 0; i < numDbsSyncedByCheckpoint; i++) {
                cleanedDeferredWriteDbs.removeFirst();
            }

            assert cleanedDeferredWriteDbs.size() ==
                previousSize - numDbsSyncedByCheckpoint;

            updateProcessedFiles();
        }
    }

    /**
     * Adds the given LN info to the pending LN set.
     */
    synchronized boolean addPendingLN(LN ln, DatabaseId dbId,
                                      byte[] key, byte[] dupKey) {
        assert ln != null;

        boolean added = pendingLNs.put
            (new Long(ln.getNodeId()),
             new LNInfo(ln, dbId, key, dupKey)) != null;

        anyPendingDuringCheckpoint = true;
        return added;
    }

    /**
     * Returns an array of LNInfo for LNs that could not be migrated in a
     * prior cleaning attempt, or null if no LNs are pending.
     */
    synchronized LNInfo[] getPendingLNs() {

        if (pendingLNs.size() > 0) {
            LNInfo[] lns = new LNInfo[pendingLNs.size()];
            pendingLNs.values().toArray(lns);
            return lns;
        } else {
            return null;
        }
    }

    /**
     * Removes the LN for the given node ID from the pending LN set.
     */
    synchronized void removePendingLN(long nodeId) {

        pendingLNs.remove(new Long(nodeId));
        updateProcessedFiles();
    }

    /**
     * Adds the given DatabaseId to the pending DB set.
     */
    synchronized boolean addPendingDB(DatabaseId dbId) {

        boolean added = pendingDBs.add(dbId);

        anyPendingDuringCheckpoint = true;
        return added;
    }

    /**
     * Returns an array of DatabaseIds for DBs that were pending deletion in a
     * prior cleaning attempt, or null if no DBs are pending.
     */
    synchronized DatabaseId[] getPendingDBs() {

        if (pendingDBs.size() > 0) {
            DatabaseId[] dbs = new DatabaseId[pendingDBs.size()];
            pendingDBs.toArray(dbs);
            return dbs;
        } else {
            return null;
        }
    }

    /**
     * Removes the DatabaseId from the pending DB set.
     */
    synchronized void removePendingDB(DatabaseId dbId) {

        pendingDBs.remove(dbId);
        updateProcessedFiles();
    }

    /**
     * Returns a copy of the safe-to-delete files.
     */
    synchronized Set copySafeToDeleteFiles() {
        if (safeToDeleteFiles.size() == 0) {
            return null;
        } else {
            return new HashSet(safeToDeleteFiles);
        }
    }

    /**
     * Removes file from the safe-to-delete set after the file itself has
     * finally been deleted.
     */
    synchronized void removeDeletedFile(Long fileNum) {
        safeToDeleteFiles.remove(fileNum);
    }

    /**
     * If there are no pending LNs or DBs outstanding, move the checkpointed
     * files to the fully-processed set.  The check for pending LNs/DBs and the
     * copying of the checkpointed files must be done atomically in a
     * synchronized block.  All methods that call this method are synchronized.
     */
    private void updateProcessedFiles() {
        if (pendingLNs.isEmpty() && pendingDBs.isEmpty()) {
            fullyProcessedFiles.addAll(checkpointedFiles);
            checkpointedFiles.clear();
        }
    }

    /*
     * Holds copy of all checkpoint-dependent cleaner state.
     */
    static public class CheckpointStartCleanerState {

        /* A snapshot of the cleaner collections at the checkpoint start. */
        private Set cleanedFiles;
        private Set fullyProcessedFiles;
        private Set deferredWriteDbs;
        
        CheckpointStartCleanerState(Set cleanedFiles,
                                    Set fullyProcessedFiles,
                                    List cleanedDeferredWriteDbs) {

            /* 
             * Create snapshots of the collections of various files at the
             * beginning of the checkpoint.
             */
            this.cleanedFiles = new HashSet(cleanedFiles);
            this.fullyProcessedFiles = new HashSet(fullyProcessedFiles);
            deferredWriteDbs = new HashSet(cleanedDeferredWriteDbs);
        }

        public boolean isEmpty() {
            return ((cleanedFiles.size() == 0) &&
                    (fullyProcessedFiles.size() == 0) &&
                    (deferredWriteDbs.size() == 0));
        }

        public Set getCleanedFiles() {
            return cleanedFiles;
        }

        public Set getFullyProcessedFiles() {
            return fullyProcessedFiles;
        }
                
        public Set getDeferredWriteDbs() {
            return deferredWriteDbs;
        }
                                  
        public int getDeferredWriteDbsSize() {
            return deferredWriteDbs.size();
        }
    }
}
