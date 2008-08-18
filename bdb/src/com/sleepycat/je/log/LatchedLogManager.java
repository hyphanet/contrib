/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LatchedLogManager.java,v 1.28 2008/05/15 01:52:41 linda Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.util.List;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.cleaner.LocalUtilizationTracker;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;

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

    void serialLog(LogItem[] itemArray, LogContext context)
        throws IOException, DatabaseException {

        logWriteLatch.acquire();
        try {
            serialLogInternal(itemArray, context);
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
    public void countObsoleteNode(long lsn,
                                  LogEntryType type,
                                  int size,
                                  DatabaseImpl nodeDb)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            countObsoleteNodeInternal(lsn, type, size, nodeDb);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#transferToUtilizationTracker
     */
    public void transferToUtilizationTracker(LocalUtilizationTracker
                                             localTracker)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            transferToUtilizationTrackerInternal(localTracker);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#countObsoleteINs
     */
    public void countObsoleteINs(List<Long> lsnList, DatabaseImpl nodeDb)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            countObsoleteINsInternal(lsnList, nodeDb);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#countObsoleteDb
     */
    public void countObsoleteDb(DatabaseImpl db)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            countObsoleteDbInternal(db);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#removeDbFileSummary
     */
    public boolean removeDbFileSummary(DatabaseImpl db, Long fileNum)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            return removeDbFileSummaryInternal(db, fileNum);
        } finally {
            logWriteLatch.release();
        }
    }

    /**
     * @see LogManager#loadEndOfLogStat
     */
    public void loadEndOfLogStat(EnvironmentStats stats)
        throws DatabaseException {

        logWriteLatch.acquire();
        try {
            loadEndOfLogStatInternal(stats);
        } finally {
            logWriteLatch.release();
        }
    }
}
