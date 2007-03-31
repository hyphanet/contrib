/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RMWLockingTest.java,v 1.6.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 * Use LockMode.RMW and verify that the FileSummaryLNs accurately reflect only
 * those LNs that have been made obsolete.
 */
public class RMWLockingTest extends TestCase {
    
    private static final int NUM_RECS = 5;

    private File envHome;
    private Environment env;
    private Database db;
    private DatabaseEntry key;
    private DatabaseEntry data;

    public RMWLockingTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        try {
            if (db != null) {
                db.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
                
        try {
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        db = null;
        env = null;
        envHome = null;
    }

    public void testBasic()
        throws DatabaseException {

        init();
        insertRecords();
        rmwModify();

        UtilizationProfile up =
            DbInternal.envGetEnvironmentImpl(env).getUtilizationProfile();

        /* 
         * Checkpoint the environment to flush all utilization tracking
         * information before verifying.
         */
        CheckpointConfig ckptConfig = new CheckpointConfig();
        ckptConfig.setForce(true);
        env.checkpoint(ckptConfig);

        assertTrue(up.verifyFileSummaryDatabase());
    }

    /**
     * Tests that we can load a log file containing offsets that correspond to
     * non-obsolete LNs.  The bad log file was created using testBasic run
     * against JE 2.0.54.  It contains version 1 FSLNs, one of which has an
     * offset which is not obsolete.
     */
    public void testBadLog()
        throws DatabaseException, IOException {

        /* Copy a log file with bad offsets to log file zero. */
        String resName = "rmw_bad_offsets.jdb";
        TestUtils.loadLog(getClass(), resName, envHome);

        /* Open the log we just copied. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(false);
        envConfig.setReadOnly(true);
        env = new Environment(envHome, envConfig);

        /*
         * Verify the UP of the bad log.  Prior to adding the code in
         * FileSummaryLN.postFetchInit that discards version 1 offsets, this
         * assertion failed.
         */
        UtilizationProfile up =
            DbInternal.envGetEnvironmentImpl(env).getUtilizationProfile();
        assertTrue(up.verifyFileSummaryDatabase());

        env.close();
        env = null;
    }

    private void init() 
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }

    /* Insert records. */
    private void insertRecords() 
        throws DatabaseException {

        key = new DatabaseEntry();
        data = new DatabaseEntry();

        IntegerBinding.intToEntry(100, data);

        for (int i = 0; i < NUM_RECS; i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }
    }

    /* lock two records with RMW, only modify one. */
    private void rmwModify() 
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        IntegerBinding.intToEntry(0, key);
        assertEquals(OperationStatus.SUCCESS,
                     db.get(txn, key, data, LockMode.RMW));
        IntegerBinding.intToEntry(1, key);
        assertEquals(OperationStatus.SUCCESS,
                     db.get(txn, key, data, LockMode.RMW));

        IntegerBinding.intToEntry(200, data);
        assertEquals(OperationStatus.SUCCESS,
                     db.put(txn, key, data));
        txn.commit();
    }
}
