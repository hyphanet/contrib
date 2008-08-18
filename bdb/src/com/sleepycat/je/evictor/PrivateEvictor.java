/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: PrivateEvictor.java,v 1.4 2008/01/07 14:28:48 cwl Exp $
 */

package com.sleepycat.je.evictor;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.IN;

/**
 * The standard Evictor that operates on the INList for a single environment.
 * A single iterator over the INList is used to implement getNextIN.
 */
public class PrivateEvictor extends Evictor {

    private EnvironmentImpl envImpl;

    private Iterator<IN> scanIter;

    public PrivateEvictor(EnvironmentImpl envImpl, String name)
        throws DatabaseException {

        super(envImpl, name);
        this.envImpl = envImpl;
        scanIter = null;
    }

    @Override
    public void loadStats(StatsConfig config, EnvironmentStats stat)
        throws DatabaseException {

        stat.setNSharedCacheEnvironments(0);
        super.loadStats(config, stat);
    }

    @Override
    public void onWakeup()
        throws DatabaseException {

        if (!envImpl.isClosed()) {
            super.onWakeup();
        }
    }

    /**
     * Standard daemon method to set envImpl to null.
     */
    public void clearEnv() {
        envImpl = null;
    }

    /**
     * Do nothing.
     */
    public void noteINListChange(int nINs) {
    }

    /**
     * Only supported by SharedEvictor.
     */
    public void addEnvironment(EnvironmentImpl envImpl) {
        throw new UnsupportedOperationException();
    }

    /**
     * Only supported by SharedEvictor.
     */
    public void removeEnvironment(EnvironmentImpl envImpl) {
        throw new UnsupportedOperationException();
    }

    /**
     * Only supported by SharedEvictor.
     */
    public boolean checkEnvs(Set<EnvironmentImpl> envs) {
        throw new UnsupportedOperationException();
    }

    /**
     * Standard logging is supported by PrivateEvictor.
     */
    Logger getLogger() {
        return envImpl.getLogger();
    }

    /**
     * Initializes the iterator, and performs UtilizationTracker eviction once
     * per batch.
     */
    long startBatch()
        throws DatabaseException {

        if (scanIter == null) {
            scanIter = envImpl.getInMemoryINs().iterator();
        }

        /* Evict utilization tracking info without holding any latches. */
        return envImpl.getUtilizationTracker().evictMemory();
    }

    /**
     * Returns the simple INList size.
     */
    int getMaxINsPerBatch() {
        return envImpl.getInMemoryINs().getSize();
    }

    /**
     * Returns the next IN, wrapping if necessary.
     */
    IN getNextIN() {
        if (envImpl.getMemoryBudget().isTreeUsageAboveMinimum()) {
            if (!scanIter.hasNext()) {
                scanIter = envImpl.getInMemoryINs().iterator();
            }
            return scanIter.hasNext() ? scanIter.next() : null;
        } else {
            return null;
        }
    }

    /* For unit testing only. */
    Iterator<IN> getScanIterator() {
        return scanIter;
    }

    /* For unit testing only. */
    void setScanIterator(Iterator<IN> iter) {
        scanIter = iter;
    }
}
