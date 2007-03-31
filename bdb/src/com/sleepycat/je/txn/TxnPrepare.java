/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnPrepare.java,v 1.8.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.nio.ByteBuffer;

import javax.transaction.xa.Xid;

import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.utilint.DbLsn;

/**
 * This class writes out a transaction prepare record.
 */
public class TxnPrepare extends TxnEnd implements Loggable {

    private Xid xid;

    public TxnPrepare(long id, Xid xid) {
	/* LastLSN is never used. */
        super(id, DbLsn.NULL_LSN);
	this.xid = xid;
    }
    
    /**
     * For constructing from the log.
     */
    public TxnPrepare() {
    }

    public Xid getXid() {
	return xid;
    }

    /*
     * Log support
     */

    protected String getTagName() {
        return "TxnPrepare";
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.LONG_BYTES +                    // id
            LogUtils.getTimestampLogSize() +            // timestamp
            LogUtils.getXidSize(xid);                   // Xid
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeLong(logBuffer, id);
        LogUtils.writeTimestamp(logBuffer, time);
	LogUtils.writeXid(logBuffer, xid);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryTypeVersion) {
        id = LogUtils.readLong(logBuffer);
        time = LogUtils.readTimestamp(logBuffer);
	xid = LogUtils.readXid(logBuffer);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<").append(getTagName());
        sb.append(" id=\"").append(id);
	sb.append("\" xid=\"").append(xid);
        sb.append("\" time=\"").append(time);
        sb.append("\">");
        sb.append("</").append(getTagName()).append(">");
    }
}
