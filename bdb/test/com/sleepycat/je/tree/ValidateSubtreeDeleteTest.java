/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ValidateSubtreeDeleteTest.java,v 1.30.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

public class ValidateSubtreeDeleteTest extends TestCase {

    private File envHome;
    private Environment env;
    private Database testDb;

    public ValidateSubtreeDeleteTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);


        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        testDb = env.openDatabase(null, "Test", dbConfig);
    }
    
    public void tearDown() throws IOException, DatabaseException {
        testDb.close();
        if (env != null) {
            try {
                env.close();
            } catch (DatabaseException E) {
            }
        }
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testBasic()
        throws Exception  {
        try {
            /* Make a 3 level tree full of data */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            byte [] testData = new byte[1];
            testData[0] = 1;
            data.setData(testData);

            Transaction txn = env.beginTransaction(null, null);
            for (int i = 0; i < 15; i ++) {
                key.setData(TestUtils.getTestArray(i));
                testDb.put(txn, key, data);
            }

            /* Should not be able to delete any of it */
            assertFalse(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(0));
            assertFalse(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(1));

            /* 
             * Should be able to delete both, the txn is aborted and the data
             * isn't there.
             */
            txn.abort();
            assertTrue(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(0));
            assertTrue(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(1));


            /*
             * Try explicit deletes.
             */
            txn = env.beginTransaction(null, null);
            for (int i = 0; i < 15; i ++) {
                key.setData(TestUtils.getTestArray(i));
                testDb.put(txn, key, data);
            }
            for (int i = 0; i < 15; i ++) {
                key.setData(TestUtils.getTestArray(i));
                testDb.delete(txn, key);
            }
            assertFalse(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(0));
            assertFalse(DbInternal.dbGetDatabaseImpl(testDb).getTree().validateDelete(1));

            // XXX, now commit the delete and compress and test that the
            // subtree is deletable. Not finished yet! Also must test deletes.
            txn.abort();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void testDuplicates()
        throws Exception  {
        try {
            /* Make a 3 level tree full of data */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            byte [] testData = new byte[1];
            testData[0] = 1;
            key.setData(testData);

            Transaction txn = env.beginTransaction(null, null);
            for (int i = 0; i < 4; i ++) {
                data.setData(TestUtils.getTestArray(i));
                testDb.put(txn, key, data);
            }

            /* Should not be able to delete any of it */
            Tree tree = DbInternal.dbGetDatabaseImpl(testDb).getTree();
            assertFalse(tree.validateDelete(0));

            /* 
             * Should be able to delete, the txn is aborted and the data
             * isn't there.
             */
            txn.abort();
            assertTrue(tree.validateDelete(0));

            /*
             * Try explicit deletes.
             */
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

}
