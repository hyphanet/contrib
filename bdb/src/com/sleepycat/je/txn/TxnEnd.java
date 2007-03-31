/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnEnd.java,v 1.33.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.nio.ByteBuffer;
import java.sql.Timestamp;

import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.utilint.DbLsn;

/**
 * This class writes out a transaction commit or transaction end record.
 */
public abstract class TxnEnd implements Loggable {

    protected long id;
    protected Timestamp time;
    private long lastLsn;

    TxnEnd(long id, long lastLsn) {
        this.id = id;
        time = new Timestamp(System.currentTimeMillis());
        this.lastLsn = lastLsn;
    }
    
    /**
     * For constructing from the log
     */
    public TxnEnd() {
        lastLsn = DbLsn.NULL_LSN;
    }

    /*
     * Accessors.
     */
    public long getId() {
        return id;
    }

    long getLastLsn() {
        return lastLsn;
    }

    protected abstract String getTagName();

    /*
     * Log support for writing.
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.LONG_BYTES +
            LogUtils.getTimestampLogSize() +
            LogUtils.getLongLogSize(); // lastLsn
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeLong(logBuffer, id);
        LogUtils.writeTimestamp(logBuffer, time);
        LogUtils.writeLong(logBuffer, lastLsn);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryTypeVersion) {
        id = LogUtils.readLong(logBuffer);
        time = LogUtils.readTimestamp(logBuffer);
        lastLsn = LogUtils.readLong(logBuffer);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<").append(getTagName());
        sb.append(" id=\"").append(id);
        sb.append("\" time=\"").append(time);
        sb.append("\">");
	sb.append(DbLsn.toString(lastLsn));
        sb.append("</").append(getTagName()).append(">");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return id;
    }
}
