/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnManager.java,v 1.59.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.transaction.xa.Xid;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Class to manage transactions.  Basically a Set of all transactions with add
 * and remove methods and a latch around the set.
 */
public class TxnManager {

    /* 
     * All NullTxns share the same id so as not to eat from the id number
     * space.
     */
    static final long NULL_TXN_ID = -1; 
    private static final String DEBUG_NAME = TxnManager.class.getName();
    
    private LockManager lockManager;
    private EnvironmentImpl env;
    private Latch allTxnLatch;
    private Set allTxns;
    /* Maps Xids to Txns. */
    private Map allXATxns;
    /* Maps Threads to Txns when there are thread implied transactions. */
    private Map thread2Txn;
    private long lastUsedTxnId;
    private int nActiveSerializable;
    
    /* Locker Stats */
    private int numCommits;
    private int numAborts;
    private int numXAPrepares;
    private int numXACommits;
    private int numXAAborts;

    public TxnManager(EnvironmentImpl env) 
    	throws DatabaseException {

        if (EnvironmentImpl.getFairLatches()) {
            lockManager = new LatchedLockManager(env);
        } else {
	    if (env.isNoLocking()) {
		lockManager = new DummyLockManager(env);
	    } else {
		lockManager = new SyncedLockManager(env);
	    }
        }

        this.env = env;
        allTxns = new HashSet();
        allTxnLatch = LatchSupport.makeLatch(DEBUG_NAME, env);
        allXATxns = Collections.synchronizedMap(new HashMap());
        thread2Txn = Collections.synchronizedMap(new HashMap());

        numCommits = 0;
        numAborts = 0;
        numXAPrepares = 0;
        numXACommits = 0;
        numXAAborts = 0;
        lastUsedTxnId = 0;
    }

    /**
     * Set the txn id sequence.
     */
    synchronized public void setLastTxnId(long lastId) {
        this.lastUsedTxnId = lastId;
    }

    /**
     * Get the last used id, for checkpoint info.
     */
    public synchronized long getLastTxnId() {
        return lastUsedTxnId;
    }

    /**
     * Get the next transaction id to use.
     */
    synchronized long incTxnId() {
        return ++lastUsedTxnId;
    }
    
    /**
     * Create a new transaction.
     * @param parent for nested transactions, not yet supported
     * @param txnConfig specifies txn attributes
     * @return the new txn
     */
    public Txn txnBegin(Transaction parent, TransactionConfig txnConfig) 
        throws DatabaseException {

        if (parent != null) {
            throw new DatabaseException
		("Nested transactions are not supported yet.");
        }
        
        return new Txn(env, txnConfig);
    }

    /**
     * Give transactions and environment access to lock manager.
     */
    public LockManager getLockManager() {
        return lockManager;
    }

    /**
     * Called when txn is created.
     */
    void registerTxn(Txn txn)
        throws DatabaseException {

        allTxnLatch.acquire();
        allTxns.add(txn);
        if (txn.isSerializableIsolation()) {
            nActiveSerializable++;
        }
        allTxnLatch.release();
    }

    /**
     * Called when txn ends.
     */
    void unRegisterTxn(Txn txn, boolean isCommit) 
        throws DatabaseException {

        allTxnLatch.acquire();
	try {
	    allTxns.remove(txn);
	    /* Remove any accumulated MemoryBudget delta for the Txn. */
	    env.getMemoryBudget().
		updateMiscMemoryUsage(txn.getAccumulatedDelta() -
				      txn.getInMemorySize());
	    if (isCommit) {
		numCommits++;
	    } else {
		numAborts++;
	    }
	    if (txn.isSerializableIsolation()) {
		nActiveSerializable--;
	    }
	} finally {
	    allTxnLatch.release();
	}
    }

    /**
     * Called when txn is created.
     */
    public void registerXATxn(Xid xid, Txn txn, boolean isPrepare)
        throws DatabaseException {

	if (!allXATxns.containsKey(xid)) {
	    allXATxns.put(xid, txn);
	    env.getMemoryBudget().updateMiscMemoryUsage
		(MemoryBudget.HASHMAP_ENTRY_OVERHEAD);
	}

	if (isPrepare) {
	    numXAPrepares++;
	}
    }

    /**
     * Called when txn ends.
     */
    void unRegisterXATxn(Xid xid, boolean isCommit) 
        throws DatabaseException {

	if (allXATxns.remove(xid) == null) {
	    throw new DatabaseException
		("XA Transaction " + xid +
		 " can not be unregistered.");
	}
	env.getMemoryBudget().updateMiscMemoryUsage
	    (0 - MemoryBudget.HASHMAP_ENTRY_OVERHEAD);
	if (isCommit) {
	    numXACommits++;
	} else {
	    numXAAborts++;
	}
    }

    /**
     * Retrieve a Txn object from an Xid.
     */
    public Txn getTxnFromXid(Xid xid)
	throws DatabaseException {

	return (Txn) allXATxns.get(xid);
    }

    /**
     * Called when txn is assoc'd with this thread.
     */
    public void setTxnForThread(Transaction txn) {

	Thread curThread = Thread.currentThread();
	thread2Txn.put(curThread, txn);
    }

    /**
     * Called when txn is assoc'd with this thread.
     */
    public Transaction unsetTxnForThread()
        throws DatabaseException {

	Thread curThread = Thread.currentThread();
	return (Transaction) thread2Txn.remove(curThread);
    }

    /**
     * Retrieve a Txn object for this Thread.
     */
    public Transaction getTxnForThread()
	throws DatabaseException {

	return (Transaction) thread2Txn.get(Thread.currentThread());
    }

    public Xid[] XARecover()
	throws DatabaseException {

	Set xidSet = allXATxns.keySet();
	Xid[] ret = new Xid[xidSet.size()];
	ret = (Xid[]) xidSet.toArray(ret);

	return ret;
    }

    /**
     * Returns whether there are any active serializable transactions,
     * excluding the transaction given (if non-null).  This is intentionally
     * returned without latching, since latching would not make the act of
     * reading an integer more atomic than it already is.
     */
    public boolean
        areOtherSerializableTransactionsActive(Locker excludeLocker) {
        int exclude =
            (excludeLocker != null &&
             excludeLocker.isSerializableIsolation()) ?
            1 : 0;
        return (nActiveSerializable - exclude > 0);
    }

    /**
     * Get the earliest LSN of all the active transactions, for checkpoint.
     */
    public long getFirstActiveLsn() 
        throws DatabaseException {

        /* 
         * Note that the latching hierarchy calls for getting allTxnLatch
         * first, then synchronizing on individual txns.
         */
	long firstActive = DbLsn.NULL_LSN;
        allTxnLatch.acquire();
	try {
	    Iterator iter = allTxns.iterator();
	    while(iter.hasNext()) {
		long txnFirstActive = ((Txn) iter.next()).getFirstActiveLsn();
		if (firstActive == DbLsn.NULL_LSN) {
		    firstActive = txnFirstActive;
		} else if (txnFirstActive != DbLsn.NULL_LSN) {
		    if (DbLsn.compareTo(txnFirstActive, firstActive) < 0) {
			firstActive = txnFirstActive;
		    }
		}
	    }
	} finally {
	    allTxnLatch.release();
	}
        return firstActive;
    }

    /*
     * Statistics
     */

    /**
     * Collect transaction related stats.
     */
    public TransactionStats txnStat(StatsConfig config)
        throws DatabaseException {

        TransactionStats stats = new TransactionStats();
        allTxnLatch.acquire();
	try {
	    stats.setNCommits(numCommits);
	    stats.setNAborts(numAborts);
	    stats.setNXAPrepares(numXAPrepares);
	    stats.setNXACommits(numXACommits);
	    stats.setNXAAborts(numXAAborts);
	    stats.setNActive(allTxns.size());
	    TransactionStats.Active[] activeSet =
		new TransactionStats.Active[stats.getNActive()];
	    stats.setActiveTxns(activeSet);
	    Iterator iter = allTxns.iterator();
	    int i = 0;
	    while (iter.hasNext()) {
		Locker txn = (Locker) iter.next();
		activeSet[i] = new TransactionStats.Active
		    (txn.toString(), txn.getId(), 0);
		i++;
	    }
	    if (config.getClear()) {
		numCommits = 0;
		numAborts = 0;
		numXACommits = 0;
		numXAAborts = 0;
	    }
	} finally {
	    allTxnLatch.release();
	}
        return stats;
    }

    /**
     * Collect lock related stats.
     */
    public LockStats lockStat(StatsConfig config) 
        throws DatabaseException {

        return lockManager.lockStat(config);
    }
}
