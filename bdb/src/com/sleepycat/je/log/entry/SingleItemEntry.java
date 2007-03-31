/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SingleItemEntry.java,v 1.1.2.1 2007/02/01 14:49:48 cwl Exp $
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.Loggable;

/**
 * This class embodies log entries that have a single loggable item.
 * On disk, an entry contains:
 * <pre>
 *     the Loggable item
 * </pre>
 */
public class SingleItemEntry extends BaseEntry implements LogEntry {

    /* 
     * Persistent fields in a SingleItemEntry.
     */
    private Loggable item;

    /**
     * Construct a log entry for reading.
     */
    public SingleItemEntry(Class logClass) {
        super(logClass);
    }

    /**
     * Construct a log entry for writing.
     */
    public SingleItemEntry(LogEntryType entryType,
                           Loggable item) {
        setLogType(entryType);
        this.item = item;
    }


    /**
     * @see LogEntry#readEntry
     */
    public void readEntry(LogEntryHeader header,
                          ByteBuffer entryBuffer,
                          boolean readFullItem)
        throws DatabaseException {

        try {
            item = (Loggable) logClass.newInstance();
            item.readFromLog(entryBuffer, 
                             header.getVersion());

        } catch (IllegalAccessException e) {
            throw new DatabaseException(e);
        } catch (InstantiationException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * @see LogEntry#dumpEntry
     */
    public StringBuffer dumpEntry(StringBuffer sb, boolean verbose) {
        item.dumpLog(sb, verbose);
        return sb;
    }

    /**
     * @see LogEntry#getMainItem
     */
    public Object getMainItem() {
        return item;
    }

    /**
     * @see LogEntry#clone
     */
    public Object clone()
        throws CloneNotSupportedException {

        return super.clone();
    }

    /**
     * @see LogEntry#getTransactionId
     */
    public long getTransactionId() {
        return item.getTransactionId();
    }

    /*
     * Writing support
     */

    public int getSize() {
        return item.getLogSize();
    }

    /**
     * @see LogEntry#writeEntry
     */
    public void writeEntry(LogEntryHeader header, ByteBuffer destBuffer) {
        item.writeToLog(destBuffer);
    }
}
