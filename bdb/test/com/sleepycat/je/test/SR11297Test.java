/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SR11297Test.java,v 1.7.2.1 2007/02/01 14:50:20 cwl Exp $
 */

package com.sleepycat.je.test;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 * Fix for SR11297.  When the first BIN in database was empty,
 * CursorImpl.positionFirstOrLast(true, null) was returning false, causing
 * Cursor.getFirst to return NOTFOUND.  This test reproduces that problem by
 * creating a database with the first BIN empty and the second BIN non-empty.
 *
 * <p>A specific sequence where partial compression takes place is necessary to
 * reproduce the problem.  A duplicate is added as the first entry in the first
 * BIN, then that BIN is filled and one entry is added to the next BIN.  Then
 * all records in the first BIN are deleted.  compress() is called once, which
 * deletes the duplicate tree and all entries in the first BIN, but the first
 * BIN will not be deleted until the next compression.  At that point in time,
 * getFirst failed to find the record in the second BIN.</p>
 */
public class SR11297Test extends TestCase {

    /* Minimum child entries per BIN. */
    private static int N_ENTRIES = 4;

    private static CheckpointConfig forceCheckpoint = new CheckpointConfig();
    static {
        forceCheckpoint.setForce(true);
    }

    private File envHome;
    private Environment env;

    public SR11297Test() {
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
    }

    private void openEnv()
        throws DatabaseException, IOException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setAllowCreate(true);
        /* Make as small a log as possible to save space in CVS. */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        /* Use a 100 MB log file size to ensure only one file is written. */
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 Integer.toString(100 * (1 << 20)));
        /* Force BINDelta. */
        envConfig.setConfigParam
            (EnvironmentParams.BIN_DELTA_PERCENT.getName(),
             Integer.toString(75));
        /* Force INDelete. */
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(),
             Integer.toString(N_ENTRIES));
        env = new Environment(envHome, envConfig);
    }

    private void closeEnv()
        throws DatabaseException {

        env.close();
        env = null;
    }

    public void test11297()
        throws DatabaseException, IOException {

        openEnv();

        /* Write db0 and db1. */
        for (int i = 0; i < 2; i += 1) {
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setSortedDuplicates(true);
            Database db = env.openDatabase(null, "db" + i, dbConfig);

            /* Write: {0, 0}, {0, 1}, {1, 0}, {2, 0}, {3, 0} */
            for (int j = 0; j < N_ENTRIES; j += 1) {
                db.put(null, entry(j), entry(0));
            }
            db.put(null, entry(0), entry(1));

            /* Delete everything but the last record. */
            for (int j = 0; j < N_ENTRIES - 1; j += 1) {
                db.delete(null, entry(j));
            }

            db.close();
        }

        checkFirstRecord();
        env.compress();
        checkFirstRecord();

        closeEnv();
    }

    /** 
     * First and only record in db1 should be {3,0}.
     */
    private void checkFirstRecord()
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(false);
        dbConfig.setReadOnly(true);
        dbConfig.setSortedDuplicates(true);
        Database db = env.openDatabase(null, "db1", dbConfig);
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = cursor.getFirst(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(3, value(key));
        assertEquals(0, value(data));
        cursor.close();
        db.close();
    }

    static DatabaseEntry entry(int val) {

        byte[] data = new byte[] { (byte) val };
        return new DatabaseEntry(data);
    }

    static int value(DatabaseEntry entry) {

        byte[] data = entry.getData();
        if (data.length != 1) {
            throw new IllegalStateException("len=" + data.length);
        }
        return data[0];
    }
}
