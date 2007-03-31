/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogEntry.java,v 1.18.2.2 2007/03/08 22:32:57 mark Exp $
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
 * The log entry on disk consists of 
 *  a. a log header defined by LogManager
 *  b. a VLSN, if this entry type requires it, and replication is on.
 *  c. the specific contents of the log entry.
 *
 * This class encompasses (b & c).
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
     * Read in an log entry. 
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
     * Sets the total size of the last logged entry, including the header.
     * Must be called after calling readEntry and writeEntry.  Some entries
     * (LNs) save the last logged size.
     */
    public void setLastLoggedSize(int size);

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
}
