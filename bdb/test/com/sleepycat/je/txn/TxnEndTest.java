/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnEndTest.java,v 1.67.2.1 2007/02/01 14:50:22 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/*
 * Test transaction aborts and commits.
 */
public class TxnEndTest extends TestCase {
    private static final int NUM_DBS = 1;
    private Environment env;
    private File envHome;
    private Database[] dbs;
    private Cursor[] cursors;

    public TxnEndTest()
	throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() 
        throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
      
        /*
         * Run environment without in compressor on so we can check the
         * compressor queue in a deterministic way.
         */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam(EnvironmentParams.
				 ENV_RUN_INCOMPRESSOR.getName(),
                                 "false");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.out.println("tearDown: " + e);
            }
        }
        env = null;

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    private void createDbs()
        throws DatabaseException {	

        // Make databases
        dbs = new Database[NUM_DBS];
        cursors = new Cursor[NUM_DBS];

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        for (int i = 0; i < NUM_DBS; i++) {
            dbs[i] = env.openDatabase(null, "testDB" + i, dbConfig);
        }
    }

    private void closeAll()
        throws DatabaseException {	

        for (int i = 0; i < NUM_DBS; i++) {
            dbs[i].close();
        }
        dbs = null;
        env.close();
        env = null;
    }

    /**
     * Create cursors with this owning transaction
     */
    private void createCursors(Transaction txn) 
        throws DatabaseException {

        for (int i = 0; i < cursors.length; i++) {
            cursors[i] = dbs[i].openCursor(txn, null);
        }
    }

    /**
     * Close the current set of cursors
     */
    private void closeCursors()
        throws DatabaseException {

        for (int i = 0; i < cursors.length; i++) {
            cursors[i].close();
        }
    }
    
    /**
     * Insert keys from i=start; i <end using a cursor
     */
    private void cursorInsertData(int start, int end)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < NUM_DBS; i++) {
            for (int d = start; d < end; d++) {
                key.setData(TestUtils.getTestArray(d));
                data.setData(TestUtils.getTestArray(d));
                cursors[i].put(key, data);
            }
        }
    }
    /**
     * Insert keys from i=start; i < end using a db
     */
    private void dbInsertData(int start, int end, Transaction txn)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < NUM_DBS; i++) {
            for (int d = start; d < end; d++) {
                key.setData(TestUtils.getTestArray(d));
                data.setData(TestUtils.getTestArray(d));
                dbs[i].put(txn, key, data);
            }
        }
    }

    /**
     * Modify keys from i=start; i <end
     */
    private void cursorModifyData(int start, int end, int valueOffset)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < NUM_DBS; i++) {
            OperationStatus status =
		cursors[i].getFirst(key, data, LockMode.DEFAULT);
            for (int d = start; d < end; d++) {
                assertEquals(OperationStatus.SUCCESS, status);
                byte[] changedVal =
                    TestUtils.getTestArray(d + valueOffset);
                data.setData(changedVal);
                cursors[i].putCurrent(data);
                status = cursors[i].getNext(key, data, LockMode.DEFAULT);
            }
        }
    }

    /**
     * Delete records from i = start; i < end.
     */
    private void cursorDeleteData(int start, int end)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry foundData = new DatabaseEntry();
        for (int i = 0; i < NUM_DBS; i++) {
            for (int d = start; d < end; d++) {
                byte[] searchValue =
                    TestUtils.getTestArray(d);
                key.setData(searchValue);
                OperationStatus status =
		    cursors[i].getSearchKey(key, foundData, LockMode.DEFAULT);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(OperationStatus.SUCCESS, cursors[i].delete());
            }
        }
    }

    /**
     * Delete records with a db.
     */
    private void dbDeleteData(int start, int end, Transaction txn)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        for (int i = 0; i < NUM_DBS; i++) {
            for (int d = start; d < end; d++) {
                byte[] searchValue =
                    TestUtils.getTestArray(d);
                key.setData(searchValue);
                dbs[i].delete(txn, key);
            }
        }
    }

    /**
     * Check that there are numKeys records in each db, and their value
     * is i + offset.
     */
    private void verifyData(int numKeys, int valueOffset)
        throws DatabaseException {

        for (int i = 0; i < NUM_DBS; i++) {
            /* Run verify */
            DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(dbs[i]);
            assertTrue(dbImpl.verify(new VerifyConfig(),
                                      dbImpl.getEmptyStats()));

            Cursor verifyCursor =
		dbs[i].openCursor(null, CursorConfig.READ_UNCOMMITTED);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status =
		verifyCursor.getFirst(key, data, LockMode.DEFAULT);
            for (int d = 0; d < numKeys; d++) {
                assertEquals("key=" + d, OperationStatus.SUCCESS, status);
                byte[] expected = TestUtils.getTestArray(d + valueOffset);
                assertTrue(Arrays.equals(expected, key.getData()));
                assertTrue("Expected= " + TestUtils.dumpByteArray(expected) +
                           " saw=" + TestUtils.dumpByteArray(data.getData()),
                           Arrays.equals(expected, data.getData()));
                status = verifyCursor.getNext(key, data, LockMode.DEFAULT);
            }
            // should be the end of this database
            assertTrue("More data than expected",
		       (status != OperationStatus.SUCCESS)); 
            verifyCursor.close();
        }
    }

    /**
     * Test basic commits, aborts with cursors
     */
    public void testBasicCursor()
        throws Throwable {

        try {
            int numKeys = 7;
            createDbs();

            // Insert more data with a user transaction, commit
            Transaction txn = env.beginTransaction(null, null);
            createCursors(txn);
            cursorInsertData(0, numKeys*2);
            closeCursors();
            txn.commit();
            verifyData(numKeys*2, 0);

            // Insert more data, abort, check that data is unchanged
            txn = env.beginTransaction(null, null);
            createCursors(txn);
            cursorInsertData(numKeys*2, numKeys*3);
            closeCursors();
            txn.abort();
            verifyData(numKeys*2, 0);

            /*
             * Check the in compressor queue, we should have some number of
             * bins on. If the queue size is 0, then check the processed stats,
             * the in compressor thread may have already woken up and dealt
             * with the entries.
             */
            EnvironmentStats envStat = env.getStats(TestUtils.FAST_STATS);
            int queueSize = envStat.getInCompQueueSize();
            assertTrue(queueSize > 0);

            // Modify data, abort, check that data is unchanged
            txn = env.beginTransaction(null, null);
            createCursors(txn);
            cursorModifyData(0, numKeys * 2, 1);
            closeCursors();
            txn.abort();
            verifyData(numKeys*2, 0);

            // Delete data, abort, check that data is still there
            txn = env.beginTransaction(null, null);
            createCursors(txn);
            cursorDeleteData(numKeys+1, numKeys*2);
            closeCursors();
            txn.abort();
            verifyData(numKeys*2, 0);
            // Check the in compressor queue, nothing should be loaded
            envStat = env.getStats(TestUtils.FAST_STATS);
            assertEquals(queueSize, envStat.getInCompQueueSize());

            // Delete data, commit, check that data is gone
            txn = env.beginTransaction(null, null);
            createCursors(txn);
            cursorDeleteData(numKeys, numKeys*2);
            closeCursors();
            txn.commit();
            verifyData(numKeys, 0);

            // Check the inCompressor queue, there should be more entries.
            envStat = env.getStats(TestUtils.FAST_STATS);
            assertTrue(envStat.getInCompQueueSize() > queueSize);

            closeAll();

        } catch (Throwable t) {
            // print stacktrace before attempt to run tearDown
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test db creation and deletion.
     */
    public void testTxnClose()
        throws DatabaseException {

        createDbs();
        Transaction txn = env.beginTransaction(null, null);
        createCursors(txn);
        try {
            txn.commit();
            fail("Commit should fail");
        } catch (DatabaseException e) {
        }
	closeCursors();
        closeAll();
    }

    class CascadingAbortTestJUnitThread extends JUnitThread {
	Transaction txn = null;
	Database db = null;

	CascadingAbortTestJUnitThread(Transaction txn,
				      Database db) {
	    super("testCascadingAborts");
	    this.txn = txn;
	    this.db = db;
	}
    }

    /**
     * Test cascading aborts in the face of deletes.
     * [work in progress: cwl 1/15/04]
     */
    public void xtestCascadingAborts()
	throws Throwable {

        Database db = null;

	try {
	    DatabaseConfig dbConfig = new DatabaseConfig();
	    dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
	    db = env.openDatabase(null, "testDB", dbConfig);

	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry data = new DatabaseEntry();

	    Transaction txn = env.beginTransaction(null, null);
	    key.setData("abb".getBytes());
	    data.setData("def".getBytes());
	    //db.put(txn, key, data, null);
	    key.setData("abc".getBytes());
	    data.setData("def".getBytes());
	    db.put(txn, key, data);
	    txn.commit();

	    //DbInternal.dbGetDatabaseImpl(db).getTree().dump();

	    Transaction txn1 = env.beginTransaction(null, null);
	    Transaction txn2 = env.beginTransaction(null, null);

	    CascadingAbortTestJUnitThread tester1 =
		new CascadingAbortTestJUnitThread(txn2, db) {
		    public void testBody()
			throws Throwable {

			Cursor c = db.openCursor(txn, null);
			DatabaseEntry data = new DatabaseEntry();
			try {
			    Thread.yield();
			    DatabaseEntry key = new DatabaseEntry();
			    key.setData("abc".getBytes());
			    OperationStatus status;
			    status =
				c.getSearchKeyRange(key, data, LockMode.DEFAULT);
			    status = c.delete();
			} catch (Throwable T) {
			    T.printStackTrace();
			} finally {
			    c.close();
			}
		    }
		};

	    tester1.start();
	    Thread.yield();
	    key.setData("abc".getBytes());
	    OperationStatus status;
	    status = db.delete(txn1, key);

	    txn1.abort();
	    Thread.yield();

	    txn2.abort();
	    tester1.finishTest();

	    //DbInternal.dbGetDatabaseImpl(db).getTree().dump();

	    if (false) {
		db.close();
		env.close();
		EnvironmentConfig envConfig = TestUtils.initEnvConfig();
		envConfig.setTransactional(true);
		envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
					 "6");
		envConfig.setConfigParam(EnvironmentParams.
					 ENV_RUN_INCOMPRESSOR.
					 getName(),
					 "false");
		envConfig.setAllowCreate(true);
		env = new Environment(envHome, envConfig);
		db = env.openDatabase(null, "testDB", dbConfig);
	    }

	    txn = env.beginTransaction(null, null);
	    System.out.println(db.getSearchBoth(txn, key, data,
                                                LockMode.DEFAULT));
	    txn.commit();
	} catch (Throwable T) {
	    T.printStackTrace();
	} finally {
	    db.close();
	}
    }

    /**
     * Test use through db.
     */
    public void testBasicDb()
        throws Throwable {

        try {
            TransactionStats stats =
                env.getTransactionStats(TestUtils.FAST_STATS);
            assertEquals(0, stats.getNAborts());
            int initialCommits = 1; // 1 commits for adding UP database
            assertEquals(initialCommits, stats.getNCommits());

            int numKeys = 7;
            createDbs();

            // Insert data with autocommit
            dbInsertData(0, numKeys, null);
            verifyData(numKeys, 0);

            // Insert data with a txn
            Transaction txn = env.beginTransaction(null, null);
            dbInsertData(numKeys, numKeys*2, txn);
            txn.commit();
            verifyData(numKeys*2, 0);

            stats = env.getTransactionStats(TestUtils.FAST_STATS);
            assertEquals(0, stats.getNAborts());
            assertEquals((initialCommits + 1 +  // 1 explicit commit above
                          (1 * NUM_DBS) +       // 1 per create/open
                          (numKeys*NUM_DBS)),   // 1 per record, using autotxn
			 stats.getNCommits());

            // delete data with a txn, abort
            txn = env.beginTransaction(null, null);
            dbDeleteData(numKeys, numKeys * 2, txn);
            verifyData(numKeys, 0);  // verify w/dirty read    
            txn.abort();
            
            closeAll();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test db creation and deletion
     */

    public void testDbCreation()
        throws DatabaseException {

        Transaction txnA = env.beginTransaction(null, null);
        Transaction txnB = env.beginTransaction(null, null);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        Database dbA =
	    env.openDatabase(txnA, "foo", dbConfig);

        // Try to see this database with another txn -- we should not see it

        dbConfig.setAllowCreate(false);

        try {
            txnB.setLockTimeout(1000);

		env.openDatabase(txnB, "foo", dbConfig);
            fail("Shouldn't be able to open foo");
        } catch (DatabaseException e) {
        }
	/* txnB must be aborted since openDatabase timed out. */
	txnB.abort();

        // Open this database with the same txn and another handle
        Database dbC =
	    env.openDatabase(txnA, "foo", dbConfig);
        
        // Now commit txnA and txnB should be able to open this.
        txnA.commit();
	txnB = env.beginTransaction(null, null);
        Database dbB =
	    env.openDatabase(txnB, "foo", dbConfig);
        txnB.commit();

        // XXX, test db deletion

        dbA.close();
        dbB.close();
        dbC.close();
    }

    /* Test that the transaction is unsable about a close. */
    public void testClose()
        throws DatabaseException {

        Transaction txnA = env.beginTransaction(null, null);
        txnA.commit();

        try {
            env.openDatabase(txnA, "foo", null);
            fail("Should not be able to use a closed exception");
        } catch (DatabaseException expected) {
        }
    }

}
