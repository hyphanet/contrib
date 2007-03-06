/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: INLogEntry.java,v 1.36 2006/11/17 23:47:24 mark Exp $
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LoggableObject;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * INLogEntry embodies all IN log entries.  These entries contain an IN and a
 * databaseId. This class can both write out an entry and read one in.
 */
public class INLogEntry
    implements LogEntry, LoggableObject, NodeLogEntry, INContainingEntry {

    /* Objects contained in an IN entry */
    private IN in;
    private DatabaseId dbId;

    /*
     * obsoleteFile was added in version 1, and changed to obsoleteLsn in
     * version 2.  If the offset is zero in the LSN, we read a version 1 entry
     * since only the file number was stored.
     */
    private long obsoleteLsn;
    
    private long nodeId;
    private Class logClass;

    /**
     * Construct a log entry for reading.
     */
    public INLogEntry(Class logClass) {
        this.logClass = logClass;
    }

    /**
     * Construct a log entry for writing to the log.
     */
    public INLogEntry(IN in) {
        this.in = in;
        this.dbId = in.getDatabase().getId();
        this.logClass = in.getClass();
        this.nodeId = in.getNodeId();
        this.obsoleteLsn = in.getLastFullVersion();
    }

    /*
     * Read support
     */

    /**
     * Read in an IN entry.
     */
    public void readEntry(ByteBuffer entryBuffer,
			  int entrySize,
                          byte entryTypeVersion,
			  boolean readFullItem)
        throws DatabaseException {

        entryTypeVersion &= LogEntryType.clearProvisional(entryTypeVersion);

        try {
            if (readFullItem) {
                /* Read IN and get node ID. */
                in = (IN) logClass.newInstance();
                in.readFromLog(entryBuffer, entryTypeVersion);
                nodeId = in.getNodeId();
            } else {
                /* Calculate position following IN. */
                int position = entryBuffer.position() + entrySize;
                if (entryTypeVersion == 1) {
                    /* Subtract size of obsoleteFile */
                    position -= LogUtils.UNSIGNED_INT_BYTES;
                } else if (entryTypeVersion >= 2) {
                    /* Subtract size of obsoleteLsn */
                    position -= LogUtils.LONG_BYTES;
                }
                /* Subtract size of dbId */
                position -= LogUtils.INT_BYTES;
                /* Read node ID and position after IN. */
                nodeId = LogUtils.readLong(entryBuffer);
                entryBuffer.position(position);
                in = null;
            }
            dbId = new DatabaseId();
            dbId.readFromLog(entryBuffer, entryTypeVersion);
            if (entryTypeVersion < 1) {
                obsoleteLsn = DbLsn.NULL_LSN;
            } else if (entryTypeVersion == 1) {
                long fileNum = LogUtils.getUnsignedInt(entryBuffer);
                if (fileNum == 0xffffffffL) {
                    obsoleteLsn = DbLsn.NULL_LSN;
                } else {
                    obsoleteLsn = DbLsn.makeLsn(fileNum, 0);
                }
            } else {
                obsoleteLsn = LogUtils.readLong(entryBuffer);
            }
        } catch (IllegalAccessException e) {
            throw new DatabaseException(e);
        } catch (InstantiationException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Returns the LSN of the prior version of this node.  Used for counting
     * the prior version as obsolete.  If the offset of the LSN is zero, only
     * the file number is known because we read a version 1 log entry.
     */
    public long getObsoleteLsn() {

        return obsoleteLsn;
    }

    /**
     * Print out the contents of an entry.
     */
    public StringBuffer dumpEntry(StringBuffer sb, boolean verbose) {
        in.dumpLog(sb, verbose);
        dbId.dumpLog(sb, verbose);
        return sb;
    }

    /**
     * @return the item in the log entry
     */
    public Object getMainItem() {
        return in;
    }

    public Object clone()
        throws CloneNotSupportedException {

        return super.clone();
    }

    /**
     * @see LogEntry#isTransactional
     */
    public boolean isTransactional() {
	return false;
    }

    /**
     * @see LogEntry#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    /*
     * Writing support
     */

    /**
     * @see LoggableObject#getLogType
     */
    public LogEntryType getLogType() {
        return in.getLogType();
    }

    /**
     * @see LoggableObject#marshallOutsideWriteLatch
     * Ask the in if it can be marshalled outside the log write latch.
     */
    public boolean marshallOutsideWriteLatch() {
        return in.marshallOutsideWriteLatch();
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
    public void postLogWork(long justLoggedLsn) {
    }

    /**
     * @see LoggableObject#getLogSize
     */
    public int getLogSize() {
        return (in.getLogSize() +
		dbId.getLogSize() +
                LogUtils.LONG_BYTES);
    }

    /**
     * @see LoggableObject#writeToLog
     */
    public void writeToLog(ByteBuffer destBuffer) {
        in.writeToLog(destBuffer);
        dbId.writeToLog(destBuffer);
        LogUtils.writeLong(destBuffer, obsoleteLsn);
    }

    /*
     * Access the in held within the entry.
     * @see INContainingEntry#getIN()
     */
    public IN getIN(EnvironmentImpl env)
        throws DatabaseException {
                
        return in;
    }

    /**
     * @see NodeLogEntry#getNodeId
     */
    public long getNodeId() {
        return nodeId;
    }

    /**
     * @see INContainingEntry#getDbId()
     */
    public DatabaseId getDbId() {

        return dbId;
    }

    /**
     * @return the LSN that represents this IN. For a vanilla IN entry, it's 
     * the last lsn read by the log reader.
     */
    public long getLsnOfIN(long lastReadLsn) {
        return lastReadLsn;
    }
}
