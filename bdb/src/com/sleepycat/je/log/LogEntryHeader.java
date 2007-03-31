/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogEntryHeader.java,v 1.1.2.2 2007/03/08 22:32:55 mark Exp $
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.Adler32;
import com.sleepycat.je.utilint.VLSN;

/**
 * A LogEntryHeader embodies the header information at the beginning of each
 * log entry file.
 */
public class LogEntryHeader {

    /**
     * Persistent fields. Layout on disk is
     * checksum - 4 bytes
     * entry type - 1 byte
     * entry version and flags - 1 byte
     * offset of previous log entry - 4 bytes
     * item size (not counting header size) - 4 bytes
     * vlsn (optional) - 16 bytes
     */

    static final int MIN_HEADER_SIZE = 14; 

    /* Only used for tests and asserts. */
    static final int MAX_HEADER_SIZE = MIN_HEADER_SIZE + VLSN.LOG_SIZE;

    private static final int CHECKSUM_BYTES = 4;   
    private static final int ENTRYTYPE_OFFSET = 4;
    private static final int PREV_OFFSET = 6;
    private static final int ITEMSIZE_OFFSET = 10;
    private static final int VLSN_OFFSET = 14;

    private long checksum;   // stored in 4 bytes as an unsigned int
    private byte entryType;
    private byte entryVersion;
    private long prevOffset;
    private int itemSize;
    private VLSN vlsn;

    /* Transient fields */
    private boolean isProvisional;
    private boolean replicate;

    /** 
     * For reading a log entry.
     */
    public LogEntryHeader(EnvironmentImpl envImpl,
                          ByteBuffer entryBuffer, 
                          boolean anticipateChecksumErrors)
	throws DatabaseException {

        checksum = LogUtils.getUnsignedInt(entryBuffer);
        entryType = entryBuffer.get(); 
        if (!LogEntryType.isValidType(entryType))
            throw new DbChecksumException
		((anticipateChecksumErrors ? null : envImpl),
                 "Read invalid log entry type: " +  entryType);

        
        entryVersion = entryBuffer.get(); 
        prevOffset = LogUtils.getUnsignedInt(entryBuffer);
        itemSize = LogUtils.readInt(entryBuffer);

        isProvisional = LogEntryType.isEntryProvisional(entryVersion);
        replicate = LogEntryType.isEntryReplicated(entryVersion);
    }

    /**
     * For writing a log header.
     */
    LogEntryHeader(LogEntry entry,
                   boolean isProvisional,
                   boolean replicate) {

        LogEntryType logEntryType = entry.getLogType();
        entryType = logEntryType.getTypeNum();
        entryVersion = logEntryType.getVersion();
        this.itemSize = entry.getSize();
        this.isProvisional = isProvisional;
        this.replicate = replicate;
    }

    long getChecksum() {
        return checksum;
    }

    public byte getType() {
        return entryType;
    }

    public byte getVersion() {
        return entryVersion;
    }

    long getPrevOffset() {
        return prevOffset;
    }

    public int getItemSize() {
        return itemSize;
    }

    public boolean getReplicate() {
        return replicate;
    }

    int getVariablePortionSize() {
        return VLSN.LOG_SIZE;
    }

    /**
     * @return number of bytes used to store this header
     */
    public int getSize() {
        if (replicate) {
            return MIN_HEADER_SIZE + VLSN.LOG_SIZE;
        } else {
            return MIN_HEADER_SIZE;
        }
    }

    /**
     */
    int getSizeMinusChecksum() {
        return getSize()- CHECKSUM_BYTES;
    }

    /**
     * Assumes this is called directly after the constructor, and that the
     * entryBuffer is positioned right before the VLSN.
     */
    void readVariablePortion(ByteBuffer entryBuffer) 
        throws LogException {
        if (replicate) {
            vlsn = new VLSN();
            vlsn.readFromLog(entryBuffer, entryVersion);
        }
    }

    /**
     * Serialize this object into the buffer and leave the buffer positioned in
     * the right place to write the following item.  The checksum, prevEntry,
     * and vlsn values will filled in later on.
     */
    void writeToLog(ByteBuffer entryBuffer) {

        /* Skip over the checksum, proceed to the entry type. */
        entryBuffer.position(ENTRYTYPE_OFFSET);
        entryBuffer.put(entryType);

        /* version and flags */
        if (isProvisional) {
            entryVersion = LogEntryType.setEntryProvisional(entryVersion);
        }
        if (replicate) {
            entryVersion = LogEntryType.setEntryReplicated(entryVersion);
        }
        entryBuffer.put(entryVersion);

        /* 
         * Leave room for the prev offset, which must be added under
         * the log write latch. Proceed to write the item size.
         */
        entryBuffer.position(ITEMSIZE_OFFSET);
        LogUtils.writeInt(entryBuffer, itemSize);

        /* 
         * Leave room for a VLSN if needed, must also be generated
         * under the log write latch.
         */
        if (replicate) {
            entryBuffer.position(entryBuffer.position() + VLSN.LOG_SIZE);
        }
    }

    /**
     * Add those parts of the header that must be calculated later.
     * That's 
     * - the prev offset, which must be done within the log write latch to
     *   be sure what that lsn is
     * - the VLSN, for the same reason
     * - the checksum, which must be added last, after all other 
     *   fields are marshalled.
     */
    ByteBuffer addPostMarshallingInfo(EnvironmentImpl envImpl,
                                      ByteBuffer entryBuffer,
                                      long lastOffset) {

        /* Add the prev pointer */
        entryBuffer.position(PREV_OFFSET);
        LogUtils.writeUnsignedInt(entryBuffer, lastOffset);

        /* Add the optional VLSN */
        if (replicate) {
            entryBuffer.position(VLSN_OFFSET);
            VLSN vlsn = envImpl.getReplicator().bumpVLSN();
            vlsn.writeToLog(entryBuffer);
        }

        /* Now calculate the checksum and write it into the buffer. */
        Checksum checksum = Adler32.makeChecksum();
        checksum.update(entryBuffer.array(),
                        CHECKSUM_BYTES,
                        entryBuffer.limit() - CHECKSUM_BYTES);
        entryBuffer.position(0);
        LogUtils.writeUnsignedInt(entryBuffer, checksum.getValue());

        /* Leave this buffer ready for copying into another buffer. */
        entryBuffer.position(0);

        return entryBuffer;
    }

    /**
     * @param sb destination string buffer
     * @param verbose if true, dump the full, verbose version
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
    }

    /**
     * For use in special case where commits are transformed to aborts because
     * of i/o errors during a logBuffer flush. See [11271].
     * Assumes that the entryBuffer is positioned at the start of the item.
     * Return with the entryBuffer positioned to the end of the log entry.
     */
    void convertCommitToAbort(ByteBuffer entryBuffer) {
        assert (entryType == LogEntryType.LOG_TXN_COMMIT.getTypeNum());

        /* Remember the start of the entry item. */
        int itemStart = entryBuffer.position();

        /* Back up to where the type is stored and change the type. */
        int entryTypePosition = 
            itemStart - (getSize() - ENTRYTYPE_OFFSET);
        entryBuffer.position(entryTypePosition);
        entryBuffer.put(LogEntryType.LOG_TXN_ABORT.getTypeNum());

        /* 
         * Recalculate the checksum. This byte buffer could be large,
         * so don't just turn the whole buffer into an array to pass
         * into the checksum object.
         */
        Checksum checksum = Adler32.makeChecksum();
        int checksumSize = itemSize + (getSize() - CHECKSUM_BYTES);
        checksum.update(entryBuffer.array(),
                        entryTypePosition,
                        checksumSize);
        entryBuffer.position(itemStart - getSize());
        LogUtils.writeUnsignedInt(entryBuffer, checksum.getValue());
    }
}
