/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogEntryTest.java,v 1.20 2008/01/07 14:29:09 cwl Exp $
 */

package com.sleepycat.je.log;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.log.entry.LogEntry;

/**
 */
public class LogEntryTest extends TestCase {

    public void testEquality()
        throws DatabaseException {

        byte testTypeNum = LogEntryType.LOG_IN.getTypeNum();

        /* Look it up by type */
        LogEntryType foundType = LogEntryType.findType(testTypeNum);
        assertEquals(foundType, LogEntryType.LOG_IN);
        assertTrue(foundType.getSharedLogEntry() instanceof
                   com.sleepycat.je.log.entry.INLogEntry);

        /* Look it up by type */
        foundType = LogEntryType.findType(testTypeNum);
        assertEquals(foundType, LogEntryType.LOG_IN);
        assertTrue(foundType.getSharedLogEntry() instanceof
                   com.sleepycat.je.log.entry.INLogEntry);

        /* Get a new entry object */
        LogEntry sharedEntry = foundType.getSharedLogEntry();
        LogEntry newEntry = foundType.getNewLogEntry();

        assertTrue(sharedEntry != newEntry);
    }
}
