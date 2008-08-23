/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileSelector.java,v 1.24.2.1 2008/08/04 21:43:30 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.MemoryBudget;
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
    private SortedSet<Long> toBeCleanedFiles;

    /*
     * When a file is selected for processing by FileProcessor from the
     * to-be-cleaned list, it is added to this processing set.  This
     * distinction is used to prevent a file from being processed by more than
     * one thread.
     */
    private Set<Long> beingCleanedFiles;

    /*
     * A file moves to the cleaned set when all log entries have been read and
     * processed.  However, entries needing migration will be marked with the
     * BIN entry MIGRATE flag, entries that could not be locked will be in the
     * pending LN set, and the DBs that were pending deletion will be in the
     * pending DB set.
     */
    private Set<Long> cleanedFiles;
    private Map<Long,Set<DatabaseId>> cleanedFilesDatabases;

    /*
     * A file moves to the checkpointed set at the end of a checkpoint if it
     * was in the cleaned set at the beginning of the checkpoint.  Because all
     * dirty BINs are flushed during the checkpoints, no files in this set
     * will have entries with the MIGRATE flag set.  However, some entries may
     * be in the pending LN set and some DBs may be in the pending DB set.
     */
    private Set<Long> checkpointedFiles;

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
     * databases for pending, deferred write LNs, because non-temporary
     * deferred write DBs are always synced during checkpoints, and temporary
     * deferred write DBs are not recovered.  Note that although DW databases
     * are non-txnal, their LNs may be pended because of lock collisions.
     */
    private Set<Long> fullyProcessedFiles;

    /*
     * A file moves to the safe-to-delete set at the end of a checkpoint if it
     * was in the fully-processed set at the beginning of the checkpoint.  All
     * parent BINs of migrated entries have now been logged.
     */
    private Set<Long> safeToDeleteFiles;

    /*
     * Pending LNs are stored in a map of {NodeID -> LNInfo}.  These are LNs
     * that could not be locked, either during processing or during migration.
     */
    private Map<Long,LNInfo> pendingLNs;

    /*
     * For processed entries with DBs that are pending deletion, we consider
     * them to be obsolete but we store their DatabaseIds in a set.  Until the
     * DB deletion is complete, we can't delete the log files containing those
     * entries.
     */
    private Set<DatabaseId> pendingDBs;

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
    private Set<Long> lowUtilizationFiles;

    FileSelector() {
        toBeCleanedFiles = new TreeSet<Long>();
        cleanedFiles = new HashSet<Long>();
        cleanedFilesDatabases = new HashMap<Long,Set<DatabaseId>>();
        checkpointedFiles = new HashSet<Long>();
        fullyProcessedFiles = new HashSet<Long>();
        safeToDeleteFiles = new HashSet<Long>();
        pendingLNs = new HashMap<Long,LNInfo>();
        pendingDBs = new HashSet<DatabaseId>();
        lowUtilizationFiles = Collections.emptySet();
        beingCleanedFiles = new HashSet<Long>();
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

        Set<Long> newLowUtilizationFiles = calcLowUtilizationFiles ?
            (new HashSet<Long>()) : null;

        /*
         * Add files until we reach the theoretical minimum utilization
         * threshold.
         */
        while (true) {

            int toBeCleanedSize;
            synchronized (this) {
                toBeCleanedSize = toBeCleanedFiles.size();
            }
            if (maxBatchFiles > 0 &&
                toBeCleanedSize >= maxBatchFiles) {
                break;
            }

            Long fileNum = profile.getBestFileForCleaning
                (this, forceCleaning, newLowUtilizationFiles,
                 toBeCleanedSize > 0 /*isBacklog*/);

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
        SortedSet<Long> availableFiles;
        synchronized (this) {
            availableFiles = new TreeSet<Long>(toBeCleanedFiles);
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
    synchronized void removeAllFileReferences(Long file, MemoryBudget budget) {
        toBeCleanedFiles.remove(file);
        beingCleanedFiles.remove(file);
        cleanedFiles.remove(file);
        Set<DatabaseId> oldDatabases = cleanedFilesDatabases.remove(file);
        adjustMemoryBudget(budget, oldDatabases, null /*newDatabases*/);
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
    synchronized void addCleanedFile(Long fileNum,
                                     Set<DatabaseId> databases,
                                     MemoryBudget budget) {
        cleanedFiles.add(fileNum);
        Set<DatabaseId> oldDatabases =
            cleanedFilesDatabases.put(fileNum, databases);
        adjustMemoryBudget(budget, oldDatabases, databases);
        beingCleanedFiles.remove(fileNum);
    }

    /**
     * Returns a read-only set of low utilization files that can be accessed
     * without synchronization.
     */
    Set<Long> getLowUtilizationFiles() {
        /* This set is read-only, so there is no need to make a copy. */
        return lowUtilizationFiles;
    }

    /**
     * Returns a read-only copy of to-be-cleaned and being-cleaned files that
     * can be accessed without synchronization.
     */
    synchronized Set<Long> getMustBeCleanedFiles() {
        Set<Long> set = new HashSet<Long>(toBeCleanedFiles);
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

        CheckpointStartCleanerState info = new CheckpointStartCleanerState
            (cleanedFiles, fullyProcessedFiles);
        return info;
    }

    /**
     * When a checkpoint is complete, move the previously cleaned and
     * fully-processed files to the checkpointed and safe-to-delete sets.
     */
    synchronized void updateFilesAtCheckpointEnd(
                     CheckpointStartCleanerState info) {

        if (!info.isEmpty()) {

            Set<Long> previouslyCleanedFiles = info.getCleanedFiles();
            if (previouslyCleanedFiles != null) {
                if (anyPendingDuringCheckpoint) {
                    checkpointedFiles.addAll(previouslyCleanedFiles);
                } else {
                    safeToDeleteFiles.addAll(previouslyCleanedFiles);
                }
                cleanedFiles.removeAll(previouslyCleanedFiles);
            }

            Set<Long> previouslyProcessedFiles =
                info.getFullyProcessedFiles();
            if (previouslyProcessedFiles != null) {
                safeToDeleteFiles.addAll(previouslyProcessedFiles);
                fullyProcessedFiles.removeAll(previouslyProcessedFiles);
            }

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
            (Long.valueOf(ln.getNodeId()),
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

        pendingLNs.remove(nodeId);
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
    synchronized Set<Long> copySafeToDeleteFiles() {
        if (safeToDeleteFiles.size() == 0) {
            return null;
        } else {
            return new HashSet<Long>(safeToDeleteFiles);
        }
    }

    /**
     * Returns a copy of the databases for a cleaned file.
     */
    synchronized Set<DatabaseId> getCleanedDatabases(Long fileNum) {
        return new HashSet<DatabaseId>(cleanedFilesDatabases.get(fileNum));
    }

    /**
     * Removes file from the safe-to-delete set after the file itself has
     * finally been deleted.
     */
    synchronized void removeDeletedFile(Long fileNum, MemoryBudget budget) {
        safeToDeleteFiles.remove(fileNum);
        Set<DatabaseId> oldDatabases = cleanedFilesDatabases.remove(fileNum);
        adjustMemoryBudget(budget, oldDatabases, null /*newDatabases*/);
    }

    /**
     * Update memory budgets when the environment is closed and will never be
     * accessed again.
     */
    synchronized void close(MemoryBudget budget) {
        for (Set<DatabaseId> oldDatabases : cleanedFilesDatabases.values()) {
            adjustMemoryBudget(budget, oldDatabases, null /*newDatabases*/);
        }
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

    /**
     * Adjust the memory budget when an entry is added to or removed from the
     * cleanedFilesDatabases map.
     */
    private void adjustMemoryBudget(MemoryBudget budget,
                                    Set<DatabaseId> oldDatabases,
                                    Set<DatabaseId> newDatabases) {
        long adjustMem = 0;
        if (oldDatabases != null) {
            adjustMem -= getCleanedFilesDatabaseEntrySize(oldDatabases);
        }
        if (newDatabases != null) {
            adjustMem += getCleanedFilesDatabaseEntrySize(newDatabases);
        }
        budget.updateAdminMemoryUsage(adjustMem);
    }

    /**
     * Returns the size of a HashMap entry that contains the given set of
     * DatabaseIds.  We don't count the DatabaseId size because it is likely
     * that it is also stored (and budgeted) in the DatabaseImpl.
     */
    private long getCleanedFilesDatabaseEntrySize(Set<DatabaseId> databases) {
        return MemoryBudget.HASHMAP_ENTRY_OVERHEAD +
               MemoryBudget.HASHSET_OVERHEAD +
               (databases.size() * MemoryBudget.HASHSET_ENTRY_OVERHEAD);
    }

    /**
     * Holds copy of all checkpoint-dependent cleaner state.
     */
    public static class CheckpointStartCleanerState {

        /* A snapshot of the cleaner collections at the checkpoint start. */
        private Set<Long> cleanedFiles;
        private Set<Long> fullyProcessedFiles;

        CheckpointStartCleanerState(Set<Long> cleanedFiles,
                                    Set<Long> fullyProcessedFiles) {

            /*
             * Create snapshots of the collections of various files at the
             * beginning of the checkpoint.
             */
            this.cleanedFiles = new HashSet<Long>(cleanedFiles);
            this.fullyProcessedFiles = new HashSet<Long>(fullyProcessedFiles);
        }

        public boolean isEmpty() {
            return ((cleanedFiles.size() == 0) &&
                    (fullyProcessedFiles.size() == 0));
        }

        public Set<Long> getCleanedFiles() {
            return cleanedFiles;
        }

        public Set<Long> getFullyProcessedFiles() {
            return fullyProcessedFiles;
        }
    }
}
