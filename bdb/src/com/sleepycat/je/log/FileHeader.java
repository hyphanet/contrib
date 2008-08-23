/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileHeader.java,v 1.48 2008/06/10 02:52:12 cwl Exp $
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
public class FileHeader implements Loggable {

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
        logVersion = LogEntryType.LOG_VERSION;
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
     * @return file header log version.
     *
     * @throws DatabaseException if the header isn't valid.
     */
    int validate(String fileName, long expectedFileNum)
        throws DatabaseException {

        if (fileNum != expectedFileNum) {
            throw new LogException
                ("Wrong filenum in header for file " +
                 fileName + " expected " +
                 expectedFileNum + " got " + fileNum);
        }

        return logVersion;
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
     * A header is always a known size.  Is public for unit testing.
     */
    public static int entrySize() {
        return
            LogUtils.LONG_BYTES +                // time
            LogUtils.UNSIGNED_INT_BYTES +        // file number
            LogUtils.LONG_BYTES +                // lastEntryInPrevFileOffset
            LogUtils.INT_BYTES;                  // logVersion
    }
    /**
     * @see Loggable#getLogSize
     * @return number of bytes used to store this object
     */
    public int getLogSize() {
        return entrySize();
    }

    /**
     * @see Loggable#writeToLog
     * Serialize this object into the buffer. Update cksum with all
     * the bytes used by this object
     * @param logBuffer is the destination buffer
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeLong(logBuffer, time.getTime());
        LogUtils.writeUnsignedInt(logBuffer, fileNum);
        LogUtils.writeLong(logBuffer, lastEntryInPrevFileOffset);
        LogUtils.writeInt(logBuffer, logVersion);
    }

    /**
     * @see Loggable#readFromLog
     * Initialize this object from the data in itemBuf.
     * @param itemBuf the source buffer
     */
    public void readFromLog(ByteBuffer logBuffer, byte entryVersion)
	throws LogException {

        /* Timestamp is always unpacked. */
        time = LogUtils.readTimestamp(logBuffer, true/*unpacked*/);
        fileNum = LogUtils.readUnsignedInt(logBuffer);
        lastEntryInPrevFileOffset = LogUtils.readLong(logBuffer);
        logVersion = LogUtils.readInt(logBuffer);
        if (logVersion > LogEntryType.LOG_VERSION) {
            throw new LogException("Expected log version " +
                                   LogEntryType.LOG_VERSION +
                                   " or earlier but found " + logVersion +
                                   " -- this version is not supported.");
        }
    }

    /**
     * @see Loggable#dumpLog
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

    /**
     * Print in xml format
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        dumpLog(sb, true);
        return sb.toString();
    }
}
