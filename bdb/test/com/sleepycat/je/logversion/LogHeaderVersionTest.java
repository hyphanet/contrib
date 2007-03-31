/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogHeaderVersionTest.java,v 1.7.2.1 2007/02/01 14:50:15 cwl Exp $
 */

package com.sleepycat.je.logversion;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests log file header versioning.  This test is used in conjunction with
 * MakeLogHeaderVersionData, a main program that was used once to generate two
 * log files with maximum and minimum valued header version numbers.
 *
 * @see MakeLogHeaderVersionData
 */
public class LogHeaderVersionTest extends TestCase {

    private File envHome;

    public LogHeaderVersionTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }
    
    public void tearDown()
        throws Exception {
                
        try {
            //*
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
            //*/
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        envHome = null;
    }

    /**
     * Tests that an exception is thrown when a log header is read with a newer
     * version than the current version.  The maxversion.jdb log file is loaded
     * as a resource by this test and written as a regular log file.  When the
     * environment is opened, we expect a LogException.
     */
    public void testGreaterVersionNotAllowed()
        throws DatabaseException, IOException {

        TestUtils.loadLog(getClass(), Utils.MAX_VERSION_NAME, envHome);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(true);

        try {
            Environment env = new Environment(envHome, envConfig);
            try {
                env.close();
            } catch (Exception ignore) {}
        } catch (DatabaseException e) {
            if (e.getCause() instanceof LogException) {
                /* Got LogException as expected. */
                return;
            }
        }
        fail("Expected LogException");
    }

    /**
     * Tests that when a file is opened with a lesser version than the current
     * version, a new log file is started for writing new log entries.  This is
     * important so that the new header version is written even if no new log
     * file is needed.  If the new version were not written, an older version
     * of JE would not recognize that there had been a version change.
     */
    public void testLesserVersionNotUpdated()
        throws DatabaseException, IOException {

        TestUtils.loadLog(getClass(), Utils.MIN_VERSION_NAME, envHome);
        File logFile = new File(envHome, TestUtils.LOG_FILE_NAME);
        long origFileSize = logFile.length();

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(true);

        Environment env = new Environment(envHome, envConfig);
        env.sync();
        env.close();

        assertEquals(origFileSize, logFile.length());
    }
}
