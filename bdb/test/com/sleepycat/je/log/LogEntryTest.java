/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: LogEntryTest.java,v 1.14 2006/09/12 19:17:20 cwl Exp $
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
        byte testVersion = LogEntryType.LOG_IN.getVersion();
        byte testProvisionalVersion =
            LogEntryType.setProvisional(testVersion);

        /* Look it up by type name and version */
        LogEntryType foundType = LogEntryType.findType(testTypeNum,
                                                           testVersion);
        assertEquals(foundType, LogEntryType.LOG_IN);
        assertTrue(foundType.getSharedLogEntry() instanceof
                   com.sleepycat.je.log.entry.INLogEntry);

        /* Look it up by type name and provisional version */
        foundType = LogEntryType.findType(testTypeNum,
                                            testProvisionalVersion);
        assertEquals(foundType, LogEntryType.LOG_IN);
        assertTrue(foundType.getSharedLogEntry() instanceof
                   com.sleepycat.je.log.entry.INLogEntry);

        /* Get a new entry object */
        LogEntry sharedEntry = foundType.getSharedLogEntry();
        LogEntry newEntry = foundType.getNewLogEntry();
        
        assertTrue(sharedEntry != newEntry);
    }
}
