/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BaseEntry.java,v 1.1.2.2 2007/03/08 22:32:56 mark Exp $
 */

package com.sleepycat.je.log.entry;

import com.sleepycat.je.DatabaseException;
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
abstract class BaseEntry {

    /*
     * These fields are transient and are  not persisted to the log
     */

    /* Used to instantiate the key objects from on-disk bytes */
    Class logClass;  

    /* 
     * Attributes of the entry type may be used to conditionalizing the reading
     * and writing of the entry.
     */
    LogEntryType entryType; 

    /**
     * Constructor to read an entry. The logEntryType must be set
     * later, through setLogType().
     */
    BaseEntry(Class logClass) {
        this.logClass = logClass;
    }

    /**
     * Constructor to write an entry. 
     */
    BaseEntry() {
    }

    /**
     * Inform a BaseEntry instance of its corresponding LogEntryType.
     */
    public void setLogType(LogEntryType entryType) {
        this.entryType = entryType;
    }

    /**
     * @return the type of log entry 
     */
    public LogEntryType getLogType() {
        return entryType;
    }

    /**
     * Returns true if this item should be counted as obsoleted when logged.
     * This currently applies to deleted LNs only.
     */
    public boolean countAsObsoleteWhenLogged() {
        return false;
    }

    /**
     * Do any processing we need to do after logging, while under the logging
     * latch.
     */
    public void postLogWork(long justLoggedLsn) 
        throws DatabaseException {
        /* by default, do nothing. */
    }

    /**
     * By default, do nothing.  This is overridden by some entries (LNs) to
     * save the last logged size
     */
    public void setLastLoggedSize(int size) {
    }
}
