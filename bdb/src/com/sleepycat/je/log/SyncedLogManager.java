/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: SyncedLogManager.java,v 1.15 2006/09/12 19:16:52 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * The SyncedLogManager uses the synchronized keyword to implement protected
 * regions.
 */
public class SyncedLogManager extends LogManager {

    /**
     * There is a single log manager per database environment.
     */
    public SyncedLogManager(EnvironmentImpl envImpl,
                            boolean readOnly)
        throws DatabaseException {

        super(envImpl, readOnly);
    }

    protected LogResult logItem(LoggableObject item,
                                boolean isProvisional,
                                boolean flushRequired,
                                boolean forceNewLogFile,
                                long oldNodeLsn,
                                boolean marshallOutsideLatch,
                                ByteBuffer marshalledBuffer,
                                UtilizationTracker tracker)
        throws IOException, DatabaseException {

        synchronized (logWriteLatch) {
            return logInternal(item, isProvisional,
                               flushRequired, forceNewLogFile, oldNodeLsn, 
                               marshallOutsideLatch, marshalledBuffer,
                               tracker);
        }
    }

    protected void flushInternal() 
        throws LogException, DatabaseException {

        try {
            synchronized (logWriteLatch) {
                logBufferPool.writeBufferToFile(0);
            }
        } catch (IOException e) {
            throw new LogException(e.getMessage());
        } 
    }
    
    /**
     * @see LogManager#getUnflushableTrackedSummary
     */
    public TrackedFileSummary getUnflushableTrackedSummary(long file)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            return getUnflushableTrackedSummaryInternal(file);
        }
    }

    /**
     * @see LogManager#countObsoleteLNs
     */
    public void countObsoleteNode(long lsn, LogEntryType type)
        throws DatabaseException {

        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        synchronized (logWriteLatch) {
            countObsoleteNodeInternal(tracker, lsn, type);
        }
    }

    /**
     * @see LogManager#countObsoleteNodes
     */
    public void countObsoleteNodes(TrackedFileSummary[] summaries)
        throws DatabaseException {

        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        synchronized (logWriteLatch) {
            countObsoleteNodesInternal(tracker, summaries);
        }
    }

    /**
     * @see LogManager#countObsoleteINs
     */
    public void countObsoleteINs(List lsnList)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            countObsoleteINsInternal(lsnList);
        }
    }
}
