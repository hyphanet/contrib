/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.

 */
package com.sleepycat.je.utilint;

import java.nio.ByteBuffer;

import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.Loggable;

public class VLSN implements Loggable {

    public static final int LOG_SIZE = 16;

    /*
     * A replicated log entry is identified by
     * generationId/environmentId/VLSN sequence id. We may change the VLSN
     * implementation so it's not a first-class object, in order to reduce its
     * in-memory footprint. In that case, the VLSN value would be a long, and
     * this class would provide static utility methods.
     */
    private int generationId;  // make unsigned?
    private int environmentId; // do we really need this?
    private long sequence; // sequence number

    public VLSN(int generationId, int environmentId, long sequence) {
        this.generationId = generationId;
        this.environmentId = environmentId;
        this.sequence = sequence;
    }

    /**
     * Constructor for VLSNs that are read from disk.
     */
    public VLSN() {
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LOG_SIZE;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer buffer) {
	buffer.putInt(generationId);
	buffer.putInt(environmentId);
	buffer.putLong(sequence);
    }

    /*  
     *  Reading from a byte buffer
     */

    /**
     * @see Loggable#writeToLog
     */
    public void readFromLog(ByteBuffer buffer, byte entryTypeVersion)
	throws LogException {
	generationId = buffer.getInt();
	environmentId = buffer.getInt();
	sequence = buffer.getLong();
    }


    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
	sb.append("VLSN: ").
	    append("generation id=").
	    append(generationId).
	    append(" environmentId=").
	    append(environmentId).
	    append(" sequence=").
	    append(sequence).
	    append("\n");

    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
    	return 0;
    }


    public int getContentSize() {
	return getLogSize();
    }

    /**
     * @param buffer is the destination buffer
     */
    public void writeToBuffer(ByteBuffer buffer) {
        writeToLog(buffer);
    }

    /**
     * BOZO, remove this
     */
    public void readFromBuffer(ByteBuffer buffer)
        throws LogException {
        readFromLog(buffer, (byte)0);
    }
}
