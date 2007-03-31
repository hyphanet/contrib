/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LNLogEntry.java,v 1.39.2.2 2007/03/08 22:32:56 mark Exp $
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.DbLsn;

/**
 * LNLogEntry embodies all LN transactional log entries.
 * On disk, an LN log entry contains:
 * <pre>
 *   ln
 *   databaseid
 *   key            
 *   abortLsn          -- if transactional
 *   abortKnownDeleted -- if transactional
 *   txn               -- if transactional
 * </pre>
 */
public class LNLogEntry extends BaseEntry implements LogEntry, NodeLogEntry {
    private static final byte ABORT_KNOWN_DELETED_MASK = (byte) 1;

    /* 
     * Persistent fields in an LN entry
     */
    private LN ln;
    private DatabaseId dbId;
    private byte[] key;
    private long abortLsn = DbLsn.NULL_LSN;
    private boolean abortKnownDeleted;
    private Txn txn;     // conditional

    /* 
     * Transient fields used by the entry.
     * 
     * Save the node id when we read the log entry from disk. Do so explicitly
     * instead of merely returning ln.getNodeId(), because we don't always
     * instantiate the LN.
     */
    private long nodeId;   

    /* Constructor to read an entry. */
    public LNLogEntry(Class LNClass) {
        super(LNClass);
    }

    /* Constructor to write an entry. */
    public LNLogEntry(LogEntryType entryType,
                      LN ln,
                      DatabaseId dbId,
                      byte[] key,
                      long abortLsn,
                      boolean abortKnownDeleted,
                      Txn txn) {
        setLogType(entryType);
        this.ln = ln;
        this.dbId = dbId;
        this.key = key;
        this.abortLsn = abortLsn;
        this.abortKnownDeleted = abortKnownDeleted;
        this.txn = txn;
        this.nodeId = ln.getNodeId();

        /* A txn should only be provided for transactional entry types */
        assert(entryType.isTransactional() == (txn!=null));
    }

    /**
     * @see LogEntry#readEntry
     */
    public void readEntry(LogEntryHeader header,
                          ByteBuffer entryBuffer,
                          boolean readFullItem)
        throws DatabaseException {

        try {
            if (readFullItem) {

                /* Read LN and get node ID. */
                ln = (LN) logClass.newInstance();
                ln.readFromLog(entryBuffer, header.getVersion());
                nodeId = ln.getNodeId();

                /* DatabaseImpl Id */
                dbId = new DatabaseId();
                dbId.readFromLog(entryBuffer, header.getVersion());

                /* Key */
                key = LogUtils.readByteArray(entryBuffer);

                if (entryType.isTransactional()) {

                    /*
                     * AbortLsn. If it was a marker LSN that was used to fill
                     * in a create, mark it null.
                     */
                    abortLsn = LogUtils.readLong(entryBuffer);
                    if (DbLsn.getFileNumber(abortLsn) ==
                        DbLsn.getFileNumber(DbLsn.NULL_LSN)) {
                        abortLsn = DbLsn.NULL_LSN;
                    }

                    abortKnownDeleted =
                        ((entryBuffer.get() & ABORT_KNOWN_DELETED_MASK) != 0) ?
                        true : false;

                    /* Locker */
                    txn = new Txn();
                    txn.readFromLog(entryBuffer, header.getVersion());

                }
            } else {

                /*
                 * Read node ID and then set buffer position to end. This takes
                 * advantage of the fact that the node id is in a known spot,
                 * at the beginning of the ln.  We currently do not support
                 * getting the db and txn ID in this mode, and we may want to
                 * change the log format to do that efficiently.
                 */
                int currentPosition = entryBuffer.position();
                int endPosition = currentPosition + header.getItemSize();
                nodeId = LogUtils.readLong(entryBuffer);
                entryBuffer.position(endPosition);
                ln = null;
            }
        } catch (IllegalAccessException e) {
            throw new DatabaseException(e);
        } catch (InstantiationException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * @see LogEntry#dumpEntry
     */
    public StringBuffer dumpEntry(StringBuffer sb, boolean verbose) {
        ln.dumpLog(sb, verbose);
        dbId.dumpLog(sb, verbose);
        sb.append(Key.dumpString(key, 0));
        if (entryType.isTransactional()) {
            if (abortLsn != DbLsn.NULL_LSN) {
                sb.append(DbLsn.toString(abortLsn));
            }
            sb.append("<knownDeleted val=\"");
            sb.append(abortKnownDeleted ? "true" : "false");
            sb.append("\"/>");
            txn.dumpLog(sb, verbose);
        }
        return sb;
    }

    /**
     * @see LogEntry#getMainItem
     */
    public Object getMainItem() {
        return ln;
    }

    /**
     * @see LogEntry#clone
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * @see LogEntry#getTransactionId
     */
    public long getTransactionId() {
        if (entryType.isTransactional()) {
            return txn.getId();
        } else {
            return 0;
        }
    }

    /**
     * @see NodeLogEntry#getNodeId
     */
    public long getNodeId() {
        return nodeId;
    }

    /*
     * Writing support
     */

    /**
     */
    public int getSize() {
        int size = ln.getLogSize() +
            dbId.getLogSize() +
            LogUtils.getByteArrayLogSize(key);
        if (entryType.isTransactional()) {
            size += LogUtils.getLongLogSize();
            size++;   // abortKnownDeleted
            size += txn.getLogSize();
        }
        return size;
    }

    public void setLastLoggedSize(int size) {
        ln.setLastLoggedSize(size);
    }

    /**
     * @see LogEntry#writeEntry
     */
    public void writeEntry(LogEntryHeader header, ByteBuffer destBuffer) {
        ln.writeToLog(destBuffer);
        dbId.writeToLog(destBuffer);
        LogUtils.writeByteArray(destBuffer, key);

        if (entryType.isTransactional()) {
            LogUtils.writeLong(destBuffer, abortLsn);
            byte aKD = 0;
            if (abortKnownDeleted) {
                aKD |= ABORT_KNOWN_DELETED_MASK;
            }
            destBuffer.put(aKD);
            txn.writeToLog(destBuffer);
        }
    }

    /**
     * Returns true for a deleted LN to count it immediately as obsolete.
     * @see LogEntry#countAsObsoleteWhenLogged
     */
    public boolean countAsObsoleteWhenLogged() {
        return ln.isDeleted();
    }

    /**
     * For LN entries, we need to record the latest LSN for that node with the
     * owning transaction, within the protection of the log latch. This is a
     * callback for the log manager to do that recording.
     *
     * @see LogEntry#postLogWork
     */
    public void postLogWork(long justLoggedLsn)
        throws DatabaseException {

        if (entryType.isTransactional()) {
            txn.addLogInfo(justLoggedLsn);
        }
    }

    /*
     * Accessors
     */
    public LN getLN() {
        return ln;
    }

    public DatabaseId getDbId() {
        return dbId;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getDupKey() {
        if (ln.isDeleted()) {
            return null;
        } else {
            return ln.getData();
        }
    }

    public long getAbortLsn() {
        return abortLsn;
    }

    public boolean getAbortKnownDeleted() {
        return abortKnownDeleted;
    }

    public Long getTxnId() {
        if (entryType.isTransactional()) {
            return new Long(txn.getId());
        } else {
            return null;
        }
    }

    public Txn getUserTxn() {
        if (entryType.isTransactional()) {
            return txn;
        } else {
            return null;
        }
    }
}
