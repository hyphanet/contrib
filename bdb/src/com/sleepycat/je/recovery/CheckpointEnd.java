/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: CheckpointEnd.java,v 1.38 2008/06/10 02:52:13 cwl Exp $
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
    private long lastLocalNodeId;
    private long lastReplicatedNodeId;
    private int lastLocalDbId;
    private int lastReplicatedDbId;
    private long lastLocalTxnId;
    private long lastReplicatedTxnId;
    private long id;

    public CheckpointEnd(String invoker,
                         long checkpointStartLsn,
                         long rootLsn,
                         long firstActiveLsn,
                         long lastLocalNodeId,
                         long lastReplicatedNodeId,
                         int lastLocalDbId,
                         int lastReplicatedDbId,
                         long lastLocalTxnId,
                         long lastReplicatedTxnId,
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
        this.lastLocalNodeId = lastLocalNodeId;
        this.lastReplicatedNodeId = lastReplicatedNodeId;
        this.lastLocalDbId = lastLocalDbId;
        this.lastReplicatedDbId = lastReplicatedDbId;
        this.lastLocalTxnId = lastLocalTxnId;
        this.lastReplicatedTxnId = lastReplicatedTxnId;
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
            LogUtils.getStringLogSize(invoker) +    // invoker
            LogUtils.getTimestampLogSize(endTime) + // endTime
	    LogUtils.getPackedLongLogSize(checkpointStartLsn) +
            1 +                                     // rootLsnExists
	    LogUtils.getPackedLongLogSize(firstActiveLsn) +
            LogUtils.getPackedLongLogSize(lastLocalNodeId) +
            LogUtils.getPackedLongLogSize(lastReplicatedNodeId) +
            LogUtils.getPackedIntLogSize(lastLocalDbId) +
            LogUtils.getPackedIntLogSize(lastReplicatedDbId) +
            LogUtils.getPackedLongLogSize(lastLocalTxnId) +
            LogUtils.getPackedLongLogSize(lastReplicatedTxnId) +
            LogUtils.getPackedLongLogSize(id);

        if (rootLsnExists) {
            size += LogUtils.getPackedLongLogSize(rootLsn);
        }
        return size;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeString(logBuffer, invoker);
        LogUtils.writeTimestamp(logBuffer, endTime);
	LogUtils.writePackedLong(logBuffer, checkpointStartLsn);
        byte booleans = (byte) (rootLsnExists ? 1 : 0);
        logBuffer.put(booleans);
        if (rootLsnExists) {
	    LogUtils.writePackedLong(logBuffer, rootLsn);
        }
	LogUtils.writePackedLong(logBuffer, firstActiveLsn);

        LogUtils.writePackedLong(logBuffer, lastLocalNodeId);
        LogUtils.writePackedLong(logBuffer, lastReplicatedNodeId);

        LogUtils.writePackedInt(logBuffer, lastLocalDbId);
        LogUtils.writePackedInt(logBuffer, lastReplicatedDbId);

        LogUtils.writePackedLong(logBuffer, lastLocalTxnId);
        LogUtils.writePackedLong(logBuffer, lastReplicatedTxnId);

        LogUtils.writePackedLong(logBuffer, id);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryVersion)
	throws LogException {

        boolean version6OrLater = (entryVersion >= 6);
        invoker = LogUtils.readString(logBuffer, !version6OrLater);
        endTime = LogUtils.readTimestamp(logBuffer, !version6OrLater);
	checkpointStartLsn = LogUtils.readLong(logBuffer, !version6OrLater);
        byte booleans = logBuffer.get();
        rootLsnExists = (booleans & 1) != 0;
        if (rootLsnExists) {
	    rootLsn = LogUtils.readLong(logBuffer, !version6OrLater);
        }
	firstActiveLsn = LogUtils.readLong(logBuffer, !version6OrLater);

        lastLocalNodeId = LogUtils.readLong(logBuffer, !version6OrLater);
        if (version6OrLater) {
            lastReplicatedNodeId = LogUtils.readLong(logBuffer,
                                                     false/*unpacked*/);
        }

        lastLocalDbId = LogUtils.readInt(logBuffer, !version6OrLater);
        if (version6OrLater) {
            lastReplicatedDbId = LogUtils.readInt(logBuffer,
                                                  false/*unpacked*/);
        }

        lastLocalTxnId = LogUtils.readLong(logBuffer, !version6OrLater);
        if (version6OrLater) {
            lastReplicatedTxnId = LogUtils.readLong(logBuffer,
                                                    false/*unpacked*/);
        }

        id = LogUtils.readLong(logBuffer, !version6OrLater);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<CkptEnd invoker=\"").append(invoker);
        sb.append("\" time=\"").append(endTime);
        sb.append("\" lastLocalNodeId=\"").append(lastLocalNodeId);
        sb.append("\" lastReplicatedNodeId=\"").append(lastReplicatedNodeId);
        sb.append("\" lastLocalDbId=\"").append(lastLocalDbId);
        sb.append("\" lastReplicatedDbId=\"").append(lastReplicatedDbId);
        sb.append("\" lastLocalTxnId=\"").append(lastLocalTxnId);
        sb.append("\" lastReplicatedTxnId=\"").append(lastReplicatedTxnId);
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

    /**
     * @see Loggable#logicalEquals
     * Always return false, this item should never be compared.
     */
    public boolean logicalEquals(Loggable other) {
        return false;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("time=").append(endTime);
        sb.append(" lastLocalNodeId=").append(lastLocalNodeId);
        sb.append(" lastReplicatedNodeId=").append(lastReplicatedNodeId);
        sb.append(" lastLocalDbId=").append(lastLocalDbId);
        sb.append(" lastReplicatedDbId=").append(lastReplicatedDbId);
        sb.append(" lastLocalTxnId=").append(lastLocalTxnId);
        sb.append(" lastReplicatedTxnId=").append(lastReplicatedTxnId);
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

    long getLastLocalNodeId() {
        return lastLocalNodeId;
    }

    long getLastReplicatedNodeId() {
        return lastReplicatedNodeId;
    }

    int getLastLocalDbId() {
        return lastLocalDbId;
    }

    int getLastReplicatedDbId() {
        return lastReplicatedDbId;
    }

    long getLastLocalTxnId() {
        return lastLocalTxnId;
    }

    long getLastReplicatedTxnId() {
        return lastReplicatedTxnId;
    }

    long getId() {
        return id;
    }
}
