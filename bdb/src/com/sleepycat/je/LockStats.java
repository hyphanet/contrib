/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LockStats.java,v 1.31 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

import com.sleepycat.je.latch.LatchStats;

/**
 * Lock statistics for a database environment.
 *
 * <p> Note that some of the lock statistics may be expensive to obtain because
 * the lock table is unavailable while the statistics are gathered. These
 * expensive statistics are only provided if {@link
 * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
 * called with a StatsConfig parameter that has been configured for "slow"
 * stats.
 */
public class LockStats implements Serializable {

    /**
     * Total locks currently in lock table.
     */
    private int nTotalLocks;

    /**
     * Total read locks currently held.
     */
    private int nReadLocks;

    /**
     * Total write locks currently held.
     */
    private int nWriteLocks;

    /**
     * Total transactions waiting for locks.
     */
    private int nWaiters;

    /**
     * Total lock owners in lock table.
     */
    private int nOwners;

    /**
     * Number of times a lock request was made.
     */
    private long nRequests;

    /**
     * Number of times a lock request blocked.
     */
    private long nWaits;

    /**
     * LockTable latch stats.
     */
    private LatchStats lockTableLatchStats;

    /**
     * Total lock owners in lock table.  Only provided when {@link
     * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
     * called in "slow" mode.
     */
    public int getNOwners() {
        return nOwners;
    }

    /**
     * Total read locks currently held.  Only provided when {@link
     * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
     * called in "slow" mode.
     */
    public int getNReadLocks() {
        return nReadLocks;
    }

    /**
     * Total locks currently in lock table.  Only provided when {@link
     * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
     * called in "slow" mode.
     */
    public int getNTotalLocks() {
        return nTotalLocks;
    }

    /**
     * Total transactions waiting for locks.  Only provided when {@link
     * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
     * called in "slow" mode.
     */
    public int getNWaiters() {
        return nWaiters;
    }

    /**
     * Total write locks currently held.  Only provided when {@link
     * com.sleepycat.je.Environment#getLockStats Environment.getLockStats} is
     * called in "slow" mode.
     */
    public int getNWriteLocks() {
        return nWriteLocks;
    }

    /**
     * Total number of lock requests to date.
     */
    public long getNRequests() {
        return nRequests;
    }

    /**
     * Total number of lock waits to date.
     */
    public long getNWaits() {
        return nWaits;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNOwners(int val) {
        nOwners = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNReadLocks(int val) {
        nReadLocks = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void accumulateNTotalLocks(int val) {
        nTotalLocks += val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNWaiters(int val) {
        nWaiters = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNWriteLocks(int val) {
        nWriteLocks = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNRequests(long requests) {
        this.nRequests = requests;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNWaits(long waits) {
        this.nWaits = waits;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void accumulateLockTableLatchStats(LatchStats latchStats) {
	if (lockTableLatchStats == null) {
	    lockTableLatchStats = latchStats;
	    return;
	}

        lockTableLatchStats.nAcquiresNoWaiters +=
	    latchStats.nAcquiresNoWaiters;
        lockTableLatchStats.nAcquiresSelfOwned +=
	    latchStats.nAcquiresSelfOwned;
        lockTableLatchStats.nAcquiresUpgrade +=
	    latchStats.nAcquiresUpgrade;
        lockTableLatchStats.nAcquiresWithContention +=
	    latchStats.nAcquiresWithContention;
        lockTableLatchStats.nAcquireNoWaitSuccessful +=
	    latchStats.nAcquireNoWaitSuccessful;
        lockTableLatchStats.nAcquireNoWaitUnsuccessful +=
	    latchStats.nAcquireNoWaitUnsuccessful;
        lockTableLatchStats.nAcquireSharedSuccessful +=
	    latchStats.nAcquireSharedSuccessful;
    }

    /**
     * For convenience, the LockStats class has a toString method that lists
     * all the data fields.
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("\nFast mode stats (always available)\n");
        sb.append("nRequests=").append(nRequests).append('\n');
        sb.append("nWaits=").append(nWaits).append('\n');

        sb.append("\nSlow mode stats (not available in fast mode)\n");
        sb.append("nTotalLocks=").append(nTotalLocks).append('\n');
        sb.append("nReadLocks=").append(nReadLocks).append('\n');
        sb.append("nWriteLocks=").append(nWriteLocks).append('\n');
        sb.append("nWaiters=").append(nWaiters).append('\n');
        sb.append("nOwners=").append(nOwners).append('\n');
        sb.append("lockTableLatch:\n").append(lockTableLatchStats);
        return sb.toString();
    }
}
