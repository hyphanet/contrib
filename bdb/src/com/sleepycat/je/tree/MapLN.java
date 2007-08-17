/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MapLN.java,v 1.69.2.3 2007/07/02 19:54:52 mark Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockType;

/**
 * A MapLN represents a Leaf Node in the JE DatabaseImpl Naming Tree. 
 */
public final class MapLN extends LN {

    private static final String BEGIN_TAG = "<mapLN>";
    private static final String END_TAG = "</mapLN>";

    private DatabaseImpl databaseImpl;
    private boolean deleted;

    /**
     * Create a new MapLn to hold a new databaseImpl. In the ideal world, we'd
     * have a base LN class so that this MapLN doesn't have a superfluous data
     * field, but we want to optimize the LN class for size and speed right
     * now.
     */
    public MapLN(DatabaseImpl db) {
        super(new byte[0]);
        databaseImpl = db;
        deleted = false;
    }

    /**
     * Create an empty MapLN, to be filled in from the log.
     */
    public MapLN() 
        throws DatabaseException {

        super();
        databaseImpl = new DatabaseImpl();
    }

    public boolean isDeleted() {
        return deleted;
    }

    void makeDeleted() {
        deleted = true;

        /* Release all references to nodes held by this database. */
        databaseImpl.getTree().setRoot(null, true);
    }

    public DatabaseImpl getDatabase() {
        return databaseImpl;
    }

    /**
     * Does a fast check without acquiring the MapLN write-lock.  This is
     * important because the overhead of requesting the lock is significant and
     * unnecessary if this DB is open or the root IN is resident.  When there
     * are lots of databases open, this method will be called often during
     * selection of BINs for eviction.  [#13415]
     * @Override
     */
    boolean isEvictableInexact() {
        return !databaseImpl.isInUse() &&
               !databaseImpl.getTree().isRootResident();
    }

    /**
     * Does a guaranteed check by acquiring the write-lock and then calling
     * isEvictableInexact.  [#13415]
     * @Override
     */
    boolean isEvictable()
        throws DatabaseException {
        
        boolean evictable = false;

        /* To prevent DB open, get a write-lock on the MapLN. */
        BasicLocker locker = new BasicLocker(databaseImpl.getDbEnvironment());
        try {
            LockResult lockResult = locker.nonBlockingLock
                (getNodeId(), LockType.WRITE, databaseImpl);

            /*
             * The isEvictableInexact result is guaranteed to hold true during
             * LN stripping if it is still true after acquiring the write-lock.
             */
            if (lockResult.getLockGrant() != LockGrantType.DENIED &&
                isEvictableInexact()) {

                /* 
                 * While holding both a write-lock on the MapLN, we are
                 * guaranteed that the DB is not currently open.  It cannot be
                 * subsequently opened until the BIN latch is released, since
                 * the BIN latch will block DbTree.getDb (called during DB
                 * open).  We will evict the LN before releasing the BIN latch.
                 * After releasing the BIN latch, if a DB open is waiting on
                 * getDb, then it will proceed, fetch the evicted LN and open
                 * the DB normally.
                 */
                evictable = true;
            }
        } finally {
            /* Release the write-lock.  The BIN latch is still held. */
            locker.operationEnd();
        }

        return evictable;
    }

    /**
     * Initialize a node that has been faulted in from the log.
     */
    public void postFetchInit(DatabaseImpl db, long sourceLsn) 
        throws DatabaseException {

        databaseImpl.setEnvironmentImpl(db.getDbEnvironment());
    }

    /**
     * Compute the approximate size of this node in memory for evictor
     * invocation purposes.
     */
    public long getMemorySizeIncludedByParent() {
        return MemoryBudget.MAPLN_OVERHEAD +
               databaseImpl.getAdditionalMemorySize();
    }

    /*
     * Dumping
     */

    public String toString() {
        return dumpString(0, true);
    }
    
    public String beginTag() {
        return BEGIN_TAG;
    }

    public String endTag() {
        return END_TAG;
    }

    public String dumpString(int nSpaces, boolean dumpTags) {
        StringBuffer sb = new StringBuffer();
        sb.append(super.dumpString(nSpaces, dumpTags));
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces));
        sb.append("<deleted val=\"").append(Boolean.toString(deleted));
        sb.append("\">");
        sb.append('\n');
        sb.append(databaseImpl.dumpString(nSpaces));
        return sb.toString();
    }

    /*
     * Logging
     */

    /**
     * Log type for transactional entries.
     */
    protected LogEntryType getTransactionalLogType() {
        return LogEntryType.LOG_MAPLN_TRANSACTIONAL;
    }

    /**
     * @see Node#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_MAPLN;
    }

    /**
     * @see LN#getLogSize
     */
    public int getLogSize() {
        return super.getLogSize() +
               databaseImpl.getLogSize() +
               LogUtils.getBooleanLogSize();
    }

    /**
     * @see LN#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        /* Ask ancestors to write to log. */
        super.writeToLog(logBuffer); 
        databaseImpl.writeToLog(logBuffer);
        LogUtils.writeBoolean(logBuffer, deleted);
    }

    /**
     * @see LN#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        super.readFromLog(itemBuffer, entryTypeVersion);
        databaseImpl.readFromLog(itemBuffer, entryTypeVersion);
        deleted = LogUtils.readBoolean(itemBuffer);
    }

    /**
     * Dump additional fields. Done this way so the additional info can be
     * within the XML tags defining the dumped log entry.
     */
    protected void dumpLogAdditional(StringBuffer sb, boolean verbose) {
        databaseImpl.dumpLog(sb, true);
    }
}
