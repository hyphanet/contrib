/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BasicLocker.java,v 1.84.2.1 2007/02/01 14:49:52 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.DbLsn;

/**
 * A concrete Locker that simply tracks locks and releases them when
 * operationEnd is called.
 */
public class BasicLocker extends Locker {

    /*
     * A BasicLocker can release all locks, so there is no need to distinguish
     * between read and write locks.
     *
     * ownedLock is used for the first lock obtained, and ownedLockSet is
     * instantiated and used only if more than one lock is obtained.  This is
     * an optimization for the common case where only one lock is held by a
     * non-transactional locker.
     *
     * There's no need to track memory utilization for these non-txnal lockers,
     * because the lockers are short lived.
     */
    private Lock ownedLock;
    private Set ownedLockSet; 

    /**
     * Creates a BasicLocker.
     */
    public BasicLocker(EnvironmentImpl env)
        throws DatabaseException {

        super(env, false, false);
    }

    /**
     * BasicLockers always have a fixed id, because they are never used for
     * recovery.
     */
    protected long generateId(TxnManager txnManager) {
        return TxnManager.NULL_TXN_ID;
    }

    protected void checkState(boolean ignoreCalledByAbort)
        throws DatabaseException {
        /* Do nothing. */
    }

    /**
     * @see Locker#lockInternal
     * @Override
     */
    LockResult lockInternal(long nodeId,
                            LockType lockType,
                            boolean noWait,
                            DatabaseImpl database)
        throws DatabaseException {
  
	/* Does nothing in BasicLocker. synchronized is for posterity. */
	synchronized (this) {
	    checkState(false);
	}

	long timeout = 0;
        boolean useNoWait = noWait || defaultNoWait;
        if (!useNoWait) {
            synchronized (this) {
                timeout = lockTimeOutMillis;
            }
        }

        /* Ask for the lock. */
        LockGrantType grant = lockManager.lock
            (nodeId, this, lockType, timeout, useNoWait, database);

        return new LockResult(grant, null);
    }

    /**
     * Get the txn that owns the lock on this node. Return null if there's no
     * owning txn found.
     */
    public Locker getWriteOwnerLocker(long nodeId) 
        throws DatabaseException {
   
        return lockManager.getWriteOwnerLocker(new Long(nodeId));
    }

    /**
     * Get the abort LSN for this node in the txn that owns the lock on this
     * node. Return null if there's no owning txn found.
     */
    public long getOwnerAbortLsn(long nodeId) 
        throws DatabaseException {
   
        Locker ownerTxn = lockManager.getWriteOwnerLocker(new Long(nodeId));
        if (ownerTxn != null) {
            return ownerTxn.getAbortLsn(nodeId);
        }
        return DbLsn.NULL_LSN;
    }

    /**
     * Is never transactional.
     */
    public boolean isTransactional() {
        return false;
    }

    /**
     * Is never serializable isolation.
     */
    public boolean isSerializableIsolation() {
        return false;
    }

    /**
     * Is never read-committed isolation.
     */
    public boolean isReadCommittedIsolation() {
        return false;
    }

    /**
     * No transactional locker is available.
     */
    public Txn getTxnLocker() {
        return null;
    }

    /**
     * Creates a new instance of this txn for the same environment.  No
     * transactional locks are held by this object, so no locks are retained.
     */
    public Locker newNonTxnLocker()
        throws DatabaseException {

        return new BasicLocker(envImpl);
    }

    /**
     * Releases all locks, since all locks held by this locker are
     * non-transactional.
     */
    public void releaseNonTxnLocks()
        throws DatabaseException {

        operationEnd(true);
    }

    /**
     * Release locks at the end of the transaction.
     */
    public void operationEnd()
        throws DatabaseException {

        operationEnd(true);
    }

    /**
     * Release locks at the end of the transaction.
     */
    public void operationEnd(boolean operationOK)
        throws DatabaseException {

        /*
         * Don't remove locks from txn's lock collection until iteration is
         * done, lest we get a ConcurrentModificationException during deadlock
	 * graph "display".  [#9544]
         */
        if (ownedLock != null) {
            lockManager.release(ownedLock, this);
            ownedLock  = null;
        }
        if (ownedLockSet != null) {
            Iterator iter = ownedLockSet.iterator();
            while (iter.hasNext()) {
                Lock l = (Lock) iter.next();
                lockManager.release(l, this);
            }

            /* Now clear lock collection. */
            ownedLockSet.clear();
        }

        /* Unload delete info, but don't wake up the compressor. */
        synchronized (this) {
            if ((deleteInfo != null) &&
		(deleteInfo.size() > 0)) {
                envImpl.addToCompressorQueue(deleteInfo.values(),
                                             false); // no wakeup
                deleteInfo.clear();
            }
        }
    }

    /**
     * Transfer any MapLN locks to the db handle.
     */
    public void setHandleLockOwner(boolean ignore /*operationOK*/,
                                   Database dbHandle,
                                   boolean dbIsClosing)
	throws DatabaseException {

        if (dbHandle != null) { 
            if (!dbIsClosing) {
                transferHandleLockToHandle(dbHandle);
            }
            unregisterHandle(dbHandle);
        }
    }

    /**
     * This txn doesn't store cursors.
     */
    public void registerCursor(CursorImpl cursor)
	throws DatabaseException {
    }

    /**
     * This txn doesn't store cursors.
     */
    public void unRegisterCursor(CursorImpl cursor)
	throws DatabaseException {
    }

    /*
     * Transactional methods are all no-oped.
     */

    /**
     * @return the abort LSN for this node.
     */
    public long getAbortLsn(long nodeId)
        throws DatabaseException {

        return DbLsn.NULL_LSN;
    }

    /**
     * @return a dummy WriteLockInfo for this node.
     */
    public WriteLockInfo getWriteLockInfo(long nodeId)
	throws DatabaseException {

	return WriteLockInfo.basicWriteLockInfo;
    }

    public void markDeleteAtTxnEnd(DatabaseImpl db, boolean deleteAtCommit)
        throws DatabaseException {

        if (deleteAtCommit) {
            db.deleteAndReleaseINs();
        }
    }

    /**
     * Add a lock to set owned by this transaction. 
     */
    void addLock(Long nodeId,
                 Lock lock,
                 LockType type,
                 LockGrantType grantStatus) 
        throws DatabaseException {

        if (ownedLock == lock ||
            (ownedLockSet != null &&
	     ownedLockSet.contains(lock))) {
            return; // Already owned
        }
        if (ownedLock == null) {
            ownedLock = lock;
        } else {
            if (ownedLockSet == null) {
                ownedLockSet = new HashSet();
            }
            ownedLockSet.add(lock);
        }
    }
    
    /**
     * Remove a lock from the set owned by this txn.
     */
    void removeLock(long nodeId, Lock lock)
        throws DatabaseException {

        if (lock == ownedLock) {
            ownedLock = null;
        } else if (ownedLockSet != null) {
            ownedLockSet.remove(lock);
        }
    }

    /**
     * Always false for this txn.
     */
    public boolean createdNode(long nodeId)
        throws DatabaseException {

        return false;
    }

    /**
     * A lock is being demoted. Move it from the write collection into the read
     * collection.
     */
    void moveWriteToReadLock(long nodeId, Lock lock) {
    }

    /**
     * stats
     */
    public LockStats collectStats(LockStats stats)
        throws DatabaseException {

        if (ownedLock != null) {
            if (ownedLock.isOwnedWriteLock(this)) {
                stats.setNWriteLocks(stats.getNWriteLocks() + 1);
            } else {
                stats.setNReadLocks(stats.getNReadLocks() + 1);
            }
        }
        if (ownedLockSet != null) {
            Iterator iter = ownedLockSet.iterator();
            
            while (iter.hasNext()) {
                Lock l = (Lock) iter.next();
                if (l.isOwnedWriteLock(this)) {
                    stats.setNWriteLocks(stats.getNWriteLocks() + 1);
                } else {
                    stats.setNReadLocks(stats.getNReadLocks() + 1);
                }
            }
        }
        return stats;
    }
}
