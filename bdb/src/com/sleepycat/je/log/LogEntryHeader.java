/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogEntryHeader.java,v 1.26 2008/06/27 18:30:29 linda Exp $
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
     * (invariant) checksum - 4 bytes
     * (invariant) entry type - 1 byte
     * (invariant) entry version and flags - 1 byte
     * (invariant) offset of previous log entry - 4 bytes
     * (invariant) item size (not counting header size) - 4 bytes
     * (optional) vlsn - 8 bytes
     *
     * Flags:
     * The provisional bit can be set for any log type in the log. It's an
     * indication to recovery that the entry shouldn't be processed when
     * rebuilding the tree. It's used to ensure the atomic logging of multiple
     * entries.
     *
     * The replicated bit is set when this particular log entry is
     * part of the replication stream and contains a VLSN in the header.
     */

    /* The invariant size of the log entry header. */
    static final int MIN_HEADER_SIZE = 14;

    /* Only used for tests and asserts. */
    public static final int MAX_HEADER_SIZE = MIN_HEADER_SIZE + VLSN.LOG_SIZE;

    private static final int CHECKSUM_BYTES = 4;
    private static final int ENTRYTYPE_OFFSET = 4;
    private static final int PREV_OFFSET = 6;
    private static final int ITEMSIZE_OFFSET = 10;
    private static final int VLSN_OFFSET = MIN_HEADER_SIZE;

    /* Flags stored in the version field. */
    private static final byte PROVISIONAL_ALWAYS_MASK = (byte) 0x80;
    private static final byte IGNORE_PROVISIONAL_ALWAYS =
                             ~PROVISIONAL_ALWAYS_MASK;
    private static final byte PROVISIONAL_BEFORE_CKPT_END_MASK = (byte) 0x40;
    private static final byte IGNORE_PROVISIONAL_BEFORE_CKPT_END =
                             ~PROVISIONAL_BEFORE_CKPT_END_MASK;
    private static final byte REPLICATED_MASK = (byte) 0x20;
    private static final byte IGNORE_REPLICATED = ~REPLICATED_MASK;

    private long checksumVal;   // stored in 4 bytes as an unsigned int
    private byte entryType;
    private byte entryVersion;
    private long prevOffset;
    private int itemSize;
    private VLSN vlsn;

    /* Version flag fields */
    private Provisional provisional;
    private boolean replicated;

    /**
     * For reading a log entry.
     * @param anticipateChecksumErrors if true, invalidate the environment
     * if the entry header is invalid.
     * @throws DbChecksumException if the entry is invalid.
     * If anticipateChecksumErrors is true and envImpl is not null, the
     * environment is also invalidated.
     */
    public LogEntryHeader(EnvironmentImpl envImpl,
                          ByteBuffer entryBuffer,
                          boolean anticipateChecksumErrors)
	throws DbChecksumException {

        checksumVal = LogUtils.readUnsignedInt(entryBuffer);
        entryType = entryBuffer.get();
        if (!LogEntryType.isValidType(entryType)) {
            throw new DbChecksumException
		((anticipateChecksumErrors ? null : envImpl),
                 "Read invalid log entry type: " +  entryType);
        }

        entryVersion = entryBuffer.get();
        prevOffset = LogUtils.readUnsignedInt(entryBuffer);
        itemSize = LogUtils.readInt(entryBuffer);

        if ((entryVersion & PROVISIONAL_ALWAYS_MASK) != 0) {
            provisional = Provisional.YES;
        } else if ((entryVersion & PROVISIONAL_BEFORE_CKPT_END_MASK) != 0) {
            provisional = Provisional.BEFORE_CKPT_END;
        } else {
            provisional = Provisional.NO;
        }
        replicated = ((entryVersion & REPLICATED_MASK) != 0);
        entryVersion &= IGNORE_PROVISIONAL_ALWAYS;
        entryVersion &= IGNORE_PROVISIONAL_BEFORE_CKPT_END;
        entryVersion &= IGNORE_REPLICATED;
    }

    /**
     * For writing a log header.  public for unit tests.
     */
    public LogEntryHeader(LogEntry entry,
			  Provisional provisional,
			  ReplicationContext repContext) {

        LogEntryType logEntryType = entry.getLogType();
        entryType = logEntryType.getTypeNum();
        entryVersion = LogEntryType.LOG_VERSION;
        this.itemSize = entry.getSize();
        this.provisional = provisional;

        assert (!((!logEntryType.isReplicationPossible()) &&
                  repContext.inReplicationStream())) :
            logEntryType + " should never be replicated.";

        if (logEntryType.isReplicationPossible()) {
            this.replicated = repContext.inReplicationStream();
        } else {
            this.replicated = false;
        }
    }

    public long getChecksum() {
        return checksumVal;
    }

    public byte getType() {
        return entryType;
    }

    public byte getVersion() {
        return entryVersion;
    }

    public long getPrevOffset() {
        return prevOffset;
    }

    public int getItemSize() {
        return itemSize;
    }

    public VLSN getVLSN() {
        return vlsn;
    }

    public boolean getReplicated() {
        return replicated;
    }

    public Provisional getProvisional() {
        return provisional;
    }

    public int getVariablePortionSize() {
        return VLSN.LOG_SIZE;
    }

    /**
     * @return number of bytes used to store this header
     */
    public int getSize() {
        if (replicated) {
            return MIN_HEADER_SIZE + VLSN.LOG_SIZE;
        } else {
            return MIN_HEADER_SIZE;
        }
    }

    /**
     * @return the number of bytes used to store the header, excepting
     * the checksum field.
     */
    int getSizeMinusChecksum() {
        return getSize()- CHECKSUM_BYTES;
    }

    /**
     * @return the number of bytes used to store the header, excepting
     * the checksum field.
     */
    int getInvariantSizeMinusChecksum() {
        return MIN_HEADER_SIZE - CHECKSUM_BYTES;
    }

    /**
     * Assumes this is called directly after the constructor, and that the
     * entryBuffer is positioned right before the VLSN.
     */
    public void readVariablePortion(ByteBuffer entryBuffer)
        throws LogException {

        if (replicated) {
            vlsn = new VLSN();
            vlsn.readFromLog(entryBuffer, entryVersion);
        }
    }

    /**
     * Serialize this object into the buffer and leave the buffer positioned in
     * the right place to write the following item.  The checksum, prevEntry,
     * and vlsn values will filled in later on.
     *
     * public for unit tests.
     */
    public void writeToLog(ByteBuffer entryBuffer) {

        /* Skip over the checksumVal, proceed to the entry type. */
        entryBuffer.position(ENTRYTYPE_OFFSET);
        entryBuffer.put(entryType);

        /* version and flags */
        byte versionFlags = entryVersion;
        if (provisional == Provisional.YES) {
            versionFlags |= PROVISIONAL_ALWAYS_MASK;
        } else if (provisional == Provisional.BEFORE_CKPT_END) {
            versionFlags |= PROVISIONAL_BEFORE_CKPT_END_MASK;
        }
        if (replicated) {
            versionFlags |= REPLICATED_MASK;
        }
        entryBuffer.put(versionFlags);

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
        if (replicated) {
            entryBuffer.position(entryBuffer.position() + VLSN.LOG_SIZE);
        }
    }

    /**
     * Add those parts of the header that must be calculated later to the
     * entryBuffer, and also assign the fields in this class.
     * That's
     * - the prev offset, which must be done within the log write latch to
     *   be sure what that lsn is
     * - the VLSN, for the same reason
     * - the checksumVal, which must be added last, after all other
     *   fields are marshalled.
     * (public for unit tests)
     */
    public ByteBuffer addPostMarshallingInfo(EnvironmentImpl envImpl,
                                             ByteBuffer entryBuffer,
                                             long lastOffset,
                                             ReplicationContext repContext) {

        /* Add the prev pointer */
        prevOffset = lastOffset;
        entryBuffer.position(PREV_OFFSET);
        LogUtils.writeUnsignedInt(entryBuffer, prevOffset);

        /* Add the optional VLSN */
        if (repContext.inReplicationStream()) {
            entryBuffer.position(VLSN_OFFSET);

            if (repContext.mustGenerateVLSN()) {
                vlsn = envImpl.getReplicator().bumpVLSN();
            } else {
                vlsn = repContext.getClientVLSN();
            }
            vlsn.writeToLog(entryBuffer);
        }

        /*
         * Now calculate the checksumVal and write it into the buffer.  Be sure
         * to set the field in this instance, for use later when printing or
         * debugging the header.
         */
        Checksum checksum = Adler32.makeChecksum();
        checksum.update(entryBuffer.array(),
                        entryBuffer.arrayOffset() + CHECKSUM_BYTES,
                        entryBuffer.limit() - CHECKSUM_BYTES);
        entryBuffer.position(0);
        checksumVal = checksum.getValue();
        LogUtils.writeUnsignedInt(entryBuffer, checksumVal);

        /* Leave this buffer ready for copying into another buffer. */
        entryBuffer.position(0);

        return entryBuffer;
    }

    /**
     * @param sb destination string buffer
     * @param verbose if true, dump the full, verbose version
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<hdr ");
        dumpLogNoTag(sb, verbose);
        sb.append("\"/>");
    }

    /**
     * Dump the header without enclosing <header> tags. Used for
     * DbPrintLog, to make the header attributes in the <entry> tag, for
     * a more compact rendering.
     * @param sb destination string buffer
     * @param verbose if true, dump the full, verbose version
     */
    void dumpLogNoTag(StringBuffer sb, boolean verbose) {
        LogEntryType lastEntryType = LogEntryType.findType(entryType);

        sb.append("type=\"").append(lastEntryType.toStringNoVersion()).
	    append("/").append((int) entryVersion);
        if (provisional != Provisional.NO) {
            sb.append("\" prov=\"");
            sb.append(provisional);
        }
        if (replicated) {
            sb.append("\" rep=\"true");
        }
        if (vlsn != null) {
            sb.append("\" ");
            vlsn.dumpLog(sb, verbose);
        } else {
            sb.append("\"");
        }
        sb.append(" prev=\"0x").append(Long.toHexString(prevOffset));
        if (verbose) {
            sb.append("\" size=\"").append(itemSize);
            sb.append("\" cksum=\"").append(checksumVal);
        }
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
                        entryTypePosition + entryBuffer.arrayOffset(),
                        checksumSize);
        entryBuffer.position(itemStart - getSize());
        checksumVal = checksum.getValue();
        LogUtils.writeUnsignedInt(entryBuffer, checksumVal);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        dumpLog(sb, true /* verbose */);
        return sb.toString();
    }

    /**
     * @return true if these two log headers are logically the same.
     * Used for replication.
     */
    public boolean logicalEquals(LogEntryHeader other) {
        /* 
         * Note that item size is not part of the logical equality, because
         * on-disk compression can make itemSize vary if the entry has VLSNs
         * that were packed differently.
         */
        return ((getType() == other.getType()) &&
                (getVersion() == other.getVersion()) &&
                (getVLSN().equals(other.getVLSN())) &&
                (getReplicated() == other.getReplicated()));

    }

    /**
     * Return whether the log entry represented by this byte buffer is a
     * replication sync possible type log entry. Leaves the byte buffer's
     * position unchanged.
     */
    public static boolean isSyncPoint(ByteBuffer buffer) 
        throws DbChecksumException {
    	
        buffer.mark();
        LogEntryHeader header = 
            new LogEntryHeader(null,  // envImpl, for checksum
                               buffer,
                               true); // anticipateChecksumError
        buffer.reset();
        return LogEntryType.isSyncPoint(header.getType());
    }

    /**
     * Return the VLSN for the log entry header in this byte buffer. Leaves the
     * byte buffer's position unchanged.
     */
    public static VLSN getVLSN(ByteBuffer buffer) 
        throws DatabaseException {
    	
        buffer.mark();
        LogEntryHeader header = 
            new LogEntryHeader(null, // envImipl,
                               buffer,
                               true); // anticipateChecksumErrors

        header.readVariablePortion(buffer);
        buffer.reset();
        return header.getVLSN();
    }
}
