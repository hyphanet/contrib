/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INDeleteInfo.java,v 1.31.2.1 2007/02/01 14:49:51 cwl Exp $
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
 * INDeleteInfo encapsulates the information logged about the removal of a
 * child from an IN during IN compression.
 */
public class INDeleteInfo implements Loggable {

    private long deletedNodeId;
    private byte[] deletedIdKey;
    private DatabaseId dbId;

    /**
     * Create a new delete info entry.
     */
    public INDeleteInfo(long deletedNodeId,
			byte[] deletedIdKey,
			DatabaseId dbId) {
        this.deletedNodeId = deletedNodeId;
        this.deletedIdKey = deletedIdKey;
        this.dbId = dbId;
    }

    /**
     * Used by logging system only.
     */
    public INDeleteInfo() {
        dbId = new DatabaseId();
    }

    /*
     * Accessors.
     */
    public long getDeletedNodeId() {
        return deletedNodeId;
    }

    public byte[] getDeletedIdKey() {
        return deletedIdKey;
    }
    
    public DatabaseId getDatabaseId() {
        return dbId;
    }

    /*
     * Logging support for writing.
     */
    public void optionalLog(LogManager logManager,
                            DatabaseImpl dbImpl)
        throws DatabaseException {

        if (!dbImpl.isDeferredWrite()) {
            logManager.log(new SingleItemEntry(LogEntryType.LOG_IN_DELETE_INFO,
                                               this));
        }
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.LONG_BYTES +
            LogUtils.getByteArrayLogSize(deletedIdKey) +
            dbId.getLogSize();
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {

        LogUtils.writeLong(logBuffer, deletedNodeId);
        LogUtils.writeByteArray(logBuffer, deletedIdKey);
        dbId.writeToLog(logBuffer);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
	throws LogException {

        deletedNodeId = LogUtils.readLong(itemBuffer);
        deletedIdKey = LogUtils.readByteArray(itemBuffer);
        dbId.readFromLog(itemBuffer, entryTypeVersion);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<INDeleteEntry node=\"").append(deletedNodeId);
        sb.append("\">");
        sb.append(Key.dumpString(deletedIdKey, 0));
        dbId.dumpLog(sb, verbose);
        sb.append("</INDeleteEntry>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }
}
