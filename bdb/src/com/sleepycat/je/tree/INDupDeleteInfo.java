/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INDupDeleteInfo.java,v 1.12.2.1 2007/02/01 14:49:51 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.entry.SingleItemEntry;

/**
 * INDupDeleteInfo encapsulates the information logged about the removal of a
 * child from a duplicate IN during IN compression.
 */
public class INDupDeleteInfo implements Loggable {

    private long deletedNodeId;
    private byte[] deletedMainKey;
    private byte[] deletedDupKey;
    private DatabaseId dbId;

    /**
     * Create a new delete info entry.
     */
    public INDupDeleteInfo(long deletedNodeId,
			   byte[] deletedMainKey,
			   byte[] deletedDupKey,
			   DatabaseId dbId) {
        this.deletedNodeId = deletedNodeId;
        this.deletedMainKey = deletedMainKey;
        this.deletedDupKey = deletedDupKey;
        this.dbId = dbId;
    }

    /**
     * Used by logging system only.
     */
    public INDupDeleteInfo() {
        dbId = new DatabaseId();
    }

    /*
     * Accessors.
     */
    public long getDeletedNodeId() {
        return deletedNodeId;
    }

    public byte[] getDeletedMainKey() {
        return deletedMainKey;
    }
    
    public byte[] getDeletedDupKey() {
        return deletedDupKey;
    }
    
    public DatabaseId getDatabaseId() {
        return dbId;
    }

    /*
     * Logging support for writing.
     */

    /*
     * Logging support for writing.
     */
    public void optionalLog(LogManager logManager,
                            DatabaseImpl dbImpl)
        throws DatabaseException {

        if (!dbImpl.isDeferredWrite()) {
            logManager.log(
               new SingleItemEntry(LogEntryType.LOG_IN_DUPDELETE_INFO, this));
        }
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.LONG_BYTES +
            LogUtils.getByteArrayLogSize(deletedMainKey) +
            LogUtils.getByteArrayLogSize(deletedDupKey) +
            dbId.getLogSize();
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {

        LogUtils.writeLong(logBuffer, deletedNodeId);
        LogUtils.writeByteArray(logBuffer, deletedMainKey);
        LogUtils.writeByteArray(logBuffer, deletedDupKey);
        dbId.writeToLog(logBuffer);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
	throws LogException {

        deletedNodeId = LogUtils.readLong(itemBuffer);
        deletedMainKey = LogUtils.readByteArray(itemBuffer);
        deletedDupKey = LogUtils.readByteArray(itemBuffer);
        dbId.readFromLog(itemBuffer, entryTypeVersion);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<INDupDeleteEntry node=\"").append(deletedNodeId);
        sb.append("\">");
        sb.append(Key.dumpString(deletedMainKey, 0));
        sb.append(Key.dumpString(deletedDupKey, 0));
        dbId.dumpLog(sb, verbose);
        sb.append("</INDupDeleteEntry>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }
}
