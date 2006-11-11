/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DeltaInfo.java,v 1.19 2006/09/12 19:16:56 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;

import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogReadable;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LogWritable;
import com.sleepycat.je.utilint.DbLsn;

/**
 * DeltaInfo holds the delta for one BIN entry in a partial BIN log entry.
 * The data here is all that we need to update a BIN to its proper state.
 */
public class DeltaInfo implements LogWritable, LogReadable {
    private byte[] key;
    private long lsn;
    private byte state;
		  
    DeltaInfo(byte[] key, long lsn, byte state) {
        this.key = key;
        this.lsn = lsn;
        this.state = state;
    }

    /**
     * For reading from the log only.
     */
    DeltaInfo() {
        lsn = DbLsn.NULL_LSN;
    }

    /* 
     * @see com.sleepycat.je.log.LogWritable#getLogSize()
     */
    public int getLogSize() {
        return
            LogUtils.getByteArrayLogSize(key) +
	    LogUtils.getLongLogSize() + // LSN
            1; // state
    }

    /* 
     * @see LogWritable#writeToLog(java.nio.ByteBuffer)
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeByteArray(logBuffer, key);
	LogUtils.writeLong(logBuffer, lsn);
        logBuffer.put(state);
    }

    /* 
     * @see com.sleepycat.je.log.LogReadable#readFromLog
     *          (java.nio.ByteBuffer)
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
	throws LogException {

        key = LogUtils.readByteArray(itemBuffer);
	lsn = LogUtils.readLong(itemBuffer);
        state = itemBuffer.get();
    }

    /* 
     * @see LogReadable#dumpLog(java.lang.StringBuffer)
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append(Key.dumpString(key, 0));
	sb.append(DbLsn.toString(lsn));
        IN.dumpDeletedState(sb, state);
    }

    /**
     * @see LogReadable#logEntryIsTransactional
     */
    public boolean logEntryIsTransactional() {
	return false;
    }

    /**
     * @see LogReadable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    /**
     * @return the Key.
     */
    byte[] getKey() {
        return key;
    }

    /**
     * @return the state flags.
     */
    byte getState() {
        return state;
    }

    /**
     * @return true if this is known to be deleted.
     */
    boolean isKnownDeleted() {
        return IN.isStateKnownDeleted(state);
    }

    /**
     * @return the LSN.
     */
    long getLsn() {
        return lsn;
    }
}
