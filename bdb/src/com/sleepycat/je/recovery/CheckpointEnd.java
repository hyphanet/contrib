/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CheckpointEnd.java,v 1.29.2.1 2007/02/01 14:49:48 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Calendar;

import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.utilint.DbLsn;

/**
 * CheckpointEnd encapsulates the information needed by a checkpoint end 
 * log entry.
 */
public class CheckpointEnd implements Loggable {
    
    /* 
     * invoker is just a way to tag each checkpoint in the
     * log for easier log based debugging. It will tell us whether the
     * checkpoint was invoked by recovery, the daemon, the api, or
     * the cleaner.
     */
    private String invoker; 

    private Timestamp endTime;
    private long checkpointStartLsn;
    private boolean rootLsnExists;
    private long rootLsn;
    private long firstActiveLsn;
    private long lastNodeId;
    private int lastDbId;
    private long lastTxnId;
    private long id;

    public CheckpointEnd(String invoker,
                         long checkpointStartLsn,
                         long rootLsn,
                         long firstActiveLsn,
                         long lastNodeId,
                         int lastDbId,
                         long lastTxnId,
                         long id) {
        if (invoker == null) {
            this.invoker = "";
        } else {
            this.invoker = invoker;
        }
            
        Calendar cal = Calendar.getInstance();
        this.endTime = new Timestamp(cal.getTime().getTime());
        this.checkpointStartLsn = checkpointStartLsn;
        this.rootLsn = rootLsn;
        if (rootLsn == DbLsn.NULL_LSN) {
            rootLsnExists = false;
        } else {
            rootLsnExists = true;
        }
        if (firstActiveLsn == DbLsn.NULL_LSN) {
            this.firstActiveLsn = checkpointStartLsn;
        } else {
            this.firstActiveLsn = firstActiveLsn;
        }
        this.lastNodeId = lastNodeId;
        this.lastDbId = lastDbId;
        this.lastTxnId = lastTxnId;
        this.id = id;
    }

    /* For logging only */
    public CheckpointEnd() {
        checkpointStartLsn = DbLsn.NULL_LSN;
        rootLsn = DbLsn.NULL_LSN;
        firstActiveLsn = DbLsn.NULL_LSN;
    }

    /*
     * Logging support for writing to the log
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        int size =
            LogUtils.getStringLogSize(invoker) + // invoker
            LogUtils.getTimestampLogSize() +     // endTime
	    LogUtils.getLongLogSize() +          // checkpointStartLsn
            LogUtils.getBooleanLogSize() +       // rootLsnExists
	    LogUtils.getLongLogSize() +          // firstActiveLsn
            LogUtils.getLongLogSize() +          // lastNodeId
            LogUtils.getIntLogSize() +           // lastDbId
            LogUtils.getLongLogSize() +          // lastTxnId
            LogUtils.getLongLogSize();           // id

        if (rootLsnExists) {
            size += LogUtils.getLongLogSize();
        }
        return size;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeString(logBuffer, invoker);
        LogUtils.writeTimestamp(logBuffer, endTime);
	LogUtils.writeLong(logBuffer, checkpointStartLsn);
        LogUtils.writeBoolean(logBuffer, rootLsnExists);
        if (rootLsnExists) {
	    LogUtils.writeLong(logBuffer, rootLsn);
        }
	LogUtils.writeLong(logBuffer, firstActiveLsn);
        LogUtils.writeLong(logBuffer, lastNodeId);
        LogUtils.writeInt(logBuffer, lastDbId);
        LogUtils.writeLong(logBuffer, lastTxnId);
        LogUtils.writeLong(logBuffer, id);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryTypeVersion)
	throws LogException {
        invoker = LogUtils.readString(logBuffer);
        endTime = LogUtils.readTimestamp(logBuffer);
	checkpointStartLsn = LogUtils.readLong(logBuffer);
        rootLsnExists = LogUtils.readBoolean(logBuffer);
        if (rootLsnExists) {
	    rootLsn = LogUtils.readLong(logBuffer);
        }
	firstActiveLsn = LogUtils.readLong(logBuffer);
        lastNodeId = LogUtils.readLong(logBuffer);
        lastDbId = LogUtils.readInt(logBuffer);
        lastTxnId = LogUtils.readLong(logBuffer);
        id = LogUtils.readLong(logBuffer);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<CkptEnd invoker=\"").append(invoker);
        sb.append("\" time=\"").append(endTime);
        sb.append("\" lastNodeId=\"").append(lastNodeId);
        sb.append("\" lastDbId=\"").append(lastDbId);
        sb.append("\" lastTxnId=\"").append(lastTxnId);
        sb.append("\" id=\"").append(id);
        sb.append("\" rootExists=\"").append(rootLsnExists);
        sb.append("\">");
        sb.append("<ckptStart>");
	sb.append(DbLsn.toString(checkpointStartLsn));
        sb.append("</ckptStart>");

        if (rootLsnExists) {
            sb.append("<root>");
	    sb.append(DbLsn.toString(rootLsn));
            sb.append("</root>");
        }
        sb.append("<firstActive>");
	sb.append(DbLsn.toString(firstActiveLsn));
        sb.append("</firstActive>");
        sb.append("</CkptEnd>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("time=").append(endTime);
        sb.append(" lastNodeId=").append(lastNodeId);
        sb.append(" lastDbId=").append(lastDbId);
        sb.append(" lastTxnId=").append(lastTxnId);
        sb.append(" id=").append(id);
        sb.append(" rootExists=").append(rootLsnExists);
        sb.append(" ckptStartLsn=").append
            (DbLsn.getNoFormatString(checkpointStartLsn));
        if (rootLsnExists) {
            sb.append(" root=").append(DbLsn.getNoFormatString(rootLsn));
        }
        sb.append(" firstActive=").
	    append(DbLsn.getNoFormatString(firstActiveLsn));
        return sb.toString();
    }

    /*
     * Accessors
     */
    long getCheckpointStartLsn() {
        return checkpointStartLsn;
    }

    long getRootLsn() {
        return rootLsn;
    }

    long getFirstActiveLsn() {
        return firstActiveLsn;
    }

    long getLastNodeId() {
        return lastNodeId;
    }
    int getLastDbId() {
        return lastDbId;
    }
    long getLastTxnId() {
        return lastTxnId;
    }
    long getId() {
        return id;
    }
}
