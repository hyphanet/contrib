/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Locker.java,v 1.101.2.3 2007/07/13 02:32:05 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINReference;
import com.sleepycat.je.tree.Key;

/**
 * Locker instances are JE's route to locking and transactional support.  This
 * class is the abstract base class for BasicLocker, ThreadLocker, Txn and
 * AutoTxn.  Locker instances are in fact only a transaction shell to get to
 * the lock manager, and don't guarantee transactional semantics. Txn and
 * AutoTxn instances are both truely transactional, but have different ending
 * behaviors.
 */
public abstract class Locker {
    private static final String DEBUG_NAME = Locker.class.getName();
    protected EnvironmentImpl envImpl;
    protected LockManager lockManager;

    protected long id;                        // transaction id
    protected boolean readUncommittedDefault; // read-uncommitted is default

    /* Timeouts */
    protected boolean defaultNoWait;      // true for non-blocking
    protected long lockTimeOutMillis;     // timeout period for lock, in ms
    private long txnTimeOutMillis;        // timeout period for txns, in ms
    private long txnStartMillis;          // for txn timeout determination

    private Lock waitingFor;              // The lock that this txn is
                                          // waiting for.

    /*
     * DeleteInfo refers to BINReferences that should be sent to the
     * INCompressor for asynchronous compressing after the transaction ends.
     */
    protected Map deleteInfo;             

    /*
     * To support handle lock transfers, each txn keeps maps handle locks to
     * database handles. This is maintained as a map where the key is the
     * handle lock id and the value is a set of database handles that
     * correspond to that handle lock. This is a 1 - many relationship because
     * a single handle lock can cover multiple database handles opened by the
     * same transaction.
     */
    protected Map handleLockToHandleMap; // 1-many, used for commits
    protected Map handleToHandleLockMap; // 1-1, used for aborts

    /**
     * The thread that created this locker.  Used for debugging, and by the
     * ThreadLocker subclass. Note that thread may be null if the Locker is
     * instantiated by reading the log.
     */
    protected Thread thread;

    /**
     * Create a locker id. This constructor is called very often, so it should
     * be as streamlined as possible.
     * 
     * @param lockManager lock manager for this environment
     * @param readUncommittedDefault if true, this transaction does
     * read-uncommitted by default
     * @param noWait if true, non-blocking lock requests are used.
     */
    public Locker(EnvironmentImpl envImpl,
                  boolean readUncommittedDefault,
                  boolean noWait) 
        throws DatabaseException {

        TxnManager txnManager = envImpl.getTxnManager();
        this.id = generateId(txnManager);
        this.envImpl = envImpl;
        lockManager = txnManager.getLockManager();
        this.readUncommittedDefault = readUncommittedDefault;
	this.waitingFor = null;

        /* get the default lock timeout. */
        defaultNoWait = noWait;
        lockTimeOutMillis = envImpl.getLockTimeout();

        /*
         * Check the default txn timeout. If non-zero, remember the txn start
         * time.
         */
        txnTimeOutMillis = envImpl.getTxnTimeout();

        if (txnTimeOutMillis != 0) {
            txnStartMillis = System.currentTimeMillis();
        } else {
            txnStartMillis = 0;
        }

        /* Save the thread used to create the locker. */
        thread = Thread.currentThread();

        /* 
         * Do lazy initialization of deleteInfo and handle lock maps, to 
         * conserve memory.
         */
    }

    /**
     * For reading from the log.
     */
    Locker() {
    }

    /**
     * A Locker has to generate its next id. Some subtypes, like BasicLocker,
     * have a single id for all instances because they are never used for
     * recovery. Other subtypes ask the txn manager for an id.
     */
    protected abstract long generateId(TxnManager txnManager);

    /**
     * @return the transaction's id.
     */
    public long getId() {
        return id;
    }

    /**
     * @return the default no-wait (non-blocking) setting.
     */
    public boolean getDefaultNoWait() {
        return defaultNoWait;
    }

    /**
     * Get the lock timeout period for this transaction, in milliseconds
     */
    public synchronized long getLockTimeout() {
        return lockTimeOutMillis;
    }

    /**
     * Set the lock timeout period for any locks in this transaction,
     * in milliseconds.
     */
    public synchronized void setLockTimeout(long timeOutMillis) {
        lockTimeOutMillis = timeOutMillis;
    }

    /**
     * Set the timeout period for this transaction, in milliseconds.
     */
    public synchronized void setTxnTimeout(long timeOutMillis) {
        txnTimeOutMillis = timeOutMillis;
        txnStartMillis = System.currentTimeMillis();
    }

    /**
     * @return true if transaction was created with read-uncommitted as a
     * default.
     */
    public boolean isReadUncommittedDefault() {
        return readUncommittedDefault;
    }

    Lock getWaitingFor() {
	return waitingFor;
    }

    void setWaitingFor(Lock lock) {
	waitingFor = lock;
    }

    /**
     * Set the state of a transaction to ONLY_ABORTABLE.
     */
    void setOnlyAbortable() {
	/* no-op unless Txn. */
    }

    protected abstract void checkState(boolean ignoreCalledByAbort)
        throws DatabaseException;

    /*
     * Obtain and release locks.
     */ 

    /**
     * Abstract method to a blocking or non-blocking lock of the given type on
     * the given nodeId.  Unlike the lock() method, this method does not throw
     * LockNotGrantedException and can therefore be used by nonBlockingLock to
     * probe for a lock without the overhead of an exception stack trace.
     *
     * @param nodeId is the node to lock.
     *
     * @param lockType is the type of lock to request.
     *
     * @param noWait is true to override the defaultNoWait setting.  If true,
     * or if defaultNoWait is true, throws LockNotGrantedException if the lock
     * cannot be granted without waiting.
     *
     * @param database is the database containing nodeId.
     *
     * @throws DeadlockException if acquiring a blocking lock would result in a
     * deadlock.
     */
    abstract LockResult lockInternal(long nodeId,
                                     LockType lockType,
                                     boolean noWait,
                                     DatabaseImpl database)
        throws DeadlockException, DatabaseException;

    /**
     * Request a blocking or non-blocking lock of the given type on the given
     * nodeId.
     *
     * @param nodeId is the node to lock.
     *
     * @param lockType is the type of lock to request.
     *
     * @param noWait is true to override the defaultNoWait setting.  If true,
     * or if defaultNoWait is true, throws LockNotGrantedException if the lock
     * cannot be granted without waiting.
     *
     * @param database is the database containing nodeId.
     *
     * @throws LockNotGrantedException if a non-blocking lock was denied.
     *
     * @throws DeadlockException if acquiring a blocking lock would result in a
     * deadlock.
     */
    public LockResult lock(long nodeId,
                           LockType lockType,
                           boolean noWait,
                           DatabaseImpl database)
        throws LockNotGrantedException, DeadlockException, DatabaseException {

        LockResult result = lockInternal(nodeId, lockType, noWait, database);

        if (result.getLockGrant() == LockGrantType.DENIED) {
            /* DENIED can only be returned for a non-blocking lock. */
            throw new LockNotGrantedException("Non-blocking lock was denied.");
        } else {
            return result;
        }
    }

    /**
     * Request a non-blocking lock of the given type on the given nodeId.
     *
     * <p>Unlike lock(), this method returns LockGrantType.DENIED if the lock
     * is denied rather than throwing LockNotGrantedException.  This method
     * should therefore not be used as the final lock for a user operation,
     * since in that case LockNotGrantedException should be thrown for a denied
     * lock.  It is normally used only to probe for a lock, and other recourse
     * is taken if the lock is denied.</p>
     *
     * @param nodeId is the node to lock.
     *
     * @param lockType is the type of lock to request.
     *
     * @param database is the database containing nodeId.
     */
    public LockResult nonBlockingLock(long nodeId,
                                      LockType lockType,
                                      DatabaseImpl database)
        throws DatabaseException {

        return lockInternal(nodeId, lockType, true, database);
    }

    /**
     * Release the lock on this LN and remove from the transaction's owning
     * set.
     */
    public void releaseLock(long nodeId)
        throws DatabaseException {

        lockManager.release(nodeId, this);
	removeLock(nodeId);
    }

    /**
     * Revert this lock from a write lock to a read lock.
     */
    public void demoteLock(long nodeId)
        throws DatabaseException {

        /*
         * If successful, the lock manager will call back to the transaction
         * and adjust the location of the lock in the lock collection.
         */
        lockManager.demote(nodeId, this);
    }

    /**
     * Returns whether this locker is transactional.
     */
    public abstract boolean isTransactional();

    /**
     * Returns whether the isolation level of this locker is serializable.
     */
    public abstract boolean isSerializableIsolation();

    /**
     * Returns whether the isolation level of this locker is read-committed.
     */
    public abstract boolean isReadCommittedIsolation();

    /**
     * Returns the underlying Txn if the locker is transactional, or null if
     * the locker is non-transactional.  For a Txn-based locker, this method
     * returns 'this'.  For a BuddyLocker, this method may returns the buddy.
     */
    public abstract Txn getTxnLocker();

    /**
     * Creates a fresh non-transactional locker, while retaining any
     * transactional locks held by this locker.  This method is called when the
     * cursor for this locker is cloned.
     *
     * <p>In general, transactional lockers return 'this' when this method is
     * called, while non-transactional lockers return a new instance.</p>
     */
    public abstract Locker newNonTxnLocker()
        throws DatabaseException;

    /**
     * Releases any non-transactional locks held by this locker.  This method
     * is called when the cursor moves to a new position or is closed.
     *
     * <p>In general, transactional lockers do nothing when this method is
     * called, while non-transactional lockers release all locks as if
     * operationEnd were called.</p>
     */
    public abstract void releaseNonTxnLocks()
        throws DatabaseException;

    /**
     * Returns whether this locker can share locks with the given locker.
     *
     * <p>All lockers share locks with a BuddyLocker whose buddy is this
     * locker.  To support BuddyLocker when overriding this method, always
     * return true if this implementation (super.sharesLocksWith(...)) returns
     * true.</p>
     */
    public boolean sharesLocksWith(Locker other) {
	if (other instanceof BuddyLocker) {
            BuddyLocker buddy = (BuddyLocker) other;
            return buddy.getBuddy() == this;
	} else {
	    return false;
	}
    }

    /**
     * The equivalent of calling operationEnd(true).
     */
    public abstract void operationEnd()
        throws DatabaseException;

    /**
     * Different types of transactions do different things when the operation
     * ends. Txns do nothing, AutoTxns commit or abort, and BasicLockers and
     * ThreadLockers just release locks.
     *
     * @param operationOK is whether the operation succeeded, since
     * that may impact ending behavior. (i.e for AutoTxn)
     */
    public abstract void operationEnd(boolean operationOK)
        throws DatabaseException;

    /**
     * We're at the end of an operation. Move this handle lock to the
     * appropriate owner.
     */
    public abstract void setHandleLockOwner(boolean operationOK,
                                            Database dbHandle,
                                            boolean dbIsClosing)
        throws DatabaseException;

    /**
     * A SUCCESS status equals operationOk.
     */
    public void operationEnd(OperationStatus status) 
        throws DatabaseException {

        operationEnd(status == OperationStatus.SUCCESS);
    }

    /**
     * Tell this transaction about a cursor.
     */
    public abstract void registerCursor(CursorImpl cursor)
        throws DatabaseException;

    /**
     * Remove a cursor from this txn.
     */
    public abstract void unRegisterCursor(CursorImpl cursor)
        throws DatabaseException;

    /*
     * Transactional support
     */

    /**
     * @return the abort LSN for this node.
     */
    public abstract long getAbortLsn(long nodeId)
        throws DatabaseException;

    /**
     * @return the WriteLockInfo for this node.
     */
    public abstract WriteLockInfo getWriteLockInfo(long nodeId)
	throws DatabaseException;

    /**
     * Database operations like remove and truncate leave behind
     * residual DatabaseImpls that must be purged at transaction
     * commit or abort. 
     */
    public abstract void markDeleteAtTxnEnd(DatabaseImpl db, 
                                            boolean deleteAtCommit)
        throws DatabaseException;

    /**
     * Add delete information, to be added to the inCompressor queue
     * when the transaction ends.
     */
    public void addDeleteInfo(BIN bin, Key deletedKey)
        throws DatabaseException {

        synchronized (this) {
            /* Maintain only one binRef per node. */
            if (deleteInfo == null) {
                deleteInfo = new HashMap();
            }
            Long nodeId = new Long(bin.getNodeId());
            BINReference binRef = (BINReference) deleteInfo.get(nodeId);
            if (binRef == null) {
                binRef = bin.createReference();
                deleteInfo.put(nodeId, binRef);  
            }
            binRef.addDeletedKey(deletedKey);
        }
    }
    
    /*
     * Manage locks owned by this transaction. Note that transactions that will
     * be multithreaded must override these methods and provide synchronized
     * implementations.
     */

    /**
     * Add a lock to set owned by this transaction.
     */
    abstract void addLock(Long nodeId,
                          LockType type,
                          LockGrantType grantStatus)
        throws DatabaseException;

    /**
     * @return true if this transaction created this node,
     * for a operation with transactional semantics.
     */
    public abstract boolean createdNode(long nodeId)
        throws DatabaseException;

    /**
     * Remove the lock from the set owned by this transaction. If specified to
     * LockManager.release, the lock manager will call this when its releasing
     * a lock.
     */
    abstract void removeLock(long nodeId)
        throws DatabaseException;

    /**
     * A lock is being demoted. Move it from the write collection into the read
     * collection.
     */
    abstract void moveWriteToReadLock(long nodeId, Lock lock);

    /**
     * Get lock count, for per transaction lock stats, for internal debugging.
     */
    public abstract LockStats collectStats(LockStats stats)
        throws DatabaseException;

    /* 
     * Check txn timeout, if set. Called by the lock manager when blocking on a
     * lock.
     */
    boolean isTimedOut()
        throws DatabaseException {

        if (txnStartMillis != 0) {
            long diff = System.currentTimeMillis() - txnStartMillis;
            if (diff > txnTimeOutMillis) {
                return true;
            } 
        }
        return false;
    }

    /* public for jca/ra/JELocalTransaction. */
    public long getTxnTimeOut() {
        return txnTimeOutMillis;
    }

    long getTxnStartMillis() {
        return txnStartMillis;
    }

    /**
     * Remove this Database from the protected Database handle set
     */
    void unregisterHandle(Database dbHandle) {

    	/* 
    	 * handleToHandleLockMap may be null if the db handle was never really
    	 * added. This might be the case because of an unregisterHandle that
    	 * comes from a finally clause, where the db handle was never
    	 * successfully opened.
    	 */
    	if (handleToHandleLockMap != null) {
            handleToHandleLockMap.remove(dbHandle);
    	}
    }

    /**
     * Remember how handle locks and handles match up.
     */
    public void addToHandleMaps(Long handleLockId, 
				Database databaseHandle) {
        Set dbHandleSet = null;
        if (handleLockToHandleMap == null) {

            /* 
	     * We do lazy initialization of the maps, since they're used
             * infrequently.
             */
            handleLockToHandleMap = new Hashtable();
            handleToHandleLockMap = new Hashtable();
        } else {
            dbHandleSet = (Set) handleLockToHandleMap.get(handleLockId);
        }

        if (dbHandleSet == null) {
            dbHandleSet = new HashSet();
            handleLockToHandleMap.put(handleLockId, dbHandleSet);
        }

        /* Map handle lockIds -> 1 or more database handles. */
        dbHandleSet.add(databaseHandle);
        /* Map database handles -> handle lock id */
        handleToHandleLockMap.put(databaseHandle, handleLockId);
    }

    /**
     * @return true if this txn is willing to give up the handle lock to
     * another txn before this txn ends.
     */
    public boolean isHandleLockTransferrable() {
        return true;
    }

    /**
     * The currentTxn passes responsiblity for this db handle lock to a txn
     * owned by the Database object.
     */
    void transferHandleLockToHandle(Database dbHandle)
        throws DatabaseException {

        /* 
         * Transfer responsiblity for this db lock from this txn to a new
         * protector.
         */
        Locker holderTxn = new BasicLocker(envImpl);
        transferHandleLock(dbHandle, holderTxn, true );
    }

    /**
     * 
     */
    public void transferHandleLock(Database dbHandle,
                                   Locker destLocker,
                                   boolean demoteToRead)
        throws DatabaseException {

        /* 
         * Transfer responsiblity for dbHandle's handle lock from this txn to
         * destLocker. If the dbHandle's databaseImpl is null, this handle
         * wasn't opened successfully.
         */
        if (DbInternal.dbGetDatabaseImpl(dbHandle) != null) {
            Long handleLockId = (Long) handleToHandleLockMap.get(dbHandle);
            if (handleLockId != null) {
                /* We have a handle lock for this db. */
                long nodeId = handleLockId.longValue();

                /* Move this lock to the destination txn. */
                lockManager.transfer(nodeId, this, destLocker, demoteToRead);

                /* 
                 * Make the destination txn remember that it now owns this
                 * handle lock.
                 */
                destLocker.addToHandleMaps(handleLockId, dbHandle);

                /* Take this out of the handle lock map. */
                Set dbHandleSet = (Set)
		    handleLockToHandleMap.get(handleLockId);
                Iterator iter = dbHandleSet.iterator();
                while (iter.hasNext()) {
                    if (((Database) iter.next()) == dbHandle) {
                        iter.remove();
                        break;
                    }
                }
                if (dbHandleSet.size() == 0) {
                    handleLockToHandleMap.remove(handleLockId);
                }
                
                /* 
                 * This Database must remember what txn owns it's handle lock.
                 */
                DbInternal.dbSetHandleLocker(dbHandle, destLocker);
            }
        }
    }
    
    /*
     * Helpers
     */
    public String toString() {
        String className = getClass().getName();
        className = className.substring(className.lastIndexOf('.') + 1);

        return Long.toString(id) + "_" +
               ((thread == null) ? "" : thread.getName()) + "_" +
               className;
    }

    /**
     * Dump lock table, for debugging
     */
    public void dumpLockTable() 
        throws DatabaseException {

        lockManager.dump();
    }
}
