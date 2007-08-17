/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LockManager.java,v 1.118.2.3 2007/07/13 02:32:05 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvConfigObserver;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.dbi.RangeRestartException;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchStats;
import com.sleepycat.je.latch.LatchSupport;

/**
 * LockManager manages locks.
 * 
 * Note that locks are counted as taking up part of the JE cache;
 */
public abstract class LockManager implements EnvConfigObserver {

    /* 
     * The total memory cost for a lock is the Lock object, plus its entry and
     * key in the lock hash table.
     *
     * The addition and removal of Lock objects, and the corresponding cost of
     * their hashmap entry and key are tracked through the LockManager.
     */
    static final long TOTAL_LOCK_OVERHEAD =
        MemoryBudget.LOCK_OVERHEAD +
        MemoryBudget.HASHMAP_ENTRY_OVERHEAD + 
        MemoryBudget.LONG_OVERHEAD;

    private static final long REMOVE_TOTAL_LOCK_OVERHEAD =
        0 - TOTAL_LOCK_OVERHEAD;

    protected int nLockTables = 1;
    protected Latch[] lockTableLatches;
    private Map[] lockTables;          // keyed by nodeId
    private EnvironmentImpl envImpl;
    private MemoryBudget memoryBudget;
    //private Level traceLevel;

    private long nRequests; // stats: number of time a request was made 
    private long nWaits;    // stats: number of time a request blocked 

    private static RangeRestartException rangeRestartException =
	new RangeRestartException();
    private static boolean lockTableDump = false;

    public LockManager(EnvironmentImpl envImpl)
    	throws DatabaseException {
    		
	DbConfigManager configMgr = envImpl.getConfigManager();
	nLockTables = configMgr.getInt(EnvironmentParams.N_LOCK_TABLES);
	lockTables = new Map[nLockTables];
	lockTableLatches = new Latch[nLockTables];
	for (int i = 0; i < nLockTables; i++) {
	    lockTables[i] = new HashMap();
	    lockTableLatches[i] =
		LatchSupport.makeLatch("Lock Table " + i, envImpl);
	}
        this.envImpl = envImpl;
        memoryBudget = envImpl.getMemoryBudget();
        nRequests = 0;
        nWaits = 0;
	/*
        traceLevel = Tracer.parseLevel
	    (env, EnvironmentParams.JE_LOGGING_LEVEL_LOCKMGR);
	*/

        /* Initialize mutable properties and register for notifications. */
        envConfigUpdate(configMgr);
        envImpl.addConfigObserver(this);
    }

    /**
     * Process notifications of mutable property changes.
     */
    public void envConfigUpdate(DbConfigManager configMgr)
        throws DatabaseException {

        LockInfo.setDeadlockStackTrace(configMgr.getBoolean
            (EnvironmentParams.TXN_DEADLOCK_STACK_TRACE));
        setLockTableDump(configMgr.getBoolean
            (EnvironmentParams.TXN_DUMPLOCKS));
    }

    /**
     * Called when the je.txn.dumpLocks property is changed.
     */
    static void setLockTableDump(boolean enable) {
        lockTableDump = enable;
    }

    protected int getLockTableIndex(Long nodeId) {
	return ((int) nodeId.longValue()) %
	    nLockTables;
    }

    protected int getLockTableIndex(long nodeId) {
	return ((int) nodeId) % nLockTables;
    }

    /**
     * Attempt to acquire a lock of <i>type</i> on <i>nodeId</i>.  If the lock
     * acquisition would result in a deadlock, throw an exception.<br> If the
     * requested lock is not currently available, block until it is or until
     * timeout milliseconds have elapsed.<br> If a lock of <i>type</i> is
     * already held, return EXISTING.<br> If a WRITE lock is held and a READ
     * lock is requested, return PROMOTION.<br>
     *
     * If a lock request is for a lock that is not currently held, return
     * either NEW or DENIED depending on whether the lock is granted or
     * not.<br>
     *
     * @param nodeId The NodeId to lock.
     *
     * @param locker The Locker to lock this on behalf of.
     *
     * @param type The lock type requested.
     *
     * @param timeout milliseconds to time out after if lock couldn't be
     * obtained.  0 means block indefinitely.  Not used if nonBlockingRequest
     * is true.
     *
     * @param nonBlockingRequest if true, means don't block if lock can't be
     * acquired, and ignore the timeout parameter.
     *
     * @return a LockGrantType indicating whether the request was fulfilled
     * or not.  LockGrantType.NEW means the lock grant was fulfilled and
     * the caller did not previously hold the lock.  PROMOTION means the
     * lock was granted and it was a promotion from READ to WRITE.  EXISTING
     * means the lock was already granted (not a promotion).  DENIED means
     * the lock was not granted either because the timeout passed without
     * acquiring the lock or timeout was -1 and the lock was not immediately
     * available.
     *
     * @throws DeadlockException if acquiring the lock would result in
     * a deadlock.
     */
    public LockGrantType lock(long nodeId,
                              Locker locker,
                              LockType type,
                              long timeout,
                              boolean nonBlockingRequest,
			      DatabaseImpl database)
        throws DeadlockException, DatabaseException {

        assert timeout >= 0;

        /*
         * Lock on locker before latching the lockTable to avoid having another
         * notifier perform the notify before the waiter is actually waiting.
         */
        synchronized (locker) {
            Long nid = new Long(nodeId);
            LockAttemptResult result =
		attemptLock(nid, locker, type, nonBlockingRequest);
            /* Got the lock, return. */
            if (result.success ||
                result.lockGrant == LockGrantType.DENIED) {
                return result.lockGrant;
            }

            assert checkNoLatchesHeld(nonBlockingRequest):
                LatchSupport.countLatchesHeld() +
                " latches held while trying to lock, lock table =" +
                LatchSupport.latchesHeldToString();

            /*
             * We must have gotten WAIT_* from the lock request. We know that
             * this is a blocking request, because if it wasn't, Lock.lock
             * would have returned DENIED. Go wait!
             */
            assert !nonBlockingRequest;
            try {
                boolean doWait = true;

                /* 
                 * Before blocking, check locker timeout. We need to check here
                 * or lock timeouts will always take precedence and we'll never
                 * actually get any txn timeouts.
                 */
                if (locker.isTimedOut()) {
                    if (validateOwnership(nid, locker, type, true,
					  memoryBudget)) {
                        doWait = false;
                    } else {
                        String errMsg =
			    makeTimeoutMsg("Transaction", locker, nodeId, type,
					   result.lockGrant, 
					   result.useLock,
					   locker.getTxnTimeOut(),
					   locker.getTxnStartMillis(),
					   System.currentTimeMillis(),
					   database);
                        throw new DeadlockException(errMsg);
                    }
                }

                boolean keepTime = (timeout > 0);
                long startTime = (keepTime ? System.currentTimeMillis() : 0);
                while (doWait) {
                    locker.setWaitingFor(result.useLock);
                    try {
                        locker.wait(timeout);
                    } catch (InterruptedException IE) {
			throw new RunRecoveryException(envImpl, IE);
                    }

                    boolean lockerTimedOut = locker.isTimedOut();
                    long now = System.currentTimeMillis();
                    boolean thisLockTimedOut =
                        (keepTime && (now - startTime > timeout));
                    boolean isRestart = 
                        (result.lockGrant == LockGrantType.WAIT_RESTART);

                    /*
                     * Re-check for ownership of the lock following wait.  If
                     * we timed out and we don't have ownership then flush this
                     * lock from both the waiters and owners while under the
                     * lock table latch.  See SR 10103.
                     */ 
                    if (validateOwnership(nid, locker, type, 
					  lockerTimedOut ||
                                          thisLockTimedOut ||
                                          isRestart,
                                          memoryBudget)) {
                        break;
                    } else {

                        /*
                         * After a restart conflict the lock will not be held.
                         */
                        if (isRestart) {
                            throw rangeRestartException;
                        }

                        if (thisLockTimedOut) {
			    locker.setOnlyAbortable();
                            String errMsg =
                                makeTimeoutMsg("Lock", locker, nodeId, type,
                                               result.lockGrant, 
                                               result.useLock,
                                               timeout, startTime, now,
					       database);
                            throw new DeadlockException(errMsg);            
                        } 

                        if (lockerTimedOut) {
			    locker.setOnlyAbortable();
                            String errMsg =
                                makeTimeoutMsg("Transaction", locker,
                                               nodeId, type,
                                               result.lockGrant, 
                                               result.useLock,
                                               locker.getTxnTimeOut(),
                                               locker.getTxnStartMillis(),
                                               now, database);
                            throw new DeadlockException(errMsg);
                        }
                    }
                }
            } finally {
		locker.setWaitingFor(null);
		assert EnvironmentImpl.maybeForceYield();
	    }

            locker.addLock(nid, type, result.lockGrant);

            return result.lockGrant;
        }
    }

    abstract protected Lock lookupLock(Long nodeId)
	throws DatabaseException;

    protected Lock lookupLockInternal(Long nodeId, int lockTableIndex)
	throws DatabaseException {

        /* Get the target lock. */
	Map lockTable = lockTables[lockTableIndex];
        Lock useLock = (Lock) lockTable.get(nodeId);
	return useLock;
    }

    abstract protected LockAttemptResult
        attemptLock(Long nodeId,
                    Locker locker,
                    LockType type,
                    boolean nonBlockingRequest) 
        throws DatabaseException;
        
    protected LockAttemptResult
	attemptLockInternal(Long nodeId,
			    Locker locker,
			    LockType type,
			    boolean nonBlockingRequest,
			    int lockTableIndex)
        throws DatabaseException {

        nRequests++;

        /* Get the target lock. */
	Map lockTable = lockTables[lockTableIndex];
        Lock useLock = (Lock) lockTable.get(nodeId);
        if (useLock == null) {
            useLock = new Lock();
            lockTable.put(nodeId, useLock);
            memoryBudget.updateLockMemoryUsage(TOTAL_LOCK_OVERHEAD,
					       lockTableIndex);
        }

        /*
         * Attempt to lock.  Possible return values are NEW, PROMOTION, DENIED,
         * EXISTING, WAIT_NEW, WAIT_PROMOTION, WAIT_RESTART.
         */
        LockGrantType lockGrant = useLock.lock(type, locker,
					       nonBlockingRequest,
					       memoryBudget, lockTableIndex);
        boolean success = false;

        /* Was the attempt successful? */
        if ((lockGrant == LockGrantType.NEW) ||
            (lockGrant == LockGrantType.PROMOTION)) {
            locker.addLock(nodeId, type, lockGrant);
            success = true;
        } else if (lockGrant == LockGrantType.EXISTING) {
            success = true;
        } else if (lockGrant == LockGrantType.DENIED) {
            /* Locker.lock will throw LockNotGrantedException. */
        } else {
            nWaits++;
        }
        return new LockAttemptResult(useLock, lockGrant, success);
    }

    /**
     * Create a informative lock or txn timeout message.
     */
    protected abstract String makeTimeoutMsg(String lockOrTxn,
                                             Locker locker,
                                             long nodeId,
                                             LockType type,
                                             LockGrantType grantType,
                                             Lock useLock,
                                             long timeout,
                                             long start,
                                             long now,
					     DatabaseImpl database)
	throws DatabaseException;

    /**
     * Do the real work of creating an lock or txn timeout message.
     */
    protected String makeTimeoutMsgInternal(String lockOrTxn,
                                            Locker locker,
                                            long nodeId,
                                            LockType type,
                                            LockGrantType grantType,
                                            Lock useLock,
                                            long timeout,
                                            long start,
                                            long now,
					    DatabaseImpl database) {

        /*
         * Because we're accessing parts of the lock, need to have protected
         * access to the lock table because things can be changing out from
         * underneath us.  This is a big hammer to grab for so long while we
         * traverse the graph, but it's only when we have a deadlock and we're
         * creating a debugging message.
         *
         * The alternative would be to handle ConcurrentModificationExceptions
         * and retry until none of them happen.
         */
        if (lockTableDump) {
            System.out.println("++++++++++ begin lock table dump ++++++++++");
            for (int i = 0; i < nLockTables; i++) {
                StringBuffer sb = new StringBuffer();
                dumpToStringNoLatch(sb, i);
                System.out.println(sb.toString());
            }
            System.out.println("++++++++++ end lock table dump ++++++++++");
        }

        StringBuffer sb = new StringBuffer();
        sb.append(lockOrTxn);
        sb.append(" expired. Locker ").append(locker);
        sb.append(": waited for lock");

        if (database!=null) {
            sb.append(" on database=").append(database.getDebugName());
        }
        sb.append(" node=").append(nodeId);
        sb.append(" type=").append(type);
        sb.append(" grant=").append(grantType);
        sb.append(" timeoutMillis=").append(timeout);
        sb.append(" startTime=").append(start);
        sb.append(" endTime=").append(now);
        sb.append("\nOwners: ").append(useLock.getOwnersClone());
        sb.append("\nWaiters: ").append(useLock.getWaitersListClone()).
	    append("\n");
        StringBuffer deadlockInfo = findDeadlock(useLock, locker);
        if (deadlockInfo != null) {
            sb.append(deadlockInfo);
        }
        return sb.toString();
    }

    /**
     * Release a lock and possibly notify any waiters that they have been
     * granted the lock.
     *
     * @param nodeId The node ID of the lock to release.
     *
     * @return true if the lock is released successfully, false if
     * the lock is not currently being held.
     */
    boolean release(long nodeId, Locker locker)
        throws DatabaseException {

	synchronized (locker) {
	    Set newOwners =
		releaseAndFindNotifyTargets(nodeId, locker);

            if (newOwners == null) {
                return false;
            }

            if (newOwners.size() > 0) {

                /* 
                 * There is a new set of owners and/or there are restart
                 * waiters that should be notified.
                 */
                Iterator iter = newOwners.iterator();
                
                while (iter.hasNext()) {
                    Locker lockerToNotify = (Locker) iter.next();

                    /* Use notifyAll to support multiple threads per txn. */
                    synchronized (lockerToNotify) {
                        lockerToNotify.notifyAll();
                    }

		    assert EnvironmentImpl.maybeForceYield();
                }
            }

            return true;
	}
    }

    /**
     * Release the lock, and return the set of new owners to notify, if any.
     *
     * @return
     * null if the lock does not exist or the given locker was not the owner,
     * a non-empty set if owners should be notified after releasing,
     * an empty set if no notification is required.
     */
    protected abstract Set
        releaseAndFindNotifyTargets(long nodeId,
                                    Locker locker)
        throws DatabaseException;

    /**
     * Do the real work of releaseAndFindNotifyTargets
     */
    protected Set
	releaseAndFindNotifyTargetsInternal(long nodeId,
					    Locker locker,
					    int lockTableIndex)
        throws DatabaseException {

	Map lockTable = lockTables[lockTableIndex];
	Lock useLock = (Lock) lockTable.get(new Long(nodeId));

        if (useLock == null) {
            /* Lock doesn't exist. */
            return null; 
        }

        Set lockersToNotify =
	    useLock.release(locker, memoryBudget, lockTableIndex);
        if (lockersToNotify == null) {
            /* Not owner. */
            return null;
        }

        /* If it's not in use at all, remove it from the lock table. */
        if ((useLock.nWaiters() == 0) &&
            (useLock.nOwners() == 0)) {
            lockTables[lockTableIndex].remove(new Long(nodeId));
            memoryBudget.updateLockMemoryUsage(REMOVE_TOTAL_LOCK_OVERHEAD,
					       lockTableIndex);
        }

        return lockersToNotify;
    }

    /**
     * Transfer ownership a lock from one locker to another locker. We're not
     * sending any notification to the waiters on the lock table, and the past
     * and present owner should be ready for the transfer.
     */
    abstract void transfer(long nodeId,
                           Locker owningLocker,
                           Locker destLocker,
                           boolean demoteToRead)
        throws DatabaseException;

    /**
     * Do the real work of transfer
     */
    protected void transferInternal(long nodeId,
                                    Locker owningLocker,
                                    Locker destLocker,
                                    boolean demoteToRead,
				    int lockTableIndex)
        throws DatabaseException {
        
	Map lockTable = lockTables[lockTableIndex];
        Lock useLock = (Lock) lockTable.get(new Long(nodeId));
        
        assert useLock != null : "Transfer, lock " + nodeId + " was null";
        if (demoteToRead) {
            useLock.demote(owningLocker);
        }
	useLock.transfer(new Long(nodeId), owningLocker, destLocker,
			 memoryBudget, lockTableIndex);
        owningLocker.removeLock(nodeId);
    }
    
    /**
     * Transfer ownership a lock from one locker to a set of other txns,
     * cloning the lock as necessary. This will always be demoted to read, as
     * we can't have multiple locker owners any other way.  We're not sending
     * any notification to the waiters on the lock table, and the past and
     * present owners should be ready for the transfer.
     */
    abstract void transferMultiple(long nodeId,
                                   Locker owningLocker,
                                   Locker[] destLockers)
        throws DatabaseException;

    /**
     * Do the real work of transferMultiple
     */
    protected void transferMultipleInternal(long nodeId,
                                            Locker owningLocker,
                                            Locker[] destLockers,
					    int lockTableIndex)
        throws DatabaseException {

	Map lockTable = lockTables[lockTableIndex];
        Lock useLock = (Lock) lockTable.get(new Long(nodeId));
            
        assert useLock != null : "Transfer, lock " + nodeId + " was null";
        useLock.demote(owningLocker);
        useLock.transferMultiple(new Long(nodeId), owningLocker, destLockers,
				 memoryBudget, lockTableIndex);
        owningLocker.removeLock(nodeId);
    }

    /**
     * Demote a lock from write to read. Call back to the owning locker to
     * move this to its read collection.
     * @param lock The lock to release. If null, use nodeId to find lock
     * @param locker
     */
    abstract void demote(long nodeId, Locker locker)
        throws DatabaseException;

    /**
     * Do the real work of demote.
     */
    protected void demoteInternal(long nodeId,
				  Locker locker,
				  int lockTableIndex)
        throws DatabaseException {

	Map lockTable = lockTables[lockTableIndex];
        Lock useLock = (Lock) lockTable.get(new Long(nodeId));
        useLock.demote(locker);
        locker.moveWriteToReadLock(nodeId, useLock);
    }

    /**
     * Test the status of the lock on nodeId.  If any transaction holds any
     * lock on it, true is returned.  If no transaction holds a lock on it,
     * false is returned.
     *
     * This method is only used by unit tests.
     *
     * @param nodeId The NodeId to check.
     * @return true if any transaction holds any lock on the nodeid. false
     * if no lock is held by any transaction.
     */
    abstract boolean isLocked(Long nodeId)
        throws DatabaseException;

    /**
     * Do the real work of isLocked.
     */
    protected boolean isLockedInternal(Long nodeId, int lockTableIndex) {

	Map lockTable = lockTables[lockTableIndex];
        Lock entry = (Lock) lockTable.get(nodeId);
        if (entry == null) {
            return false;
        }

        return entry.nOwners() != 0;
    }
    
    /**
     * Return true if this locker owns this a lock of this type on given node.
     *
     * This method is only used by unit tests.
     */
    abstract boolean isOwner(Long nodeId, Locker locker, LockType type)
        throws DatabaseException;

    /**
     * Do the real work of isOwner.
     */
    protected boolean isOwnerInternal(Long nodeId,
                                      Locker locker,
                                      LockType type,
				      int lockTableIndex) {

	Map lockTable = lockTables[lockTableIndex];
        Lock entry = (Lock) lockTable.get(nodeId);
        if (entry == null) {
            return false;
        }

        return entry.isOwner(locker, type);
    }

    /**
     * Return true if this locker is waiting on this lock.
     *
     * This method is only used by unit tests.
     */
    abstract boolean isWaiter(Long nodeId, Locker locker)
        throws DatabaseException;

    /**
     * Do the real work of isWaiter.
     */
    protected boolean isWaiterInternal(Long nodeId,
				       Locker locker,
				       int lockTableIndex) {

	Map lockTable = lockTables[lockTableIndex];
        Lock entry = (Lock) lockTable.get(nodeId);
        if (entry == null) {
            return false;
        }

        return entry.isWaiter(locker);
    }

    /**
     * Return the number of waiters for this lock.
     */
    abstract int nWaiters(Long nodeId)
        throws DatabaseException;

    /**
     * Do the real work of nWaiters.
     */
    protected int nWaitersInternal(Long nodeId, int lockTableIndex) {

	Map lockTable = lockTables[lockTableIndex];
        Lock entry = (Lock) lockTable.get(nodeId);
        if (entry == null) {
            return -1;
        }

        return entry.nWaiters();
    }

    /**
     * Return the number of owners of this lock.
     */
    abstract int nOwners(Long nodeId)
        throws DatabaseException;

    /**
     * Do the real work of nWaiters.
     */
    protected int nOwnersInternal(Long nodeId, int lockTableIndex) {

	Map lockTable = lockTables[lockTableIndex];
        Lock entry = (Lock) lockTable.get(nodeId);
        if (entry == null) {
            return -1;
        }

        return entry.nOwners();
    }

    /**
     * @return the transaction that owns the write lock for this 
     */
    abstract Locker getWriteOwnerLocker(Long nodeId)
        throws DatabaseException;

    /**
     * Do the real work of getWriteOwnerLocker.
     */
    protected Locker getWriteOwnerLockerInternal(Long nodeId,
						 int lockTableIndex)
        throws DatabaseException {

	Map lockTable = lockTables[lockTableIndex];
        Lock lock = (Lock) lockTable.get(nodeId);
        if (lock == null) {
            return null;
        } else if (lock.nOwners() > 1) {
            /* not a write lock */
            return null;
        } else {
            return lock.getWriteOwnerLocker();
        }
    }

    /*
     * Check if we got ownership while we were waiting.  If we didn't get
     * ownership, and we timed out, remove this locker from the set of
     * waiters. Do this in a critical section to prevent any orphaning of the
     * lock -- we must be in a critical section between the time that we check
     * ownership and when we flush any waiters (SR #10103)
     * @return true if you are the owner.
     */
    abstract protected boolean validateOwnership(Long nodeId,
                                                 Locker locker, 
                                                 LockType type,
                                                 boolean flushFromWaiters,
                                                 MemoryBudget mb)
        throws DatabaseException;

    /*
     * Do the real work of validateOwnershipInternal.
     */
    protected boolean validateOwnershipInternal(Long nodeId,
                                                Locker locker, 
                                                LockType type,
                                                boolean flushFromWaiters,
						MemoryBudget mb,
						int lockTableIndex)
        throws DatabaseException {

        if (isOwnerInternal(nodeId, locker, type, lockTableIndex)) {
            return true;
        }
            
        if (flushFromWaiters) {
            Lock entry = (Lock) lockTables[lockTableIndex].get(nodeId);
            if (entry != null) {
                entry.flushWaiter(locker, mb, lockTableIndex);
            }
        }
        return false;
    }

    /**
     * Statistics
     */
    public LockStats lockStat(StatsConfig config)
        throws DatabaseException {
                
        LockStats stats = new LockStats();
        stats.setNRequests(nRequests);
        stats.setNWaits(nWaits);
        if (config.getClear()) {
            nWaits = 0;
            nRequests = 0;
        }

	for (int i = 0; i < nLockTables; i++) {
	    LatchStats latchStats =
		(LatchStats) lockTableLatches[i].getLatchStats();
	    stats.accumulateLockTableLatchStats(latchStats);
	}

        /* Dump info about the lock table. */
        if (!config.getFast()) {
            dumpLockTable(stats);
        }
        return stats;
    }

    /**
     * Dump the lock table to the lock stats.
     */
    abstract protected void dumpLockTable(LockStats stats) 
        throws DatabaseException;

    /**
     * Do the real work of dumpLockTableInternal.
     */
    protected void dumpLockTableInternal(LockStats stats, int i) {
	Map lockTable = lockTables[i];
        stats.accumulateNTotalLocks(lockTable.size());
	Iterator iter = lockTable.values().iterator();
	while (iter.hasNext()) {
	    Lock lock = (Lock) iter.next();
	    stats.setNWaiters(stats.getNWaiters() +
			      lock.nWaiters());
	    stats.setNOwners(stats.getNOwners() +
			     lock.nOwners());

	    /* Go through all the owners for a lock. */
	    Iterator ownerIter = lock.getOwnersClone().iterator();
	    while (ownerIter.hasNext()) {
		LockInfo info = (LockInfo) ownerIter.next();
		if (info.getLockType().isWriteLock()) {
		    stats.setNWriteLocks(stats.getNWriteLocks() + 1);
		} else {
		    stats.setNReadLocks(stats.getNReadLocks() + 1);
		}
	    }
	}
    }

    /**
     * Debugging
     */
    public void dump()
        throws DatabaseException {

        System.out.println(dumpToString());
    }

    public String dumpToString()
        throws DatabaseException {
        
        StringBuffer sb = new StringBuffer();
	for (int i = 0; i < nLockTables; i++) {
	    lockTableLatches[i].acquire();
	    try {
                dumpToStringNoLatch(sb, i);
	    } finally {
		lockTableLatches[i].release();
	    }
	}
        return sb.toString();
    }

    private void dumpToStringNoLatch(StringBuffer sb, int whichTable) {
        Map lockTable = lockTables[whichTable];
        Iterator entries = lockTable.entrySet().iterator();

        while (entries.hasNext()) {
	    Map.Entry entry = (Map.Entry) entries.next();
            Long nid = (Long) entry.getKey();
            Lock lock = (Lock) entry.getValue();
            sb.append("---- Node Id: ").append(nid).append("----\n");
            sb.append(lock);
            sb.append('\n');
        }
    }

    private boolean checkNoLatchesHeld(boolean nonBlockingRequest) {
        if (nonBlockingRequest) {
            return true; // don't check if it's a non blocking request.
        } else {
            return (LatchSupport.countLatchesHeld() == 0);
        }
    }

    private StringBuffer findDeadlock(Lock lock, Locker rootLocker) {

	Set ownerSet = new HashSet();
	ownerSet.add(rootLocker);
	StringBuffer ret = findDeadlock1(ownerSet, lock, rootLocker);
	if (ret != null) {
	    return ret;
	} else {
	    return null;
	}
    }

    private StringBuffer findDeadlock1(Set ownerSet,
				       Lock lock,
                                       Locker rootLocker) {

	Iterator ownerIter = lock.getOwnersClone().iterator();
	while (ownerIter.hasNext()) {
	    LockInfo info = (LockInfo) ownerIter.next();
	    Locker locker = info.getLocker();
	    Lock waitsFor = locker.getWaitingFor();
	    if (ownerSet.contains(locker) ||
		locker == rootLocker) {
		/* Found a cycle. */
		StringBuffer ret = new StringBuffer();
		ret.append("Transaction ").append(locker.toString());
		ret.append(" owns ").append(System.identityHashCode(lock));
		ret.append(" ").append(info).append("\n");
		ret.append("Transaction ").append(locker.toString());
		ret.append(" waits for ");
		if (waitsFor == null) {
		    ret.append(" nothing");
		} else {
		    ret.append(" node ");
		    ret.append(System.identityHashCode(waitsFor));
		}
		ret.append("\n");
		return ret;
	    }
	    if (waitsFor != null) {
		ownerSet.add(locker);
		StringBuffer sb = findDeadlock1(ownerSet, waitsFor,
                                                rootLocker);
		if (sb != null) {
		    String waitInfo =
			"Transaction " + locker + " waits for " +
			waitsFor + "\n";
		    sb.insert(0, waitInfo);
		    return sb;
		}
		ownerSet.remove(locker); // is this necessary?
	    }
	}

	return null;
    }

    /**
     * This is just a struct to hold a multi-value return.
     */
    static class LockAttemptResult {
        boolean success;
        Lock useLock;
        LockGrantType lockGrant;

        LockAttemptResult(Lock useLock,
                          LockGrantType lockGrant,
                          boolean success) {

            this.useLock = useLock;
            this.lockGrant = lockGrant;
            this.success = success;
        }
    }
}
