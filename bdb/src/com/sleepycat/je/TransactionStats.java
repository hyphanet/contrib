/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: TransactionStats.java,v 1.35 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;
import java.util.Date;

/**
 * Transaction statistics for a database environment.
 */
public class TransactionStats implements Serializable {

    /**
     * The time the last completed checkpoint finished (as the number of
     * seconds since the Epoch, returned by the IEEE/ANSI Std 1003.1 (POSIX)
     * time interface).
     */
    private long lastCheckpointTime;

    /**
     * The last transaction ID allocated.
     */
    private long lastTxnId;

    /**
     * The number of transactions that are currently active.
     */
    private int nActive;

    /**
     * The number of transactions that have begun.
     */
    private long nBegins;

    /**
     * The number of transactions that have aborted.
     */
    private long nAborts;

    /**
     * The number of transactions that have committed.
     */
    private long nCommits;

    /**
     * The number of XA transactions that have aborted.
     */
    private long nXAAborts;

    /**
     * The number of XA transactions that have been prepared.
     */
    private long nXAPrepares;

    /**
     * The number of XA transactions that have committed.
     */
    private long nXACommits;

    /**
     * The array of active transactions. Each element of the array is an object
     * of type TransactionStats.Active.
     */
    private Active activeTxns[];

    /**
     * @hidden
     * Internal use only.
     */
    public TransactionStats() {
    }

    /**
     * The Active class represents an active transaction.
     */
    public static class Active implements Serializable {

	/**
	 * The transaction ID of the transaction.
	 */
	private long txnId;

	/**
	 * The transaction ID of the parent transaction (or 0, if no parent).
	 */
	private long parentId;

        /**
         * The transaction name, including the thread name if available.
         */
        private String name;

        /**
         * The transaction ID of the transaction.
         */
        public long getId() {
            return txnId;
        }

        /**
         * The transaction ID of the parent transaction (or 0, if no parent).
         */
        public long getParentId() {
            return parentId;
        }

        /**
         * The transaction name, including the thread name if available.
         */
        public String getName() {
            return name;
        }

	/**
         * @hidden
	 * Internal use only.
	 */
        public Active(String name, long txnId, long parentId) {
            this.name = name;
            this.txnId = txnId;
            this.parentId = parentId;
        }

        @Override
	public String toString() {
	    return "txnId = " + txnId + " txnName = " + name;
	}
    }

    /**
     * Return the array of active transactions.
     *
     * @return The array of active transactions.
     */
    public Active[] getActiveTxns() {
        return activeTxns;
    }

    /**
     * The time the last completed checkpoint finished (as the number of
     * seconds since the Epoch, returned by the IEEE/ANSI Std 1003.1 (POSIX)
     * time interface).
     */
    public long getLastCheckpointTime() {
        return lastCheckpointTime;
    }

    /**
     * The last transaction ID allocated.
     */
    public long getLastTxnId() {
        return lastTxnId;
    }

    /**
     * The number of transactions that have aborted.
     */
    public long getNAborts() {
        return nAborts;
    }

    /**
     * The number of XA transactions that have aborted.
     */
    public long getNXAAborts() {
        return nXAAborts;
    }

    /**
     * The number of XA transactions that have been prepared.
     */
    public long getNXAPrepares() {
        return nXAPrepares;
    }

    /**
     * The number of transactions that are currently active.
     */
    public int getNActive() {
        return nActive;
    }

    /**
     * The number of transactions that have begun.
     */
    public long getNBegins() {
        return nBegins;
    }

    /**
     * The number of transactions that have committed.
     */
    public long getNCommits() {
        return nCommits;
    }

    /**
     * The number of XA transactions that have committed.
     */
    public long getNXACommits() {
        return nXACommits;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setActiveTxns(Active[] actives) {
        activeTxns = actives;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLastCheckpointTime(long l) {
        lastCheckpointTime = l;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLastTxnId(long val) {
        lastTxnId = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNAborts(long val) {
        nAborts = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNXAAborts(long val) {
        nXAAborts = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNActive(int val) {
        nActive = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNBegins(long val) {
        nBegins = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNCommits(long val) {
        nCommits = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNXACommits(long val) {
        nXACommits = val;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setNXAPrepares(long val) {
        nXAPrepares = val;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("nBegins=").append(nBegins).append('\n');
        sb.append("nAborts=").append(nAborts).append('\n');
        sb.append("nCommits=").append(nCommits).append('\n');
        sb.append("nXAPrepares=").append(nXAPrepares).append('\n');
        sb.append("nXAAborts=").append(nXAAborts).append('\n');
        sb.append("nXACommits=").append(nXACommits).append('\n');
        sb.append("nActive=").append(nActive).append('\n');
        sb.append("activeTxns=[");
        if (activeTxns != null) {
            for (int i = 0; i < activeTxns.length; i += 1) {
                sb.append("  ").append(activeTxns[i]).append('\n');
            }
        }
        sb.append("]\n");
        sb.append("lastTxnId=").append(lastTxnId).append('\n');
        sb.append("lastCheckpointTime=").
           append(new Date(lastCheckpointTime)).append('\n');
        return sb.toString();
    }
}
