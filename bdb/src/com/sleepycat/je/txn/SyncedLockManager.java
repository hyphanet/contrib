/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SyncedLockManager.java,v 1.11.2.2 2007/07/13 02:32:05 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;

/**
 * SyncedLockManager uses the synchronized keyword to implement its critical
 * sections.
 */
public class SyncedLockManager extends LockManager {

    public SyncedLockManager(EnvironmentImpl envImpl) 
    	throws DatabaseException {

        super(envImpl);
    }

    /**
     * @see LockManager#attemptLock
     */
    protected LockAttemptResult attemptLock(Long nodeId,
                                            Locker locker,
                                            LockType type,
                                            boolean nonBlockingRequest) 
        throws DatabaseException {
        
	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return attemptLockInternal(nodeId, locker, type,
				       nonBlockingRequest, lockTableIndex);
        }
    }
        
    /**
     * @see LockManager#lookupLock
     */
    protected Lock lookupLock(Long nodeId) 
        throws DatabaseException {
        
	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return lookupLockInternal(nodeId, lockTableIndex);
        }
    }
        
    /**
     * @see LockManager#makeTimeoutMsg
     */
    protected String makeTimeoutMsg(String lockOrTxn,
                                    Locker locker,
                                    long nodeId,
                                    LockType type,
                                    LockGrantType grantType,
                                    Lock useLock,
                                    long timeout,
                                    long start,
                                    long now,
				    DatabaseImpl database) {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return makeTimeoutMsgInternal(lockOrTxn, locker, nodeId, type,
                                          grantType, useLock, timeout,
                                          start, now, database);
        }
    }

    /**
     * @see LockManager#releaseAndNotifyTargets
     */
    protected Set releaseAndFindNotifyTargets(long nodeId,
                                              Locker locker) 
        throws DatabaseException {

	long nid = nodeId;
	int lockTableIndex = getLockTableIndex(nid);
        synchronized(lockTableLatches[lockTableIndex]) {
            return releaseAndFindNotifyTargetsInternal
		(nodeId, locker, lockTableIndex);
        }
    }

    /**
     * @see LockManager#transfer
     */
    void transfer(long nodeId,
                  Locker owningLocker,
                  Locker destLocker,
                  boolean demoteToRead) 
        throws DatabaseException {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            transferInternal(nodeId, owningLocker, destLocker,
			     demoteToRead, lockTableIndex);
        }
    }

    /**
     * @see LockManager#transferMultiple
     */
    void transferMultiple(long nodeId,
                          Locker owningLocker,
                          Locker[] destLockers)
        throws DatabaseException {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            transferMultipleInternal(nodeId, owningLocker,
				     destLockers, lockTableIndex);
        }
    }

    /**
     * @see LockManager#demote
     */
    void demote(long nodeId, Locker locker)
        throws DatabaseException {
        
	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            demoteInternal(nodeId, locker, lockTableIndex);
        }
    }

    /**
     * @see LockManager#isLocked
     */
    boolean isLocked(Long nodeId) {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return isLockedInternal(nodeId, lockTableIndex);
        }
    }

    /**
     * @see LockManager#isOwner
     */
    boolean isOwner(Long nodeId, Locker locker, LockType type) {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return isOwnerInternal(nodeId, locker, type, lockTableIndex);
        }
    }

    /**
     * @see LockManager#isWaiter
     */
    boolean isWaiter(Long nodeId, Locker locker) {
        
	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return isWaiterInternal(nodeId, locker, lockTableIndex);
        }
    }

    /**
     * @see LockManager#nWaiters
     */
    int nWaiters(Long nodeId) {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return nWaitersInternal(nodeId, lockTableIndex);
        }
    }

    /**
     * @see LockManager#nOwners
     */
    int nOwners(Long nodeId) {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return nOwnersInternal(nodeId, lockTableIndex);
        }
    }

    /**
     * @see LockManager#getWriterOwnerLocker
     */
    Locker getWriteOwnerLocker(Long nodeId)
        throws DatabaseException {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return getWriteOwnerLockerInternal(nodeId, lockTableIndex);
        }
    }

    /**
     * @see LockManager#validateOwnership
     */
    protected boolean validateOwnership(Long nodeId,
                                        Locker locker, 
                                        LockType type,
                                        boolean flushFromWaiters,
					MemoryBudget mb)
        throws DatabaseException {

	int lockTableIndex = getLockTableIndex(nodeId);
        synchronized(lockTableLatches[lockTableIndex]) {
            return validateOwnershipInternal
		(nodeId, locker, type, flushFromWaiters, mb, lockTableIndex);
        }
    }

    /**
     * @see LockManager#dumpLockTable
     */
    protected void dumpLockTable(LockStats stats) 
        throws DatabaseException {
        
	for (int i = 0; i < nLockTables; i++) {
	    synchronized(lockTableLatches[i]) {
		dumpLockTableInternal(stats, i);
	    }
	}
    }
}
