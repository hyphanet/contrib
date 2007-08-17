/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LatchedLogManager.java,v 1.17.2.2 2007/06/13 03:55:37 mark Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;

/**
 * The LatchedLogManager uses the latches to implement critical sections.
 */
public class LatchedLogManager extends LogManager {
                                           
    /**
     * There is a single log manager per database environment.
     */
    public LatchedLogManager(EnvironmentImpl envImpl,
                            boolean readOnly)
        throws DatabaseException {

        super(envImpl, readOnly);
    }

    protected LogResult logItem(LogEntryHeader header,
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

        logWriteLatch.acquire();
        try {
            return logInternal
                (header, item, isProvisional, flushRequired, forceNewLogFile,
                 oldNodeLsn, oldNodeSize, marshallOutsideLatch,
                 marshalledBuffer, tracker, shouldReplicate);
        } finally {
            logWriteLatch.release();
        }
    }

    protected void flushInternal() 
        throws LogException, DatabaseException {

        logWriteLatch.acquire();
        try {
            logBufferPool.writeBufferToFile(0);
        } catch (IOException e) {
            throw new LogException(e.getMessage());
        } finally {
            logWriteLatch.release();
        }
    }
    
    /**
     * @see LogManager#getUnflusableTrackedSummary
     */
    public TrackedFileSummary getUnflushableTrackedSummary(long file)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            return getUnflushableTrackedSummaryInternal(file);
        } finally {
            logWriteLatch.release();
        }
    }
    
    /**
     * @see LogManager#removeTrackedFile
     */
    public void removeTrackedFile(TrackedFileSummary tfs)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            removeTrackedFileInternal(tfs);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#countObsoleteLNs
     */
    public void countObsoleteNode(long lsn, LogEntryType type, int size)
        throws DatabaseException {

        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        logWriteLatch.acquire();
        try {
            countObsoleteNodeInternal(tracker, lsn, type, size);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#countObsoleteNodes
     */
    public void countObsoleteNodes(TrackedFileSummary[] summaries)
        throws DatabaseException {

        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        logWriteLatch.acquire();
        try {
            countObsoleteNodesInternal(tracker, summaries);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#countObsoleteINs
     */
    public void countObsoleteINs(List lsnList)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            countObsoleteINsInternal(lsnList);
        } finally {
            logWriteLatch.release();
        }
    }
}
