/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStats.java,v 1.58 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;
import java.text.DecimalFormat;

import com.sleepycat.je.utilint.DbLsn;

/**
 * System wide statistics for a single environment.
 */
public class EnvironmentStats implements Serializable {

    /* INCompressor */

    /**
     * The number of bins encountered by the INCompressor that were split
     * between the time they were put on the compressor queue and when the
     * compressor ran.
     */
    private long splitBins;

    /**
     * The number of bins encountered by the INCompressor that had their
     * database closed between the time they were put on the compressor queue
     * and when the compressor ran.
     */
    private long dbClosedBins;

    /**
     * The number of bins encountered by the INCompressor that had cursors
     * referring to them when the compressor ran.
     */
    private long cursorsBins;

    /**
     * The number of bins encountered by the INCompressor that were not
     * actually empty when the compressor ran.
     */
    private long nonEmptyBins;

    /**
     * The number of bins that were successfully processed by the IN
     * Compressor.
     */
    private long processedBins;

    /**
     * The number of entries in the INCompressor queue when the getStats() call
     * was made.
     */
    private long inCompQueueSize;

    /* Evictor */

    /**
     * The number of passes made to the evictor.
     */
    private long nEvictPasses;

    /**
     * The accumulated number of nodes selected to evict.
     */
    private long nNodesSelected;

    /**
     * The accumulated number of nodes scanned in order to select the eviction
     * set.
     */
    private long nNodesScanned;

    /**
     * The accumulated number of nodes evicted.
     */
    private long nNodesExplicitlyEvicted;

    /**
     * The accumulated number of database root nodes evicted.
     */
    private long nRootNodesEvicted;

    /**
     * The number of BINs stripped by the evictor.
     */
    private long nBINsStripped;

    /**
     * The number of bytes we need to evict in order to get under budget.
     */
    private long requiredEvictBytes;

    /* Checkpointer */

    /**
     * The total number of checkpoints run so far.
     */
    private long nCheckpoints;

    /**
     * The Id of the last checkpoint.
     */
    private long lastCheckpointId;

    /**
     * The accumulated number of full INs flushed to the log.
     */
    private long nFullINFlush;

    /**
     * The accumulated number of full BINs flushed to the log.
     */
    private long nFullBINFlush;

    /**
     * The accumulated number of Delta INs flushed to the log.
     */
    private long nDeltaINFlush;

    /**
     * The location in the log of the last checkpoint start.
     */
    private long lastCheckpointStart;

    /**
     * The location in the log of the last checkpoint end.
     */
    private long lastCheckpointEnd;

    /**
     * The location of the next entry to be written to the log.
     */
    private long endOfLog;

    /* Cleaner */

    /** The number of files to be cleaned to reach the target utilization. */
    private int cleanerBacklog;

    /** The number of cleaner runs this session. */
    private long nCleanerRuns;

    /** The number of cleaner file deletions this session. */
    private long nCleanerDeletions;

    /**
     * The accumulated number of INs obsolete.
     */
    private long nINsObsolete;

    /**
     * The accumulated number of INs cleaned.
     */
    private long nINsCleaned;

    /**
     * The accumulated number of INs that were not found in the tree anymore
     * (deleted).
     */
    private long nINsDead;

    /**
     * The accumulated number of INs migrated.
     */
    private long nINsMigrated;

    /**
     * The accumulated number of LNs obsolete.
     */
    private long nLNsObsolete;

    /**
     * The accumulated number of LNs cleaned.
     */
    private long nLNsCleaned;

    /**
     * The accumulated number of LNs that were not found in the tree anymore
     * (deleted).
     */
    private long nLNsDead;

    /**
     * The accumulated number of LNs encountered that were locked.
     */
    private long nLNsLocked;

    /**
     * The accumulated number of LNs encountered that were migrated forward
     * in the log.
     */
    private long nLNsMigrated;

    /**
     * The accumulated number of LNs that were marked for migration during
     * cleaning.
     */
    private long nLNsMarked;

    /**
     * The accumulated number of LNs processed without a tree lookup.
     */
    private long nLNQueueHits;

    /**
     * The accumulated number of LNs processed because they were previously
     * locked.
     */
    private long nPendingLNsProcessed;

    /**
     * The accumulated number of LNs processed because they were previously
     * marked for migration.
     */
    private long nMarkedLNsProcessed;

    /**
     * The accumulated number of LNs processed because they are soon to be
     * cleaned.
     */
    private long nToBeCleanedLNsProcessed;

    /**
     * The accumulated number of LNs processed because they qualify for
     * clustering.
     */
    private long nClusterLNsProcessed;

    /**
     * The accumulated number of pending LNs that could not be locked for
     * migration because of a long duration application lock.
     */
    private long nPendingLNsLocked;

    /**
     * The accumulated number of log entries read by the cleaner.
     */
    private long nCleanerEntriesRead;

    /*
     * Cache
     */
    private int nSharedCacheEnvironments; // num of envs sharing the cache
    private long sharedCacheTotalBytes;   // shared cache consumed, in bytes
    private long cacheTotalBytes; // local cache consumed, in bytes
    private long bufferBytes;  // cache consumed by the log buffers, in bytes
    private long dataBytes;    // cache consumed by the Btree, in bytes
    private long adminBytes;   // part of cache used by log cleaner metadata,
                               // and other administrative structures
    private long lockBytes;    // part of cache used by locks and txns
    private long nNotResident; // had to be instantiated from an LSN
    private long nCacheMiss;   // had to retrieve from disk
    private int  nLogBuffers;  // number of existing log buffers

    /*
     * Random vs Sequential IO and byte counts.
     */
    private long nRandomReads;
    private long nRandomWrites;
    private long nSequentialReads;
    private long nSequentialWrites;
    private long nRandomReadBytes;
    private long nRandomWriteBytes;
    private long nSequentialReadBytes;
    private long nSequentialWriteBytes;

    /*
     * Log activity
     */
    private long nFSyncs;   // Number of fsyncs issued. May be less than
                              // nFSyncRequests because of group commit
    private long nFSyncRequests; // Number of fsyncs requested.
    private long nFSyncTimeouts; // Number of group fsync requests that
                                   // turned into singleton fsyncs.
    /*
     * Number of reads which had to be repeated when faulting in an object from
     * disk because the read chunk size controlled by je.log.faultReadSize is
     * too small.
     */
    private long nRepeatFaultReads;

    /*
     * Number of times we have to use the temporary marshalling buffer to write
     * to the log.
     */
    private long nTempBufferWrites;

    /*
     * Number of times we try to read a log entry larger than the read buffer
     * size and can't grow the log buffer to accomodate the large object. This
     * happens during scans of the log during activities like environment open
     * or log cleaning. Implies that the the read chunk size controlled by
     * je.log.iteratorReadSize is too small.
     */
    private long nRepeatIteratorReads;

    /* FileManager open file cache stats. */
    private int nFileOpens;
    private int nOpenFiles;

    /*
     * Approximation of the total log size in bytes.
     */
    private long totalLogSize;

    /**
     * @hidden
     * Internal use only.
     */
    public EnvironmentStats() {
        reset();
    }

    /**
     * Resets all stats.
     */
    private void reset() {
        // InCompressor
        splitBins = 0;
        dbClosedBins = 0;
        cursorsBins = 0;
        nonEmptyBins = 0;
        processedBins = 0;
        inCompQueueSize = 0;

        // Evictor
        nEvictPasses = 0;
        nNodesSelected = 0;
        nNodesScanned = 0;
        nNodesExplicitlyEvicted = 0;
        nRootNodesEvicted = 0;
        nBINsStripped = 0;
        requiredEvictBytes = 0;

        // Checkpointer
        nCheckpoints = 0;
        lastCheckpointId = 0;
        nFullINFlush = 0;
        nFullBINFlush = 0;
        nDeltaINFlush = 0;
        lastCheckpointStart = DbLsn.NULL_LSN;
        lastCheckpointEnd = DbLsn.NULL_LSN;
	endOfLog = DbLsn.NULL_LSN;

        // Cleaner
        cleanerBacklog = 0;
        nCleanerRuns = 0;
        nCleanerDeletions = 0;
        nINsObsolete = 0;
        nINsCleaned = 0;
        nINsDead = 0;
        nINsMigrated = 0;
        nLNsObsolete = 0;
        nLNsCleaned = 0;
        nLNsDead = 0;
        nLNsLocked = 0;
        nLNsMigrated = 0;
        nLNsMarked = 0;
        nLNQueueHits = 0;
        nPendingLNsProcessed = 0;
        nMarkedLNsProcessed = 0;
        nToBeCleanedLNsProcessed = 0;
        nClusterLNsProcessed = 0;
        nPendingLNsLocked = 0;
        nCleanerEntriesRead = 0;

        // Cache
        nSharedCacheEnvironments = 0;
        sharedCacheTotalBytes = 0;
        cacheTotalBytes = 0;
        nNotResident = 0;
        nCacheMiss = 0;
        nLogBuffers = 0;
        bufferBytes = 0;

        // IO
        nRandomReads = 0;
        nRandomWrites = 0;
        nSequentialReads = 0;
        nSequentialWrites = 0;
        nRandomReadBytes = 0;
        nRandomWriteBytes = 0;
        nSequentialReadBytes = 0;
        nSequentialWriteBytes = 0;

        // Log
        nFSyncs = 0;
        nFSyncRequests = 0;
        nFSyncTimeouts = 0;
        nRepeatFaultReads = 0;
	nTempBufferWrites = 0;
        nRepeatIteratorReads = 0;
        nFileOpens = 0;
        nOpenFiles = 0;
        totalLogSize = 0;
    }

    /**
     * The number of bins encountered by the INCompressor that had cursors
     * referring to them when the compressor ran.
     */
    public long getCursorsBins() {
        return cursorsBins;
    }

    /**
     * The number of bins encountered by the INCompressor that had their
     * database closed between the time they were put on the compressor queue
     * and when the compressor ran.
     */
    public long getDbClosedBins() {
        return dbClosedBins;
    }

    /**
     * The number of entries in the INCompressor queue when the getStats()
     * call was made.
     */
    public long getInCompQueueSize() {
        return inCompQueueSize;
    }

    /**
     * The Id of the last checkpoint.
     */
    public long getLastCheckpointId() {
        return lastCheckpointId;
    }

    /**
     * The total number of requests for database objects which were not in
     * memory.
     */
    public long getNCacheMiss() {
        return nCacheMiss;
    }

    /**
     * The total number of checkpoints run so far.
     */
    public long getNCheckpoints() {
        return nCheckpoints;
    }

    /**
     * The number of files to be cleaned to reach the target utilization.
     */
    public int getCleanerBacklog() {
        return cleanerBacklog;
    }

    /**
     * The number of cleaner runs this session.
     */
    public long getNCleanerRuns() {
        return nCleanerRuns;
    }

    /**
     * The number of cleaner file deletions this session.
     */
    public long getNCleanerDeletions() {
        return nCleanerDeletions;
    }

    /**
     * The accumulated number of Delta INs flushed to the log.
     */
    public long getNDeltaINFlush() {
        return nDeltaINFlush;
    }

    /**
     * The location in the log of the last checkpoint end.
     */
    public long getLastCheckpointEnd() {
        return lastCheckpointEnd;
    }

    /**
     * The location of the next entry to be written to the log.
     *
     * <p>Note that the log entries prior to this position may not yet have
     * been flushed to disk.  Flushing can be forced using a Sync or
     * WriteNoSync commit, or a checkpoint.</p>
     */
    public long getEndOfLog() {
        return endOfLog;
    }

    /**
     * The location in the log of the last checkpoint start.
     */
    public long getLastCheckpointStart() {
        return lastCheckpointStart;
    }

    /**
     * The accumulated number of log entries read by the cleaner.
     */
    public long getNCleanerEntriesRead() {
        return nCleanerEntriesRead;
    }

    /**
     * The number of passes made to the evictor.
     */
    public long getNEvictPasses() {
        return nEvictPasses;
    }

    /**
     * The number of fsyncs issued through the group commit manager.
     */
    public long getNFSyncs() {
        return nFSyncs;
    }

    /**
     * The number of fsyncs requested through the group commit manager.
     */
    public long getNFSyncRequests() {
        return nFSyncRequests;
    }

    /**
     * The number of fsync requests submitted to the group commit manager which
     * timed out.
     */
    public long getNFSyncTimeouts() {
        return nFSyncTimeouts;
    }

    /**
     * The accumulated number of full INs flushed to the log.
     */
    public long getNFullINFlush() {
        return nFullINFlush;
    }

    /**
     * The accumulated number of full BINS flushed to the log.
     */
    public long getNFullBINFlush() {
        return nFullBINFlush;
    }

    /**
     * The accumulated number of INs obsolete.
     */
    public long getNINsObsolete() {
        return nINsObsolete;
    }

    /**
     * The accumulated number of INs cleaned.
     */
    public long getNINsCleaned() {
        return nINsCleaned;
    }

    /**
     * The accumulated number of INs that were not found in the tree anymore
     * (deleted).
     */
    public long getNINsDead() {
        return nINsDead;
    }

    /**
     * The accumulated number of INs migrated.
     */
    public long getNINsMigrated() {
        return nINsMigrated;
    }

    /**
     * The accumulated number of LNs obsolete.
     */
    public long getNLNsObsolete() {
        return nLNsObsolete;
    }

    /**
     * The accumulated number of LNs cleaned.
     */
    public long getNLNsCleaned() {
        return nLNsCleaned;
    }

    /**
     * The accumulated number of LNs that were not found in the tree anymore
     * (deleted).
     */
    public long getNLNsDead() {
        return nLNsDead;
    }

    /**
     * The accumulated number of LNs encountered that were locked.
     */
    public long getNLNsLocked() {
        return nLNsLocked;
    }

    /**
     * The accumulated number of LNs encountered that were migrated forward in
     * the log.
     */
    public long getNLNsMigrated() {
        return nLNsMigrated;
    }

    /**
     * The accumulated number of LNs that were marked for migration during
     * cleaning.
     */
    public long getNLNsMarked() {
        return nLNsMarked;
    }

    /**
     * The accumulated number of LNs processed without a tree lookup.
     */
    public long getNLNQueueHits() {
        return nLNQueueHits;
    }

    /**
     * The accumulated number of LNs processed because they were previously
     * locked.
     */
    public long getNPendingLNsProcessed() {
        return nPendingLNsProcessed;
    }

    /**
     * The accumulated number of LNs processed because they were previously
     * marked for migration.
     */
    public long getNMarkedLNsProcessed() {
        return nMarkedLNsProcessed;
    }

    /**
     * The accumulated number of LNs processed because they are soon to be
     * cleaned.
     */
    public long getNToBeCleanedLNsProcessed() {
        return nToBeCleanedLNsProcessed;
    }

    /**
     * The accumulated number of LNs processed because they qualify for
     * clustering.
     */
    public long getNClusterLNsProcessed() {
        return nClusterLNsProcessed;
    }

    /**
     * The accumulated number of pending LNs that could not be locked for
     * migration because of a long duration application lock.
     */
    public long getNPendingLNsLocked() {
        return nPendingLNsLocked;
    }

    /**
     * The number of log buffers currently instantiated.
     */
    public int getNLogBuffers() {
        return nLogBuffers;
    }

    /**
     * The number of disk reads which required respositioning the disk head
     * more than 1MB from the previous file position.  Reads in a different
     * *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate and may differ from the actual number of
     * random disk reads depending on the type of disks and file system, disk
     * geometry, and file system cache size.
     */
    public long getNRandomReads() {
        return nRandomReads;
    }

    /**
     * The number of bytes read which required respositioning the disk head
     * more than 1MB from the previous file position.  Reads in a different
     * *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNRandomReadBytes() {
        return nRandomReadBytes;
    }

    /**
     * The number of disk writes which required respositioning the disk head by
     * more than 1MB from the previous file position.  Writes to a different
     * *.jdb log file (i.e. a file "flip") then the last IO constitute a random
     * write.
     * <p>
     * This number is approximate and may differ from the actual number of
     * random disk writes depending on the type of disks and file system, disk
     * geometry, and file system cache size.
     */
    public long getNRandomWrites() {
        return nRandomWrites;
    }

    /**
     * The number of bytes written which required respositioning the disk head
     * more than 1MB from the previous file position.  Writes in a different
     * *.jdb log file then the last IO constitute a random write.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNRandomWriteBytes() {
        return nRandomWriteBytes;
    }

    /**
     * The number of disk reads which did not require respositioning the disk
     * head more than 1MB from the previous file position.  Reads in a
     * different *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate and may differ from the actual number of
     * sequential disk reads depending on the type of disks and file system,
     * disk geometry, and file system cache size.
     */
    public long getNSequentialReads() {
        return nSequentialReads;
    }

    /**
     * The number of bytes read which did not require respositioning the disk
     * head more than 1MB from the previous file position.  Reads in a
     * different *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNSequentialReadBytes() {
        return nSequentialReadBytes;
    }

    /**
     * The number of disk writes which did not require respositioning the disk
     * head by more than 1MB from the previous file position.  Writes to a
     * different *.jdb log file (i.e. a file "flip") then the last IO
     * constitute a random write.
     * <p>
     * This number is approximate and may differ from the actual number of
     * sequential disk writes depending on the type of disks and file system,
     * disk geometry, and file system cache size.
     */
    public long getNSequentialWrites() {
        return nSequentialWrites;
    }

    /**
     * The number of bytes written which did not require respositioning the
     * disk head more than 1MB from the previous file position.  Writes in a
     * different *.jdb log file then the last IO constitute a random write.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNSequentialWriteBytes() {
        return nSequentialWriteBytes;
    }

    /**
     * The accumulated number of nodes evicted.
     */
    public long getNNodesExplicitlyEvicted() {
        return nNodesExplicitlyEvicted;
    }

    /**
     * The accumulated number of database root nodes evicted.
     */
    public long getNRootNodesEvicted() {
        return nRootNodesEvicted;
    }

    /**
     * The number of BINS stripped by the evictor.
     */
    public long getNBINsStripped() {
        return nBINsStripped;
    }

    /**
     * The number of bytes that must be evicted in order to get within the
     * memory budget.
     */
    public long getRequiredEvictBytes() {
        return requiredEvictBytes;
    }

    /**
     * The accumulated number of nodes scanned in order to select the
     * eviction set.
     */
    public long getNNodesScanned() {
        return nNodesScanned;
    }

    /**
     * The accumulated number of nodes selected to evict.
     */
    public long getNNodesSelected() {
        return nNodesSelected;
    }

    /**
     * The number of environments using the shared cache.  This method says
     * nothing about whether this environment is using the shared cache or not.
     */
    public int getNSharedCacheEnvironments() {
        return nSharedCacheEnvironments;
    }

    /**
     * The total amount of the shared JE cache in use, in bytes.  If this
     * environment uses the shared cache, this method returns the total amount
     * used by all environments that are sharing the cache.  If this
     * environment does not use the shared cache, this method returns zero.
     *
     * <p>To get the configured maximum cache size, see {@link
     * EnvironmentMutableConfig#getCacheSize}.</p>
     */
    public long getSharedCacheTotalBytes() {
        return sharedCacheTotalBytes;
    }

    /**
     * The total amount of JE cache in use, in bytes.  If this environment uses
     * the shared cache, this method returns only the amount used by this
     * environment.
     *
     * <p>This method returns the sum of {@link #getDataBytes}, {@link
     * #getAdminBytes}, {@link #getLockBytes} and {@link #getBufferBytes}.</p>
     *
     * <p>To get the configured maximum cache size, see {@link
     * EnvironmentMutableConfig#getCacheSize}.</p>
     */
    public long getCacheTotalBytes() {
        return cacheTotalBytes;
    }

    /**
     * The total memory currently consumed by log buffers, in bytes.  If this
     * environment uses the shared cache, this method returns only the amount
     * used by this environment.
     */
    public long getBufferBytes() {
        return bufferBytes;
    }

    /**
     * The amount of JE cache used for holding data, keys and internal Btree
     * nodes, in bytes.  If this environment uses the shared cache, this method
     * returns only the amount used by this environment.
     */
    public long getDataBytes() {
        return dataBytes;
    }

    /**
     * The number of bytes of JE cache used for log cleaning metadata and other
     * administrative structures.  If this environment uses the shared cache,
     * this method returns only the amount used by this environment.
     */
    public long getAdminBytes() {
        return adminBytes;
    }

    /**
     * The number of bytes of JE cache used for holding locks and transactions.
     * If this environment uses the shared cache, this method returns only the
     * amount used by this environment.
     */
    public long getLockBytes() {
        return lockBytes;
    }

    /**
     * The amount of JE cache used for all items except for the log buffers, in
     * bytes.  If this environment uses the shared cache, this method returns
     * only the amount used by this environment.
     *
     * @deprecated Please use {@link #getDataBytes} to get the amount of cache
     * used for data and use {@link #getAdminBytes}, {@link #getLockBytes} and
     * {@link #getBufferBytes} to get other components of the total cache usage
     * ({@link #getCacheTotalBytes}).
     */
    public long getCacheDataBytes() {
        return cacheTotalBytes - bufferBytes;
    }

    /**
     * The number of requests for database objects not contained within the
     * in memory data structures.
     */
    public long getNNotResident() {
        return nNotResident;
    }

    /**
     * The number of bins encountered by the INCompressor that were not
     * actually empty when the compressor ran.
     */
    public long getNonEmptyBins() {
        return nonEmptyBins;
    }

    /**
     * The number of bins that were successfully processed by the IN
     * Compressor.
     */
    public long getProcessedBins() {
        return processedBins;
    }

    /**
     * The number of reads which had to be repeated when faulting in an object
     * from disk because the read chunk size controlled by je.log.faultReadSize
     * is too small.
     */
    public long getNRepeatFaultReads() {
        return nRepeatFaultReads;
    }

    /**
     * The number of writes which had to be completed using the temporary
     * marshalling buffer because the fixed size log buffers specified by
     * je.log.totalBufferBytes and je.log.numBuffers were not large enough.
     */
    public long getNTempBufferWrites() {
        return nTempBufferWrites;
    }

    /**
     * The number of times we try to read a log entry larger than the read
     * buffer size and can't grow the log buffer to accommodate the large
     * object. This happens during scans of the log during activities like
     * environment open or log cleaning. Implies that the read chunk size
     * controlled by je.log.iteratorReadSize is too small.
     */
    public long getNRepeatIteratorReads() {
        return nRepeatIteratorReads;
    }

    /**
     * The number of times a log file has been opened.
     */
    public int getNFileOpens() {
        return nFileOpens;
    }

    /**
     * The number of files currently open in the file cache.
     */
    public int getNOpenFiles() {
        return nOpenFiles;
    }

    /**
     * An approximation of the current total log size in bytes.
     */
    public long getTotalLogSize() {
        return totalLogSize;
    }

    /**
     * The number of bins encountered by the INCompressor that were split
     * between the time they were put on the compressor queue and when the
     * compressor ran.
     */
    public long getSplitBins() {
        return splitBins;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNSharedCacheEnvironments(int nSharedCacheEnvironments) {
        this.nSharedCacheEnvironments = nSharedCacheEnvironments;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setSharedCacheTotalBytes(long sharedCacheTotalBytes) {
        this.sharedCacheTotalBytes = sharedCacheTotalBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setCacheTotalBytes(long cacheTotalBytes) {
        this.cacheTotalBytes = cacheTotalBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDataBytes(long dataBytes) {
        this.dataBytes = dataBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setAdminBytes(long adminBytes) {
        this.adminBytes = adminBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLockBytes(long lockBytes) {
        this.lockBytes = lockBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNNotResident(long nNotResident) {
        this.nNotResident = nNotResident;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCacheMiss(long nCacheMiss) {
        this.nCacheMiss = nCacheMiss;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLogBuffers(int nLogBuffers) {
        this.nLogBuffers = nLogBuffers;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setBufferBytes(long bufferBytes) {
        this.bufferBytes = bufferBytes;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setCursorsBins(long val) {
        cursorsBins = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDbClosedBins(long val) {
        dbClosedBins = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setInCompQueueSize(long val) {
        inCompQueueSize = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLastCheckpointId(long l) {
        lastCheckpointId = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCheckpoints(long val) {
        nCheckpoints = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setCleanerBacklog(int val) {
        cleanerBacklog = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCleanerRuns(long val) {
        nCleanerRuns = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCleanerDeletions(long val) {
        nCleanerDeletions = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNDeltaINFlush(long val) {
        nDeltaINFlush = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLastCheckpointEnd(long lsn) {
        lastCheckpointEnd = lsn;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setEndOfLog(long lsn) {
        endOfLog = lsn;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLastCheckpointStart(long lsn) {
        lastCheckpointStart = lsn;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCleanerEntriesRead(long val) {
        nCleanerEntriesRead = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNEvictPasses(long val) {
        nEvictPasses = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFSyncs(long val) {
        nFSyncs = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFSyncRequests(long val) {
        nFSyncRequests = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFSyncTimeouts(long val) {
        nFSyncTimeouts = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFullINFlush(long val) {
        nFullINFlush = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFullBINFlush(long val) {
        nFullBINFlush = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNINsObsolete(long val) {
        nINsObsolete = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNINsCleaned(long val) {
        nINsCleaned = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNINsDead(long val) {
        nINsDead = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNINsMigrated(long val) {
        nINsMigrated = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsObsolete(long val) {
        nLNsObsolete = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsCleaned(long val) {
        nLNsCleaned = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsDead(long val) {
        nLNsDead = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsLocked(long val) {
        nLNsLocked = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsMigrated(long val) {
        nLNsMigrated = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNsMarked(long val) {
        nLNsMarked = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNLNQueueHits(long val) {
        nLNQueueHits = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNPendingLNsProcessed(long val) {
        nPendingLNsProcessed = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNMarkedLNsProcessed(long val) {
        nMarkedLNsProcessed = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNToBeCleanedLNsProcessed(long val) {
        nToBeCleanedLNsProcessed = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRandomReads(long val) {
        nRandomReads = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRandomWrites(long val) {
        nRandomWrites = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNSequentialReads(long val) {
        nSequentialReads = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNSequentialWrites(long val) {
        nSequentialWrites = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRandomReadBytes(long val) {
        nRandomReadBytes = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRandomWriteBytes(long val) {
        nRandomWriteBytes = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNSequentialReadBytes(long val) {
        nSequentialReadBytes = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNSequentialWriteBytes(long val) {
        nSequentialWriteBytes = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNClusterLNsProcessed(long val) {
        nClusterLNsProcessed = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNPendingLNsLocked(long val) {
        nPendingLNsLocked = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNNodesExplicitlyEvicted(long l) {
        nNodesExplicitlyEvicted = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRootNodesEvicted(long l) {
        nRootNodesEvicted = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setRequiredEvictBytes(long l) {
        requiredEvictBytes = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNBINsStripped(long l) {
        nBINsStripped = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNNodesScanned(long l) {
        nNodesScanned = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNNodesSelected(long l) {
        nNodesSelected = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNonEmptyBins(long val) {
        nonEmptyBins = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setProcessedBins(long val) {
        processedBins = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRepeatFaultReads(long val) {
        nRepeatFaultReads = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNTempBufferWrites(long val) {
        nTempBufferWrites = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRepeatIteratorReads(long val) {
        nRepeatIteratorReads = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNFileOpens(int val) {
        nFileOpens = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNOpenFiles(int val) {
        nOpenFiles = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setTotalLogSize(long val) {
        totalLogSize = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setSplitBins(long val) {
        splitBins = val;
    }

    /**
     * Returns a String representation of the stats in the form of
     * &lt;stat&gt;=&lt;value&gt;
     */
    @Override
    public String toString() {
        DecimalFormat f = new DecimalFormat("###,###,###,###,###,###,###");

        StringBuffer sb = new StringBuffer();
        sb.append("\nCompression stats\n");
        sb.append("splitBins=").append(f.format(splitBins)).append('\n');
        sb.append("dbClosedBins=").append(f.format(dbClosedBins)).append('\n');
        sb.append("cursorsBins=").append(f.format(cursorsBins)).append('\n');
        sb.append("nonEmptyBins=").append(f.format(nonEmptyBins)).append('\n');
        sb.append("processedBins=").
            append(f.format(processedBins)).append('\n');
        sb.append("inCompQueueSize=").
            append(f.format(inCompQueueSize)).append('\n');

        // Evictor
        sb.append("\nEviction stats\n");
        sb.append("nEvictPasses=").append(f.format(nEvictPasses)).append('\n');
        sb.append("nNodesSelected=").
            append(f.format(nNodesSelected)).append('\n');
        sb.append("nNodesScanned=").
            append(f.format(nNodesScanned)).append('\n');
        sb.append("nNodesExplicitlyEvicted=").
           append(f.format(nNodesExplicitlyEvicted)).append('\n');
        sb.append("nRootNodesEvicted=").
           append(f.format(nRootNodesEvicted)).append('\n');
        sb.append("nBINsStripped=").
            append(f.format(nBINsStripped)).append('\n');
        sb.append("requiredEvictBytes=").
            append(f.format(requiredEvictBytes)).append('\n');

        // Checkpointer
        sb.append("\nCheckpoint stats\n");
        sb.append("nCheckpoints=").append(f.format(nCheckpoints)).append('\n');
        sb.append("lastCheckpointId=").
            append(f.format(lastCheckpointId)).append('\n');
        sb.append("nFullINFlush=").append(f.format(nFullINFlush)).append('\n');
        sb.append("nFullBINFlush=").
            append(f.format(nFullBINFlush)).append('\n');
        sb.append("nDeltaINFlush=").
            append(f.format(nDeltaINFlush)).append('\n');
        sb.append("lastCheckpointStart=").
           append(DbLsn.getNoFormatString(lastCheckpointStart)).append('\n');
        sb.append("lastCheckpointEnd=").
           append(DbLsn.getNoFormatString(lastCheckpointEnd)).append('\n');
        sb.append("endOfLog=").
           append(DbLsn.getNoFormatString(endOfLog)).append('\n');

        // Cleaner
        sb.append("\nCleaner stats\n");
        sb.append("cleanerBacklog=").
            append(f.format(cleanerBacklog)).append('\n');
        sb.append("nCleanerRuns=").
            append(f.format(nCleanerRuns)).append('\n');
        sb.append("nCleanerDeletions=").
            append(f.format(nCleanerDeletions)).append('\n');
        sb.append("nINsObsolete=").append(f.format(nINsObsolete)).append('\n');
        sb.append("nINsCleaned=").append(f.format(nINsCleaned)).append('\n');
        sb.append("nINsDead=").append(f.format(nINsDead)).append('\n');
        sb.append("nINsMigrated=").append(f.format(nINsMigrated)).append('\n');
        sb.append("nLNsObsolete=").append(f.format(nLNsObsolete)).append('\n');
        sb.append("nLNsCleaned=").append(f.format(nLNsCleaned)).append('\n');
        sb.append("nLNsDead=").append(f.format(nLNsDead)).append('\n');
        sb.append("nLNsLocked=").append(f.format(nLNsLocked)).append('\n');
        sb.append("nLNsMigrated=").append(f.format(nLNsMigrated)).append('\n');
        sb.append("nLNsMarked=").append(f.format(nLNsMarked)).append('\n');
        sb.append("nLNQueueHits=").
            append(f.format(nLNQueueHits)).append('\n');
        sb.append("nPendingLNsProcessed=").
            append(f.format(nPendingLNsProcessed)).append('\n');
        sb.append("nMarkedLNsProcessed=").
            append(f.format(nMarkedLNsProcessed)).append('\n');
        sb.append("nToBeCleanedLNsProcessed=").
            append(f.format(nToBeCleanedLNsProcessed)).append('\n');
        sb.append("nClusterLNsProcessed=").
            append(f.format(nClusterLNsProcessed)).append('\n');
        sb.append("nPendingLNsLocked=").
            append(f.format(nPendingLNsLocked)).append('\n');
        sb.append("nCleanerEntriesRead=").
            append(f.format(nCleanerEntriesRead)).append('\n');

        // Cache
        sb.append("\nCache stats\n");
        sb.append("nNotResident=").append(f.format(nNotResident)).append('\n');
        sb.append("nCacheMiss=").append(f.format(nCacheMiss)).append('\n');
        sb.append("nLogBuffers=").append(f.format(nLogBuffers)).append('\n');
        sb.append("bufferBytes=").append(f.format(bufferBytes)).append('\n');
        sb.append("dataBytes=").append(f.format(dataBytes)).append('\n');
        sb.append("adminBytes=").append(f.format(adminBytes)).append('\n');
        sb.append("lockBytes=").append(f.format(lockBytes)).append('\n');
        sb.append("cacheTotalBytes=").
            append(f.format(cacheTotalBytes)).append('\n');
        sb.append("sharedCacheTotalBytes=").
            append(f.format(sharedCacheTotalBytes)).append('\n');
        sb.append("nSharedCacheEnvironments=").
            append(f.format(nSharedCacheEnvironments)).append('\n');

        // IO
        sb.append("\nIO Stats\n");
        sb.append("nRandomReads=").append(f.format(nRandomReads)).append('\n');
        sb.append("nRandomWrites=").append(f.format(nRandomWrites)).
            append('\n');
        sb.append("nSequentialReads=").append(f.format(nSequentialReads)).
            append('\n');
        sb.append("nSequentialWrites=").append(f.format(nSequentialWrites)).
            append('\n');
        sb.append("nRandomReadBytes=").append(f.format(nRandomReadBytes)).
            append('\n');
        sb.append("nRandomWriteBytes=").append(f.format(nRandomWriteBytes)).
            append('\n');
        sb.append("nSequentialReadBytes=").
            append(f.format(nSequentialReadBytes)).append('\n');
        sb.append("nSequentialWriteBytes=").
            append(f.format(nSequentialWriteBytes)).append('\n');

        // Logging
        sb.append("\nLogging stats\n");
        sb.append("nFSyncs=").append(f.format(nFSyncs)).append('\n');
        sb.append("nFSyncRequests=").
            append(f.format(nFSyncRequests)).append('\n');
        sb.append("nFSyncTimeouts=").
            append(f.format(nFSyncTimeouts)).append('\n');
        sb.append("nRepeatFaultReads=").
            append(f.format(nRepeatFaultReads)).append('\n');
        sb.append("nTempBufferWrite=").
            append(f.format(nTempBufferWrites)).append('\n');
        sb.append("nRepeatIteratorReads=").
            append(f.format(nRepeatIteratorReads)).append('\n');
        sb.append("nFileOpens=").
            append(f.format(nFileOpens)).append('\n');
        sb.append("nOpenFiles=").
            append(f.format(nOpenFiles)).append('\n');
        sb.append("totalLogSize=").
            append(f.format(totalLogSize)).append('\n');

        return sb.toString();
    }
}
