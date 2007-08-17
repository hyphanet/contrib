/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Txn.java,v 1.148.2.7 2007/07/13 02:32:05 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.recovery.RecoveryManager;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.TreeLocation;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * A Txn is one that's created by a call to Environment.txnBegin.  This class
 * must support multithreaded use.
 */
public class Txn extends Locker implements Loggable {
    public static final byte TXN_NOSYNC = 0;
    public static final byte TXN_WRITE_NOSYNC = 1;
    public static final byte TXN_SYNC = 2;

    private static final String DEBUG_NAME =
        Txn.class.getName();

    private byte txnState;

    /*
     * Cursors opened under this txn. Implemented as a simple linked list to
     * conserve on memory.
     */
    private CursorImpl cursorSet; 

    /* txnState bits. */
    private static final byte USABLE = 0;
    private static final byte CLOSED = 1;
    private static final byte ONLY_ABORTABLE = 2;
    private static final byte STATE_BITS = 3;
    /* Set if prepare() has been called on this transaction. */
    private static final byte IS_PREPARED = 4;
    /* Set if xa_end(TMSUSPEND) has been called on this transaction. */
    private static final byte XA_SUSPENDED = 8;

    /*
     * A Txn can be used by multiple threads. Modification to the read and
     * write lock collections is done by synchronizing on the txn.
     */
    private Set readLocks;    // Set<Long> (nodeIds)
    private Map writeInfo;    // key=nodeid, data = WriteLockInfo

    private final int READ_LOCK_OVERHEAD = MemoryBudget.HASHSET_ENTRY_OVERHEAD;
    private final int WRITE_LOCK_OVERHEAD =
	MemoryBudget.HASHMAP_ENTRY_OVERHEAD +
	MemoryBudget.WRITE_LOCKINFO_OVERHEAD;

    /* 
     * We have to keep a set of DatabaseCleanupInfo objects so after 
     * commit or abort of Environment.truncateDatabase() or 
     * Environment.removeDatabase(), we can appropriately purge the
     * unneeded MapLN and DatabaseImpl.
     * Synchronize access to this set on this object.
     */
    private Set deletedDatabases; 

    /*
     * We need a map of the latest databaseImpl objects to drive the undo
     * during an abort, because it's too hard to look up the database object in
     * the mapping tree. (The normal code paths want to take locks, add
     * cursors, etc.
     */
    private Map undoDatabases;

    /* Last LSN logged for this transaction. */
    private long lastLoggedLsn = DbLsn.NULL_LSN;

    /* 
     * First LSN logged for this transaction -- used for keeping track of the
     * first active LSN point, for checkpointing. This field is not persistent.
     */
    private long firstLoggedLsn = DbLsn.NULL_LSN;

    /* Whether to flush and sync on commit by default. */
    private byte defaultFlushSyncBehavior;

    /* Whether to use Serializable isolation (prevent phantoms). */
    private boolean serializableIsolation;

    /* Whether to use Read-Committed isolation. */
    private boolean readCommittedIsolation;

    /* 
     * In-memory size, in bytes. A Txn tracks the memory needed for itself and
     * the readlock, writeInfo, undoDatabases, and deletedDatabases
     * collections, including the cost of each collection entry. However, the
     * actual Lock object memory cost is maintained within the Lock class.
     */
    private int inMemorySize;
    
    /*
     * accumluted memory budget delta.  Once this exceeds
     * ACCUMULATED_LIMIT we inform the MemoryBudget that a change
     * has occurred.
     */
    private int accumulatedDelta = 0;

    /*
     * Max allowable accumulation of memory budget changes before MemoryBudget
     * should be updated. This allows for consolidating multiple calls to
     * updateXXXMemoryBudget() into one call.  Not declared final so that unit
     * tests can modify this.  See SR 12273.
     */
    public static int ACCUMULATED_LIMIT = 10000;

    /**
     * Create a transaction from Environment.txnBegin.
     */
    public Txn(EnvironmentImpl envImpl, TransactionConfig config)
        throws DatabaseException {

        /*
         * Initialize using the config but don't hold a reference to it, since
         * it has not been cloned.
         */
        super(envImpl, config.getReadUncommitted(), config.getNoWait());
	init(envImpl, config);
    }

    public Txn(EnvironmentImpl envImpl, TransactionConfig config, long id)
        throws DatabaseException {

        /*
         * Initialize using the config but don't hold a reference to it, since
         * it has not been cloned.
         */
        super(envImpl, config.getReadUncommitted(), config.getNoWait());
	init(envImpl, config);

	this.id = id;
    }

    private void init(EnvironmentImpl envImpl, TransactionConfig config)
	throws DatabaseException {

        serializableIsolation = config.getSerializableIsolation();
        readCommittedIsolation = config.getReadCommitted();

        /* 
         * Figure out what we should do on commit. TransactionConfig could be
         * set with conflicting values; take the most stringent ones first.
         * All environment level defaults were applied by the caller.
         *
         * ConfigSync  ConfigWriteNoSync ConfigNoSync   default
         *    0                 0             0         sync
         *    0                 0             1         nosync
         *    0                 1             0         write nosync
         *    0                 1             1         write nosync
         *    1                 0             0         sync
         *    1                 0             1         sync
         *    1                 1             0         sync
         *    1                 1             1         sync
         */
        if (config.getSync()) {
            defaultFlushSyncBehavior = TXN_SYNC;
        } else if (config.getWriteNoSync()) {
            defaultFlushSyncBehavior = TXN_WRITE_NOSYNC;
        } else if (config.getNoSync()) {
            defaultFlushSyncBehavior = TXN_NOSYNC;
        } else {
            defaultFlushSyncBehavior = TXN_SYNC;
        }

        lastLoggedLsn = DbLsn.NULL_LSN;
        firstLoggedLsn = DbLsn.NULL_LSN;

        txnState = USABLE;

        /* 
         * Note: readLocks, writeInfo, undoDatabases, deleteDatabases are
         * initialized lazily in order to conserve memory. WriteInfo and
         * undoDatabases are treated as a package deal, because they are both
         * only needed if a transaction does writes.
         *
         * When a lock is added to this transaction, we add the collection
         * entry overhead to the memory cost, but don't add the lock
         * itself. That's taken care of by the Lock class.
         */
	updateMemoryUsage(MemoryBudget.TXN_OVERHEAD);

        this.envImpl.getTxnManager().registerTxn(this);
    }

    /**
     * Constructor for reading from log.
     */
    public Txn() {
        lastLoggedLsn = DbLsn.NULL_LSN;
    }

    /**
     * UserTxns get a new unique id for each instance.
     */
    protected long generateId(TxnManager txnManager) {
        return txnManager.incTxnId();
    }

    /**
     * Access to last LSN.
     */
    long getLastLsn() {
        return lastLoggedLsn;
    }

    public boolean getPrepared() {
	return (txnState & IS_PREPARED) != 0;
    }

    public void setPrepared(boolean prepared) {
	if (prepared) {
	    txnState |= IS_PREPARED;
	} else {
	    txnState &= ~IS_PREPARED;
	}
    }

    public void setSuspended(boolean suspended) {
	if (suspended) {
	    txnState |= XA_SUSPENDED;
	} else {
	    txnState &= ~XA_SUSPENDED;
	}
    }

    public boolean isSuspended() {
	return (txnState & XA_SUSPENDED) != 0;
    }

    /**
     * Gets a lock on this nodeId and, if it is a write lock, saves an abort
     * LSN.  Caller will set the abortLsn later, after the write lock has been
     * obtained.
     *
     * @see Locker#lockInternal
     * @Override
     */
    LockResult lockInternal(long nodeId,
			    LockType lockType,
                            boolean noWait,
                            DatabaseImpl database)
        throws DatabaseException {

	long timeout = 0;
        boolean useNoWait = noWait || defaultNoWait;
	synchronized (this) {
	    checkState(false);
            if (!useNoWait) {
                timeout = lockTimeOutMillis;
            }
	}

        /* Ask for the lock. */
        LockGrantType grant = lockManager.lock
            (nodeId, this, lockType, timeout, useNoWait, database);
    
	WriteLockInfo info = null;
	if (writeInfo != null) {
	    if (grant != LockGrantType.DENIED && lockType.isWriteLock()) {
		synchronized (this) {
		    info = (WriteLockInfo) writeInfo.get(new Long(nodeId));
		    /* Save the latest version of this database for undoing. */
		    undoDatabases.put(database.getId(), database);
		}
	    }
	}

	return new LockResult(grant, info);
    }

    public int prepare(Xid xid)
	throws DatabaseException {

	if ((txnState & IS_PREPARED) != 0) {
	    throw new DatabaseException
		("prepare() has already been called for Transaction " +
		 id + ".");
	}
	synchronized (this) {
	    checkState(false);
	    if (checkCursorsForClose()) {
		throw new DatabaseException
		    ("Transaction " + id +
		     " prepare failed because there were open cursors.");
	    }

	    setPrepared(true);
	    if (writeInfo == null) {
		return XAResource.XA_RDONLY;
	    }

            SingleItemEntry prepareEntry =
                new SingleItemEntry(LogEntryType.LOG_TXN_PREPARE,
                                    new TxnPrepare(id, xid));
            /* Flush required. */
	    LogManager logManager = envImpl.getLogManager();
	    logManager.logForceFlush(prepareEntry, true); // sync required
	}
	return XAResource.XA_OK;
    }

    public void commit(Xid xid)
	throws DatabaseException {

	commit(TXN_SYNC);
        envImpl.getTxnManager().unRegisterXATxn(xid, true);
	return;
    }

    public void abort(Xid xid)
	throws DatabaseException {

	abort(true);
        envImpl.getTxnManager().unRegisterXATxn(xid, false);
	return;
    }

    /**
     * Call commit() with the default sync configuration property.
     */
    public long commit()
        throws DatabaseException {

        return commit(defaultFlushSyncBehavior);
    }

    /**
     * Commit this transaction
     * 1. Releases read locks
     * 2. Writes a txn commit record into the log
     * 3. Flushes the log to disk.
     * 4. Add deleted LN info to IN compressor queue
     * 5. Release all write locks 
     * 
     * If any step of this fails, we must convert this transaction to an abort.
     */
    public long commit(byte flushSyncBehavior)
        throws DatabaseException {

        try {
            long commitLsn = DbLsn.NULL_LSN;
            synchronized (this) {
		checkState(false);
                if (checkCursorsForClose()) {
                    throw new DatabaseException
                        ("Transaction " + id +
                         " commit failed because there were open cursors.");
                }

                /*
                 * Save transferred write locks, if any.  Their abort LSNs are
                 * counted as obsolete further below.  Create the list lazily
                 * to avoid creating it in the normal case (no handle locks).
                 */
                List transferredWriteLockInfo = null;

                /* Transfer handle locks to their owning handles. */
                if (handleLockToHandleMap != null) {
                    Iterator handleLockIter =
                        handleLockToHandleMap.entrySet().iterator(); 
                    while (handleLockIter.hasNext()) {
                        Map.Entry entry = (Map.Entry) handleLockIter.next();
                        Long nodeId = (Long) entry.getKey();
                        if (writeInfo != null) {
                            WriteLockInfo info =
                                (WriteLockInfo) writeInfo.get(nodeId);
                            if (info != null) {
                                if (transferredWriteLockInfo == null) {
                                    transferredWriteLockInfo = new ArrayList();
                                }
                                transferredWriteLockInfo.add(info);
                            }
                        }
                        transferHandleLockToHandleSet
                            (nodeId, (Set) entry.getValue());
                    }
                }

                LogManager logManager = envImpl.getLogManager();

                /* 
                 * Release all read locks, clear lock collection. Optimize for
                 * the case where there are no read locks.
                 */
                int numReadLocks = clearReadLocks();

                /* 
                 * Log the commit if we ever held any write locks. Note that
                 * with dbhandle write locks, we may have held the write lock
                 * but then had it transferred away.
                 */
                int numWriteLocks = 0;
                if (writeInfo != null) {
                    numWriteLocks = writeInfo.size();
                    SingleItemEntry commitEntry =
                        new SingleItemEntry(LogEntryType.LOG_TXN_COMMIT,
                                            new TxnCommit(id, lastLoggedLsn));
		    if (flushSyncBehavior == TXN_SYNC) {
			/* Flush and sync required. */
			commitLsn = logManager.
			    logForceFlush(commitEntry, true);
		    } else if (flushSyncBehavior == TXN_WRITE_NOSYNC) {
			/* Flush but no sync required. */
			commitLsn = logManager.
			    logForceFlush(commitEntry, false);
		    } else {
			/* No flush, no sync required. */
			commitLsn = logManager.log(commitEntry);
		    }
                
                    /* 
                     * Set database state for deletes before releasing any
                     * write locks.
                     */
                    setDeletedDatabaseState(true);

                    /*
                     * Used to prevent double counting abortLNS if there is
                     * more then one node with the same abortLSN in this txn.
                     * Two nodes with the same abortLSN occur when a deleted
                     * slot is reused in the same txn.
                     */
                    Set alreadyCountedLsnSet = new HashSet();

                    /* Release all write locks, clear lock collection. */
                    Iterator iter = writeInfo.entrySet().iterator(); 
                    while (iter.hasNext()) {
                        Map.Entry entry = (Map.Entry) iter.next();
                        Long nodeId = (Long) entry.getKey();
			lockManager.release(nodeId.longValue(), this);
                        countWriteAbortLSN((WriteLockInfo) entry.getValue(),
					   alreadyCountedLsnSet);
		    }
                    writeInfo = null;

                    /* Count obsolete LSNs for transferred write locks. */
                    if (transferredWriteLockInfo != null) {
                        for (int i = 0;
                             i < transferredWriteLockInfo.size();
                             i += 1) {
                            WriteLockInfo info = (WriteLockInfo)
                                transferredWriteLockInfo.get(i);
                            countWriteAbortLSN(info, alreadyCountedLsnSet);
                        }
                    }

                    /* Unload delete info, but don't wake up the compressor. */
                    if ((deleteInfo != null) && deleteInfo.size() > 0) {
                        envImpl.addToCompressorQueue(deleteInfo.values(),
                                                     false); // don't wakeup
                        deleteInfo.clear();
                    }
                }

                traceCommit(numWriteLocks, numReadLocks);
            }

            /*
             * Purge any databaseImpls not needed as a result of the commit.
             * Be sure to do this outside the synchronization block, to avoid
             * conflict w/checkpointer.
             */
            cleanupDatabaseImpls(true);

            /* 
             * Unregister this txn. Be sure to do this outside the
             * synchronization block, to avoid conflict w/checkpointer.
             */
            close(true);
            return commitLsn;
        } catch (RunRecoveryException e) {

            /* May have received a thread interrupt. */
            throw e;
	} catch (Error e) {
	    envImpl.invalidate(e);
	    throw e;
        } catch (Exception t) {

            try {

		/*
		 * If the exception thrown is a DatabaseException it indicates
		 * that the write() call hit an IOException, probably out of
		 * disk space, and attempted to rewrite all commit records as
		 * abort records.  Since the abort records are already
		 * rewritten (or at least attempted to be rewritten), there is
		 * no reason to have abort attempt to write an abort record
		 * again.  See [11271].
		 */
                abortInternal(flushSyncBehavior == TXN_SYNC,
			      !(t instanceof DatabaseException));
                Tracer.trace(envImpl, "Txn", "commit",
                             "Commit of transaction " + id + " failed", t);
	    } catch (Error e) {
		envImpl.invalidate(e);
		throw e;
            } catch (Exception abortT2) {
                throw new DatabaseException
                    ("Failed while attempting to commit transaction " +
                     id +
                     ". The attempt to abort and clean up also failed. " +
                     "The original exception seen from commit = " +
                     t.getMessage() +
                     " The exception from the cleanup = " +
                     abortT2.getMessage(),
                     t);
            }
                
            /* Now throw an exception that shows the commit problem. */
            throw new DatabaseException
                ("Failed while attempting to commit transaction " + id +
                 ", aborted instead. Original exception = " +
                 t.getMessage(), t);
        }
    }

    /**
     * Count the abortLSN as obsolete.  Do not count if a slot with a deleted
     * LN was reused (abortKnownDeleted), to avoid double counting.  And count
     * each abortLSN only once.
     */
    private void countWriteAbortLSN(WriteLockInfo info,
                                    Set alreadyCountedLsnSet)
        throws DatabaseException {

        if (info.abortLsn != DbLsn.NULL_LSN && 
            !info.abortKnownDeleted) {
            Long longLsn = new Long(info.abortLsn);
            if (!alreadyCountedLsnSet.contains(longLsn)) {
                envImpl.getLogManager().countObsoleteNode
                    (info.abortLsn, null, info.abortLogSize);
                alreadyCountedLsnSet.add(longLsn);
            }
        }
    }

    /**
     * Abort this transaction. Steps are:
     * 1. Release LN read locks.
     * 2. Write a txn abort entry to the log. This is only for log
     *    file cleaning optimization and there's no need to guarantee a
     *    flush to disk.  
     * 3. Find the last LN log entry written for this txn, and use that
     *    to traverse the log looking for nodes to undo. For each node,
     *    use the same undo logic as recovery to rollback the transaction. Note
     *    that we walk the log in order to undo in reverse order of the
     *    actual operations. For example, suppose the txn did this:
     *       delete K1/D1 (in LN 10)
     *       create K1/D1 (in LN 20)
     *    If we process LN10 before LN 20, we'd inadvertently create a 
     *    duplicate tree of "K1", which would be fatal for the mapping tree.
     * 4. Release the write lock for this LN.
     */
    public long abort(boolean forceFlush)
        throws DatabaseException {

	return abortInternal(forceFlush, true);
    }

    private long abortInternal(boolean forceFlush, boolean writeAbortRecord)
	throws DatabaseException {

        try {
            int numReadLocks;
            int numWriteLocks;
            long abortLsn;

            synchronized (this) {
                checkState(true);

                /* Log the abort. */
                SingleItemEntry abortEntry =
                    new SingleItemEntry(LogEntryType.LOG_TXN_ABORT,
                                        new TxnAbort(id, lastLoggedLsn));
                abortLsn = DbLsn.NULL_LSN;
                if (writeInfo != null) {
		    if (writeAbortRecord) {
			if (forceFlush) {
			    abortLsn = envImpl.getLogManager().
				logForceFlush(abortEntry, true);
			} else {
			    abortLsn =
				envImpl.getLogManager().log(abortEntry);
			}
		    }
                }

                /* Undo the changes. */
                undo();

                /*
                 * Release all read locks after the undo (since the undo may
                 * need to read in mapLNs).
                 */
                numReadLocks = (readLocks == null) ? 0 : clearReadLocks();

                /* 
                 * Set database state for deletes before releasing any write
                 * locks.
                 */
                setDeletedDatabaseState(false);

                /* Throw away write lock collection. */
                numWriteLocks = (writeInfo == null) ? 0 : clearWriteLocks();

                /*
                 * Let the delete related info (binreferences and dbs) get
                 * gc'ed. Don't explicitly iterate and clear -- that's far less
                 * efficient, gives GC wrong input.
                 */
                deleteInfo = null;
            }

            /*
             * Purge any databaseImpls not needed as a result of the abort.  Be
             * sure to do this outside the synchronization block, to avoid
             * conflict w/checkpointer.
             */ 
            cleanupDatabaseImpls(false);

            synchronized (this) {
                boolean openCursors = checkCursorsForClose();
                Tracer.trace(Level.FINE,
                             envImpl,
                             "Abort:id = " + id +
                             " numWriteLocks= " + numWriteLocks +
                             " numReadLocks= " + numReadLocks +
                             " openCursors= " + openCursors);
                if (openCursors) {
                    throw new DatabaseException
                        ("Transaction " + id +
                         " detected open cursors while aborting");
                }
                /* Unload any db handles protected by this txn. */
                if (handleToHandleLockMap != null) {
                    Iterator handleIter =
                        handleToHandleLockMap.keySet().iterator(); 
                    while (handleIter.hasNext()) {
                        Database handle = (Database) handleIter.next();
                        DbInternal.dbInvalidate(handle);
                    }
                }

                return abortLsn;
            }
        } finally {

            /* 
             * Unregister this txn, must be done outside synchronization block
             * to avoid conflict w/checkpointer.
             */
            close(false);
        }
    }

    /**
     * Rollback the changes to this txn's write locked nodes.
     */
    private void undo() 
        throws DatabaseException {
        
        Long nodeId = null;
        long undoLsn = lastLoggedLsn;
        LogManager logManager = envImpl.getLogManager();

        try {
            Set alreadyUndone = new HashSet();
            TreeLocation location = new TreeLocation();
            while (undoLsn != DbLsn.NULL_LSN) {

                LNLogEntry undoEntry =
		    (LNLogEntry) logManager.getLogEntry(undoLsn);
                LN undoLN = undoEntry.getLN();
                nodeId = new Long(undoLN.getNodeId());

                /* 
                 * Only process this if this is the first time we've seen this
                 * node. All log entries for a given node have the same
                 * abortLsn, so we don't need to undo it multiple times.
                 */
                if (!alreadyUndone.contains(nodeId)) {
                    alreadyUndone.add(nodeId);
                    DatabaseId dbId = undoEntry.getDbId();
                    DatabaseImpl db = (DatabaseImpl) undoDatabases.get(dbId);
                    undoLN.postFetchInit(db, undoLsn);
                    long abortLsn = undoEntry.getAbortLsn();
                    boolean abortKnownDeleted =
                        undoEntry.getAbortKnownDeleted();
                    try {
                        RecoveryManager.undo(Level.FINER,
                                             db,
                                             location,
                                             undoLN,
                                             undoEntry.getKey(),
                                             undoEntry.getDupKey(),
                                             undoLsn,
                                             abortLsn,
                                             abortKnownDeleted,
                                             null, false);
                    } finally {
                        if (location.bin != null) {
                            location.bin.releaseLatchIfOwner();
                        }
                    }
            
                    /*
                     * The LN undone is counted as obsolete if it is not a
                     * deleted LN.  Deleted LNs are counted as obsolete when
                     * they are logged.
                     */
                    if (!undoLN.isDeleted()) {
                        logManager.countObsoleteNode
                            (undoLsn,
                             null,  // type
                             undoLN.getLastLoggedSize());
                    }
                }

                /* Move on to the previous log entry for this txn. */
                undoLsn = undoEntry.getUserTxn().getLastLsn();
            }
        } catch (RuntimeException e) {
            throw new DatabaseException("Txn undo for node=" + nodeId +
                                        " LSN=" +
                                        DbLsn.getNoFormatString(undoLsn), e);
        } catch (DatabaseException e) {
            Tracer.trace(envImpl, "Txn", "undo", 
			 "for node=" + nodeId + " LSN=" +
			 DbLsn.getNoFormatString(undoLsn), e);
            throw e;
        }
    }

    private int clearWriteLocks()
	throws DatabaseException {

	int numWriteLocks = writeInfo.size();

	/* Release all write locks, clear lock collection. */
	Iterator iter = writeInfo.entrySet().iterator(); 
	while (iter.hasNext()) {
	    Map.Entry entry = (Map.Entry) iter.next();
	    Long nodeId = (Long) entry.getKey();
	    lockManager.release(nodeId.longValue(), this);
	}
	writeInfo = null;
	return numWriteLocks;
    }

    private int clearReadLocks()
	throws DatabaseException {

	int numReadLocks = 0;
	if (readLocks != null) {
	    numReadLocks = readLocks.size();
	    Iterator iter = readLocks.iterator();
	    while (iter.hasNext()) {
		Long rLock = (Long) iter.next();
		lockManager.release(rLock.longValue(), this);
	    }
	    readLocks = null;
	}
	return numReadLocks;
    }

    /**
     * Called by the recovery manager when logging a transaction aware object.
     * This method is synchronized by the caller, by being called within the
     * log latch. Record the last LSN for this transaction, to create the
     * transaction chain, and also record the LSN in the write info for abort
     * logic.
     */
    public void addLogInfo(long lastLsn)
        throws DatabaseException {

        /* Save the last LSN  for maintaining the transaction LSN chain. */
        lastLoggedLsn = lastLsn;

        /* Save handle to LSN for aborts. */
        synchronized (this) {

            /*
             * If this is the first LSN, save it for calculating the first LSN
             * of any active txn, for checkpointing.
             */
            if (firstLoggedLsn == DbLsn.NULL_LSN) {
                firstLoggedLsn = lastLsn;
            }
        }
    }

    /**
     * @return first logged LSN, to aid recovery rollback.
     */
    long getFirstActiveLsn() 
        throws DatabaseException {

        synchronized (this) {
            return firstLoggedLsn;
        }
    }

    /**
     * @param dbImpl databaseImpl to remove
     * @param deleteAtCommit true if this databaseImpl should be cleaned on 
     *    commit, false if it should be cleaned on abort.
     * @param mb environment memory budget.
     */
    public void markDeleteAtTxnEnd(DatabaseImpl dbImpl, boolean deleteAtCommit)
        throws DatabaseException {

        synchronized (this) {
            int delta = 0;
            if (deletedDatabases == null) {
                deletedDatabases = new HashSet();
                delta += MemoryBudget.HASHSET_OVERHEAD;
            }

            deletedDatabases.add(new DatabaseCleanupInfo(dbImpl,
                                                         deleteAtCommit));
            delta += MemoryBudget.HASHSET_ENTRY_OVERHEAD + 
                MemoryBudget.OBJECT_OVERHEAD;
	    updateMemoryUsage(delta);

            /* releaseDb will be called by cleanupDatabaseImpls. */
        }
    }

    /* 
     * Leftover databaseImpls that are a by-product of database operations like
     * removeDatabase(), truncateDatabase() will be deleted after the write
     * locks are released. However, do set the database state appropriately
     * before the locks are released.
     */
    private void setDeletedDatabaseState(boolean isCommit) 
        throws DatabaseException {

        if (deletedDatabases != null) {
            Iterator iter = deletedDatabases.iterator();
            while (iter.hasNext()) {
                DatabaseCleanupInfo info = (DatabaseCleanupInfo) iter.next();
                if (info.deleteAtCommit == isCommit) {
                    info.dbImpl.startDeleteProcessing();
                }
            }
        }
    }

    /**
     * Cleanup leftover databaseImpls that are a by-product of database
     * operations like removeDatabase(), truncateDatabase().
     * 
     * This method must be called outside the synchronization on this txn,
     * because it calls releaseDeletedINs, which gets the TxnManager's
     * allTxns latch. The checkpointer also gets the allTxns latch, and within
     * that latch, needs to synchronize on individual txns, so we must avoid a
     * latching hiearchy conflict.
     */
    private void cleanupDatabaseImpls(boolean isCommit) 
        throws DatabaseException {

        if (deletedDatabases != null) {
            /* Make a copy of the deleted databases while synchronized. */
            DatabaseCleanupInfo[] infoArray;
            synchronized (this) {
                infoArray = new DatabaseCleanupInfo[deletedDatabases.size()];
                deletedDatabases.toArray(infoArray);
            }
            for (int i = 0; i < infoArray.length; i += 1) {
                DatabaseCleanupInfo info = infoArray[i];
                if (info.deleteAtCommit == isCommit) {
                    /* releaseDb will be called by releaseDeletedINs. */
                    info.dbImpl.releaseDeletedINs();
                } else {
                    envImpl.releaseDb(info.dbImpl);
                }
            }
            deletedDatabases = null;
        }
    }

    /**
     * Add lock to the appropriate queue.
     */
    void addLock(Long nodeId,
                 LockType type,
		 LockGrantType grantStatus) 
        throws DatabaseException {

        synchronized (this) {
            int delta = 0;
            if (type.isWriteLock()) {
                if (writeInfo == null) {
                    writeInfo = new HashMap();
                    undoDatabases = new HashMap();
                    delta += MemoryBudget.TWOHASHMAPS_OVERHEAD;
                }

                writeInfo.put(nodeId, 
                              new WriteLockInfo());
                delta += WRITE_LOCK_OVERHEAD;
                
                if ((grantStatus == LockGrantType.PROMOTION) ||
                    (grantStatus == LockGrantType.WAIT_PROMOTION)) {
                    readLocks.remove(nodeId);
                    delta -= READ_LOCK_OVERHEAD;
                }
		updateMemoryUsage(delta);
            } else {
                addReadLock(nodeId);
            }
        }
    }

    private void addReadLock(Long nodeId) {
        int delta = 0;
        if (readLocks == null) {
            readLocks = new HashSet();
            delta = MemoryBudget.HASHSET_OVERHEAD;
        }
        
        readLocks.add(nodeId);
        delta += READ_LOCK_OVERHEAD;
	updateMemoryUsage(delta);
    }

    /**
     * Remove the lock from the set owned by this transaction. If specified to
     * LockManager.release, the lock manager will call this when its releasing
     * a lock. Usually done because the transaction doesn't need to really keep
     * the lock, i.e for a deleted record.
     */
    void removeLock(long nodeId)
        throws DatabaseException {

        /* 
         * We could optimize by passing the lock type so we know which
         * collection to look in. Be careful of demoted locks, which have
         * shifted collection.
         *
         * Don't bother updating memory utilization here -- we'll update at
         * transaction end.
         */
        synchronized (this) {
	    if ((readLocks != null) &&
		readLocks.remove(new Long(nodeId))) {
		updateMemoryUsage(0 - READ_LOCK_OVERHEAD);
	    } else if ((writeInfo != null) &&
		       (writeInfo.remove(new Long(nodeId)) != null)) {
		updateMemoryUsage(0 - WRITE_LOCK_OVERHEAD);
            }
        }
    }

    /**
     * A lock is being demoted. Move it from the write collection into the read
     * collection.
     */
    void moveWriteToReadLock(long nodeId, Lock lock) {
        
        boolean found = false;
        synchronized (this) {
            if ((writeInfo != null) &&
                (writeInfo.remove(new Long(nodeId)) != null)) {
                found = true;
		updateMemoryUsage(0 - WRITE_LOCK_OVERHEAD);
            }

            assert found : "Couldn't find lock for Node " + nodeId +
                " in writeInfo Map.";
            addReadLock(new Long(nodeId));
        }
    }

    private void updateMemoryUsage(int delta) {
        inMemorySize += delta;
	accumulatedDelta += delta;
	if (accumulatedDelta > ACCUMULATED_LIMIT ||
	    accumulatedDelta < -ACCUMULATED_LIMIT) {
	    envImpl.getMemoryBudget().updateMiscMemoryUsage(accumulatedDelta);
	    accumulatedDelta = 0;
	}
    }

    int getAccumulatedDelta() {
	return accumulatedDelta;
    }

    /**
     * @return true if this transaction created this node. We know that this
     * is true if the node is write locked and has a null abort LSN.
     */
    public boolean createdNode(long nodeId) 
        throws DatabaseException {

        boolean created = false;
        synchronized (this) {
            if (writeInfo != null) {
                WriteLockInfo info = (WriteLockInfo)
                    writeInfo.get(new Long(nodeId));
                if (info != null) {
                    created = info.createdThisTxn;
                }
            }
	}
        return created;
    }

    /**
     * @return the abortLsn for this node.
     */
    public long getAbortLsn(long nodeId) 
        throws DatabaseException {

        WriteLockInfo info = null;
        synchronized (this) {
            if (writeInfo != null) {
                info = (WriteLockInfo) writeInfo.get(new Long(nodeId));
            }
        }

        if (info == null) {
            return DbLsn.NULL_LSN;
        } else {
            return info.abortLsn;
        }
    }

    /**
     * @return the WriteLockInfo for this node.
     */
    public WriteLockInfo getWriteLockInfo(long nodeId)
	throws DatabaseException {

        WriteLockInfo info = WriteLockInfo.basicWriteLockInfo;
        synchronized (this) {
            if (writeInfo != null) {
                info = (WriteLockInfo) writeInfo.get(new Long(nodeId));
            }
        }

	return info;
    }

    /**
     * Is always transactional.
     */
    public boolean isTransactional() {
        return true;
    }

    /**
     * Is serializable isolation if so configured.
     */
    public boolean isSerializableIsolation() {
        return serializableIsolation;
    }

    /**
     * Is read-committed isolation if so configured.
     */
    public boolean isReadCommittedIsolation() {
        return readCommittedIsolation;
    }

    /**
     * This is a transactional locker.
     */
    public Txn getTxnLocker() {
        return this;
    }

    /**
     * Returns 'this', since this locker holds no non-transactional locks.
     */
    public Locker newNonTxnLocker()
        throws DatabaseException {

        return this;
    }

    /**
     * This locker holds no non-transactional locks.
     */
    public void releaseNonTxnLocks()
        throws DatabaseException {
    }

    /**
     * Created transactions do nothing at the end of the operation.
     */
    public void operationEnd()
        throws DatabaseException {
    }

    /**
     * Created transactions do nothing at the end of the operation.
     */
    public void operationEnd(boolean operationOK)
        throws DatabaseException {
    }

    /**
     * Created transactions don't transfer locks until commit.
     */
    public void setHandleLockOwner(boolean ignore /*operationOK*/,
                                   Database dbHandle, 
                                   boolean dbIsClosing) 
        throws DatabaseException {

        if (dbIsClosing) {

            /* 
             * If the Database handle is closing, take it out of the both the
             * handle lock map and the handle map. We don't need to do any
             * transfers at commit time, and we don't need to do any
             * invalidations at abort time.
             */
            Long handleLockId = (Long) handleToHandleLockMap.get(dbHandle);
            if (handleLockId != null) {
                Set dbHandleSet = (Set)
		    handleLockToHandleMap.get(handleLockId);  
                boolean removed = dbHandleSet.remove(dbHandle);
                assert removed :
		    "Can't find " + dbHandle + " from dbHandleSet";
                if (dbHandleSet.size() == 0) {
                    Object foo = handleLockToHandleMap.remove(handleLockId);
                    assert (foo != null) :
			"Can't find " + handleLockId +
			" from handleLockIdtoHandleMap.";
                }
            }

            unregisterHandle(dbHandle);

        } else {

            /* 
             * If the db is still open, make sure the db knows this txn is its
             * handle lock protector and that this txn knows it owns this db
             * handle.
             */
            if (dbHandle != null) {
                DbInternal.dbSetHandleLocker(dbHandle, this);
            }
        }
    }

    /**
     * Cursors operating under this transaction are added to the collection.
     */
    public void registerCursor(CursorImpl cursor) 
        throws DatabaseException {

        synchronized(this) {
            /* Add to the head of the list. */
            cursor.setLockerNext(cursorSet);
            if (cursorSet != null) {
                cursorSet.setLockerPrev(cursor);
            }
            cursorSet = cursor;
        }
    }

    /**
     * Remove a cursor from the collection.
     */
    public void unRegisterCursor(CursorImpl cursor) 
        throws DatabaseException {

        synchronized (this) {
            CursorImpl prev = cursor.getLockerPrev();
            CursorImpl next = cursor.getLockerNext();
            if (prev == null) {
                cursorSet = next;
            } else {
                prev.setLockerNext(next);
            }

            if (next != null) {
                next.setLockerPrev(prev);
            }
            cursor.setLockerPrev(null);
            cursor.setLockerNext(null);
        }
    }

    /**
     * @return true if this txn is willing to give up the handle lock to
     * another txn before this txn ends.
     */
    public boolean isHandleLockTransferrable() {
        return false;
    }

    /**
     * Check if all cursors associated with the txn are closed. If not, those
     * open cursors will be forcibly closed.
     * @return true if open cursors exist
     */
    private boolean checkCursorsForClose()
        throws DatabaseException {

        CursorImpl c = cursorSet;
        while (c != null) {
            if (!c.isClosed()) {
                return true;
            }
            c = c.getLockerNext();
        }

        return false;
    }

    /**
     * stats
     */
    public LockStats collectStats(LockStats stats)
        throws DatabaseException {

        synchronized (this) {
            int nReadLocks = (readLocks == null) ? 0 : readLocks.size();
            stats.setNReadLocks(stats.getNReadLocks() + nReadLocks);
            int nWriteLocks = (writeInfo == null) ? 0 : writeInfo.size();
            stats.setNWriteLocks(stats.getNWriteLocks() + nWriteLocks);
        }

        return stats;
    }

    /**
     * Set the state of a transaction to ONLY_ABORTABLE.
     */
    public void setOnlyAbortable() {
	txnState &= ~STATE_BITS;
	txnState |= ONLY_ABORTABLE;
    }

    /**
     * Get the state of a transaction's ONLY_ABORTABLE.
     */
    public boolean getOnlyAbortable() {
	return (txnState & ONLY_ABORTABLE) != 0;
    }

    /**
     * Throw an exception if the transaction is not open.
     *
     * If calledByAbort is true, it means we're being called
     * from abort().
     *
     * Caller must invoke with "this" synchronized.
     */
    protected void checkState(boolean calledByAbort)
        throws DatabaseException {

        boolean ok = false;
	boolean onlyAbortable = false;
	byte state = (byte) (txnState & STATE_BITS);
	ok = (state == USABLE);
	onlyAbortable = (state == ONLY_ABORTABLE);

	if (!calledByAbort && onlyAbortable) {

	    /*
	     * It's ok for FindBugs to whine about id not being synchronized.
	     */
            throw new DatabaseException
		("Transaction " + id + " must be aborted.");
	}

	if (ok ||
	    (calledByAbort && onlyAbortable)) {
	    return;
	}

	/*
	 * It's ok for FindBugs to whine about id not being synchronized.
	 */
	throw new DatabaseException
	    ("Transaction " + id + " has been closed.");
    }

    /**
     */
    private void close(boolean isCommit)
        throws DatabaseException {

        synchronized (this) {
	    txnState &= ~STATE_BITS;
	    txnState |= CLOSED;
        }

        /* 
         * UnregisterTxn must be called outside the synchronization on this
         * txn, because it gets the TxnManager's allTxns latch. The
         * checkpointer also gets the allTxns latch, and within that latch,
         * needs to synchronize on individual txns, so we must avoid a latching
         * hiearchy conflict.
         */
        envImpl.getTxnManager().unRegisterTxn(this, isCommit);
    }

    /*
     * Log support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.LONG_BYTES + // id
               LogUtils.LONG_BYTES;  // lastLoggedLsn
    }

    /**
     * @see Loggable#writeToLog
     */
    /*
     * It's ok for FindBugs to whine about id not being synchronized.
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeLong(logBuffer, id);
        LogUtils.writeLong(logBuffer, lastLoggedLsn);
    }

    /**
     * @see Loggable#readFromLog
     *
     * It's ok for FindBugs to whine about id not being synchronized.
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryTypeVersion) {
        id = LogUtils.readLong(logBuffer);
        lastLoggedLsn = LogUtils.readLong(logBuffer);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<txn id=\"");
        sb.append(super.toString());
        sb.append("\">");
	sb.append(DbLsn.toString(lastLoggedLsn));
        sb.append("</txn>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return getId();
    }

    /**
     * Transfer a single handle lock to the set of corresponding handles at
     * commit time.
     */
    private void transferHandleLockToHandleSet(Long handleLockId,
					       Set dbHandleSet) 
        throws DatabaseException {

        /* Create a set of destination transactions */
        int numHandles = dbHandleSet.size();
        Database [] dbHandles = new Database[numHandles];
        dbHandles = (Database []) dbHandleSet.toArray(dbHandles);
        Locker [] destTxns = new Locker[numHandles];
        for (int i = 0; i < numHandles; i++) {
            destTxns[i] = new BasicLocker(envImpl);
        }
                
        /* Move this lock to the destination txns. */
        long nodeId = handleLockId.longValue();
        lockManager.transferMultiple(nodeId, this, destTxns);

        for (int i = 0; i < numHandles; i++) {

            /* 
             * Make this handle and its handle protector txn remember each
             * other.
             */
            destTxns[i].addToHandleMaps(handleLockId, dbHandles[i]);
            DbInternal.dbSetHandleLocker(dbHandles[i], destTxns[i]);
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.  The string
     * construction can be numerous enough to show up on a performance profile.
     */
    private void traceCommit(int numWriteLocks, int numReadLocks) {
        Logger logger = envImpl.getLogger();
        if (logger.isLoggable(Level.FINE)) {
            StringBuffer sb = new StringBuffer();
            sb.append(" Commit:id = ").append(id);
            sb.append(" numWriteLocks=").append(numWriteLocks);
            sb.append(" numReadLocks = ").append(numReadLocks);
            Tracer.trace(Level.FINE, envImpl, sb.toString());
        }
    }

    int getInMemorySize() {
        return inMemorySize;
    }

    /**
     * Store information about a DatabaseImpl that will have to be
     * purged at transaction commit or abort. This handles cleanup after
     * operations like Environment.truncateDatabase, 
     * Environment.removeDatabase. Cleanup like this is done outside the
     * usual transaction commit or node undo processing, because
     * the mapping tree is always AutoTxn'ed to avoid deadlock and is 
     * essentially  non-transactional
     */
    private static class DatabaseCleanupInfo {
        DatabaseImpl dbImpl;

        /* if true, clean on commit. If false, clean on abort. */
        boolean deleteAtCommit; 

        DatabaseCleanupInfo(DatabaseImpl dbImpl,
                            boolean deleteAtCommit) {
            this.dbImpl = dbImpl;
            this.deleteAtCommit = deleteAtCommit;
        }
    }
}
