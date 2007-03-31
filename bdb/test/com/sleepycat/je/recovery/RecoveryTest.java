/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryTest.java,v 1.57.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Comparator;
import java.util.Hashtable;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class RecoveryTest extends RecoveryTestBase {

    /**
     * Basic insert, delete data.
     */
    public void testBasic()
        throws Throwable {

        doBasic(true);
    }

    /**
     * Basic insert, delete data with BtreeComparator
     */
    public void testBasicRecoveryWithBtreeComparator()
        throws Throwable {

	btreeComparisonFunction = new BtreeComparator(true);
        doBasic(true);
    }

    /**
     * Test that put(OVERWRITE) works correctly with duplicates.
     */
    public void testDuplicateOverwrite()
	throws Throwable {

        createEnvAndDbs(1 << 10, false, NUM_DBS);
        try {
            Hashtable expectedData = new Hashtable();

	    Transaction txn = env.beginTransaction(null, null);
	    DatabaseEntry key = new DatabaseEntry("aaaaa".getBytes());
	    DatabaseEntry data1 = new DatabaseEntry("dddddddddd".getBytes());
	    DatabaseEntry data2 = new DatabaseEntry("eeeeeeeeee".getBytes());
	    DatabaseEntry data3 = new DatabaseEntry("ffffffffff".getBytes());
	    Database db = dbs[0];
	    assertEquals(OperationStatus.SUCCESS,
			 db.put(null, key, data1));
	    addExpectedData(expectedData, 0, key, data1, true);
	    assertEquals(OperationStatus.SUCCESS,
			 db.put(null, key, data2));
	    addExpectedData(expectedData, 0, key, data2, true);
	    assertEquals(OperationStatus.SUCCESS,
			 db.put(null, key, data3));
	    addExpectedData(expectedData, 0, key, data3, true);
	    assertEquals(OperationStatus.SUCCESS,
			 db.put(null, key, data3));
	    txn.commit();
	    closeEnv();

	    recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Basic insert, delete data.
     */
    public void testBasicFewerCheckpoints()
        throws Throwable {

        doBasic(false);
    }

    public void testSR8984Part1()
        throws Throwable {

	doTestSR8984Work(true);
    }

    public void testSR8984Part2()
        throws Throwable {

	doTestSR8984Work(false);
    }

    private void doTestSR8984Work(boolean sameKey)
	throws DatabaseException {

	final int NUM_EXTRA_DUPS = 150;
	EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        /* Make an environment and open it */
        envConfig.setTransactional(false);
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
				 "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
	envConfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
				 "false");

	envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
	Database db = env.openDatabase(null, "testSR8984db", dbConfig);

	DatabaseEntry key = new DatabaseEntry("k1".getBytes());
	DatabaseEntry data = new DatabaseEntry("d1".getBytes());
	assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
	assertEquals(OperationStatus.SUCCESS, db.delete(null, key));
        
	if (!sameKey) {
	    data.setData("d2".getBytes());
	}
	/* Cause a dup tree of some depth to be created. */
	assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
	for (int i = 3; i < NUM_EXTRA_DUPS; i++) {
	    data.setData(("d" + i).getBytes());
	    assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
	}

	data.setData("d1".getBytes());

	Cursor c = db.openCursor(null, null);
	assertEquals(OperationStatus.SUCCESS,
		     c.getFirst(key, data, LockMode.DEFAULT));

	c.close();
	db.close();
        
        /* Force an abrupt close so there is no checkpoint at the end. */
        closeEnv();
        env = new Environment(envHome, envConfig);
	db = env.openDatabase(null, "testSR8984db", dbConfig);
	c = db.openCursor(null, null);
	assertEquals(OperationStatus.SUCCESS,
		     c.getFirst(key, data, LockMode.DEFAULT));
	assertEquals(NUM_EXTRA_DUPS - 2, c.count());
	c.close();
	db.close();
	env.close();
    }

    /**
     * Insert data, delete data into several dbs.
     */
    public void doBasic(boolean runCheckpointerDaemon)
        throws Throwable {

        createEnvAndDbs(1 << 20, runCheckpointerDaemon, NUM_DBS);
        int numRecs = NUM_RECS;

        try {
            // Set up an repository of expected data
            Hashtable expectedData = new Hashtable();
            
            // insert all the data
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1, true, NUM_DBS);
            txn.commit();

            // delete all the even records
            txn = env.beginTransaction(null, null);
            deleteData(txn, expectedData, false, true, NUM_DBS);
            txn.commit();

            // modify all the records
            txn = env.beginTransaction(null, null);
            modifyData(txn, NUM_RECS/2, expectedData, 1, true, NUM_DBS);
            txn.commit();

            closeEnv();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Insert data, delete all data into several dbs.
     */
    public void testBasicDeleteAll()
        throws Throwable {

        createEnvAndDbs(1024, true, NUM_DBS);
        int numRecs = NUM_RECS;
        try {
            // Set up an repository of expected data
            Hashtable expectedData = new Hashtable();

            // insert all the data
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1, true, NUM_DBS);
            txn.commit();

            // modify half the records
            txn = env.beginTransaction(null, null);
            modifyData(txn, numRecs/2, expectedData, 1, true, NUM_DBS);
            txn.commit();

            // delete all the records
            txn = env.beginTransaction(null, null);
            deleteData(txn, expectedData, true, true, NUM_DBS);
            txn.commit();

            closeEnv();

            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }

    protected static class BtreeComparator implements Comparator {
	protected boolean ascendingComparison = true;

	public BtreeComparator() {
	}

	protected BtreeComparator(boolean ascendingComparison) {
	    this.ascendingComparison = ascendingComparison;
	}

	public int compare(Object o1, Object o2) {
	    byte[] arg1;
	    byte[] arg2;
	    if (ascendingComparison) {
		arg1 = (byte[]) o1;
		arg2 = (byte[]) o2;
	    } else {
		arg1 = (byte[]) o2;
		arg2 = (byte[]) o1;
	    }
	    int a1Len = arg1.length;
	    int a2Len = arg2.length;

	    int limit = Math.min(a1Len, a2Len);

	    for (int i = 0; i < limit; i++) {
		byte b1 = arg1[i];
		byte b2 = arg2[i];
		if (b1 == b2) {
		    continue;
		} else {
		    /* Remember, bytes are signed, so convert to shorts so that
		       we effectively do an unsigned byte comparison. */
		    short s1 = (short) (b1 & 0x7F);
		    short s2 = (short) (b2 & 0x7F);
		    if (b1 < 0) {
			s1 |= 0x80;
		    }
		    if (b2 < 0) {
			s2 |= 0x80;
		    }
		    return (s1 - s2);
		}
	    }

	    return (a1Len - a2Len);
	}
    }
}
