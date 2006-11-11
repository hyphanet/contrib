/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: FileHeader.java,v 1.35 2006/09/12 19:16:51 cwl Exp $
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Calendar;

import com.sleepycat.je.DatabaseException;

/**
 * A FileHeader embodies the header information at the beginning of each log
 * file.
 */
public class FileHeader implements LoggableObject, LogReadable {

    /* 
     * Version 3
     * ---------
     * [12328] Add main and dupe tree fanout values for DatabaseImpl.
     * [12557] Add IN LSN array compression.
     * [11597] Add a change to FileSummaryLNs: obsolete offset tracking was
     * added and multiple records are stored for a single file rather than a
     * single record.  Each record contains the offsets that were tracked since
     * the last record was written.
     * [11597] Add the full obsolete LSN in LNLogEntry.
     *
     * Version 4
     * ---------
     * [#14422] Bump MapLN version from 1 to 2.  Instead of a String for the
     * comparator class name, store either a serialized string or Comparator.
     */
    private static final int LOG_VERSION = 4;

    /* 
     * fileNum is the number of file, starting at 0. An unsigned int, so stored
     * in a long in memory, but in 4 bytes on disk
     */
    private long fileNum; 
    private long lastEntryInPrevFileOffset;
    private Timestamp time;
    private int logVersion;

    FileHeader(long fileNum, long lastEntryInPrevFileOffset) {
        this.fileNum = fileNum;
        this.lastEntryInPrevFileOffset = lastEntryInPrevFileOffset;
        Calendar now = Calendar.getInstance();
        time = new Timestamp(now.getTimeInMillis());
        logVersion = LOG_VERSION;
    }

    /** 
     * For logging only.
     */
    public FileHeader() {
    }

    public int getLogVersion() {
        return logVersion;
    }

    /**
     * @return whether the file header has an old version number.
     *
     * @throws DatabaseException if the header isn't valid.
     */
    boolean validate(String fileName, long expectedFileNum) 
        throws DatabaseException {

        if (fileNum != expectedFileNum) {
            throw new LogException
                ("Wrong filenum in header for file " +
                 fileName + " expected " +
                 expectedFileNum + " got " + fileNum);
        }

        return logVersion < LOG_VERSION;
    }

    /**
     * @return the offset of the last entry in the previous file.
     */
    long getLastEntryInPrevFileOffset() {
        return lastEntryInPrevFileOffset;
    }

    /*
     * Logging support
     */

    /**
     * @see LoggableObject#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_FILE_HEADER;
    }

    /**
     * @see LoggableObject#marshallOutsideWriteLatch
     * Can be marshalled outside the log write latch.
     */
    public boolean marshallOutsideWriteLatch() {
        return true;
    }

    /**
     * @see LoggableObject#countAsObsoleteWhenLogged
     */
    public boolean countAsObsoleteWhenLogged() {
        return false;
    }

    /**
     * @see LoggableObject#postLogWork
     */
    public void postLogWork(long justLoggedLsn) 
        throws DatabaseException {
    }

    /**
     * A header is always a known size.
     */
    static int entrySize() {
        return
            LogUtils.getTimestampLogSize() + // time
            LogUtils.UNSIGNED_INT_BYTES +    // file number
            LogUtils.LONG_BYTES +            // lastEntryInPrevFileOffset
            LogUtils.INT_BYTES;              // logVersion
    }
    /**
     * @see LoggableObject#getLogSize
     * @return number of bytes used to store this object
     */
    public int getLogSize() {
        return entrySize();
    }            

    /**
     * @see LoggableObject#writeToLog
     * Serialize this object into the buffer. Update cksum with all
     * the bytes used by this object
     * @param logBuffer is the destination buffer
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeTimestamp(logBuffer, time);
        LogUtils.writeUnsignedInt(logBuffer,fileNum);
        LogUtils.writeLong(logBuffer, lastEntryInPrevFileOffset);
        LogUtils.writeInt(logBuffer, logVersion);
    }

    /**
     * @see LogReadable#readFromLog
     * Initialize this object from the data in itemBuf.
     * @param itemBuf the source buffer
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryTypeVersion)
	throws LogException {
        time = LogUtils.readTimestamp(logBuffer);
        fileNum = LogUtils.getUnsignedInt(logBuffer);
        lastEntryInPrevFileOffset = LogUtils.readLong(logBuffer);
        logVersion = LogUtils.readInt(logBuffer);
        if (logVersion > LOG_VERSION) {
            throw new LogException("Expected log version " + LOG_VERSION +
                                   " or earlier but found " + logVersion +
                                   " -- this version is not supported.");
        }
    }

    /**
     * @see LogReadable#dumpLog
     * @param sb destination string buffer
     * @param verbose if true, dump the full, verbose version
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<FileHeader num=\"0x");
        sb.append(Long.toHexString(fileNum));
        sb.append("\" lastEntryInPrevFileOffset=\"0x");
        sb.append(Long.toHexString(lastEntryInPrevFileOffset));
        sb.append("\" logVersion=\"0x");
        sb.append(Integer.toHexString(logVersion));
        sb.append("\" time=\"").append(time);
        sb.append("\"/>");
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
     * Print in xml format
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        dumpLog(sb, true);
        return sb.toString();
    }
}
