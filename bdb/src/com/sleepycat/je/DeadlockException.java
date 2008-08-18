/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DeadlockException.java,v 1.18 2008/02/27 15:03:51 mark Exp $
 */

package com.sleepycat.je;

/**
 * DeadlockException is thrown to a thread of control when multiple threads
 * competing for a lock are deadlocked or when a lock request would need to
 * block and the transaction has been configured to not wait for locks. The
 * exception carrys two arrays of transaction ids, one of the owners and the
 * other of the waiters, at the time of the timeout.
 */
public class DeadlockException extends DatabaseException {

    private long[] ownerTxnIds;
    private long[] waiterTxnIds;
    private long timeoutMillis;

    public DeadlockException() {
	super();
    }

    public DeadlockException(Throwable t) {
        super(t);
    }

    public DeadlockException(String message) {
	super(message);
    }

    public DeadlockException(String message, Throwable t) {
        super(message, t);
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setOwnerTxnIds(long[] ownerTxnIds) {
	this.ownerTxnIds = ownerTxnIds;
    }

    /**
     * Returns an array of longs containing transaction ids of owners at the
     * the time of the timeout.
     *
     * @return an array of longs containing transaction ids of owners at the
     * the time of the timeout.
     */
    public long[] getOwnerTxnIds() {
	return ownerTxnIds;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setWaiterTxnIds(long[] waiterTxnIds) {
	this.waiterTxnIds = waiterTxnIds;
    }

    /**
     * Returns an array of longs containing transaction ids of waiters at the
     * the time of the timeout.
     *
     * @return an array of longs containing transaction ids of waiters at the
     * the time of the timeout.
     */
    public long[] getWaiterTxnIds() {
	return waiterTxnIds;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setTimeoutMillis(long timeoutMillis) {
	this.timeoutMillis = timeoutMillis;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public long getTimeoutMillis() {
	return timeoutMillis;
    }
}
