/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LockStats.java,v 1.24.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

import com.sleepycat.je.latch.LatchStats;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
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
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNOwners() {
        return nOwners;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNReadLocks() {
        return nReadLocks;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNTotalLocks() {
        return nTotalLocks;
    }       

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNWaiters() {
        return nWaiters;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNWriteLocks() {
        return nWriteLocks;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNRequests() {
        return nRequests;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getNWaits() {
        return nWaits;
    }

    /**
     * Internal use only.
     */
    public void setNOwners(int val) {
        nOwners = val;
    }

    /**
     * Internal use only.
     */
    public void setNReadLocks(int val) {
        nReadLocks = val;
    }

    /**
     * Internal use only.
     */
    public void accumulateNTotalLocks(int val) {
        nTotalLocks += val;
    }

    /**
     * Internal use only.
     */
    public void setNWaiters(int val) {
        nWaiters = val;
    }

    /**
     * Internal use only.
     */
    public void setNWriteLocks(int val) {
        nWriteLocks = val;
    }

    /**
     * Internal use only.
     */
    public void setNRequests(long requests) {
        this.nRequests = requests; 
    }

    /**
     * Internal use only.
     */
    public void setNWaits(long waits) {
        this.nWaits = waits; 
    }

    /**
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
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("nTotalLocks=").append(nTotalLocks).append('\n');
        sb.append("nReadLocks=").append(nReadLocks).append('\n');
        sb.append("nWriteLocks=").append(nWriteLocks).append('\n');
        sb.append("nWaiters=").append(nWaiters).append('\n');
        sb.append("nOwners=").append(nOwners).append('\n');
        sb.append("nRequests=").append(nRequests).append('\n');
        sb.append("nWaits=").append(nWaits).append('\n');
        sb.append("lockTableLatch:\n").append(lockTableLatchStats);
        return sb.toString();    
    }
}
