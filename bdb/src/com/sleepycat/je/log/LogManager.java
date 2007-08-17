/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogManager.java,v 1.163.2.4 2007/06/13 03:55:37 mark Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.List;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.Operation;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.Tracer;

/**
 * The LogManager supports reading and writing to the JE log.
 */
abstract public class LogManager {

    // no-op loggable object
    private static final String DEBUG_NAME = LogManager.class.getName();
    
    protected LogBufferPool logBufferPool; // log buffers
    protected Latch logWriteLatch;           // synchronizes log writes
    private boolean doChecksumOnRead;      // if true, do checksum on read
    private FileManager fileManager;       // access to files
    protected EnvironmentImpl envImpl;
    private boolean readOnly;
    private int readBufferSize; // how many bytes to read when faulting in.
    /* The last LSN in the log during recovery. */
    private long lastLsnAtRecovery = DbLsn.NULL_LSN;

    /* Stats */

    /* 
     * Number of times we have to repeat a read when we fault in an object
     * because the initial read was too small.    
     */
    private int nRepeatFaultReads; 

    /* 
     * Number of times we have to use the temporary marshalling buffer to
     * write to the log.
     */
    private long nTempBufferWrites;

    /* For unit tests */
    private TestHook readHook; // used for generating exceptions on log reads

    /**
     * There is a single log manager per database environment.
     */
    public LogManager(EnvironmentImpl envImpl,
                      boolean readOnly)
        throws DatabaseException {

        // Set up log buffers
        this.envImpl = envImpl;
        this.fileManager = envImpl.getFileManager();
        DbConfigManager configManager = envImpl.getConfigManager();
	this.readOnly = readOnly;
        logBufferPool = new LogBufferPool(fileManager, envImpl);

        /* See if we're configured to do a checksum when reading in objects. */
        doChecksumOnRead =
	    configManager.getBoolean(EnvironmentParams.LOG_CHECKSUM_READ);

        logWriteLatch = LatchSupport.makeLatch(DEBUG_NAME, envImpl);
        readBufferSize =
	    configManager.getInt(EnvironmentParams.LOG_FAULT_READ_SIZE);
    }

    public boolean getChecksumOnRead() {
        return doChecksumOnRead;
    }

    public long getLastLsnAtRecovery() {
	return lastLsnAtRecovery;
    }

    public void setLastLsnAtRecovery(long lastLsnAtRecovery) {
	this.lastLsnAtRecovery = lastLsnAtRecovery;
    }

    /**
     * Reset the pool when the cache is resized.  This method is called after
     * the memory budget has been calculated.
     */
    public void resetPool(DbConfigManager configManager)
	throws DatabaseException {

        logBufferPool.reset(configManager);
    }

    /*
     * Writing to the log
     */

    /**
     * Log this single object and force a write of the log files.
     * @param item object to be logged
     * @param fsyncRequired if true, log files should also be fsynced.
     * @return LSN of the new log entry
     */
    public long logForceFlush(LogEntry item,
                              boolean fsyncRequired)
	throws DatabaseException {

        return log(item,
                   false, // is provisional
                   true,  // flush required
                   fsyncRequired,
		   false, // forceNewLogFile
		   false, // backgroundIO
                   DbLsn.NULL_LSN,  // old lsn
                   0);              // old size
    }

    /**
     * Log this single object and force a flip of the log files.
     * @param item object to be logged
     * @param fsyncRequired if true, log files should also be fsynced.
     * @return LSN of the new log entry
     */
    public long logForceFlip(LogEntry item)
	throws DatabaseException {

        return log(item,
                   false, // is provisional
                   true,  // flush required
                   false, // fsync required
		   true,  // forceNewLogFile
		   false, // backgroundIO
                   DbLsn.NULL_LSN,  // old lsn
                   0);              // old size
    }

    /**
     * Write a log entry.
     * @return LSN of the new log entry
     */
    public long log(LogEntry item) 
	throws DatabaseException {

        return log(item,
                   false,           // is provisional
                   false,           // flush required
                   false,           // fsync required
		   false,           // forceNewLogFile
		   false,           // backgroundIO
                   DbLsn.NULL_LSN,  // old lsn
                   0);              // old size
    }

    /**
     * Write a log entry.
     * @return LSN of the new log entry
     */
    public long log(LogEntry item,
		    boolean isProvisional,
		    boolean backgroundIO,
		    long oldNodeLsn,
                    int oldNodeSize)
	throws DatabaseException {

        return log(item,
                   isProvisional,
                   false, // flush required
                   false, // fsync required
		   false, // forceNewLogFile
		   backgroundIO,
                   oldNodeLsn,
                   oldNodeSize);
    }

    /**
     * Write a log entry.
     * @param item is the item to be logged.
     * @param isProvisional true if this entry should not be read during
     * recovery.
     * @param flushRequired if true, write the log to the file after
     * adding the item. i.e. call java.nio.channel.FileChannel.write().
     * @param fsyncRequired if true, fsync the last file after adding the item.
     * @param forceNewLogFile if true, flip to a new log file before logging
     * the item.
     * @param backgroundIO if true, sleep when the backgroundIOLimit is
     * exceeded.
     * @param oldNodeLsn is the previous version of the node to be counted as
     * obsolete, or NULL_LSN if the item is not a node or has no old LSN.
     * @param oldNodeSize is the log size of the previous version of the node
     * when oldNodeLsn is not NULL_LSN and the old node is an LN.  For old INs,
     * zero must be specified.
     * @return LSN of the new log entry
     */
    private long log(LogEntry item,
                     boolean isProvisional,
                     boolean flushRequired,
                     boolean fsyncRequired,
		     boolean forceNewLogFile,
		     boolean backgroundIO,
                     long oldNodeLsn,
                     int oldNodeSize)
	throws DatabaseException {

	if (readOnly) {
	    return DbLsn.NULL_LSN;
	}

        boolean marshallOutsideLatch =
            item.getLogType().marshallOutsideLatch();
        ByteBuffer marshalledBuffer = null;
        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        LogResult logResult = null;
        boolean shouldReplicate = envImpl.isReplicated() &&
            item.getLogType().isTypeReplicated();

        try {

            /* 
             * If possible, marshall this item outside the log write latch to
             * allow greater concurrency by shortening the write critical
             * section.  Note that the header may only be created during
             * marshalling because it calls item.getSize().
             */
            LogEntryHeader header = null;

            if (marshallOutsideLatch) {
                header = new LogEntryHeader(item,
                                            isProvisional,
                                            shouldReplicate);
                marshalledBuffer = marshallIntoBuffer(header,
                                                      item,
                                                      isProvisional,
                                                      shouldReplicate);
            }

            logResult = logItem(header, item, isProvisional, flushRequired,
                                forceNewLogFile, oldNodeLsn, oldNodeSize,
                                marshallOutsideLatch, marshalledBuffer,
                                tracker, shouldReplicate);

        } catch (BufferOverflowException e) {

            /* 
             * A BufferOverflowException may be seen when a thread is
             * interrupted in the middle of the log and the nio direct buffer
             * is mangled is some way by the NIO libraries. JE applications
             * should refrain from using thread interrupt as a thread
             * communications mechanism because nio behavior in the face of
             * interrupts is uncertain. See SR [#10463].
             *
             * One way or another, this type of io exception leaves us in an
             * unworkable state, so throw a run recovery exception.
             */
            throw new RunRecoveryException(envImpl, e);
        } catch (IOException e) {

            /*
             * Other IOExceptions, such as out of disk conditions, should
             * notify the application but leave the environment in workable
             * condition.
             */
            throw new DatabaseException(Tracer.getStackTrace(e), e);
        }

        /*
         * Finish up business outside of the log write latch critical section.
         */

        /* 
	 * If this logged object needs to be fsynced, do so now using the group
	 * commit mechanism.
         */
        if (fsyncRequired) {
            fileManager.groupSync();
        }

        /* 
         * Periodically, as a function of how much data is written, ask the
	 * checkpointer or the cleaner to wake up.
         */
        envImpl.getCheckpointer().wakeupAfterWrite();
        if (logResult.wakeupCleaner) {
            tracker.activateCleaner();
        }

        /* Update background writes. */
        if (backgroundIO) {
            envImpl.updateBackgroundWrites
                (logResult.entrySize, logBufferPool.getLogBufferSize());
        }

        return logResult.currentLsn;
    }

    abstract protected LogResult logItem(LogEntryHeader header,
                                         LogEntry item,
                                         boolean isProvisional,
                                         boolean flushRequired,
					 boolean forceNewLogFile,
                                         long oldNodeLsn,
                                         int oldNodeSize,
                                         boolean marshallOutsideLatch,
                                         ByteBuffer marshalledBuffer,
                                         UtilizationTracker tracker,
                                         boolean shouldReplicate)
        throws IOException, DatabaseException;

    /**
     * Called within the log write critical section. 
     */
    protected LogResult logInternal(LogEntryHeader header,
                                    LogEntry item,
                                    boolean isProvisional,
                                    boolean flushRequired,
				    boolean forceNewLogFile,
                                    long oldNodeLsn,
                                    int oldNodeSize,
                                    boolean marshallOutsideLatch,
                                    ByteBuffer marshalledBuffer,
                                    UtilizationTracker tracker,
                                    boolean shouldReplicate)
        throws IOException, DatabaseException {

        /* 
         * Do obsolete tracking before marshalling a FileSummaryLN into the log
         * buffer so that a FileSummaryLN counts itself.  countObsoleteNode
         * must be called before computing the entry size, since it can change
         * the size of a FileSummaryLN entry that we're logging
         */
        LogEntryType entryType = item.getLogType();
        if (oldNodeLsn != DbLsn.NULL_LSN) {
            tracker.countObsoleteNode(oldNodeLsn, entryType, oldNodeSize);
        }

        /*
         * If an item must be protected within the log write latch for 
         * marshalling, take care to also calculate its size in the protected 
         * section. Note that we have to get the size *before* marshalling so
         * that the currentLsn and size are correct for utilization tracking.
         */
        int entrySize;
        if (marshallOutsideLatch) {
            entrySize = marshalledBuffer.limit();
            assert header != null;
        } else {
            assert header == null;
            header = new LogEntryHeader(item, isProvisional, shouldReplicate);
            entrySize = header.getSize() + header.getItemSize();
        }

        /* 
         * Get the next free slot in the log, under the log write latch.  Bump
         * the LSN values, which gives us a valid previous pointer, which is
         * part of the log entry header. That's why doing the checksum must be
         * in the log write latch -- we need to bump the LSN first, and bumping
         * the LSN must be done within the log write latch.
         */
	if (forceNewLogFile) {
	    fileManager.forceNewLogFile();
	}

        boolean flippedFile = fileManager.bumpLsn(entrySize);
        long currentLsn = DbLsn.NULL_LSN;
        boolean wakeupCleaner = false;
	boolean usedTemporaryBuffer = false;
	boolean success = false;
        try {
            currentLsn = fileManager.getLastUsedLsn();
            
            /* 
             * countNewLogEntry and countObsoleteNodeInexact cannot change a
             * FileSummaryLN size, so they are safe to call after
             * getSizeForWrite.
             */
            wakeupCleaner =
                tracker.countNewLogEntry(currentLsn, entryType, entrySize);

            /*
             * LN deletions are obsolete immediately.  Inexact counting is
             * used to save resources because the cleaner knows that all
             * deleted LNs are obsolete.
             */
            if (item.countAsObsoleteWhenLogged()) {
                tracker.countObsoleteNodeInexact
                    (currentLsn, entryType, entrySize);
            }

            /* 
             * This item must be marshalled within the log write latch.
             */
            if (!marshallOutsideLatch) {
                marshalledBuffer = marshallIntoBuffer(header,
                                                      item,
                                                      isProvisional,
                                                      shouldReplicate);
            }

            /* Sanity check */
            if (entrySize != marshalledBuffer.limit()) {
                throw new DatabaseException(
                 "Logged item entrySize= " + entrySize +
                 " but marshalledSize=" + marshalledBuffer.limit() +
                 " type=" + entryType + " currentLsn=" +
                 DbLsn.getNoFormatString(currentLsn));
            }
                                            
            /*
             * Ask for a log buffer suitable for holding this new entry.  If
             * the current log buffer is full, or if we flipped into a new
             * file, write it to disk and get a new, empty log buffer to
             * use. The returned buffer will be latched for write.
             */
            LogBuffer useLogBuffer =
                logBufferPool.getWriteBuffer(entrySize, flippedFile);

            /* Add checksum, prev offset, vlsn to entry. */
            marshalledBuffer =
                header.addPostMarshallingInfo(envImpl,
                                              marshalledBuffer,
                                              fileManager.getPrevEntryOffset());

	    /*
	     * If the LogBufferPool buffer (useBuffer) doesn't have sufficient
	     * space (since they're fixed size), just use the temporary buffer
	     * and throw it away when we're done.  That way we don't grow the
	     * LogBuffers in the pool permanently.  We risk an OOME on this
	     * temporary usage, but we'll risk it.  [#12674]
	     */
            useLogBuffer.latchForWrite();
            try {
                ByteBuffer useBuffer = useLogBuffer.getDataBuffer();
                if (useBuffer.capacity() - useBuffer.position() < entrySize) {
                    fileManager.writeLogBuffer
                        (new LogBuffer(marshalledBuffer, currentLsn));
                    usedTemporaryBuffer = true;
                    assert useBuffer.position() == 0;
                    nTempBufferWrites++;
                } else {
                    /* Copy marshalled object into write buffer. */
                    useBuffer.put(marshalledBuffer);
                }
            } finally {
                useLogBuffer.release();
            }

            /* 
             * If this is a replicated log entry and this site is part of a
             * replication group, send this operation to other sites.
             * The replication logic takes care of deciding whether this site
             * is a master.
             */
            if (shouldReplicate) {
                envImpl.getReplicator().replicateOperation(
                                            Operation.PLACEHOLDER,
                                            marshalledBuffer);
                    
            }
	    success = true;
        } finally {
	    if (!success) {

		/* 
		 * The LSN pointer, log buffer position, and corresponding file
		 * position march in lockstep.
		 *
		 * 1. We bump the LSN.
		 * 2. We copy loggable item into the log buffer.
		 * 3. We may try to write the log buffer.
		 * 
		 * If we've failed to put the item into the log buffer (2), we
		 * need to restore old LSN state so that the log buffer doesn't
		 * have a hole. [SR #12638] If we fail after (2), we don't need
		 * to restore state, because log buffers will still match file
		 * positions.
		 */
		fileManager.restoreLastPosition();
	    }
	}
        
	/* 
	 * Tell the log buffer pool that we finished the write.  Record the
	 * LSN against this logbuffer, and write the buffer to disk if
	 * needed.
	 */
	if (!usedTemporaryBuffer) {
	    logBufferPool.writeCompleted(currentLsn, flushRequired);
	}

        /*
         * If the txn is not null, the first item is an LN. Update the txn with
         * info about the latest LSN. Note that this has to happen within the
         * log write latch.
         */
        item.postLogWork(currentLsn);

        return new LogResult(currentLsn, wakeupCleaner, entrySize);
    }

    /**
     * Serialize a loggable object into this buffer.
     */
    private ByteBuffer marshallIntoBuffer(LogEntryHeader header,
                                          LogEntry item,
                                          boolean isProvisional,
                                          boolean shouldReplicate)
	throws DatabaseException {

        int entrySize = header.getSize() + header.getItemSize();

        ByteBuffer destBuffer = ByteBuffer.allocate(entrySize);
        header.writeToLog(destBuffer);

        /* Put the entry in. */
        item.writeEntry(header, destBuffer);

        /* Some entries (LNs) save the last logged size. */
        item.setLastLoggedSize(entrySize);

        /* Set the limit so it can be used as the size of the entry. */
        destBuffer.flip();

        return destBuffer;
    }

    /**
     * Serialize a log entry into this buffer with proper entry header. Return
     * it ready for a copy.
     */
    ByteBuffer putIntoBuffer(LogEntry item,
                             long prevLogEntryOffset)
	throws DatabaseException {

        LogEntryHeader header = new LogEntryHeader(item,
                                                   false,  // isProvisional,
                                                   false); // shouldReplicate

        ByteBuffer destBuffer =
	    marshallIntoBuffer(header,
                               item, 
                               false,  // isProvisional
                               false); // shouldReplicate
        
        return header.addPostMarshallingInfo(envImpl,
                                             destBuffer,
                                             0); // lastOffset
    }


    /*
     * Reading from the log.
     */

    /**
     * Instantiate all the objects in the log entry at this LSN.
     * @param lsn location of entry in log.
     * @return log entry that embodies all the objects in the log entry.
     */
    public LogEntry getLogEntry(long lsn) 
        throws DatabaseException {

	/*
	 * Fail loudly if the environment is invalid.  A RunRecoveryException
	 * must have occurred.
	 */
	envImpl.checkIfInvalid();

        /*
         * Get a log source for the log entry which provides an abstraction
         * that hides whether the entry is in a buffer or on disk. Will
         * register as a reader for the buffer or the file, which will take a
         * latch if necessary.
         */
        LogSource logSource = getLogSource(lsn);

        /* Read the log entry from the log source. */
        return getLogEntryFromLogSource(lsn, logSource);
    }

    LogEntry getLogEntry(long lsn, RandomAccessFile file)
        throws DatabaseException {

        return getLogEntryFromLogSource
	    (lsn, new FileSource(file, readBufferSize, fileManager));
    }

    /**
     * Instantiate all the objects in the log entry at this LSN. This will
     * release the log source at the first opportunity.
     *
     * @param lsn location of entry in log
     * @return log entry that embodies all the objects in the log entry
     */
    private LogEntry getLogEntryFromLogSource(long lsn,
                                              LogSource logSource) 
        throws DatabaseException {

        try {

            /* 
             * Read the log entry header into a byte buffer. This assumes
             * that the minimum size of this byte buffer (determined by
             * je.log.faultReadSize) is always >= the maximum log entry header.
             */
            long fileOffset = DbLsn.getFileOffset(lsn);
            ByteBuffer entryBuffer = logSource.getBytes(fileOffset);
            assert ((entryBuffer.limit() - entryBuffer.position()) >=
                    LogEntryHeader.MAX_HEADER_SIZE);

            /* Read the header */
            LogEntryHeader header =
                new LogEntryHeader(envImpl,
                                   entryBuffer,
                                   false); //anticipateChecksumErrors
            header.readVariablePortion(entryBuffer);

            ChecksumValidator validator = null;
            if (doChecksumOnRead) {
                /* Add header to checksum bytes */
                validator = new ChecksumValidator();
                int headerSizeMinusChecksum = header.getSizeMinusChecksum();
                int itemStart = entryBuffer.position();
                entryBuffer.position(itemStart -
                                     headerSizeMinusChecksum);
                validator.update(envImpl,
                                 entryBuffer,
                                 headerSizeMinusChecksum,
                                 false); // anticipateChecksumErrors
                entryBuffer.position(itemStart);
            }

            /*
             * Now that we know the size, read the rest of the entry
             * if the first read didn't get enough.
             */
            int itemSize = header.getItemSize();
            if (entryBuffer.remaining() < itemSize) {
                entryBuffer = logSource.getBytes(fileOffset + header.getSize(),
                                                 itemSize);
                nRepeatFaultReads++;
            }

            /*
             * Do entry validation. Run checksum before checking the entry
             * type, it will be the more encompassing error.
             */
            if (doChecksumOnRead) {
                /* Check the checksum first. */
                validator.update(envImpl, entryBuffer, itemSize, false);
                validator.validate(envImpl, header.getChecksum(), lsn);
            }

            assert LogEntryType.isValidType(header.getType()):
                "Read non-valid log entry type: " + header.getType();

            /* Read the entry. */
            LogEntry logEntry = 
                LogEntryType.findType(header.getType(),
                                      header.getVersion()).getNewLogEntry();
            logEntry.readEntry(header,
                               entryBuffer,
                               true);  // readFullItem

            /* Some entries (LNs) save the last logged size. */
            logEntry.setLastLoggedSize(itemSize + header.getSize());

            /* For testing only; generate a read io exception. */
            if (readHook != null) {
                readHook.doIOHook();
            }

            /* 
             * Done with the log source, release in the finally clause.  Note
             * that the buffer we get back from logSource is just a duplicated
             * buffer, where the position and state are copied but not the
             * actual data. So we must not release the logSource until we are
             * done marshalling the data from the buffer into the object
             * itself.
             */
            return logEntry;
        } catch (DatabaseException e) {

            /* 
	     * Propagate DatabaseExceptions, we want to preserve any subtypes
             * for downstream handling.
             */
            throw e;
        } catch (ClosedChannelException e) {

            /* 
             * The channel should never be closed. It may be closed because
             * of an interrupt received by another thread. See SR [#10463]
             */
            throw new RunRecoveryException(envImpl,
                                           "Channel closed, may be "+
                                           "due to thread interrupt",
                                           e);
        } catch (Exception e) {
            throw new DatabaseException(e);
        } finally {
            if (logSource != null) {
                logSource.release();
            }
        }
    }

    /**
     * Fault in the first object in the log entry log entry at this LSN.
     * @param lsn location of object in log
     * @return the object in the log
     */
    public Object get(long lsn)
        throws DatabaseException {

        LogEntry entry = getLogEntry(lsn);
        return entry.getMainItem();
    }

    /**
     * Find the LSN, whether in a file or still in the log buffers.
     * Is public for unit testing.
     */
    public LogSource getLogSource(long lsn)
        throws DatabaseException {

        /*
	 * First look in log to see if this LSN is still in memory.
	 */
        LogBuffer logBuffer = logBufferPool.getReadBuffer(lsn);

        if (logBuffer == null) {
            try {
                /* Not in the in-memory log -- read it off disk. */
                return new FileHandleSource
                    (fileManager.getFileHandle(DbLsn.getFileNumber(lsn)),
                     readBufferSize,
		     fileManager);
            } catch (LogFileNotFoundException e) {
                /* Add LSN to exception message. */
                throw new LogFileNotFoundException
		    (DbLsn.getNoFormatString(lsn) + ' ' + e.getMessage());
            }
        } else {
            return logBuffer;
        }
    }

    /**
     * Flush all log entries, fsync the log file.
     */
    public void flush()
	throws DatabaseException {

	if (!readOnly) {
            flushInternal();
            fileManager.syncLogEnd();
	}
    }

    /**
     * May be used to avoid sync to speed unit tests.
     */
    public void flushNoSync()
	throws DatabaseException {

	if (!readOnly) {
            flushInternal();
	}
    }

    abstract protected void flushInternal()
        throws LogException, DatabaseException;


    public void loadStats(StatsConfig config, EnvironmentStats stats) 
        throws DatabaseException {

        stats.setNRepeatFaultReads(nRepeatFaultReads);
	stats.setNTempBufferWrites(nTempBufferWrites);
        if (config.getClear()) {
            nRepeatFaultReads = 0;
            nTempBufferWrites = 0;
        }

        logBufferPool.loadStats(config, stats);
        fileManager.loadStats(config, stats);
    }

    /**
     * Returns a tracked summary for the given file which will not be flushed.
     * Used for watching changes that occur while a file is being cleaned.
     */
    abstract public TrackedFileSummary getUnflushableTrackedSummary(long file)
        throws DatabaseException;

    protected TrackedFileSummary getUnflushableTrackedSummaryInternal(long file)
        throws DatabaseException {

        return envImpl.getUtilizationTracker().
                       getUnflushableTrackedSummary(file);
    }

    /**
     * Removes the tracked summary for the given file.
     */
    abstract public void removeTrackedFile(TrackedFileSummary tfs)
        throws DatabaseException;

    protected void removeTrackedFileInternal(TrackedFileSummary tfs) {
        tfs.reset();
    }

    /**
     * Count node as obsolete under the log write latch.  This is done here
     * because the log write latch is managed here, and all utilization
     * counting must be performed under the log write latch.
     */
    abstract public void countObsoleteNode(long lsn,
                                           LogEntryType type,
                                           int size)
        throws DatabaseException;

    protected void countObsoleteNodeInternal(UtilizationTracker tracker,
                                             long lsn,
                                             LogEntryType type,
                                             int size)
        throws DatabaseException {
        
        tracker.countObsoleteNode(lsn, type, size);
    }

    /**
     * Counts file summary info under the log write latch.
     */
    abstract public void countObsoleteNodes(TrackedFileSummary[] summaries)
        throws DatabaseException;

    protected void countObsoleteNodesInternal(UtilizationTracker tracker,
                                              TrackedFileSummary[] summaries)
        throws DatabaseException {
        
        for (int i = 0; i < summaries.length; i += 1) {
            TrackedFileSummary summary = summaries[i];
            tracker.addSummary(summary.getFileNumber(), summary);
        }
    }

    /**
     * Counts the given obsolete IN LSNs under the log write latch.
     */
    abstract public void countObsoleteINs(List lsnList)
        throws DatabaseException;

    protected void countObsoleteINsInternal(List lsnList)
        throws DatabaseException {
        
        UtilizationTracker tracker = envImpl.getUtilizationTracker();

        for (int i = 0; i < lsnList.size(); i += 1) {
            Long offset = (Long) lsnList.get(i);
            tracker.countObsoleteNode
                (offset.longValue(), LogEntryType.LOG_IN, 0);
        }
    }

    /* For unit testing only. */
    public void setReadHook(TestHook hook) {
        readHook = hook;
    }

    /** 
     * LogResult holds the multivalue return from logInternal.
     */
    static class LogResult {
        long currentLsn;
        boolean wakeupCleaner;
        int entrySize;

        LogResult(long currentLsn,
                  boolean wakeupCleaner,
                  int entrySize) {
            this.currentLsn = currentLsn;
            this.wakeupCleaner = wakeupCleaner;
            this.entrySize = entrySize;
        }
    }
}
