/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogBufferPool.java,v 1.72.2.1 2007/02/01 14:49:47 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;

/**
 * LogBufferPool keeps a set of log buffers.
 */
class LogBufferPool {
    private static final String DEBUG_NAME = LogBufferPool.class.getName();

    private EnvironmentImpl envImpl = null;
    private int logBufferSize;      // size of each log buffer
    private LinkedList bufferPool;  // List of log buffers 

    /* Buffer that holds the current log end. All writes go to this buffer. */
    private LogBuffer currentWriteBuffer;

    private FileManager fileManager;

    /* Stats */
    private long nNotResident = 0;  // had to be instantiated from an lsn
    private long nCacheMiss = 0;    // had to retrieve from disk
    private boolean runInMemory;

    /*
     * bufferPoolLatch is synchronizes access and changes to the buffer pool.
     * Related latches are the log write latch in LogManager and the read
     * latches in each log buffer. The log write latch is always taken before
     * the bufferPoolLatch. The bufferPoolLatch is always taken before any
     * logBuffer read latch. When faulting in an object from the log, the order
     * of latching is:
     *          bufferPoolLatch.acquire()
     *          LogBuffer read latch acquire();
     *          bufferPoolLatch.release();
     *          LogBuffer read latch release()
     * bufferPoolLatch is also used to protect assignment to the
     * currentWriteBuffer field.
     */
    private Latch bufferPoolLatch;

    LogBufferPool(FileManager fileManager,
                  EnvironmentImpl envImpl)
        throws DatabaseException {
        
        this.fileManager = fileManager;
        this.envImpl = envImpl;
        bufferPoolLatch =
	    LatchSupport.makeLatch(DEBUG_NAME + "_FullLatch", envImpl);

        /* Configure the pool. */
        DbConfigManager configManager = envImpl.getConfigManager();
        runInMemory = envImpl.isMemOnly();
        reset(configManager);

        /* Current buffer is the active buffer that writes go into. */
        currentWriteBuffer = (LogBuffer) bufferPool.getFirst();
    }

    final int getLogBufferSize() {
        return logBufferSize;
    }

    /**
     * Initialize the pool at construction time and when the cache is resized.
     * This method is called after the memory budget has been calculated.
     */
    void reset(DbConfigManager configManager)
        throws DatabaseException {

        /*
         * When running in memory, we can't clear the existing pool and
         * changing the buffer size is not very useful, so just return.
         */
        if (runInMemory && bufferPool != null) {
            return;
        }

        /*
         * Based on the log budget, figure the number and size of
         * log buffers to use.
         */
        int numBuffers =
	    configManager.getInt(EnvironmentParams.NUM_LOG_BUFFERS);
        long logBufferBudget = envImpl.getMemoryBudget().getLogBufferBudget();

        /* Buffers must be int sized. */
        int newBufferSize = (int) logBufferBudget / numBuffers; 

        /* list of buffers that are available for log writing */
        LinkedList newPool = new LinkedList();

        /*
         * If we're running in memory only, don't pre-allocate all the buffers.
         * This case only occurs when called from the constructor.
         */
        if (runInMemory) {
            numBuffers = 1;
        }

        for (int i = 0; i < numBuffers; i++) {
            newPool.add(new LogBuffer(newBufferSize, envImpl));
        }

        /*
         * The following applies when this method is called to reset the pool
         * when an existing pool is in use:
         * - The old pool will no longer be referenced.
         * - Buffers being read in the old pool will be no longer referenced
         * after the read operation is complete.
         * - The currentWriteBuffer field is not changed here; it will be no
         * longer referenced after it is written to the file and a new
         * currentWriteBuffer is assigned.
         * - The logBufferSize can be changed now because it is only used for
         * allocating new buffers; it is not used as the size of the
         * currentWriteBuffer.
         */
        bufferPoolLatch.acquire();
        bufferPool = newPool;
        logBufferSize = newBufferSize;
        bufferPoolLatch.release();
    }

    /**
     * Get a log buffer for writing sizeNeeded bytes. If currentWriteBuffer is
     * too small or too full, flush currentWriteBuffer and get a new one.
     * Called within the log write latch.
     *
     * @return a buffer that can hold sizeNeeded bytes.
     */
    LogBuffer getWriteBuffer(int sizeNeeded, boolean flippedFile)
        throws IOException, DatabaseException {
        
        /*
         * We need a new log buffer either because this log buffer is full, or
         * the LSN has marched along to the next file.  Each log buffer only
         * holds entries that belong to a single file.  If we've flipped over
         * into the next file, we'll need to get a new log buffer even if the
         * current one has room.
         */
	if ((!currentWriteBuffer.hasRoom(sizeNeeded)) || flippedFile) {

	    /*
	     * Write the currentWriteBuffer to the file and reset
	     * currentWriteBuffer.
	     */
	    writeBufferToFile(sizeNeeded);
	}

	if (flippedFile) {
	    /* Now that the old buffer has been written to disk, fsync. */
	    if (!runInMemory) {
		fileManager.syncLogEndAndFinishFile();
	    }
	}

        return currentWriteBuffer;
    }

    /**
     * Write the contents of the currentWriteBuffer to disk.  Leave this buffer
     * in memory to be available to would be readers.  Set up a new
     * currentWriteBuffer. Assumes the log write latch is held.
     * 
     * @param sizeNeeded is the size of the next object we need to write to
     * the log. May be 0 if this is called on behalf of LogManager.flush().
     */
    void writeBufferToFile(int sizeNeeded)
        throws IOException, DatabaseException {

        int bufferSize =
	    ((logBufferSize > sizeNeeded) ? logBufferSize : sizeNeeded);

        /* We're done with the buffer, flip to make it readable. */
        currentWriteBuffer.latchForWrite();
        LogBuffer latchedBuffer = currentWriteBuffer;
        try {
            ByteBuffer currentByteBuffer = currentWriteBuffer.getDataBuffer();
            int savePosition = currentByteBuffer.position();
            int saveLimit = currentByteBuffer.limit();
            currentByteBuffer.flip();

            /* Dispose of it and get a new buffer for writing. */
            if (runInMemory) {
                /* We're done with the current buffer. */
                latchedBuffer.release();
                latchedBuffer = null;
                /* We're supposed to run in-memory, allocate another buffer. */
                bufferPoolLatch.acquire();
                currentWriteBuffer = new LogBuffer(bufferSize, envImpl);
                bufferPool.add(currentWriteBuffer);
                bufferPoolLatch.release();
            } else {

                /* 
                 * If we're configured for writing (not memory-only situation),
                 * write this buffer to disk and find a new buffer to use.
                 */
                try {
                    fileManager.writeLogBuffer(currentWriteBuffer);

                    /* Rewind so readers can see this. */
                    currentWriteBuffer.getDataBuffer().rewind();

                    /* We're done with the current buffer. */
                    latchedBuffer.release();
                    latchedBuffer = null;

                    /*
                     * Now look in the linked list for a buffer of the right
                     * size.
                     */
                    LogBuffer nextToUse = null;
                    try {
                        bufferPoolLatch.acquire();
                        Iterator iter = bufferPool.iterator();
                        nextToUse = (LogBuffer) iter.next();

                        boolean done = bufferPool.remove(nextToUse);        
                        assert done;
                        nextToUse.reinit(); 

                        /* Put the nextToUse buffer at the end of the queue. */
                        bufferPool.add(nextToUse);

                        /* Assign currentWriteBuffer with the latch held. */
                        currentWriteBuffer = nextToUse;
                    } finally {
                        bufferPoolLatch.releaseIfOwner();
                    }
                } catch (DatabaseException DE) {
                    currentByteBuffer.position(savePosition);
                    currentByteBuffer.limit(saveLimit);
                    throw DE;
                }
            }
        } finally {
            if (latchedBuffer != null) {
                latchedBuffer.release();
            }
        }
    }

    /**
     * A loggable object has been freshly marshalled into the write log buffer.
     * 1. Update buffer so it knows what LSNs it contains.
     * 2. If this object requires a flush, write this buffer out to the 
     * backing file.
     * Assumes log write latch is held.
     */
    void writeCompleted(long lsn, boolean flushRequired)
        throws DatabaseException, IOException  {

        currentWriteBuffer.registerLsn(lsn);
        if (flushRequired) {
            writeBufferToFile(0);
        }
    }

    /**
     * Find a buffer that holds this LSN.
     * @return the buffer that contains this LSN, latched and ready to
     *         read, or return null.
     */
    LogBuffer getReadBuffer(long lsn)
	throws DatabaseException {

        LogBuffer foundBuffer = null;

        bufferPoolLatch.acquire();
	try {
	    nNotResident++;
	    Iterator iter = bufferPool.iterator();
	    while (iter.hasNext()) {
		LogBuffer l = (LogBuffer) iter.next();
		if (l.containsLsn(lsn)) {
		    foundBuffer = l;
		    break;
		}
	    }

	    /*
	     * Check the currentWriteBuffer separately, since if the pool was
	     * recently reset it will not be in the pool.
	     */
	    if (foundBuffer == null &&
		currentWriteBuffer.containsLsn(lsn)) {
		foundBuffer = currentWriteBuffer;
	    }

	    if (foundBuffer == null) {
		nCacheMiss++;
	    }

	} finally {
	    bufferPoolLatch.releaseIfOwner();
	}

        if (foundBuffer == null) {
            return null;
        } else {
            return foundBuffer;
        }
    }

    void loadStats(StatsConfig config, EnvironmentStats stats) 
        throws DatabaseException {

        stats.setNCacheMiss(nCacheMiss);
        stats.setNNotResident(nNotResident);
        if (config.getClear()) {
            nCacheMiss = 0;
            nNotResident = 0;
        }

        /* Also return buffer pool memory usage */
        bufferPoolLatch.acquire();
        long bufferBytes = 0;
        int nLogBuffers = 0;
        try {
            Iterator iter = bufferPool.iterator();
            while (iter.hasNext()) {
                LogBuffer l = (LogBuffer) iter.next();
                nLogBuffers++;
                bufferBytes += l.getCapacity();
            }
        } finally {
            bufferPoolLatch.release();
        }
        stats.setNLogBuffers(nLogBuffers);
        stats.setBufferBytes(bufferBytes);
    }
}
