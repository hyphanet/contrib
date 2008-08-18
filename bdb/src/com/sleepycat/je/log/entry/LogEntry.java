/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogEntry.java,v 1.27 2008/01/07 14:28:51 cwl Exp $
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.LogEntryType;

/**
 * A Log entry allows you to read, write and dump a database log entry.  Each
 * entry may be made up of one or more loggable items.
 *
 * The log entry on disk consists of  a log header defined by LogManager
 * and the specific contents of the log entry.
 */
public interface LogEntry extends Cloneable {

    /**
     * Inform a LogEntry instance of its corresponding LogEntryType.
     */
    public void setLogType(LogEntryType entryType);

    /**
     * @return the type of log entry
     */
    public LogEntryType getLogType();

    /**
     * Read in a log entry.
     */
    public void readEntry(LogEntryHeader header,
                          ByteBuffer entryBuffer,
                          boolean readFullItem)
        throws DatabaseException;

    /**
     * Print out the contents of an entry.
     */
    public StringBuffer dumpEntry(StringBuffer sb, boolean verbose);

    /**
     * @return the first item of the log entry
     */
    public Object getMainItem();

    /**
     * @return return the transaction id if this log entry is transactional,
     * 0 otherwise.
     */
    public long getTransactionId();

    /**
     * @return size of byte buffer needed to store this entry.
     */
    public int getSize();

    /**
     * @return total size of last logged entry, or zero if unknown.  The last
     * logged size is known for LNs, and is used for obsolete size counting.
     */
    public int getLastLoggedSize();

    /**
     * Serialize this object into the buffer.
     * @param logBuffer is the destination buffer
     */
    public void writeEntry(LogEntryHeader header,
                           ByteBuffer logBuffer);

    /**
     * Returns true if this item should be counted as obsoleted when logged.
     * This currently applies to deleted LNs only.
     */
    public boolean countAsObsoleteWhenLogged();

    /**
     * Do any processing we need to do after logging, while under the logging
     * latch.
     */
    public void postLogWork(long justLoggedLsn)
        throws DatabaseException;

    /**
     * @return a shallow clone.
     */
    public Object clone() throws CloneNotSupportedException;

    /**
     * @return true if these two log entries are logically the same.
     * Used for replication.
     */
    public boolean logicalEquals(LogEntry other);
}
