/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: FileManagerTestUtils.java,v 1.6 2006/10/30 21:14:46 bostic Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;

public class FileManagerTestUtils {
    public static void createLogFile(FileManager fileManager,
    	                             EnvironmentImpl envImpl,
    	                             long logFileSize)
        throws DatabaseException, IOException {

        LogBuffer logBuffer = new LogBuffer(50, envImpl);
        logBuffer.getDataBuffer().flip();
        fileManager.bumpLsn(logFileSize - FileManager.firstLogEntryOffset());
        logBuffer.registerLsn(fileManager.getLastUsedLsn());
        fileManager.writeLogBuffer(logBuffer);
        fileManager.syncLogEndAndFinishFile();
    }
}

