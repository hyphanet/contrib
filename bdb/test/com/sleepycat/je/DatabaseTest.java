/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DatabaseTest.java,v 1.103.2.3 2007/06/13 13:58:40 mark Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.util.TestUtils;

/**
 * Basic database operations, excluding configuration testing.
 */
public class DatabaseTest extends TestCase {
    private static final boolean DEBUG = false;
    private static final int NUM_RECS = 257;
    private static final int NUM_DUPS = 10;

    private File envHome;
    private Environment env;

    public DatabaseTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        try {
            /* Close in case we hit an exception and didn't close */
            if (env != null) {
		env.close();
            }
        } catch (DatabaseException e) {
            /* Ok if already closed */
        }
        env = null; // for JUNIT, to reduce memory usage when run in a suite.
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Make sure we can't create a transactional cursor on a non-transactional 
     * database.
     */
    public void testCursor() 
        throws Exception {
        
        Environment txnalEnv = null;
        Database nonTxnalDb = null;
        Cursor txnalCursor = null;
        Transaction txn = null;

        try {

            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            txnalEnv = new Environment(envHome, envConfig);

            // Make a db and open it
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(false);
            nonTxnalDb = txnalEnv.openDatabase(null, "testDB", dbConfig);

            // We should not be able to open a txnal cursor.
            txn = txnalEnv.beginTransaction(null, null);
            try {
                txnalCursor = nonTxnalDb.openCursor(txn, null);
                fail("Openin a txnal cursor on a nontxnal db is invalid.");
            } catch (DatabaseException e) {
                // expected
            }
        } finally {
            if (txn != null) {
                txn.abort();
            }
            if (txnalCursor != null) {
                txnalCursor.close();
            }
            if (nonTxnalDb != null) {
                nonTxnalDb.close();
            }
            if (txnalEnv != null) {
                txnalEnv.close();
            }

        }
    }

    public void testPutExisting()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.get(txn, key, getData, LockMode.DEFAULT));
                assertEquals(0, Key.compareKeys(data.getData(),
						getData.getData(), null));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                assertEquals(OperationStatus.SUCCESS, myDb.getSearchBoth
                             (txn, key, getData, LockMode.DEFAULT));
                assertEquals(0, Key.compareKeys(data.getData(),
						getData.getData(), null));
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /*
     * Test that zero length data always returns the same (static) byte[].
     */
    public void testZeroLengthData()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
	    byte[] appZLBA = new byte[0];
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(appZLBA);
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.get(txn, key, getData, LockMode.DEFAULT));
		assertFalse(getData.getData() == appZLBA);
		assertTrue(getData.getData() ==
			   LogUtils.ZERO_LENGTH_BYTE_ARRAY);
                assertEquals(0, Key.compareKeys(data.getData(),
						getData.getData(), null));
            }
	    txn.commit();
            myDb.close();
            env.close();

	    /*
	     * Read back from the log.
	     */

            myDb = initEnvAndDb(true, false, true, false, null);
            key = new DatabaseEntry();
            data = new DatabaseEntry();
            getData = new DatabaseEntry();
	    txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.get(txn, key, getData, LockMode.DEFAULT));
		assertTrue(getData.getData() ==
			   LogUtils.ZERO_LENGTH_BYTE_ARRAY);
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDeleteNonDup()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
            }

            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(txn, key));
                OperationStatus status =
		    myDb.get(txn, key, getData, LockMode.DEFAULT);
                if (status != OperationStatus.KEYEMPTY &&
                    status != OperationStatus.NOTFOUND) {
                    fail("invalid Database.get return: " + status);
                }
                assertEquals(OperationStatus.NOTFOUND,
			     myDb.delete(txn, key));
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDeleteDup()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                for (int j = 0; j < NUM_DUPS; j++) {
                    data.setData(TestUtils.getTestArray(i + j));
                    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(txn, key, data));
                }
            }
	    txn.commit();

	    txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(txn, key));
                OperationStatus status =
		    myDb.get(txn, key, getData, LockMode.DEFAULT);
                if (status != OperationStatus.KEYEMPTY &&
                    status != OperationStatus.NOTFOUND) {
                    fail("invalid Database.get return");
                }
                assertEquals(OperationStatus.NOTFOUND,
			     myDb.delete(txn, key));
            }
	    txn.commit();
            myDb.close();
            env.close();

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /* Remove until 14264 is resolved.
    public void testDeleteDupWithData()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                for (int j = 0; j < NUM_DUPS; j++) {
                    data.setData(TestUtils.getTestArray(i + j));
                    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(txn, key, data));
                }
            }
	    txn.commit();

	    txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
		for (int j = 0; j < NUM_DUPS; j++) {
		    data.setData(TestUtils.getTestArray(i + j));
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.delete(txn, key, data));
		    OperationStatus status =
			myDb.getSearchBoth(txn, key, data, LockMode.DEFAULT);
		    if (status != OperationStatus.KEYEMPTY &&
			status != OperationStatus.NOTFOUND) {
			fail("invalid Database.get return");
		    }
		    assertEquals(OperationStatus.NOTFOUND,
				 myDb.delete(txn, key, data));
		}
            }
	    txn.commit();
            myDb.close();
            env.close();

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDeleteDupWithSingleRecord()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry getData = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
            }
	    txn.commit();

	    txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
		data.setData(TestUtils.getTestArray(i));
		assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(txn, key, data));
		OperationStatus status =
		    myDb.getSearchBoth(txn, key, data, LockMode.DEFAULT);
		if (status != OperationStatus.KEYEMPTY &&
		    status != OperationStatus.NOTFOUND) {
		    fail("invalid Database.get return");
		}
		assertEquals(OperationStatus.NOTFOUND,
			     myDb.delete(txn, key, data));
	    }
	    txn.commit();
            myDb.close();
            env.close();

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
    */

    public void testPutDuplicate()
        throws Throwable {
        
        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
                data.setData(TestUtils.getTestArray(i * 2));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testPutNoDupData()
        throws Throwable {
        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoDupData(txn, key, data));
                assertEquals(OperationStatus.KEYEXIST,
                             myDb.putNoDupData(txn, key, data));
                data.setData(TestUtils.getTestArray(i+1));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoDupData(txn, key, data));
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testPutNoOverwriteInANoDupDb()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoOverwrite(txn, key, data));
                assertEquals(OperationStatus.KEYEXIST,
			     myDb.putNoOverwrite(txn, key, data));
            }
	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testPutNoOverwriteInADupDbTxn()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            for (int i = NUM_RECS; i > 0; i--) {
		Transaction txn1 = env.beginTransaction(null, null);
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoOverwrite(txn1, key, data));
                assertEquals(OperationStatus.KEYEXIST,
			     myDb.putNoOverwrite(txn1, key, data));
                data.setData(TestUtils.getTestArray(i << 1));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn1, key, data));
                data.setData(TestUtils.getTestArray(i << 2));
                assertEquals(OperationStatus.KEYEXIST,
			     myDb.putNoOverwrite(txn1, key, data));
		assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(txn1, key));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoOverwrite(txn1, key, data));
		txn1.commit();
            }
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testPutNoOverwriteInADupDbNoTxn()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, false, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoOverwrite(null, key, data));
                assertEquals(OperationStatus.KEYEXIST,
			     myDb.putNoOverwrite(null, key, data));
                data.setData(TestUtils.getTestArray(i << 1));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(null, key, data));
                data.setData(TestUtils.getTestArray(i << 2));
                assertEquals(OperationStatus.KEYEXIST,
			     myDb.putNoOverwrite(null, key, data));
		assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(null, key));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.putNoOverwrite(null, key, data));
            }
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDatabaseCount()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
            }

            long count = myDb.count();
            assertEquals(NUM_RECS, count);

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDeferredWriteDatabaseCount()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, true, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(null, key, data));
            }

            long count = myDb.count();
            assertEquals(NUM_RECS, count);

            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testStat()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
            }

            BtreeStats stat = (BtreeStats)
		myDb.getStats(TestUtils.FAST_STATS);

            assertEquals(0, stat.getInternalNodeCount());
            assertEquals(0, stat.getDuplicateInternalNodeCount());
            assertEquals(0, stat.getBottomInternalNodeCount());
            assertEquals(0, stat.getDuplicateBottomInternalNodeCount());
            assertEquals(0, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(0, stat.getDupCountLeafNodeCount());
            assertEquals(0, stat.getMainTreeMaxDepth());
            assertEquals(0, stat.getDuplicateTreeMaxDepth());

            stat = (BtreeStats) myDb.getStats(null);

            assertEquals(15, stat.getInternalNodeCount());
            assertEquals(0, stat.getDuplicateInternalNodeCount());
            assertEquals(52, stat.getBottomInternalNodeCount());
            assertEquals(0, stat.getDuplicateBottomInternalNodeCount());
            assertEquals(NUM_RECS, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(0, stat.getDupCountLeafNodeCount());
            assertEquals(4, stat.getMainTreeMaxDepth());
            assertEquals(0, stat.getDuplicateTreeMaxDepth());

            stat = (BtreeStats) myDb.getStats(TestUtils.FAST_STATS);

            assertEquals(15, stat.getInternalNodeCount());
            assertEquals(52, stat.getBottomInternalNodeCount());
            assertEquals(NUM_RECS, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(0, stat.getDupCountLeafNodeCount());
            assertEquals(4, stat.getMainTreeMaxDepth());
            assertEquals(0, stat.getDuplicateTreeMaxDepth());

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDatabaseCountEmptyDB()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();

            long count = myDb.count();
            assertEquals(0, count);

            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDatabaseCountWithDeletedEntries()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, false, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
	    int deletedCount = 0;
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
		if ((i % 5) == 0) {
		    myDb.delete(txn, key);
		    deletedCount++;
		}
            }

            long count = myDb.count();
            assertEquals(NUM_RECS - deletedCount, count);

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testStatDups()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
		for (int j = 0; j < 10; j++) {
		    data.setData(TestUtils.getTestArray(i + j));
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(txn, key, data));
		}
            }

            BtreeStats stat = (BtreeStats)
		myDb.getStats(TestUtils.FAST_STATS);

            assertEquals(0, stat.getInternalNodeCount());
            assertEquals(0, stat.getDuplicateInternalNodeCount());
            assertEquals(0, stat.getBottomInternalNodeCount());
            assertEquals(0, stat.getDuplicateBottomInternalNodeCount());
            assertEquals(0, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(0, stat.getDupCountLeafNodeCount());
            assertEquals(0, stat.getMainTreeMaxDepth());
            assertEquals(0, stat.getDuplicateTreeMaxDepth());

            stat = (BtreeStats) myDb.getStats(null);

            assertEquals(23, stat.getInternalNodeCount());
            assertEquals(NUM_RECS, stat.getDuplicateInternalNodeCount());
            assertEquals(85, stat.getBottomInternalNodeCount());
            assertEquals(771, stat.getDuplicateBottomInternalNodeCount());
            assertEquals(2570, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(NUM_RECS, stat.getDupCountLeafNodeCount());
            assertEquals(4, stat.getMainTreeMaxDepth());
            assertEquals(2, stat.getDuplicateTreeMaxDepth());

            stat = (BtreeStats) myDb.getStats(TestUtils.FAST_STATS);

            assertEquals(23, stat.getInternalNodeCount());
            assertEquals(NUM_RECS, stat.getDuplicateInternalNodeCount());
            assertEquals(85, stat.getBottomInternalNodeCount());
            assertEquals(771, stat.getDuplicateBottomInternalNodeCount());
            assertEquals(2570, stat.getLeafNodeCount());
            assertEquals(0, stat.getDeletedLeafNodeCount());
            assertEquals(NUM_RECS, stat.getDupCountLeafNodeCount());
            assertEquals(4, stat.getMainTreeMaxDepth());
            assertEquals(2, stat.getDuplicateTreeMaxDepth());

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDatabaseCountDups()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));
		for (int j = 0; j < 10; j++) {
		    data.setData(TestUtils.getTestArray(i + j));
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(txn, key, data));
		}
            }

            long count = myDb.count();

            assertEquals(2570, count);

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDeferredWriteDatabaseCountDups()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true, true, true, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(null, key, data));
		for (int j = 0; j < 10; j++) {
		    data.setData(TestUtils.getTestArray(i + j));
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(null, key, data));
		}
            }

            long count = myDb.count();

            assertEquals(2570, count);

            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testStatDeletes()
        throws Throwable {

	deleteTestInternal(1, 2, 0, 2);
	deleteTestInternal(2, 2, 2, 2);
	deleteTestInternal(10, 2, 10, 10);
	deleteTestInternal(11, 2, 10, 12);
    }

    private void deleteTestInternal(int numRecs,
				    int numDupRecs,
				    int expectedLNs,
				    int expectedDeletedLNs)
	throws Throwable {

        try {
	    TestUtils.removeLogFiles("Setup", envHome, false);
            Database myDb = initEnvAndDb(true, true, true, false, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);
            for (int i = numRecs; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
		for (int j = 0; j < numDupRecs; j++) {
		    data.setData(TestUtils.getTestArray(i + j));
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.put(txn, key, data));
		}
            }

            for (int i = numRecs; i > 0; i -= 2) {
                key.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.delete(txn, key));
            }

            BtreeStats stat = (BtreeStats) myDb.getStats(null);

            assertEquals(expectedLNs, stat.getLeafNodeCount());
            assertEquals(expectedDeletedLNs, stat.getDeletedLeafNodeCount());
            assertEquals(numRecs, stat.getDupCountLeafNodeCount());

	    txn.commit();
            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Exercise the preload method, which warms up the cache.
     */
    public void testPreloadByteLimit()
        throws Throwable {

        /* Set up a test db */
        Database myDb = initEnvAndDb(false, false, true, false, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
	Transaction txn = env.beginTransaction(null, null);
        for (int i = 2500; i > 0; i--) {
            key.setData(TestUtils.getTestArray(i));
            data.setData(TestUtils.getTestArray(i));
            assertEquals(OperationStatus.SUCCESS,
			 myDb.put(txn, key, data));
        }

        /* Recover the database, restart w/no evictor. */
        long postCreateMemUsage = env.getMemoryUsage();
        INList inlist = env.getEnvironmentImpl().getInMemoryINs();
        long postCreateResidentNodes = inlist.getSize();
	txn.commit();
        myDb.close();
        env.close();
        myDb = initEnvAndDb
	    (true, false, true, false,
	     MemoryBudget.MIN_MAX_MEMORY_SIZE_STRING);

        /* 
         * Do two evictions, because the first eviction will only strip
         * LNs. We need to actually evict BINS because preload only pulls in
         * IN/BINs
         */
        env.evictMemory(); // first eviction strips LNS.
        env.evictMemory(); // second one will evict BINS

        long postEvictMemUsage = env.getMemoryUsage();
        inlist = env.getEnvironmentImpl().getInMemoryINs(); // re-get inList
        long postEvictResidentNodes = inlist.getSize();

        /* Now preload, but not up to the full size of the db */
	PreloadConfig conf = new PreloadConfig();
	conf.setMaxBytes(92000);
        PreloadStats stats =
	    myDb.preload(conf); /* Cache size is currently 92160. */

	assertEquals(PreloadStatus.FILLED_CACHE, stats.status);

        long postPreloadMemUsage = env.getMemoryUsage();
        long postPreloadResidentNodes = inlist.getSize();

        /* Now iterate to get everything back into memory */
        Cursor cursor = myDb.openCursor(null, null);
        int count = 0;
        OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
        while (status == OperationStatus.SUCCESS) {
            count++;
            status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
	cursor.close();

        long postIterationMemUsage = env.getMemoryUsage();
        long postIterationResidentNodes = inlist.getSize();

        if (DEBUG) {
            System.out.println("postCreateMemUsage: " + postCreateMemUsage);
            System.out.println("postEvictMemUsage: " + postEvictMemUsage);
            System.out.println("postPreloadMemUsage: " + postPreloadMemUsage);
            System.out.println("postIterationMemUsage: " +
                               postIterationMemUsage);
            System.out.println("postEvictResidentNodes: " +
                               postEvictResidentNodes);
            System.out.println("postPreloadResidentNodes: " +
                               postPreloadResidentNodes);
            System.out.println("postIterationResidentNodes: " +
                               postIterationResidentNodes);
            System.out.println("postCreateResidentNodes: " +
                               postCreateResidentNodes);
        }

        assertTrue(postEvictMemUsage < postCreateMemUsage);
        assertTrue(postEvictMemUsage < postPreloadMemUsage);
        assertTrue("postPreloadMemUsage=" + postPreloadMemUsage +
                   " postIterationMemUsage=" + postIterationMemUsage,
                   postPreloadMemUsage < postIterationMemUsage);
        assertTrue(postIterationMemUsage <= postCreateMemUsage);
        assertTrue(postEvictResidentNodes < postPreloadResidentNodes);
        //assertEquals(postCreateResidentNodes, postIterationResidentNodes);
        assertTrue(postCreateResidentNodes >= postIterationResidentNodes);

        myDb.close();
        env.close();
    }

    public void testPreloadTimeLimit()
        throws Throwable {

        /* Set up a test db */
        Database myDb = initEnvAndDb(false, false, true, false, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
	Transaction txn = env.beginTransaction(null, null);
        for (int i = 25000; i > 0; i--) {
            key.setData(TestUtils.getTestArray(i));
            data.setData(new byte[1]);
            assertEquals(OperationStatus.SUCCESS,
			 myDb.put(txn, key, data));
        }

        /* Recover the database, restart w/no evictor. */
        long postCreateMemUsage = env.getMemoryUsage();
        INList inlist = env.getEnvironmentImpl().getInMemoryINs();
        long postCreateResidentNodes = inlist.getSize();
	txn.commit();
        myDb.close();
        env.close();
        myDb = initEnvAndDb(true, false, true, false, null);

        /* 
         * Do two evictions, because the first eviction will only strip
         * LNs. We need to actually evict BINS because preload only pulls in
         * IN/BINs
         */
        env.evictMemory(); // first eviction strips LNS.
        env.evictMemory(); // second one will evict BINS

        long postEvictMemUsage = env.getMemoryUsage();
        inlist = env.getEnvironmentImpl().getInMemoryINs(); // re-get inList
        long postEvictResidentNodes = inlist.getSize();

        /* Now preload, but not up to the full size of the db */
	PreloadConfig conf = new PreloadConfig();
	conf.setMaxMillisecs(50);
        PreloadStats stats = myDb.preload(conf);
	assertEquals(PreloadStatus.EXCEEDED_TIME, stats.status);

        long postPreloadMemUsage = env.getMemoryUsage();
        long postPreloadResidentNodes = inlist.getSize();

        /* Now iterate to get everything back into memory */
        Cursor cursor = myDb.openCursor(null, null);
        int count = 0;
        OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
        while (status == OperationStatus.SUCCESS) {
            count++;
            status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
	cursor.close();

        long postIterationMemUsage = env.getMemoryUsage();
        long postIterationResidentNodes = inlist.getSize();

        if (DEBUG) {
            System.out.println("postCreateMemUsage: " + postCreateMemUsage);
            System.out.println("postEvictMemUsage: " + postEvictMemUsage);
            System.out.println("postPreloadMemUsage: " + postPreloadMemUsage);
            System.out.println("postIterationMemUsage: " +
                               postIterationMemUsage);
            System.out.println("postEvictResidentNodes: " +
                               postEvictResidentNodes);
            System.out.println("postPreloadResidentNodes: " +
                               postPreloadResidentNodes);
            System.out.println("postIterationResidentNodes: " +
                               postIterationResidentNodes);
            System.out.println("postCreateResidentNodes: " +
                               postCreateResidentNodes);
        }

        assertTrue(postEvictMemUsage < postCreateMemUsage);
        assertTrue(postEvictMemUsage < postPreloadMemUsage);
        assertTrue("postPreloadMemUsage=" + postPreloadMemUsage +
                   " postIterationMemUsage=" + postIterationMemUsage,
                   postPreloadMemUsage < postIterationMemUsage);
        assertTrue(postIterationMemUsage <= postCreateMemUsage);
        assertTrue(postEvictResidentNodes < postPreloadResidentNodes);
        //assertEquals(postCreateResidentNodes, postIterationResidentNodes);
        assertTrue(postCreateResidentNodes >= postIterationResidentNodes);

        myDb.close();
        env.close();
    }

    /**
     * Load the entire database with preload.
     */
    public void testPreloadEntireDatabase()
        throws Throwable {

        /* Create a test db with one record */
        Database myDb = initEnvAndDb(false, false, false, false, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        key.setData(TestUtils.getTestArray(0));
        data.setData(TestUtils.getTestArray(0));
        assertEquals(OperationStatus.SUCCESS, myDb.put(null, key, data));

        /* Close and reopen. */
        myDb.close();
        env.close();
        myDb = initEnvAndDb(false, false, false, false, null);

        /*
         * Preload the entire database.  In JE 2.0.54 this would cause a
         * NullPointerException.
         */
	PreloadConfig conf = new PreloadConfig();
	conf.setMaxBytes(100000);
        myDb.preload(conf);

        myDb.close();
        env.close();
    }

    /**
     * Preloads a dup database to verify the bug fix for preloading dups on
     * 3.2.x. [#15365]
     */
    public void testPreloadDups()
        throws Throwable {

        Database myDb = initEnvAndDb
            (false, true /*allowDuplicates*/, false, false, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        key.setData(TestUtils.getTestArray(0));
        data.setData(TestUtils.getTestArray(0));
        assertSame(OperationStatus.SUCCESS,
                   myDb.putNoDupData(null, key, data));
        data.setData(TestUtils.getTestArray(1));
        assertSame(OperationStatus.SUCCESS,
                   myDb.putNoDupData(null, key, data));

        /* Close and reopen. */
        myDb.close();
        env.close();
        myDb = initEnvAndDb
            (false, true /*allowDuplicates*/, false, false, null);

        /*
         * Preload the entire database.  Before the bug fix, an assertion would
         * fire in DatabaseImpl.PreloadLSNTreeWalker.fetchLSN when processing
         * the DupCountLN.
         */
	PreloadConfig conf = new PreloadConfig();
	conf.setMaxBytes(100000);
        myDb.preload(conf);

        myDb.close();
        env.close();
    }

    /**
     * Test preload(N, 0) where N > cache size (throws IllArgException).
     */
    public void testPreloadBytesExceedsCache()
        throws Throwable {

        /* Create a test db with one record */
        Database myDb = initEnvAndDb(false, false, false, false, "100000");
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        key.setData(TestUtils.getTestArray(0));
        data.setData(TestUtils.getTestArray(0));
        assertEquals(OperationStatus.SUCCESS, myDb.put(null, key, data));

        /* Close and reopen. */
        myDb.close();
        env.close();
        myDb = initEnvAndDb(false, false, false, false, "100000");

	/* maxBytes > cache size.  Should throw IllegalArgumentException. */
	try {
	    PreloadConfig conf = new PreloadConfig();
	    conf.setMaxBytes(100001);
	    myDb.preload(conf);
	    fail("should have thrown IAE");
	} catch (IllegalArgumentException IAE) {
	}

        myDb.close();
        env.close();
    }

    public void testDbClose()
        throws Throwable {

        /* Set up a test db */
        Database myDb = initEnvAndDb(false, false, true, false, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
	Transaction txn = env.beginTransaction(null, null);
        for (int i = 2500; i > 0; i--) {
            key.setData(TestUtils.getTestArray(i));
            data.setData(TestUtils.getTestArray(i));
            assertEquals(OperationStatus.SUCCESS,
			 myDb.put(txn, key, data));
        }

	/* Create a cursor, use it, then close db without closing cursor. */
        Cursor cursor = myDb.openCursor(txn, null);
        assertEquals(OperationStatus.SUCCESS,
        	     cursor.getFirst(key, data, LockMode.DEFAULT));

	try {
	    myDb.close();
	    fail("didn't throw DatabaseException for unclosed cursor");
	} catch (DatabaseException DBE) {
	}
	txn.commit();
        env.close();
    }

    public void testDbCloseUnopenedDb()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
	Database myDb = new Database(env);
	try {
	    myDb.close();
	} catch (DatabaseException DBE) {
	    fail("shouldn't catch DatabaseException for closing unopened db");
	}
	env.close();
    }

    /**
     * Test that open cursor isn't possible on a closed database.
     */
    public void testOpenCursor() 
        throws DatabaseException {
        Database db = initEnvAndDb(true, false, true, false, null);
        Cursor cursor = db.openCursor(null, null);
        cursor.close();
        db.close();
        try {
            db.openCursor(null, null);
            fail("Should throw exception because databse is closed");
        } catch (DatabaseException e) {
        }
    }

    public void testBufferOverflowingPut()
        throws Throwable {

        try {

            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            //envConfig.setConfigParam("je.log.totalBufferBytes", "5000");
            envConfig.setAllowCreate(true);
            env = new Environment(envHome, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setSortedDuplicates(true);
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            Database myDb = env.openDatabase(null, "testDB", dbConfig);

            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry(new byte[10000000]);
	    try {
		key.setData(TestUtils.getTestArray(10));
		myDb.put(null, key, data);
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }
            myDb.close();
            env.close();
	    env = null;
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Check that the handle lock is not left behind when a non-transactional
     * open of a primary DB fails while populating the secondary. [#15558]
     */
    public void testFailedNonTxnDbOpen()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig priConfig = new DatabaseConfig();
        priConfig.setAllowCreate(true);
        Database priDb = env.openDatabase(null, "testDB", priConfig);

        priDb.put(null, new DatabaseEntry(new byte[1]),
                        new DatabaseEntry(new byte[2]));

        SecondaryConfig secConfig = new SecondaryConfig();
        secConfig.setAllowCreate(true);
        secConfig.setAllowPopulate(true);
        /* Use priDb as foreign key DB for ease of testing. */
        secConfig.setForeignKeyDatabase(priDb);
        secConfig.setKeyCreator(new SecondaryKeyCreator() {
            public boolean createSecondaryKey(SecondaryDatabase secondary,
                                              DatabaseEntry key,
                                              DatabaseEntry data,
                                              DatabaseEntry result)
                throws DatabaseException {
                result.setData
                    (data.getData(), data.getOffset(), data.getSize());
                return true;
            }
        });
        try {
            env.openSecondaryDatabase(null, "testDB2", priDb, secConfig);
            fail();
        } catch (DatabaseException e) {
            /* Fails because [0,0] does not exist as a key in priDb. */
            assertTrue(e.toString(),
                       e.toString().indexOf("foreign key not allowed") > 0);
        }

        priDb.close();
        env.close();
        env = null;
    }

    /**
     * Set up the environment and db.
     */
    private Database initEnvAndDb(boolean dontRunEvictor,
                                  boolean allowDuplicates,
				  boolean transactional,
				  boolean deferredWrite,
                                  String memSize)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(transactional);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_CHECK_LEAKS.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX_DUPTREE.getName(),
				 "6");
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        if (dontRunEvictor) {
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_EVICTOR.getName(),
                                     "false");

            /* 
             * Don't let critical eviction run or it will interfere with the
             * preload test.
             */
            envConfig.setConfigParam(EnvironmentParams.
                                     EVICTOR_CRITICAL_PERCENTAGE.getName(),
                                     "500");
        }

        if (memSize != null) {
            envConfig.setConfigParam(EnvironmentParams.
                                     MAX_MEMORY.getName(),
                                     memSize);
        }

        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        /* Make a db and open it. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setSortedDuplicates(allowDuplicates);
        dbConfig.setAllowCreate(true);
	if (!deferredWrite) {
	    dbConfig.setTransactional(transactional);
	}
        dbConfig.setDeferredWrite(deferredWrite);
        Database myDb = env.openDatabase(null, "testDB", dbConfig);
        return myDb;
    }

    /**
     * X'd out because this is expected to be used in the debugger to set
     * specific breakpoints and step through in a synchronous manner.
     */
    private Database pNOCDb;

    public void xxtestPutNoOverwriteConcurrently()
	throws Throwable {

	pNOCDb = initEnvAndDb(true, true, true, false, null);
	JUnitThread tester1 =
	    new JUnitThread("testNonBlocking1") {
		public void testBody() {
		    try {
			Transaction txn1 = env.beginTransaction(null, null);
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();
			key.setData(TestUtils.getTestArray(1));
			data.setData(TestUtils.getTestArray(1));
			OperationStatus status =
			    pNOCDb.putNoOverwrite(txn1, key, data);
			txn1.commit();
			System.out.println("thread1: " + status);
		    } catch (DatabaseException DBE) {
			DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testNonBlocking2") {
		public void testBody() {
		    try {
			Transaction txn2 = env.beginTransaction(null, null);
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();
			key.setData(TestUtils.getTestArray(1));
			data.setData(TestUtils.getTestArray(2));
			OperationStatus status =
			    pNOCDb.putNoOverwrite(txn2, key, data);
			txn2.commit();
			System.out.println("thread2: " + status);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }
}
