/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TxnManager.java,v 1.81 2008/05/15 09:44:34 chao Exp $
 */

package com.sleepycat.je.txn;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.transaction.xa.Xid;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Class to manage transactions.  Basically a Set of all transactions with add
 * and remove methods and a latch around the set.
 */
public class TxnManager {

    /*
     * All NullTxns share the same id so as not to eat from the id number
     * space.
     *
     * Negative transaction ids are used by the master node of a replication
     * group. That sequence begins at -10 to avoid conflict with the
     * NULL_TXN_ID and leave room for other special purpose ids.
     */
    static final long NULL_TXN_ID = -1;
    private static final long FIRST_NEGATIVE_ID = -10;
    private LockManager lockManager;
    private EnvironmentImpl envImpl;
    private Set<Txn> allTxns;
    /* Maps Xids to Txns. */
    private Map<Xid, Txn> allXATxns;
    /* Maps Threads to Txns when there are thread implied transactions. */
    private Map<Thread, Transaction> thread2Txn;

    /*
     * Positive and negative transaction ids are used in a replicated system,
     * to let replicated transactions intermingle with local transactions.
     */
    private AtomicLong lastUsedLocalTxnId;
    private AtomicLong lastUsedReplicatedTxnId;
    private int nActiveSerializable;

    /* Locker Stats */
    private long numBegins;
    private long numCommits;
    private long numAborts;
    private long numXAPrepares;
    private long numXACommits;
    private long numXAAborts;

    public TxnManager(EnvironmentImpl envImpl)
        throws DatabaseException {

        if (EnvironmentImpl.getFairLatches()) {
            lockManager = new LatchedLockManager(envImpl);
        } else {
            if (envImpl.isNoLocking()) {
                lockManager = new DummyLockManager(envImpl);
            } else {
                lockManager = new SyncedLockManager(envImpl);
            }
        }

        this.envImpl = envImpl;
        allTxns = new HashSet<Txn>();
        allXATxns = Collections.synchronizedMap(new HashMap<Xid, Txn>());
        thread2Txn = Collections.synchronizedMap(new HashMap<Thread, Transaction>());

        numBegins = 0;
        numCommits = 0;
        numAborts = 0;
        numXAPrepares = 0;
        numXACommits = 0;
        numXAAborts = 0;
        lastUsedLocalTxnId = new AtomicLong(0);
        lastUsedReplicatedTxnId = new AtomicLong(FIRST_NEGATIVE_ID);
    }

    /**
     * Set the txn id sequence.
     */
    public void setLastTxnId(long lastReplicatedTxnId, long lastLocalId) {
        lastUsedReplicatedTxnId.set(lastReplicatedTxnId);
        lastUsedLocalTxnId.set(lastLocalId);
    }

    /**
     * Get the last used id, for checkpoint info.
     */
    public long getLastLocalTxnId() {
        return lastUsedLocalTxnId.get();
    }

    public long getLastReplicatedTxnId() {
        return lastUsedReplicatedTxnId.get();
    }

    public long getNextReplicatedTxnId() {
        return lastUsedReplicatedTxnId.decrementAndGet();
    }

    /**
     * Get the next transaction id to use.
     */
    long getNextTxnId() {
        assert(!(envImpl.isReplicated() && envImpl.getReplicator().isMaster()));
        return lastUsedLocalTxnId.incrementAndGet();
    }

    /*
     * Only set the replicated txn id if the replayTxnId represents a
     * newer, later value in the replication stream.
     */
    public void updateFromReplay(long replayTxnId) {

        assert replayTxnId < 0 :
            "replay txn id is unexpectedly positive " + replayTxnId;

        while (true) {
            long currentVal = lastUsedReplicatedTxnId.get();
            if (replayTxnId < currentVal) {

                /*
                 * This replayTxnId is newer than any other replicatedTxnId
                 * known by this node.
                 */
                boolean ok = lastUsedReplicatedTxnId.weakCompareAndSet
                    (currentVal, replayTxnId);
                if (ok) {
                    break;
                }
            } else {
                break;
            }
        }
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

        return Txn.createTxn(envImpl, txnConfig);
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

        synchronized (allTxns) {
            allTxns.add(txn);
            if (txn.isSerializableIsolation()) {
                nActiveSerializable++;
            }
            numBegins++;
        }
    }

    /**
     * Called when txn ends.
     */
    void unRegisterTxn(Txn txn, boolean isCommit)
        throws DatabaseException {

        synchronized (allTxns) {
            allTxns.remove(txn);

            /* Remove any accumulated MemoryBudget delta for the Txn. */
            envImpl.getMemoryBudget().
                updateTxnMemoryUsage(0 - txn.getBudgetedMemorySize());
            if (isCommit) {
                numCommits++;
            } else {
                numAborts++;
            }
            if (txn.isSerializableIsolation()) {
                nActiveSerializable--;
            }
        }
    }

    /**
     * Called when txn is created.
     */
    public void registerXATxn(Xid xid, Txn txn, boolean isPrepare)
        throws DatabaseException {

        if (!allXATxns.containsKey(xid)) {
            allXATxns.put(xid, txn);
            envImpl.getMemoryBudget().updateTxnMemoryUsage
                (MemoryBudget.HASHMAP_ENTRY_OVERHEAD);
        }

        if (isPrepare) {
            numXAPrepares++;
        }
    }

    /**
     * Called when XATransaction is prepared.
     */
    public void notePrepare() {      
        numXAPrepares++;
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
        envImpl.getMemoryBudget().updateTxnMemoryUsage
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

        return allXATxns.get(xid);
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
        return thread2Txn.remove(curThread);
    }

    /**
     * Retrieve a Txn object for this Thread.
     */
    public Transaction getTxnForThread()
        throws DatabaseException {

        return thread2Txn.get(Thread.currentThread());
    }

    public Xid[] XARecover()
        throws DatabaseException {

        Set<Xid> xidSet = allXATxns.keySet();
        Xid[] ret = new Xid[xidSet.size()];
        ret = xidSet.toArray(ret);

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
     * Returns NULL_LSN is no transaction is currently active.
     */
    public long getFirstActiveLsn()
        throws DatabaseException {

        /*
         * Note that the latching hierarchy calls for syncroninzing on
         * allTxns first, then synchronizing on individual txns.
         */
        long firstActive = DbLsn.NULL_LSN;
        synchronized (allTxns) {
            Iterator<Txn> iter = allTxns.iterator();
            while (iter.hasNext()) {
                long txnFirstActive = iter.next().getFirstActiveLsn();
                if (firstActive == DbLsn.NULL_LSN) {
                    firstActive = txnFirstActive;
                } else if (txnFirstActive != DbLsn.NULL_LSN) {
                    if (DbLsn.compareTo(txnFirstActive, firstActive) < 0) {
                        firstActive = txnFirstActive;
                    }
                }
            }
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
        synchronized (allTxns) {
            stats.setNBegins(numBegins);
            stats.setNCommits(numCommits);
            stats.setNAborts(numAborts);
            stats.setNXAPrepares(numXAPrepares);
            stats.setNXACommits(numXACommits);
            stats.setNXAAborts(numXAAborts);
            stats.setNActive(allTxns.size());
            TransactionStats.Active[] activeSet =
                new TransactionStats.Active[stats.getNActive()];
            stats.setActiveTxns(activeSet);
            Iterator<Txn> iter = allTxns.iterator();
            int i = 0;
            while (iter.hasNext()) {
                Locker txn = iter.next();
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
