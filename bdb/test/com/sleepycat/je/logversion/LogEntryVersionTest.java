/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogEntryVersionTest.java,v 1.12.2.2 2007/03/31 22:06:14 mark Exp $
 */

package com.sleepycat.je.logversion;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.TestUtilLogReader;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests that prior versions of log entries can be read.  This test is used in
 * conjunction with MakeLogEntryVersionData, a main program that was used once
 * to generate log files named je-x.y.z.jdb, where x.y.z is the version of JE
 * used to create the log.  When a new test log file is created with
 * MakeLogEntryVersionData, add a new test_x_y_z() method to this class.
 *
 * @see MakeLogEntryVersionData
 */
public class LogEntryVersionTest extends TestCase {

    private File envHome;
    private Environment env;
    private Database db1;
    private Database db2;

    public LogEntryVersionTest() {
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
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
                
        try {
            //*
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
            //*/
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        envHome = null;
        env = null;
        db1 = null;
        db2 = null;
    }

    private void openEnv(String jeVersion, boolean readOnly)
        throws DatabaseException, IOException {

        /* Copy log file resource to log file zero. */
        String resName = "je-" + jeVersion + ".jdb";
        TestUtils.loadLog(getClass(), resName, envHome);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(false);
        envConfig.setReadOnly(readOnly);
        envConfig.setTransactional(true);
        env = new Environment(envHome, envConfig);
        
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(false);
        dbConfig.setReadOnly(readOnly);
        dbConfig.setSortedDuplicates(true);
        db1 = env.openDatabase(null, Utils.DB1_NAME, dbConfig);
        db2 = env.openDatabase(null, Utils.DB2_NAME, dbConfig);
    }

    private void closeEnv()
        throws DatabaseException {

        db1.close();
        db1 = null;
        db2.close();
        db2 = null;
        env.close();
        env = null;
    }

    public void test_1_5_4()
        throws DatabaseException, IOException {

        doTest("1.5.4");
    }

    public void test_1_7_0()
        throws DatabaseException, IOException {

        doTest("1.7.0");
    }

    /**
     * JE 2.0: FileHeader version 3.
     */
    public void test_2_0_0()
        throws DatabaseException, IOException {

        doTest("2.0.0");
    }

    /**
     * JE 3.0.12: FileHeader version 4.
     */
    public void test_3_0_12()
        throws DatabaseException, IOException {

        /*
         * The test was not run until JE 3.1.25, but no format changes were
         * made between 3.0.12 and 3.1.25.
         */
        doTest("3.1.25");
    }

    /**
     * JE 3.2.22: FileHeader version 5.
     */
    public void test_3_2_22()
        throws DatabaseException, IOException {

        doTest("3.2.22");
    }

    private void doTest(String jeVersion)
        throws DatabaseException, IOException {

        openEnv(jeVersion, true /*readOnly*/);

        VerifyConfig verifyConfig = new VerifyConfig();
        verifyConfig.setAggressive(true);
        assertTrue(env.verify(verifyConfig, System.err));

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;

        /* Database 1 is empty because the txn was aborted. */
        Cursor cursor = db1.openCursor(null, null);
        try {
            status = cursor.getFirst(key, data, null);
            assertEquals(OperationStatus.NOTFOUND, status);
        } finally {
            cursor.close();
        }

        /* Database 2 has one record: {3, 0} */
        cursor = db2.openCursor(null, null);
        try {
            status = cursor.getFirst(key, data, null);
            assertEquals(OperationStatus.SUCCESS, status);
            assertEquals(3, Utils.value(key));
            assertEquals(0, Utils.value(data));
            status = cursor.getNext(key, data, null);
            assertEquals(OperationStatus.NOTFOUND, status);
        } finally {
            cursor.close();
        }

        /* Verify log entry types. */
        String resName = "je-" + jeVersion + ".txt";
        LineNumberReader textReader = new LineNumberReader
            (new InputStreamReader(getClass().getResourceAsStream(resName)));
        TestUtilLogReader logReader = new TestUtilLogReader
            (DbInternal.envGetEnvironmentImpl(env));
        while (logReader.readNextEntry()) {
            String foundType = logReader.getEntryType().toString();
            String expectedType = textReader.readLine();
            assertNotNull
                ("No more expected types after line " +
                 textReader.getLineNumber() + " but found: " + foundType,
                 expectedType);
            assertEquals
                ("At line " + textReader.getLineNumber(),
                 expectedType.substring(0, expectedType.indexOf('/')),
                 foundType.substring(0, foundType.indexOf('/')));
        }
        String remainingLine = textReader.readLine();
        assertNull
            ("Another expected type at line " +
             textReader.getLineNumber() + " but no more found",
             remainingLine);

        assertTrue(env.verify(verifyConfig, System.err));
        closeEnv();

        /*
         * Do enough inserts to cause a split and perform some other write
         * operations for good measure.
         */
        openEnv(jeVersion, false /*readOnly*/);
        for (int i = -127; i < 127; i += 1) {
            status = db2.put(null, Utils.entry(i), Utils.entry(0));
            assertEquals(OperationStatus.SUCCESS, status);
        }
        /* Do updates. */
        for (int i = -127; i < 127; i += 1) {
            status = db2.put(null, Utils.entry(i), Utils.entry(1));
            assertEquals(OperationStatus.SUCCESS, status);
        }
        /* Do deletes. */
        for (int i = -127; i < 127; i += 1) {
            status = db2.delete(null, Utils.entry(i));
            assertEquals(OperationStatus.SUCCESS, status);
        }
        /* Same for duplicates. */
        for (int i = -127; i < 127; i += 1) {
            status = db2.put(null, Utils.entry(0), Utils.entry(i));
            assertEquals(OperationStatus.SUCCESS, status);
        }
        for (int i = -127; i < 127; i += 1) {
            status = db2.put(null, Utils.entry(0), Utils.entry(i));
            assertEquals(OperationStatus.SUCCESS, status);
        }
        cursor = db2.openCursor(null, null);
        try {
            status = cursor.getFirst(key, data, null);
            while (status == OperationStatus.SUCCESS) {
                status = cursor.delete();
                assertEquals(OperationStatus.SUCCESS, status);
                status = cursor.getNext(key, data, null);
            }
        } finally {
            cursor.close();
        }

        assertTrue(env.verify(verifyConfig, System.err));
        closeEnv();
    }
}
