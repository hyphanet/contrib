/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbHandleLockTest.java,v 1.23.2.1 2007/02/01 14:50:04 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.util.TestUtils;

public class DbHandleLockTest extends TestCase {
    private File envHome;
    private Environment env;

    public DbHandleLockTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
    }
    
    public void tearDown()
        throws Exception {

        try {
            /* Close in case we hit an exception and didn't close */
            env.close();
        } catch (DatabaseException e) {
            /* Ok if already closed */
        }

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testOpenHandle()
        throws Throwable {

        try {
            Transaction txnA =
		env.beginTransaction(null, TransactionConfig.DEFAULT);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database db = env.openDatabase(txnA, "foo", dbConfig);

            /* 
	     * At this point, we expect a write lock on the NameLN (the handle
	     * lock).
             */
            LockStats lockStat = env.getLockStats(null);
            assertEquals(1, lockStat.getNTotalLocks());
            assertEquals(1, lockStat.getNWriteLocks());
            assertEquals(0, lockStat.getNReadLocks());

            txnA.commit();
            lockStat = env.getLockStats(null);
            assertEquals(1, lockStat.getNTotalLocks());
            assertEquals(0, lockStat.getNWriteLocks());
            assertEquals(1, lockStat.getNReadLocks());

            /* Updating the root from another txn should be possible. */
            insertData(10, db);
            db.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testSR12068()
	throws Throwable {

	try {
            Transaction txnA =
		env.beginTransaction(null, TransactionConfig.DEFAULT);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database db = env.openDatabase(txnA, "foo", dbConfig);
	    db.close();

            dbConfig.setExclusiveCreate(true);
	    try {
		db = env.openDatabase(txnA, "foo", dbConfig);
		fail("should throw database exeception");
	    } catch (DatabaseException DE) {
		/* expected Database already exists. */
	    }
            dbConfig.setAllowCreate(false);
            dbConfig.setExclusiveCreate(false);
	    db = env.openDatabase(txnA, "foo", dbConfig);
	    db.close();
	    txnA.commit();
	    txnA = env.beginTransaction(null, TransactionConfig.DEFAULT);
	    env.removeDatabase(txnA, "foo");
	    txnA.commit();
	} catch (Throwable T) {
	    T.printStackTrace();
	    throw T;
	}
    }

    private void insertData(int numRecs, Database db) 
        throws Throwable {

        for (int i = 0; i < numRecs; i++) {
            DatabaseEntry key = new DatabaseEntry(TestUtils.getTestArray(i));
            DatabaseEntry data = new DatabaseEntry(TestUtils.getTestArray(i));
            assertEquals(OperationStatus.SUCCESS,
			 db.put(null, key, data));
        }
    }
}
