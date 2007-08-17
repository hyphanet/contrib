/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LN.java,v 1.125.2.5 2007/07/02 19:54:52 mark Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.entry.DeletedDupLNLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.txn.WriteLockInfo;
import com.sleepycat.je.utilint.DbLsn;

/**
 * An LN represents a Leaf Node in the JE tree.
 */
public class LN extends Node implements Loggable {
    private static final String BEGIN_TAG = "<ln>";
    private static final String END_TAG = "</ln>";

    private byte[] data;

    /* 
     * States: bit fields
     * 
     * -Dirty means that the in-memory version is not present on disk.
     * -The last logged bits store the total size of the last logged entry.
     */
    private static final int DIRTY_BIT = 0x80000000;
    private static final int CLEAR_DIRTY_BIT = ~DIRTY_BIT;
    private static final int LAST_LOGGED_SIZE_MASK = 0x7FFFFFFF;
    private static final int CLEAR_LAST_LOGGED_SIZE = ~LAST_LOGGED_SIZE_MASK;
    private int state; // not persistent

    /**
     * Create an empty LN, to be filled in from the log.
     */
    public LN() {
        super(false);
        this.data = null;
    }
    
    /**
     * Create a new LN from a byte array.
     */
    public LN(byte[] data) {
        super(true);
        if (data == null) {
            this.data = null;
        } else {
            init(data, 0, data.length);
        }
        setDirty();
    }
    
    /**
     * Create a new LN from a DatabaseEntry.
     */
    public LN(DatabaseEntry dbt) {
        super(true);
        byte[] data = dbt.getData();
        if (data == null) {
            this.data = null;
        } else if (dbt.getPartial()) {
            init(data,
                 dbt.getOffset(),
                 dbt.getPartialOffset() + dbt.getSize(),
                 dbt.getPartialOffset(),
                 dbt.getSize());
        } else {
            init(data, dbt.getOffset(), dbt.getSize());
        }
        setDirty();
    }

    private void init(byte[] data, int off, int len, int doff, int dlen) {
	if (len == 0) {
	    this.data = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
	} else {
	    this.data = new byte[len];
	    System.arraycopy(data, off, this.data, doff, dlen);
	}
    }

    private void init(byte[] data, int off, int len) {
        init(data, off, len, 0, len);
    }

    public byte[] getData() {
        return data;
    }
    
    public byte[] copyData() {
        int len = data.length;
        byte[] ret = new byte[len];
        System.arraycopy(data, 0, ret, 0, len);
        return ret;
    }

    public boolean isDeleted() {
        return (data == null);
    }

    void makeDeleted() {
        data = null;
    }

    public boolean isDirty() {
        return ((state & DIRTY_BIT) != 0);
    }

    void setDirty() {
        state |= DIRTY_BIT;
    }

    private void clearDirty() {
        state &= CLEAR_DIRTY_BIT;
    }

    /* 
     * If you get to an LN, this subtree isn't valid for delete. True, the LN
     * may have been deleted, but you can't be sure without taking a lock, and
     * the validate -subtree-for-delete process assumes that bin compressing
     * has happened and there are no committed, deleted LNS hanging off the
     * BIN.
     */
    boolean isValidForDelete() {
        return false;
    }

    /**
     * Returns true by default, but is overridden by MapLN to prevent eviction
     * of open databases.  This method is meant to be a fast but not guaranteed
     * check and is used during selection of BINs for LN stripping.  [#13415]
     */
    boolean isEvictableInexact() {
        return true;
    }

    /**
     * Returns true by default, but is overridden by MapLN to prevent eviction
     * of open databases.  This method is meant to be a guaranteed check and is
     * used after a BIN has been selected for LN stripping but before actually
     * stripping an LN. [#13415]
     */
    boolean isEvictable()
        throws DatabaseException {

        return true;
    }

    /**
     * A LN can never be a child in the search chain.
     */
    protected boolean isSoughtNode(long nid, boolean updateGeneration) {
        return false;
    }

    /**
     * A LN can never be the ancestor of another node.
     */
    protected boolean canBeAncestor(boolean targetContainsDuplicates) {
        return false;
    }

    /**
     * Delete this LN's data and log the new version. 
     */
    public long delete(DatabaseImpl database,
		       byte[] lnKey,
		       byte[] dupKey,
		       long oldLsn,
		       Locker locker)
        throws DatabaseException {

        int oldSize = getLastLoggedSize();
        makeDeleted();
        setDirty();

        /* Log if necessary */
        EnvironmentImpl env = database.getDbEnvironment();
        long newLsn = DbLsn.NULL_LSN;
        if (dupKey != null) {

            /* 
             * If this is a deferred write database, and the LN has
             * never been logged, we don't need to log the delete either,
             * since we are currently running in non-txnal mode. This
             * will have to be adapted when we support txnal mode.
             */
            if (database.isDeferredWrite() &&
                oldLsn == DbLsn.NULL_LSN) {
                clearDirty();
            } else {

                /*
                 * Log as a deleted duplicate LN by passing dupKey.  Note that
                 * we log a deleted duplicate LN even in Deferred Write mode,
                 * because the data (dupKey) is set to null when it is deleted,
                 * so logging it later is not possible.
                 */
                newLsn = log(env, database.getId(), lnKey, dupKey, oldLsn,
                             oldSize, locker,
                             false,  // isProvisional
                             false); // backgroundIO
            }
        } else {

            /*
             * Non duplicate LN, just log the normal way.
             */
            newLsn = optionalLog(env, database, lnKey, oldLsn, oldSize, locker);
        }
        return newLsn;
    }

    /**
     * Modify the LN's data and log the new version.
     */
    public long modify(byte[] newData,
		       DatabaseImpl database,
		       byte[] lnKey,
		       long oldLsn,
		       Locker locker)
        throws DatabaseException {

        int oldSize = getLastLoggedSize();
        data = newData;
        setDirty();

        /* Log the new LN. */
        EnvironmentImpl env = database.getDbEnvironment();
        long newLsn =
            optionalLog(env, database, lnKey, oldLsn, oldSize, locker);
        return newLsn;
    }

    /**
     * Add yourself to the in memory list if you're a type of node that should
     * belong.
     */
    void rebuildINList(INList inList) {
        // don't add, LNs don't belong on the list.
    }

    /**
     * No need to do anything, stop the search.
     */
    void accountForSubtreeRemoval(INList inList,
                                  UtilizationTracker tracker) {
        /* Don't remove, LNs not on this list. */
    }

    /**
     * Compute the approximate size of this node in memory for evictor
     * invocation purposes.
     */
    public long getMemorySizeIncludedByParent() {
        int size = MemoryBudget.LN_OVERHEAD;
        if (data != null) {
            size += MemoryBudget.byteArraySize(data.length);
        }
        return size;
    }

    /*
     * Dumping
     */

    public String beginTag() {
        return BEGIN_TAG;
    }

    public String endTag() {
        return END_TAG;
    }

    public String dumpString(int nSpaces, boolean dumpTags) {
        StringBuffer self = new StringBuffer();
        if (dumpTags) {
	    self.append(TreeUtils.indent(nSpaces));
            self.append(beginTag());
            self.append('\n');
        }

        self.append(super.dumpString(nSpaces + 2, true));
        self.append('\n');
        if (data != null) {
            self.append(TreeUtils.indent(nSpaces+2));
            self.append("<data>");
            self.append(TreeUtils.dumpByteArray(data));
            self.append("</data>");
            self.append('\n');
        }
        if (dumpTags) {
            self.append(TreeUtils.indent(nSpaces));
            self.append(endTag());
        }
        return self.toString();
    }

    /*
     * Logging Support
     */

    /**
     * Log this LN and clear the dirty flag. Whether it's logged as a
     * transactional entry or not depends on the type of locker.
     * @param env the environment.
     * @param dbId database id of this node. (Not stored in LN)
     * @param key key of this node. (Not stored in LN)
     * @param oldLsn is the LSN of the previous version or null.
     * @param oldSize is the size of the previous version or zero.
     * @param locker owning locker.
     */
    public long log(EnvironmentImpl env,
		    DatabaseId dbId,
		    byte[] key,
		    long oldLsn,
                    int oldSize,
		    Locker locker,
                    boolean backgroundIO)
        throws DatabaseException {

        return log(env, dbId, key,
                   null,   // delDupKey
                   oldLsn, oldSize, locker, backgroundIO,
                   false); // provisional
    }

    /**
     * Log this LN if it's not part of a deferred-write db.  Whether it's
     * logged as a transactional entry or not depends on the type of locker.
     * @param env the environment.
     * @param dbId database id of this node. (Not stored in LN)
     * @param key key of this node. (Not stored in LN)
     * @param oldLsn is the LSN of the previous version or NULL_LSN.
     * @param oldSize is the size of the previous version or zero.
     * @param locker owning locker.
     */
    public long optionalLog(EnvironmentImpl env,
                            DatabaseImpl databaseImpl,
                            byte[] key,
                            long oldLsn,
                            int oldSize,
                            Locker locker)
        throws DatabaseException {

        if (databaseImpl.isDeferredWrite()) {
            return DbLsn.NULL_LSN;
        } else {
            return log
                (env, databaseImpl.getId(), key,
                 null,   // delDupKey
                 oldLsn, oldSize, locker,
                 false,  // backgroundIO
                 false); // provisional
        }
    }

    /**
     * Log a provisional, non-txnal version of an LN.
     * @param env the environment.
     * @param dbId database id of this node. (Not stored in LN)
     * @param key key of this node. (Not stored in LN)
     * @param oldLsn is the LSN of the previous version or NULL_LSN.
     * @param oldSize is the size of the previous version or zero.
     */
    public long optionalLogProvisional(EnvironmentImpl env,
                                       DatabaseImpl databaseImpl,
                                       byte[] key, 
                                       long oldLsn,
                                       int oldSize)
        throws DatabaseException {

        if (databaseImpl.isDeferredWrite()) {
            return DbLsn.NULL_LSN;
        } else {
            return log
                (env, databaseImpl.getId(), key,
                 null,   // delDupKey
                 oldLsn, oldSize,
                 null,  // locker
                 false, // backgroundIO
                 true); // provisional
        }
    }

    /**
     * Log this LN. Clear dirty bit. Whether it's logged as a transactional
     * entry or not depends on the type of locker.
     * @param env the environment.
     * @param dbId database id of this node. (Not stored in LN)
     * @param key key of this node. (Not stored in LN)
     * @param delDupKey if non-null, the dupKey for deleting the LN.
     * @param oldLsn is the LSN of the previous version or NULL_LSN.
     * @param oldSize is the size of the previous version or zero.
     * @param locker owning locker.
     */
    long log(EnvironmentImpl env,
             DatabaseId dbId,
             byte[] key,
             byte[] delDupKey,
             long oldLsn,
             int oldSize,
             Locker locker,
             boolean backgroundIO,
             boolean isProvisional)
        throws DatabaseException {

        boolean isDelDup = (delDupKey != null);
        LogEntryType entryType;
        long logAbortLsn;
	boolean logAbortKnownDeleted;
        Txn logTxn;
        if (locker != null && locker.isTransactional()) {
            entryType = isDelDup ? LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL
                                 : getTransactionalLogType();
	    WriteLockInfo info = locker.getWriteLockInfo(getNodeId());
	    logAbortLsn = info.getAbortLsn();
	    logAbortKnownDeleted = info.getAbortKnownDeleted();
            logTxn = locker.getTxnLocker();
            assert logTxn != null;
            if (oldLsn == logAbortLsn) {
                info.setAbortLogSize(oldSize);
            }
        } else {
            entryType = isDelDup ? LogEntryType.LOG_DEL_DUPLN
                                 : getLogType();
            logAbortLsn = DbLsn.NULL_LSN;
	    logAbortKnownDeleted = false;
            logTxn = null;
        }

        /* Don't count abortLsn as obsolete, this is done during commit. */
        if (oldLsn == logAbortLsn) {
            oldLsn = DbLsn.NULL_LSN;
        }

        LNLogEntry logEntry;
        if (isDelDup) {

            /*
             * Deleted Duplicate LNs are logged with two keys -- the one
             * that identifies the main tree (the dup key) and the one that
             * places them in the duplicate tree (really the data) since we
             * can't recreate the latter because the data field has been
             * nulled. Note that the dupKey is passed to the log manager
             * FIRST, because the dup key is the one that navigates us in
             * the main tree. The "key" is the one that navigates us in the
             * duplicate tree.
             */
            logEntry =
                new DeletedDupLNLogEntry(entryType,
                                         this,
                                         dbId,
                                         delDupKey,
                                         key,
                                         logAbortLsn,
                                         logAbortKnownDeleted,
                                         logTxn);
        } else {
            /* Not a deleted duplicate LN -- use a regular LNLogEntry. */
            logEntry = new LNLogEntry(entryType, 
                                      this,
                                      dbId,
                                      key,
                                      logAbortLsn,
                                      logAbortKnownDeleted,
                                      logTxn);
        }

        LogManager logManager = env.getLogManager();
        long lsn = logManager.log(logEntry, isProvisional,
                                  backgroundIO, oldLsn, oldSize);
        clearDirty();
        return lsn;
    }

    /**
     * Log type for transactional entries
     */
    protected LogEntryType getTransactionalLogType() {
        return LogEntryType.LOG_LN_TRANSACTIONAL;
    }

    /**
     * @see Node#getLogType()
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_LN;
    }

    /**
     * Returns the total last logged log size, including the LNLogEntry
     * overhead of this LN when it was last logged and the log entry
     * header.  Used for computing obsolete size when an LNLogEntry is not in
     * hand.
     */
    public int getLastLoggedSize() {
        return state & LAST_LOGGED_SIZE_MASK;
    }

    /**
     * Saves the last logged size.
     */
    public void setLastLoggedSize(int size) {
        /* Clear the old size and OR in the new size. */
        state = (state & CLEAR_LAST_LOGGED_SIZE) | size;
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        int size = super.getLogSize();

        // data
        size += LogUtils.getBooleanLogSize(); // isDeleted flag
        if (!isDeleted()) {
            size += LogUtils.getByteArrayLogSize(data);
        }

        return size;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        /* Ask ancestors to write to log. */
        super.writeToLog(logBuffer); 

        /* data: isData null flag, then length, then data. */
        boolean dataExists = !isDeleted();
        LogUtils.writeBoolean(logBuffer, dataExists);
        if (dataExists) {
            LogUtils.writeByteArray(logBuffer, data);
        }
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        super.readFromLog(itemBuffer, entryTypeVersion);

        boolean dataExists = LogUtils.readBoolean(itemBuffer);
        if (dataExists) {
            data = LogUtils.readByteArray(itemBuffer);
        }
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append(beginTag());
        super.dumpLog(sb, verbose);

        if (data != null) {
            sb.append("<data>");
            sb.append(TreeUtils.dumpByteArray(data));
            sb.append("</data>");
        }
        
        dumpLogAdditional(sb, verbose);

        sb.append(endTag());
    }

    /*
     * Allows subclasses to add additional fields before the end tag.
     */
    protected void dumpLogAdditional(StringBuffer sb, boolean verbose) {
    }
}
