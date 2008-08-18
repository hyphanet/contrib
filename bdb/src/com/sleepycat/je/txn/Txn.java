/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Txn.java,v 1.194 2008/05/28 15:39:59 sam Exp $
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
import com.sleepycat.je.Durability;
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
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.recovery.RecoveryManager;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.TreeLocation;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * A Txn is the internal representation of a transaction created by a call to
 * Environment.txnBegin.  This class must support multi-threaded use.
 */
public class Txn extends Locker implements Loggable {

    @SuppressWarnings("unused")
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
    private Set<Long> readLocks;    // Set<Long> (nodeIds)
    private Map<Long,WriteLockInfo> writeInfo;    // key=nodeid

    private final int READ_LOCK_OVERHEAD = MemoryBudget.HASHSET_ENTRY_OVERHEAD;
    private final int WRITE_LOCK_OVERHEAD =
        MemoryBudget.HASHMAP_ENTRY_OVERHEAD +
        MemoryBudget.WRITE_LOCKINFO_OVERHEAD;

    /*
     * We have to keep a set of DatabaseCleanupInfo objects so after commit or
     * abort of Environment.truncateDatabase() or Environment.removeDatabase(),
     * we can appropriately purge the unneeded MapLN and DatabaseImpl.
     * Synchronize access to this set on this object.
     */
    private Set<DatabaseCleanupInfo> deletedDatabases;

    /*
     * We need a map of the latest databaseImpl objects to drive the undo
     * during an abort, because it's too hard to look up the database object in
     * the mapping tree. (The normal code paths want to take locks, add
     * cursors, etc.
     */
    protected Map<DatabaseId,DatabaseImpl> undoDatabases;

    /* Last LSN logged for this transaction. */
    protected long lastLoggedLsn = DbLsn.NULL_LSN;

    /*
     * First LSN logged for this transaction -- used for keeping track of the
     * first active LSN point, for checkpointing. This field is not persistent.
     */
    private long firstLoggedLsn = DbLsn.NULL_LSN;

    /* The configured durability at the time the transaction was created. */
    private Durability defaultDurability;

    /* The durability used for the actual commit. */
    private Durability commitDurability;

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
     * Accumulated memory budget delta.  Once this exceeds ACCUMULATED_LIMIT we
     * inform the MemoryBudget that a change has occurred.
     */
    private int accumulatedDelta = 0;

    /*
     * Max allowable accumulation of memory budget changes before MemoryBudget
     * should be updated. This allows for consolidating multiple calls to
     * updateXXXMemoryBudget() into one call.  Not declared final so that unit
     * tests can modify this.  See SR 12273.
     */
    public static int ACCUMULATED_LIMIT = 10000;

    /*
     * Each Txn instance has a handle on a ReplicationContext instance for use
     * in logging a TxnCommit or TxnAbort log entries.
     */
    protected ReplicationContext repContext;

    /*
     * Used to track mixed mode (sync/durability) transaction API usage. When
     * the sync based api is removed, these tracking ivs can be as well.
     */
    private boolean explicitSyncConfigured = false;
    private boolean explicitDurabilityConfigured = false;

    /* Determines whether the transaction is auto-commit */
    private boolean isAutoCommit = false;


    /**
     * Constructor for reading from log.
     */
    public Txn() {
        lastLoggedLsn = DbLsn.NULL_LSN;
    }

    /**
     * Create a transaction from Environment.txnBegin. Should only be used in
     * cases where we are sure we'll set the repContext field before the
     * transaction is ended. For example, should not be used to create
     * standalone Txns for a unit test.
     */
    protected Txn(EnvironmentImpl envImpl, TransactionConfig config)
        throws DatabaseException {

        /*
         * Initialize using the config but don't hold a reference to it, since
         * it has not been cloned.
         */
        super(envImpl, config.getReadUncommitted(), config.getNoWait(), 0);
        initTxn(envImpl, config);
    }

    static Txn createTxn(EnvironmentImpl envImpl, TransactionConfig config)
        throws DatabaseException {

        Txn ret = null;
        try {
            ret = envImpl.isReplicated() ?
                  envImpl.getReplicator().createRepTxn(envImpl, config) :
                  new Txn(envImpl, config);
            ret.initApiReadLock();
        } catch (DatabaseException DE) {
            if (ret != null) {
                ret.close(false);
            }
            throw DE;
        }
        return ret;
    }

    /**
     * This is only for use by subtypes which arbitrarily impose a transaction
     * id value onto the transaction. This is done by implementing a version of
     * Locker.generateId() which uses the proposed id.
     */
    protected Txn(EnvironmentImpl envImpl,
                  TransactionConfig config,
                  boolean noAPIReadLock,
                  long mandatedId)
        throws DatabaseException {

        /*
         * Initialize using the config but don't hold a reference to it, since
         * it has not been cloned.
         */
        super(envImpl,
              config.getReadUncommitted(),
              config.getNoWait(),
              noAPIReadLock,
              mandatedId);
        initTxn(envImpl, config);
    }

    /**
     * Create a Txn for use in a unit test, where we won't need a auto Txn or a
     * com.sleepycat.je.Transaction. In a real life transaction, we don't know
     * a priori at the time of Txn construction whether the transaction needs
     * to be replicated.
     */
    public Txn(EnvironmentImpl envImpl,
               TransactionConfig config,
               ReplicationContext repContext)
        throws DatabaseException {

        /*
         * Initialize using the config but don't hold a reference to it, since
         * it has not been cloned.
         */
        super(envImpl, config.getReadUncommitted(), config.getNoWait(), 0);
        initTxn(envImpl, config);
        setRepContext(repContext);
    }

    public static Txn createTxn(EnvironmentImpl envImpl,
                                TransactionConfig config,
                                ReplicationContext repContext)
        throws DatabaseException {

        Txn ret = null;
        try {
            ret = envImpl.isReplicated() ?
                  envImpl.getReplicator().createRepTxn(envImpl,
                                                       config,
                                                       repContext) :
                  new Txn(envImpl, config, repContext);
            ret.initApiReadLock();
        } catch (DatabaseException DE) {
            if (ret != null) {
                ret.close(false);
            }
            throw DE;
        }
        return ret;
    }

    public static Txn createAutoTxn(EnvironmentImpl envImpl,
                                    TransactionConfig config,
                                    boolean noAPIReadLock,
                                    ReplicationContext repContext)
        throws DatabaseException {

        Txn ret = null;
        try {
            ret = envImpl.isReplicated() ?
                  envImpl.getReplicator().createRepTxn(envImpl,
                                                       config,
                                                       noAPIReadLock,
                                                       0 /* mandatedId */) :
                  new Txn(envImpl, config, noAPIReadLock, 0 /* mandatedId */);
            ret.isAutoCommit = true;
            ret.setRepContext(repContext);
            ret.initApiReadLock();
        } catch (DatabaseException DE) {
            if (ret != null) {
                ret.close(false);
            }
            throw DE;
        }
        return ret;
    }

    private void initTxn(EnvironmentImpl envImpl,
                         TransactionConfig config)
        throws DatabaseException {

        serializableIsolation = config.getSerializableIsolation();
        readCommittedIsolation = config.getReadCommitted();
        defaultDurability = config.getDurability();
        if (defaultDurability == null) {
            explicitDurabilityConfigured = false;
            defaultDurability = config.getDurabilityFromSync();
        } else {
            explicitDurabilityConfigured = true;
        }
        explicitSyncConfigured =
            config.getSync() || config.getNoSync() || config.getWriteNoSync();

        assert(!(explicitDurabilityConfigured && explicitSyncConfigured));

        lastLoggedLsn = DbLsn.NULL_LSN;
        firstLoggedLsn = DbLsn.NULL_LSN;

        txnState = USABLE;

        txnBeginHook(config);

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
     * UserTxns get a new unique id for each instance.
     */
    protected long generateId(TxnManager txnManager,
                              long ignore /* mandatedId */) {
        return txnManager.getNextTxnId();
    }

    /**
     * Access to last LSN.
     */
    public long getLastLsn() {
        return lastLoggedLsn;
    }

    /**
     *
     * Returns the durability used for the commit operation. It's only
     * available after a commit operation has been initiated.
     *
     * @return the durability associated with the commit, or null if the
     * commit has not yet been initiated.
     */
    public Durability getCommitDurability() {
        return commitDurability;
    }

    /**
    *
    * Returns the durability associated the transaction at the time it's first
    * created.
    *
    * @return the durability associated with the transaction at creation.
    */
   public Durability getDefaultDurability() {
       return defaultDurability;
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
                timeout = getLockTimeout();
            }
        }

        /* Ask for the lock. */
        LockGrantType grant = lockManager.lock
            (nodeId, this, lockType, timeout, useNoWait, database);

        WriteLockInfo info = null;
        if (writeInfo != null) {
            if (grant != LockGrantType.DENIED && lockType.isWriteLock()) {
                synchronized (this) {
                    info = writeInfo.get(Long.valueOf(nodeId));
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
            envImpl.getTxnManager().notePrepare();
            if (writeInfo == null) {
                return XAResource.XA_RDONLY;
            }

            SingleItemEntry prepareEntry =
                new SingleItemEntry(LogEntryType.LOG_TXN_PREPARE,
                                    new TxnPrepare(id, xid));
            /* Flush required. */
            LogManager logManager = envImpl.getLogManager();
            logManager.logForceFlush(prepareEntry,
                                     true,  // fsyncrequired
                                     ReplicationContext.NO_REPLICATE);
        }
        return XAResource.XA_OK;
    }

    public void commit(Xid xid)
        throws DatabaseException {

        commit(TransactionConfig.SYNC);
        envImpl.getTxnManager().unRegisterXATxn(xid, true);
        return;
    }

    public void abort(Xid xid)
        throws DatabaseException {

        abort(true /* forceFlush */);
        envImpl.getTxnManager().unRegisterXATxn(xid, false);
        return;
    }

    /**
     * Call commit() with the default sync configuration property.
     */
    public long commit()
        throws DatabaseException {

        return commit(defaultDurability);
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
    public long commit(Durability durability)
        throws DatabaseException {

        /*
         * A replication exception that cannot abort the transaction since the
         * commit record has been written.
         */
        DatabaseException repNoAbortException = null;

        /* A replication exception requiring a transaction abort. */
        DatabaseException repAbortException = null;

        this.commitDurability = durability;

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
                List<WriteLockInfo> transferredWriteLockInfo = null;

                /* Transfer handle locks to their owning handles. */
                if (handleLockToHandleMap != null) {
                    Iterator<Map.Entry<Long,Set<Database>>> handleLockIter =
                        handleLockToHandleMap.entrySet().iterator();
                    while (handleLockIter.hasNext()) {
                        Map.Entry<Long,Set<Database>> entry =
                            handleLockIter.next();
                        Long nodeId = entry.getKey();
                        if (writeInfo != null) {
                            WriteLockInfo info = writeInfo.get(nodeId);
                            if (info != null) {
                                if (transferredWriteLockInfo == null) {
                                    transferredWriteLockInfo =
                                        new ArrayList<WriteLockInfo>();
                                }
                                transferredWriteLockInfo.add(info);
                            }
                        }
                        transferHandleLockToHandleSet
                            (nodeId, entry.getValue());
                    }
                }

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

                    try {
                        preLogCommitHook();
                    } catch (DatabaseException preCommitException) {
                        repAbortException = preCommitException;
                        throw preCommitException;
                    }

                    try {
                        commitLsn = logCommitEntry(durability.getLocalSync());
                    } catch (Exception e) {
                        /* Cleanup and propagate the exception. */
                        postLogAbortHook();
                        throw e;
                    }

                    try {
                        postLogCommitHook();
                    } catch (DatabaseException postCommitException) {
                        repNoAbortException = postCommitException;
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
                    Set<Long> alreadyCountedLsnSet = new HashSet<Long>();

                    /* Release all write locks, clear lock collection. */
                    Iterator<Map.Entry<Long,WriteLockInfo>> iter =
                        writeInfo.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Long,WriteLockInfo> entry = iter.next();
                        Long nodeId = entry.getKey();
                        lockManager.release(nodeId, this);
                        /* Count obsolete LSNs for released write locks. */
                        countWriteAbortLSN(entry.getValue(),
                                           alreadyCountedLsnSet);
                    }
                    writeInfo = null;

                    /* Count obsolete LSNs for transferred write locks. */
                    if (transferredWriteLockInfo != null) {
                        for (int i = 0;
                             i < transferredWriteLockInfo.size();
                             i += 1) {
                            WriteLockInfo info =
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
            if (repNoAbortException == null) {
                return commitLsn;
            }
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
                /*
                 * TODO: We need an explicit indication for an IOException in
                 * the HA release.  Replication hooks may throw
                 * DatabaseException instances that do not represent
                 * IOExceptions.
                 */
                abortInternal(durability.getLocalSync() ==
                              Durability.SyncPolicy.SYNC,
                              ((repAbortException != null) ||
                               !(t instanceof DatabaseException)));
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

            if (t == repAbortException) {
                /*
                 * Don't wrap the replication exception, since the application
                 * may need to catch and handle it; it's already a Database
                 * exception.
                 */
                throw repAbortException;
            }
            /* Now throw an exception that shows the commit problem. */
            throw new DatabaseException
                ("Failed while attempting to commit transaction " + id +
                 ", aborted instead. Original exception = " +
                 t.getMessage(), t);
        }
        assert(repNoAbortException != null);
        /* Rethrow any pending post-commit replication exceptions. */
        throw repNoAbortException;
    }

    /**
     * Creates and logs the txn commit entry, enforcing the flush/Sync behavior.
     *
     * @param flushSyncBehavior the local durability requirements
     *
     * @return the LSN denoting the commit log entry
     *
     * @throws DatabaseException
     */
    private long logCommitEntry(Durability.SyncPolicy flushSyncBehavior)
            throws DatabaseException {

        LogManager logManager = envImpl.getLogManager();
        SingleItemEntry commitEntry =
            new SingleItemEntry(LogEntryType.LOG_TXN_COMMIT,
                                new TxnCommit(id, lastLoggedLsn,
                                              0 /* masterNodeId */));

        switch (flushSyncBehavior) {

            case SYNC:
                return logManager.logForceFlush(commitEntry,
                                                true, // fsyncRequired
                                                repContext);

            case WRITE_NO_SYNC:
                return logManager.logForceFlush(commitEntry,
                                                false, // fsyncRequired
                                                repContext);
            default:
                return logManager.log(commitEntry, repContext);
        }
    }

    /**
     * Count the abortLSN as obsolete.  Do not count if a slot with a deleted
     * LN was reused (abortKnownDeleted), to avoid double counting.  And count
     * each abortLSN only once.
     */
    private void countWriteAbortLSN(WriteLockInfo info,
                                    Set<Long> alreadyCountedLsnSet)
        throws DatabaseException {

        if (info.abortLsn != DbLsn.NULL_LSN &&
            !info.abortKnownDeleted) {
            Long longLsn = Long.valueOf(info.abortLsn);
            if (!alreadyCountedLsnSet.contains(longLsn)) {
                envImpl.getLogManager().countObsoleteNode
                    (info.abortLsn, null, info.abortLogSize, info.abortDb);
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

        return abortInternal(forceFlush,
                             true);     // writeAbortRecord
    }

    private long abortInternal(boolean forceFlush,
                               boolean writeAbortRecord)
        throws DatabaseException {

        try {
            int numReadLocks;
            int numWriteLocks;
            long abortLsn;

            synchronized (this) {
                checkState(true);

                /* Log the abort. */
                abortLsn = DbLsn.NULL_LSN;
                try {
                    if (writeInfo != null) {
                        if (writeAbortRecord) {
                            SingleItemEntry abortEntry = new SingleItemEntry
                                (LogEntryType.LOG_TXN_ABORT,
                                 new TxnAbort(id, lastLoggedLsn,
                                              0 /* masterNodeId */));
                            if (forceFlush) {
                                abortLsn = envImpl.getLogManager().
                                    logForceFlush(abortEntry,
                                                  true /*fsyncRequired*/,
                                                  repContext);
                            } else {
                                abortLsn = envImpl.getLogManager().
                                    log(abortEntry, repContext);
                            }
                        }
                    }
                } finally {

                    /* Undo the changes. */
                    undo();

                    /*
                     * Release all read locks after the undo (since the undo
                     * may need to read in mapLNs).
                     */
                    numReadLocks = (readLocks == null) ? 0 : clearReadLocks();

                    /*
                     * Set database state for deletes before releasing any
                     * write locks.
                     */
                    setDeletedDatabaseState(false);

                    /* Throw away write lock collection. */
                    numWriteLocks =
                        (writeInfo == null) ? 0 : clearWriteLocks();

                    /*
                     * Let the delete related info (binreferences and dbs) get
                     * gc'ed. Don't explicitly iterate and clear -- that's far
                     * less efficient, gives GC wrong input.
                     */
                    deleteInfo = null;
                }
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
                    Iterator<Database> handleIter =
                        handleToHandleLockMap.keySet().iterator();
                    while (handleIter.hasNext()) {
                        Database handle = handleIter.next();
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
            Set<Long> alreadyUndone = new HashSet<Long>();
            TreeLocation location = new TreeLocation();
            while (undoLsn != DbLsn.NULL_LSN) {

                LNLogEntry undoEntry =
                    (LNLogEntry) logManager.getLogEntry(undoLsn);
                LN undoLN = undoEntry.getLN();
                nodeId = Long.valueOf(undoLN.getNodeId());

                /*
                 * Only process this if this is the first time we've seen this
                 * node. All log entries for a given node have the same
                 * abortLsn, so we don't need to undo it multiple times.
                 */
                if (!alreadyUndone.contains(nodeId)) {
                    alreadyUndone.add(nodeId);
                    DatabaseId dbId = undoEntry.getDbId();
                    DatabaseImpl db = undoDatabases.get(dbId);
                    undoLN.postFetchInit(db, undoLsn);
                    long abortLsn = undoEntry.getAbortLsn();
                    boolean abortKnownDeleted =
                        undoEntry.getAbortKnownDeleted();
                    RecoveryManager.undo(Level.FINER, db, location, undoLN,
                                         undoEntry.getKey(),
                                         undoEntry.getDupKey(), undoLsn,
                                         abortLsn, abortKnownDeleted,
                                         null, false);

                    /*
                     * The LN undone is counted as obsolete if it is not a
                     * deleted LN.  Deleted LNs are counted as obsolete when
                     * they are logged.
                     */
                    if (!undoLN.isDeleted()) {
                        logManager.countObsoleteNode
                            (undoLsn,
                             null,  // type
                             undoLN.getLastLoggedSize(), db);
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
        Iterator<Map.Entry<Long,WriteLockInfo>> iter =
            writeInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long,WriteLockInfo> entry = iter.next();
            Long nodeId = entry.getKey();
            lockManager.release(nodeId, this);
        }
        writeInfo = null;
        return numWriteLocks;
    }

    private int clearReadLocks()
        throws DatabaseException {

        int numReadLocks = 0;
        if (readLocks != null) {
            numReadLocks = readLocks.size();
            Iterator<Long> iter = readLocks.iterator();
            while (iter.hasNext()) {
                Long rLockNid = iter.next();
                lockManager.release(rLockNid, this);
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
                deletedDatabases = new HashSet<DatabaseCleanupInfo>();
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
            Iterator<DatabaseCleanupInfo> iter = deletedDatabases.iterator();
            while (iter.hasNext()) {
                DatabaseCleanupInfo info = iter.next();
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
     * because it calls finishDeleteProcessing, which gets the TxnManager's
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
                    /* releaseDb will be called by finishDeleteProcessing. */
                    info.dbImpl.finishDeleteProcessing();
                } else {
                    envImpl.getDbTree().releaseDb(info.dbImpl);
                }
            }
            deletedDatabases = null;
        }
    }

    /**
     * Add lock to the appropriate queue.
     */
    protected void addLock(Long nodeId,
                           LockType type,
                           LockGrantType grantStatus)
        throws DatabaseException {

        synchronized (this) {
            int delta = 0;
            if (type.isWriteLock()) {
                if (writeInfo == null) {
                    writeInfo = new HashMap<Long,WriteLockInfo>();
                    undoDatabases = new HashMap<DatabaseId,DatabaseImpl>();
                    delta += MemoryBudget.TWOHASHMAPS_OVERHEAD;
                }

                writeInfo.put(nodeId, new WriteLockInfo());
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
            readLocks = new HashSet<Long>();
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
                readLocks.remove(nodeId)) {
                updateMemoryUsage(0 - READ_LOCK_OVERHEAD);
            } else if ((writeInfo != null) &&
                       (writeInfo.remove(nodeId) != null)) {
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
                (writeInfo.remove(nodeId) != null)) {
                found = true;
                updateMemoryUsage(0 - WRITE_LOCK_OVERHEAD);
            }

            assert found : "Couldn't find lock for Node " + nodeId +
                " in writeInfo Map.";
            addReadLock(nodeId);
        }
    }

    private void updateMemoryUsage(int delta) {
        inMemorySize += delta;
        accumulatedDelta += delta;
        if (accumulatedDelta > ACCUMULATED_LIMIT ||
            accumulatedDelta < -ACCUMULATED_LIMIT) {
            envImpl.getMemoryBudget().updateTxnMemoryUsage(accumulatedDelta);
            accumulatedDelta = 0;
        }
    }

    /**
     * Returns the amount of memory currently budgeted for this transaction.
     */
    int getBudgetedMemorySize() {
        return inMemorySize - accumulatedDelta;
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
                WriteLockInfo info = writeInfo.get(nodeId);
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
                info = writeInfo.get(nodeId);
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
                info = writeInfo.get(nodeId);
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
     * Determines whether this is an auto transaction.
     */
    public boolean isAutoTxn() {
        return isAutoCommit;
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
     * @hidden
     *
     * Returns true if the sync api was used for configuration
     */
    public boolean getExplicitSyncConfigured() {
        return explicitSyncConfigured;
    }

    /**
     * @hidden
     * Returns true if the durability api was used for configuration.
     */
    public boolean getExplicitDurabilityConfigured() {
        return explicitDurabilityConfigured;
    }

    /**
     * This is a transactional locker.
     */
    public Txn getTxnLocker() {
        return this;
    }

    /**
     * Returns 'this', since this locker holds no non-transactional locks.
     * Since this is returned, sharing of locks is obviously supported.
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
    public void nonTxnOperationEnd()
        throws DatabaseException {
    }

    /*
     * @see com.sleepycat.je.txn.Locker#operationEnd(boolean)
     */
    public void operationEnd(boolean operationOK)
        throws DatabaseException {

        if (!isAutoCommit) {
            /* Created transactions do nothing at the end of the operation. */
            return;
        }

        if (operationOK) {
            commit();
        } else {
            abort(false);    // no sync required
        }
    }

    /*
     * @see com.sleepycat.je.txn.Locker#setHandleLockOwner
     * (boolean, com.sleepycat.je.Database, boolean)
     */
    public void setHandleLockOwner(boolean operationOK,
                                   Database dbHandle,
                                   boolean dbIsClosing)
        throws DatabaseException {

        if (isAutoCommit) {
            /* Transfer locks on an auto commit */
            if (operationOK) {
                if (!dbIsClosing) {
                    transferHandleLockToHandle(dbHandle);
                }
                unregisterHandle(dbHandle);
            }
            /* Done if auto commit */
            return;
        }

        /* Created transactions don't transfer locks until commit. */
        if (dbIsClosing) {

            /*
             * If the Database handle is closing, take it out of the both the
             * handle lock map and the handle map. We don't need to do any
             * transfers at commit time, and we don't need to do any
             * invalidations at abort time.
             */
            Long handleLockId = handleToHandleLockMap.get(dbHandle);
            if (handleLockId != null) {
                Set<Database> dbHandleSet =
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
    @Override
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
            stats.accumulateNTotalLocks(nReadLocks + nWriteLocks);
        }

        return stats;
    }

    /**
     * Set the state of a transaction to ONLY_ABORTABLE.
     */
    @Override
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
            throw new IllegalStateException
                ("Transaction " + id + " must be aborted.");
        }

        if (ok ||
            (calledByAbort && onlyAbortable)) {
            return;
        }

        /*
         * It's ok for FindBugs to whine about id not being synchronized.
         */
        throw new IllegalStateException
            ("Transaction " + id + " has been closed.");
    }

    /**
     * Different subclasses find a repContext at different times, depending on
     * when they have the context to know whether a transaction should be
     * replicated. Auto Txns set this at construction time, Txns set this when
     * the transaction is configured, ReplicatedTxns set it when the txn commit
     * or abort arrives.
     */
    public void setRepContext(ReplicationContext repContext) {
        this.repContext = repContext;
    }

    /**
     */
    protected void close(boolean isCommit)
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
         * hierarchy conflict.
         */
        envImpl.getTxnManager().unRegisterTxn(this, isCommit);

        /* Close this Locker. */
        close();
    }

    /*
     * Log support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.getPackedLongLogSize(id) +
            LogUtils.getPackedLongLogSize(lastLoggedLsn);
    }

    /**
     * @see Loggable#writeToLog
     */
    /*
     * It's ok for FindBugs to whine about id not being synchronized.
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writePackedLong(logBuffer, id);
        LogUtils.writePackedLong(logBuffer, lastLoggedLsn);
    }

    /**
     * @see Loggable#readFromLog
     *
     * It's ok for FindBugs to whine about id not being synchronized.
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryVersion) {
        id = LogUtils.readLong(logBuffer, (entryVersion < 6));
        lastLoggedLsn = LogUtils.readLong(logBuffer, (entryVersion < 6));
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<txn id=\"");
        sb.append(getId());
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
     * @see Loggable#logicalEquals
     */
    public boolean logicalEquals(Loggable other) {

        if (!(other instanceof Txn)) {
            return false;
        }

        return id == ((Txn) other).id;
    }

    /**
     * Transfer a single handle lock to the set of corresponding handles at
     * commit time.
     */
    private void transferHandleLockToHandleSet(Long handleLockId,
                                               Set<Database> dbHandleSet)
        throws DatabaseException {

        /* Create a set of destination transactions */
        int numHandles = dbHandleSet.size();
        Database[] dbHandles = new Database[numHandles];
        dbHandles = dbHandleSet.toArray(dbHandles);
        Locker[] destTxns = new Locker[numHandles];
        for (int i = 0; i < numHandles; i++) {
            destTxns[i] = BasicLocker.createBasicLocker(envImpl);
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

    /**
     * Store information about a DatabaseImpl that will have to be
     * purged at transaction commit or abort. This handles cleanup after
     * operations like Environment.truncateDatabase,
     * Environment.removeDatabase. Cleanup like this is done outside the
     * usual transaction commit or node undo processing, because
     * the mapping tree is always auto Txn'ed to avoid deadlock and is
     * essentially  non-transactional.
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

    /* Transaction hooks used for replication support. */

    /**
     * A replicated environment introduces some new considerations when entering
     * a transaction scope via an Environment.transactionBegin() operation.
     *
     * On a Replica, the transactionBegin() operation must wait until the
     * Replica has synched up to where it satisfies the ConsistencyPolicy that
     * is in effect.
     *
     * On a Master, the transactionBegin() must wait until the Feeder has
     * sufficient connections to ensure that it can satisfy the
     * ReplicaAckPolicy, since if it does not, it will fail at commit() and the
     * work done in the transaction will need to be undone.
     *
     * This hook provides the mechanism for implementing the above support for
     * replicated transactions. It ignores all non-replicated transactions.
     *
     * The hook throws ReplicaStateException, if a Master switches to a
     * Replica state while waiting for its Replicas connections. Changes from a
     * Replica to a Master are handled transparently to the application.
     * Exceptions manifest themselves as DatabaseException at the interface to
     * minimize use of Replication based exceptions in core JE.
     *
     * @param config the transaction config that applies to the txn
     *
     * @throw DatabaseException if there is a failure
     */
    protected void txnBeginHook(TransactionConfig config)
        throws DatabaseException {
        /* Overridden by Txn subclasses when appropriate */
    }

    /**
     * This hook is invoked before the commit of a transaction that made changes
     * to a replicated environment. It's invoked for transactions
     * executed on the master or replica, but is only relevant to transactions
     * being done on the master. When invoked for a transaction on a replica the
     * implementation just returns.
     *
     * The hook is invoked at a very specific point in the normal commit
     * sequence: immediately before the commit log entry is written to the log.
     * It represents the last chance to abort the transaction and provides an
     * opportunity to make some final checks before allowing the commit can go
     * ahead. Note that it should be possible to abort the transaction at the
     * time the hook is invoked.
     *
     * After invocation of the "pre" hook one of the "post" hooks:
     * postLogCommitHook or postLogAbortHook must always be invoked.
     *
     * Exceptions thrown by this hook result in the transaction being aborted
     * and the exception being propagated back to the application.
     *
     * @param txn the transaction being committed
     *
     * @throws DatabaseException if there was a problem and that the transaction
     * should be aborted.
     */
    protected void preLogCommitHook()
        throws DatabaseException {
        /* Overridden by Txn subclasses when appropriate */
    }

    /**
     * This hook is invoked after the commit record has been written to the log,
     * but before write locks have been released, so that other application
     * cannot see the changes made by the transaction. At this point the
     * transaction has been committed by the Master.
     *
     * Exceptions thrown by this hook result in the transaction being completed
     * on the Master, that is, locks are released, etc. and the exception is
     * propagated back to the application.
     *
     * @throws DatabaseException to indicate that there was a replication
     * related problem that needs to be communicated back to the application.
     */
    protected void postLogCommitHook() throws DatabaseException {
        /* Overridden by Txn subclasses when appropriate */
    }


    /**
     * Invoked if the transaction associated with the preLogCommitHook was
     * subsequently aborted, for example due to a lack of disk space. This
     * method is responsible for any cleanup that may need to be done as a
     * result of the abort.
     *
     * Note that only one of the "post" hooks (commit or abort) is invoked
     * following the invocation of the "pre" hook.
     */
    protected void postLogAbortHook() {
        /* Overridden by Txn subclasses when appropriate */
    }
}
