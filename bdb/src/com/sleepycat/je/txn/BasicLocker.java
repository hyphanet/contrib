/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: BasicLocker.java,v 1.102 2008/05/13 20:03:10 sam Exp $
 */

package com.sleepycat.je.txn;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.DbLsn;

/**
 * A non-transactional Locker that simply tracks locks and releases them when
 * releaseNonTxnLocks or operationEnd is called.
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
    private Long ownedLock;
    private Set<Long> ownedLockSet;

    /**
     * Creates a BasicLocker.
     */
    protected BasicLocker(EnvironmentImpl env)
        throws DatabaseException {

        super(env,
              false, // readUncommittedDefault
              false, // noWait
              0);    // mandatedId
    }

    public static BasicLocker createBasicLocker(EnvironmentImpl env)
        throws DatabaseException {

	BasicLocker ret = null;
	try {
	    ret = new BasicLocker(env);
	    ret.initApiReadLock();
	} catch (DatabaseException DE) {
	    ret.operationEnd(false);
	    throw DE;
	}
	return ret;
    }

    /**
     * Creates a BasicLocker with a noWait argument.
     */
    protected BasicLocker(EnvironmentImpl env, boolean noWait)
        throws DatabaseException {

        super(env,
              false, // readUncommittedDefault
              noWait,
              0);    // mandatedId
    }

    public static BasicLocker createBasicLocker(EnvironmentImpl env,
						boolean noWait)
        throws DatabaseException {

	BasicLocker ret = null;
	try {
	    ret = new BasicLocker(env, noWait);
	    ret.initApiReadLock();
	} catch (DatabaseException DE) {
	    ret.operationEnd(false);
	    throw DE;
	}
	return ret;
    }

    /**
     * Creates a BasicLocker with the noWait and noAPIReadLock arguments.
     */
    protected BasicLocker(EnvironmentImpl env,
			  boolean noWait,
			  boolean noAPIReadLock)
        throws DatabaseException {

        super(env,
              false, // readUncommittedDefault
              noWait,
	      noAPIReadLock,
              0);    // mandatedId
    }

    public static BasicLocker createBasicLocker(EnvironmentImpl env,
						boolean noWait,
						boolean noAPIReadLock)
        throws DatabaseException {

	BasicLocker ret = null;
	try {
	    ret = new BasicLocker(env, noWait, noAPIReadLock);
	    ret.initApiReadLock();
	} catch (DatabaseException DE) {
	    ret.operationEnd(false);
	    throw DE;
	}
	return ret;
    }

    /**
     * BasicLockers always have a fixed id, because they are never used for
     * recovery.
     */
    protected long generateId(TxnManager txnManager,
                              long ignore /* mandatedId */) {
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
                timeout = getLockTimeout();
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

        return lockManager.getWriteOwnerLocker(Long.valueOf(nodeId));
    }

    /**
     * Get the abort LSN for this node in the txn that owns the lock on this
     * node. Return null if there's no owning txn found.
     */
    public long getOwnerAbortLsn(long nodeId)
        throws DatabaseException {

        Locker ownerTxn =
            lockManager.getWriteOwnerLocker(Long.valueOf(nodeId));
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
     * Throws UnsupportedOperationException unconditionally.
     *
     * If we were to create a new BasicLocker here, it would not share locks
     * with this locker, which violates the definition of this method.  This
     * method is not currently called in direct uses of BasicLocker and is
     * overridden by subclasses where it is allowed (e.g., ThreadLocker and
     * ReadCommittedLocker).
     */
    public Locker newNonTxnLocker()
        throws DatabaseException {

        throw new UnsupportedOperationException();
    }

    /**
     * Releases all locks, since all locks held by this locker are
     * non-transactional.
     */
    public void releaseNonTxnLocks()
        throws DatabaseException {

        /*
         * Don't remove locks from txn's lock collection until iteration is
         * done, lest we get a ConcurrentModificationException during deadlock
	 * graph "display".  [#9544]
         */
        if (ownedLock != null) {
            lockManager.release(ownedLock, this);
            ownedLock = null;
        }
        if (ownedLockSet != null) {
            Iterator<Long> iter = ownedLockSet.iterator();
            while (iter.hasNext()) {
                Long nid = iter.next();
                lockManager.release(nid, this);
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
     * Release locks and close the cursor at the end of the operation.
     */
    public void nonTxnOperationEnd()
        throws DatabaseException {

        operationEnd(true);
    }

    /**
     * Release locks and close the cursor at the end of the operation.
     */
    public void operationEnd(boolean operationOK)
        throws DatabaseException {

        releaseNonTxnLocks();

        /* Close this Locker. */
        close();
    }

    /**
     * Transfer any MapLN locks to the db handle.
     */
    public void setHandleLockOwner(boolean operationOK,
                                   Database dbHandle,
                                   boolean dbIsClosing)
	throws DatabaseException {

        if (dbHandle != null) {
            if (operationOK && !dbIsClosing) {
                transferHandleLockToHandle(dbHandle);
            } else {

                /*
                 * Release DB if there is a failure.  This is done by Txn abort
                 * by calling Database.invalidate, but for a non-transactional
                 * locker must be done here.  [#13415]
                 */
                envImpl.getDbTree().
                    releaseDb(DbInternal.dbGetDatabaseImpl(dbHandle));
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
            /* releaseDb will be called by startAndFinishDelete. */
            db.startAndFinishDelete();
        } else {
            envImpl.getDbTree().releaseDb(db);
        }
    }

    /**
     * Add a lock to set owned by this transaction.
     */
    protected void addLock(Long nodeId,
                           LockType type,
                           LockGrantType grantStatus)
        throws DatabaseException {

        if ((ownedLock != null &&
	    ownedLock.equals(nodeId)) ||
            (ownedLockSet != null &&
	     ownedLockSet.contains(nodeId))) {
            return; // Already owned
        }
        if (ownedLock == null) {
            ownedLock = nodeId;
        } else {
            if (ownedLockSet == null) {
                ownedLockSet = new HashSet<Long>();
            }
            ownedLockSet.add(nodeId);
        }
    }

    /**
     * Remove a lock from the set owned by this txn.
     */
    void removeLock(long nodeId)
        throws DatabaseException {

        if (ownedLock != null &&
	    ownedLock == nodeId) {
            ownedLock = null;
        } else if (ownedLockSet != null) {
            ownedLockSet.remove(nodeId);
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
	    Lock l = lockManager.lookupLock(ownedLock);
	    if (l != null) {
		if (l.isOwnedWriteLock(this)) {
		    stats.setNWriteLocks(stats.getNWriteLocks() + 1);
		} else {
		    stats.setNReadLocks(stats.getNReadLocks() + 1);
		}
	    }
	}
        if (ownedLockSet != null) {
            Iterator<Long> iter = ownedLockSet.iterator();

            while (iter.hasNext()) {
                Long nid = iter.next();
		Lock l = lockManager.lookupLock(nid);
		if (l != null) {
		    if (l.isOwnedWriteLock(this)) {
			stats.setNWriteLocks(stats.getNWriteLocks() + 1);
		    } else {
			stats.setNReadLocks(stats.getNReadLocks() + 1);
		    }
		}
            }
        }
        return stats;
    }
}
