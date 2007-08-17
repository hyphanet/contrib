/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStats.java,v 1.43.2.3 2007/05/23 20:31:05 mark Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;
import java.text.DecimalFormat;

import com.sleepycat.je.utilint.DbLsn;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class EnvironmentStats implements Serializable {
    /* INCompressor */

    /**
     * The number of bins encountered by the INCompressor that were split
     * between the time they were put on the compressor queue and when
     * the compressor ran.
     */
    private int splitBins;

    /**
     * The number of bins encountered by the INCompressor that had their
     * database closed between the time they were put on the
     * compressor queue and when the compressor ran.
     */
    private int dbClosedBins;

    /**
     * The number of bins encountered by the INCompressor that had cursors
     * referring to them when the compressor ran.
     */
    private int cursorsBins;

    /**
     * The number of bins encountered by the INCompressor that were
     * not actually empty when the compressor ran.
     */
    private int nonEmptyBins;

    /**
     * The number of bins that were successfully processed by the IN
     * Compressor.
     */
    private int processedBins;

    /**
     * The number of entries in the INCompressor queue when the getStats()
     * call was made.
     */
    private int inCompQueueSize;

    /* Evictor */

    /**
     * The number of passes made to the evictor.
     */
    private int nEvictPasses;

    /**
     * The accumulated number of nodes selected to evict.
     */
    private long nNodesSelected;

    /**
     * The accumulated number of nodes scanned in order to select the
     * eviction set.
     */
    private long nNodesScanned;

    /**
     * The accumulated number of nodes evicted.
     */
    private long nNodesExplicitlyEvicted;

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
    private int nCheckpoints;

    /**
     * The Id of the last checkpoint.
     */
    private long lastCheckpointId;

    /**
     * The accumulated number of full INs flushed to the log.
     */
    private int nFullINFlush;

    /**
     * The accumulated number of full BINs flushed to the log.
     */
    private int nFullBINFlush;

    /**
     * The accumulated number of Delta INs flushed to the log.
     */
    private int nDeltaINFlush;

    /**
     * The location in the log of the last checkpoint start.
     */
    private long lastCheckpointStart;

    /**
     * The location in the log of the last checkpoint end.
     */
    private long lastCheckpointEnd;

    /* Cleaner */

    /** The number of files to be cleaned to reach the target utilization. */
    private int cleanerBacklog;

    /** The number of cleaner runs this session. */
    private int nCleanerRuns;

    /** The number of cleaner file deletions this session. */
    private int nCleanerDeletions;

    /**
     * The accumulated number of INs obsolete.
     */
    private int nINsObsolete;

    /**
     * The accumulated number of INs cleaned.
     */
    private int nINsCleaned;

    /**
     * The accumulated number of INs that were not found in the tree anymore
     * (deleted).
     */
    private int nINsDead;

    /**
     * The accumulated number of INs migrated.
     */
    private int nINsMigrated;

    /**
     * The accumulated number of LNs obsolete.
     */
    private int nLNsObsolete;

    /**
     * The accumulated number of LNs cleaned.
     */
    private int nLNsCleaned;

    /**
     * The accumulated number of LNs that were not found in the tree anymore
     * (deleted).
     */
    private int nLNsDead;

    /**
     * The accumulated number of LNs encountered that were locked.
     */
    private int nLNsLocked;

    /**
     * The accumulated number of LNs encountered that were migrated forward
     * in the log.
     */
    private int nLNsMigrated;

    /**
     * The accumulated number of LNs that were marked for migration during
     * cleaning.
     */
    private int nLNsMarked;

    /**
     * The accumulated number of LNs processed without a tree lookup.
     */
    private int nLNQueueHits;

    /**
     * The accumulated number of LNs processed because they were previously
     * locked.
     */
    private int nPendingLNsProcessed;

    /**
     * The accumulated number of LNs processed because they were previously
     * marked for migration.
     */
    private int nMarkedLNsProcessed;

    /**
     * The accumulated number of LNs processed because they are soon to be
     * cleaned.
     */
    private int nToBeCleanedLNsProcessed;

    /**
     * The accumulated number of LNs processed because they qualify for
     * clustering.
     */
    private int nClusterLNsProcessed;

    /**
     * The accumulated number of pending LNs that could not be locked for
     * migration because of a long duration application lock.
     */
    private int nPendingLNsLocked;

    /**
     * The accumulated number of log entries read by the cleaner.
     */
    private int nCleanerEntriesRead;

    /*
     * Cache
     */
    private long cacheDataBytes; // part of cache consumed by data, in bytes
    private long nNotResident;   // had to be instantiated from an LSN
    private long nCacheMiss;     // had to retrieve from disk
    private int  nLogBuffers;    // number of existing log buffers
    private long bufferBytes;    // cache consumed by the log buffers, 
                                 // in bytes
    private long adminBytes;     // part of cache used by transactions,
                                 // log cleaning metadata, and other 
                                 // administrative structures
    private long lockBytes;      // part of cache used by locks

    /*
     * Log activity
     */
    private long nFSyncs;   // Number of fsyncs issued. May be less than
                              // nFSyncRequests because of group commit
    private long nFSyncRequests; // Number of fsyncs requested. 
    private long nFSyncTimeouts; // Number of group fsync requests that
                                   // turned into singleton fsyncs.
    /* 
     * Number of reads which had to be repeated when faulting in an
     * object from disk because the read chunk size controlled by
     * je.log.faultReadSize is too small.
     */
    private long nRepeatFaultReads; 

    /* 
     * Number of times we have to use the temporary marshalling buffer to
     * write to the log.
     */
    private long nTempBufferWrites;

    /* 
     * Number of times we try to read a log entry larger than the read
     * buffer size and can't grow the log buffer to accomodate the large
     * object. This happens during scans of the log during activities like
     * environment open or log cleaning. Implies that the the read
     * chunk size controlled by je.log.iteratorReadSize is too small.
     */
    private long nRepeatIteratorReads;

    /*
     * Approximation of the total log size in bytes.
     */
    private long totalLogSize;
    
    /**
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
        cacheDataBytes = 0;
        nNotResident = 0;
        nCacheMiss = 0;
        nLogBuffers = 0;
        bufferBytes = 0;

        // Log
        nFSyncs = 0;
        nFSyncRequests = 0;
        nFSyncTimeouts = 0;
        nRepeatFaultReads = 0;
	nTempBufferWrites = 0;
        nRepeatIteratorReads = 0;
        totalLogSize = 0;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getBufferBytes() {
        return bufferBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getCursorsBins() {
        return cursorsBins;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getDbClosedBins() {
        return dbClosedBins;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getInCompQueueSize() {
        return inCompQueueSize;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getLastCheckpointId() {
        return lastCheckpointId;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNCacheMiss() {
        return nCacheMiss;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNCheckpoints() {
        return nCheckpoints;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getCleanerBacklog() {
        return cleanerBacklog;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNCleanerRuns() {
        return nCleanerRuns;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNCleanerDeletions() {
        return nCleanerDeletions;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNDeltaINFlush() {
        return nDeltaINFlush;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getLastCheckpointEnd() {
        return lastCheckpointEnd;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getLastCheckpointStart() {
        return lastCheckpointStart;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNCleanerEntriesRead() {
        return nCleanerEntriesRead;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNEvictPasses() {
        return nEvictPasses;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNFSyncs() {
        return nFSyncs;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNFSyncRequests() {
        return nFSyncRequests;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNFSyncTimeouts() {
        return nFSyncTimeouts;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNFullINFlush() {
        return nFullINFlush;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNFullBINFlush() {
        return nFullBINFlush;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNINsObsolete() {
        return nINsObsolete;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNINsCleaned() {
        return nINsCleaned;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNINsDead() {
        return nINsDead;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNINsMigrated() {
        return nINsMigrated;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsObsolete() {
        return nLNsObsolete;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsCleaned() {
        return nLNsCleaned;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsDead() {
        return nLNsDead;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsLocked() {
        return nLNsLocked;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsMigrated() {
        return nLNsMigrated;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNsMarked() {
        return nLNsMarked;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLNQueueHits() {
        return nLNQueueHits;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNPendingLNsProcessed() {
        return nPendingLNsProcessed;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNMarkedLNsProcessed() {
        return nMarkedLNsProcessed;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNToBeCleanedLNsProcessed() {
        return nToBeCleanedLNsProcessed;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNClusterLNsProcessed() {
        return nClusterLNsProcessed;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNPendingLNsLocked() {
        return nPendingLNsLocked;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNLogBuffers() {
        return nLogBuffers;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNNodesExplicitlyEvicted() {
        return nNodesExplicitlyEvicted;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNBINsStripped() {
        return nBINsStripped;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getRequiredEvictBytes() {
        return requiredEvictBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNNodesScanned() {
        return nNodesScanned;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNNodesSelected() {
        return nNodesSelected;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getCacheTotalBytes() {
        return cacheDataBytes + bufferBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getCacheDataBytes() {
        return cacheDataBytes;
    }

    /**
     * The number of bytes of JE cache used for holding transaction objects,
     * log cleaning metadata, and other administrative structures. This is a
     * subset of cacheDataBytes.
     */
    public long getAdminBytes() {
        return adminBytes;
    }

    /**
     * The number of bytes of JE cache used for holding lock objects.
     * This is a subset of cacheDataBytes.
     */
    public long getLockBytes() {
        return lockBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNNotResident() {
        return nNotResident;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNonEmptyBins() {
        return nonEmptyBins;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getProcessedBins() {
        return processedBins;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNRepeatFaultReads() {
        return nRepeatFaultReads;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNTempBufferWrites() {
        return nTempBufferWrites;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNRepeatIteratorReads() {
        return nRepeatIteratorReads;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getTotalLogSize() {
        return totalLogSize;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getSplitBins() {
        return splitBins;
    }

    /**
     * Internal use only.
     */
    public void setCacheDataBytes(long cacheDataBytes) {
        this.cacheDataBytes = cacheDataBytes;
    }

    /**
     * Internal use only.
     */
    public void setAdminBytes(long adminBytes) {
        this.adminBytes = adminBytes;
    }

    /**
     * Internal use only.
     */
    public void setLockBytes(long lockBytes) {
        this.lockBytes = lockBytes;
    }

    /**
     * Internal use only.
     */
    public void setNNotResident(long nNotResident) {
        this.nNotResident = nNotResident;
    }

    /**
     * Internal use only.
     */
    public void setNCacheMiss(long nCacheMiss) {
        this.nCacheMiss = nCacheMiss;
    }

    /**
     * Internal use only.
     */
    public void setNLogBuffers(int nLogBuffers) {
        this.nLogBuffers = nLogBuffers;
    }

    /**
     * Internal use only.
     */
    public void setBufferBytes(long bufferBytes) {
        this.bufferBytes = bufferBytes;
    }

    /**
     * Internal use only.
     */
    public void setCursorsBins(int val) {
        cursorsBins = val;
    }

    /**
     * Internal use only.
     */
    public void setDbClosedBins(int val) {
        dbClosedBins = val;
    }

    /**
     * Internal use only.
     */
    public void setInCompQueueSize(int val) {
        inCompQueueSize = val;
    }

    /**
     * Internal use only.
     */
    public void setLastCheckpointId(long l) {
        lastCheckpointId = l;
    }

    /**
     * Internal use only.
     */
    public void setNCheckpoints(int val) {
        nCheckpoints = val;
    }

    /**
     * Internal use only.
     */
    public void setCleanerBacklog(int val) {
        cleanerBacklog = val;
    }

    /**
     * Internal use only.
     */
    public void setNCleanerRuns(int val) {
        nCleanerRuns = val;
    }

    /**
     * Internal use only.
     */
    public void setNCleanerDeletions(int val) {
        nCleanerDeletions = val;
    }

    /**
     * Internal use only.
     */
    public void setNDeltaINFlush(int val) {
        nDeltaINFlush = val;
    }

    /**
     * Internal use only.
     */
    public void setLastCheckpointEnd(long lsn) {
        lastCheckpointEnd = lsn;
    }

    /**
     * Internal use only.
     */
    public void setLastCheckpointStart(long lsn) {
        lastCheckpointStart = lsn;
    }

    /**
     * Internal use only.
     */
    public void setNCleanerEntriesRead(int val) {
        nCleanerEntriesRead = val;
    }

    /**
     * Internal use only.
     */
    public void setNEvictPasses(int val) {
        nEvictPasses = val;
    }

    /**
     * Internal use only.
     */
    public void setNFSyncs(long val) {
        nFSyncs = val;
    }

    /**
     * Internal use only.
     */
    public void setNFSyncRequests(long val) {
        nFSyncRequests = val;
    }

    /**
     * Internal use only.
     */
    public void setNFSyncTimeouts(long val) {
        nFSyncTimeouts = val;
    }

    /**
     * Internal use only.
     */
    public void setNFullINFlush(int val) {
        nFullINFlush = val;
    }

    /**
     * Internal use only.
     */
    public void setNFullBINFlush(int val) {
        nFullBINFlush = val;
    }

    /**
     * Internal use only.
     */
    public void setNINsObsolete(int val) {
        nINsObsolete = val;
    }

    /**
     * Internal use only.
     */
    public void setNINsCleaned(int val) {
        nINsCleaned = val;
    }

    /**
     * Internal use only.
     */
    public void setNINsDead(int val) {
        nINsDead = val;
    }

    /**
     * Internal use only.
     */
    public void setNINsMigrated(int val) {
        nINsMigrated = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsObsolete(int val) {
        nLNsObsolete = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsCleaned(int val) {
        nLNsCleaned = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsDead(int val) {
        nLNsDead = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsLocked(int val) {
        nLNsLocked = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsMigrated(int val) {
        nLNsMigrated = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNsMarked(int val) {
        nLNsMarked = val;
    }

    /**
     * Internal use only.
     */
    public void setNLNQueueHits(int val) {
        nLNQueueHits = val;
    }

    /**
     * Internal use only.
     */
    public void setNPendingLNsProcessed(int val) {
        nPendingLNsProcessed = val;
    }

    /**
     * Internal use only.
     */
    public void setNMarkedLNsProcessed(int val) {
        nMarkedLNsProcessed = val;
    }

    /**
     * Internal use only.
     */
    public void setNToBeCleanedLNsProcessed(int val) {
        nToBeCleanedLNsProcessed = val;
    }

    /**
     * Internal use only.
     */
    public void setNClusterLNsProcessed(int val) {
        nClusterLNsProcessed = val;
    }

    /**
     * Internal use only.
     */
    public void setNPendingLNsLocked(int val) {
        nPendingLNsLocked = val;
    }

    /**
     * Internal use only.
     */
    public void setNNodesExplicitlyEvicted(long l) {
        nNodesExplicitlyEvicted = l;
    }

    /**
     * Internal use only.
     */
    public void setRequiredEvictBytes(long l) {
        requiredEvictBytes = l;
    }

    /**
     * Internal use only.
     */
    public void setNBINsStripped(long l) {
        nBINsStripped = l;
    }

    /**
     * Internal use only.
     */
    public void setNNodesScanned(long l) {
        nNodesScanned = l;
    }

    /**
     * Internal use only.
     */
    public void setNNodesSelected(long l) {
        nNodesSelected = l;
    }

    /**
     * Internal use only.
     */
    public void setNonEmptyBins(int val) {
        nonEmptyBins = val;
    }

    /**
     * Internal use only.
     */
    public void setProcessedBins(int val) {
        processedBins = val;
    }

    /**
     * Internal use only.
     */
    public void setNRepeatFaultReads(long val) {
        nRepeatFaultReads = val;
    }

    /**
     * Internal use only.
     */
    public void setNTempBufferWrites(long val) {
        nTempBufferWrites = val;
    }

    /**
     * Internal use only.
     */
    public void setNRepeatIteratorReads(long val) {
        nRepeatIteratorReads = val;
    }

    /**
     * Internal use only.
     */
    public void setTotalLogSize(long val) {
        totalLogSize = val;
    }

    /**
     * Internal use only.
     */
    public void setSplitBins(int val) {
        splitBins = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
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
        sb.append("cacheDataBytes=").
            append(f.format(cacheDataBytes)).append('\n');
        sb.append("adminBytes=").append(f.format(adminBytes)).append('\n');
        sb.append("lockBytes=").append(f.format(lockBytes)).append('\n');
        sb.append("cacheTotalBytes=").
            append(f.format(getCacheTotalBytes())).append('\n');

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
        sb.append("totalLogSize=").
            append(f.format(totalLogSize)).append('\n');

        return sb.toString();
    }
}
