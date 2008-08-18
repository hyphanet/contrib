/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: SyncedLogManager.java,v 1.28 2008/05/15 01:52:41 linda Exp $
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

    void serialLog(LogItem[] itemArray, LogContext context)
        throws IOException, DatabaseException {

        synchronized (logWriteLatch) {
            serialLogInternal(itemArray, context);
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
     * @see LogManager#removeTrackedFile
     */
    public void removeTrackedFile(TrackedFileSummary tfs)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            removeTrackedFileInternal(tfs);
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

        synchronized (logWriteLatch) {
            countObsoleteNodeInternal(lsn, type, size, nodeDb);
        }
    }

    /**
     * @see LogManager#transferToUtilizationTracker
     */
    public void transferToUtilizationTracker(LocalUtilizationTracker
                                             localTracker)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            transferToUtilizationTrackerInternal(localTracker);
        }
    }

    /**
     * @see LogManager#countObsoleteINs
     */
    public void countObsoleteINs(List<Long> lsnList, DatabaseImpl nodeDb)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            countObsoleteINsInternal(lsnList, nodeDb);
        }
    }

    /**
     * @see LogManager#countObsoleteDb
     */
    public void countObsoleteDb(DatabaseImpl db)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            countObsoleteDbInternal(db);
        }
    }

    /**
     * @see LogManager#removeDbFileSummary
     */
    public boolean removeDbFileSummary(DatabaseImpl db, Long fileNum)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            return removeDbFileSummaryInternal(db, fileNum);
        }
    }

    /**
     * @see LogManager#loadEndOfLogStat
     */
    public void loadEndOfLogStat(EnvironmentStats stats)
        throws DatabaseException {

        synchronized (logWriteLatch) {
            loadEndOfLogStatInternal(stats);
        }
    }
}
