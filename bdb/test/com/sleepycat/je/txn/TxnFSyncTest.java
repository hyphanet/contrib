/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnFSyncTest.java,v 1.10.2.1 2007/02/01 14:50:22 cwl Exp $
 */

package com.sleepycat.je.txn;

import junit.framework.Test;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.test.TxnTestCase;
import com.sleepycat.je.util.TestUtils;

/*
 * Make sure that transactions sync to disk. Mimic a crash by failing to
 * close the environment and explicitly flush the log manager. If we haven't
 * properly written and synced data to disk, we'll have unflushed data and
 * we won't find the expected data in the log.
 *
 * Note that this test is run with the TxnTestCase framework and will 
 * be exercised with app-created and autocommit txns.
 */ 

public class TxnFSyncTest extends TxnTestCase {

    private static final int NUM_RECS = 5;

    private static EnvironmentConfig envConfig = TestUtils.initEnvConfig();
    static {
        envConfig.setAllowCreate(true);
        setupEnvConfig(envConfig);
    }

    private static void setupEnvConfig(EnvironmentConfig envConfig) {
        envConfig.setTransactional(true);
        envConfig.setConfigParam(
            EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
    }
 
    public static Test suite() {
        /* Run these tests with user and autocommit txns. */
        return txnTestSuite(TxnFSyncTest.class,
                            envConfig,
                            new String [] {TxnTestCase.TXN_USER,
                                           TxnTestCase.TXN_AUTO});
    }
    
    public void testFSyncButNoClose() 
        throws Exception {

        try {
            /* Create a database. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(isTransactional);
            dbConfig.setAllowCreate(true);
            Transaction txn = txnBegin();
            Database db = env.openDatabase(txn, "foo", dbConfig);

            /* Insert data. */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            for (int i = 0; i < NUM_RECS; i++) {
                Integer val = new Integer(i);
                key.setData(val.toString().getBytes());
                data.setData(val.toString().getBytes());

                assertEquals(OperationStatus.SUCCESS,
                             db.putNoOverwrite(txn, key, data));
            }
            txnCommit(txn);
            
            /* 
             * Now throw away this environment WITHOUT flushing the log
             * manager. We do need to release the environment file lock
             * and all file handles so we can recover in this test and
             * run repeated test cases within this one test program.
             */
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            envImpl.getFileManager().clear(); // release file handles
            envImpl.getFileManager().close(); // release file lock
            env = null;
            DbEnvPool.getInstance().clear();

            /* 
             * Open the environment and database again. The database should
             * exist.
             */
            EnvironmentConfig envConfig2 = TestUtils.initEnvConfig();
            setupEnvConfig(envConfig2);
            env = new Environment(envHome, envConfig2);
            dbConfig.setAllowCreate(false);
            db = env.openDatabase(null, "foo", dbConfig);

            /* Read all the data. */
            for (int i = 0; i < NUM_RECS; i++) {
                Integer val = new Integer(i);
                key.setData(val.toString().getBytes());

                assertEquals(OperationStatus.SUCCESS,
                             db.get(null, key, data, LockMode.DEFAULT));
                /* add test of data. */
            }
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}

