/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CursorTest.java,v 1.78.2.1 2007/02/01 14:50:04 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.util.TestUtils;

public class CursorTest extends TestCase {
    private static final boolean DEBUG = false;
    private static final int NUM_RECS = 257;

    /* 
     * Use a ridiculous value because we've seen extreme slowness on ocicat
     * where dbperf is often running.
     */
    private static final long LOCK_TIMEOUT = 50000000L;

    private static final String DUPKEY = "DUPKEY";

    private Environment env;
    private Database db;
    private PhantomTestConfiguration config;

    private File envHome;
    
    private volatile int sequence;

    public CursorTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
	throws IOException {

        if (env != null) {
            try {
                env.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
        }
	db = null;
	env = null;

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testGetConfig()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        env = new Environment(envHome, envConfig);
        Transaction txn = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(txn, "testDB", dbConfig);
	txn.commit();
	Cursor cursor = null;
	Transaction txn1 =
	    env.beginTransaction(null, TransactionConfig.DEFAULT);
	try {
	    cursor = db.openCursor(txn1, CursorConfig.DEFAULT);
	    CursorConfig config = cursor.getConfig();
	    if (config == CursorConfig.DEFAULT) {
		fail("didn't clone");
	    }
	} catch (DatabaseException DBE) {
	    DBE.printStackTrace();
	    fail("caught DatabaseException " + DBE);
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	    txn1.abort();
	    db.close();
	    env.close();
            env = null;
	}
    }

    /**
     * Put some data in a database, take it out. Yank the file size down so we
     * have many files.
     */
    public void testBasic()
	throws Throwable {

	try {
	    insertMultiDb(1);
	} catch (Throwable t) {
	    t.printStackTrace();
	    throw t;
	}
    }

    public void testMulti()
	throws Throwable {

	try {
	    insertMultiDb(4);
	} catch (Throwable t) {
	    t.printStackTrace();
	    throw t;
	}
    }

    /**
     * Specifies a test configuration.  This is just a struct for holding
     * parameters to be passed down to threads in inner classes.
     */
    class PhantomTestConfiguration {
	String testName;
	String thread1EntryToLock;
	String thread1OpArg;
	String thread2Start;
	String expectedResult;
	boolean doInsert;
	boolean doGetNext;
	boolean doCommit;

	PhantomTestConfiguration(String testName,
				 String thread1EntryToLock,
				 String thread1OpArg,
				 String thread2Start,
				 String expectedResult,
				 boolean doInsert,
				 boolean doGetNext,
				 boolean doCommit) {
	    this.testName = testName;
	    this.thread1EntryToLock = thread1EntryToLock;
	    this.thread1OpArg = thread1OpArg;
	    this.thread2Start = thread2Start;
	    this.expectedResult = expectedResult;
	    this.doInsert = doInsert;
	    this.doGetNext = doGetNext;
	    this.doCommit = doCommit;
	}
    }

    /**
     * This series of tests sets up a simple 2 BIN tree with a specific set of
     * elements (see setupDatabaseAndEnv()).  It creates two threads.
     *
     * Thread 1 positions a cursor on an element on the edge of a BIN (either
     * the last element on the left BIN or the first element on the right BIN).
     * This locks that element.  It throws control to thread 2.
     *
     * Thread 2 positions a cursor on the adjacent element on the other BIN
     * (either the first element on the right BIN or the last element on the
     * left BIN, resp.)  It throws control to thread 1.  After it signals
     * thread 1 to continue, thread 2 does either a getNext or getPrev.  This
     * should block because thread 1 has the next/prev element locked.
     *
     * Thread 1 then waits a short time (250ms) so that thread 2 can execute
     * the getNext/getPrev.  Thread 1 then inserts or deletes the "phantom
     * element" right in between the cursors that were set up in the previous
     * two steps, sleeps a second, and either commits or aborts.
     *
     * Thread 2 will then return from the getNext/getPrev.  The returned key
     * from the getNext/getPrev is then verified.
     *
     * The Serializable isolation level is not used for either thread so as to
     * allow phantoms; otherwise, this test would deadlock.
     *
     * These parameters are all configured through a PhantomTestConfiguration
     * instance passed to phantomWorker which has the template for the steps
     * described above.
     */

    /**
     * Phantom test inserting and committing a phantom while doing a getNext.
     */
    public void testPhantomInsertGetNextCommit()
	throws Throwable {
        
        try {
            phantomWorker
                (new PhantomTestConfiguration
                 ("testPhantomInsertGetNextCommit",
                  "F", "D", "C", "D",
                  true, true, true));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Phantom test inserting and aborting a phantom while doing a getNext.
     */
    public void testPhantomInsertGetNextAbort()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomInsertGetNextAbort",
	      "F", "D", "C", "F",
	      true, true, false));
    }

    /**
     * Phantom test inserting and committing a phantom while doing a getPrev.
     */
    public void testPhantomInsertGetPrevCommit()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomInsertGetPrevCommit",
	      "C", "F", "G", "F",
	      true, false, true));
    }

    /**
     * Phantom test inserting and aborting a phantom while doing a getPrev.
     */
    public void testPhantomInsertGetPrevAbort()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomInsertGetPrevAbort",
	      "C", "F", "G", "C",
	      true, false, false));
    }

    /**
     * Phantom test deleting and committing an edge element while doing a
     * getNext.
     */
    public void testPhantomDeleteGetNextCommit()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDeleteGetNextCommit",
	      "F", "F", "C", "G",
	      false, true, true));
    }

    /**
     * Phantom test deleting and aborting an edge element while doing a
     * getNext.
     */
    public void testPhantomDeleteGetNextAbort()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDeleteGetNextAbort",
	      "F", "F", "C", "F",
	      false, true, false));
    }

    /**
     * Phantom test deleting and committing an edge element while doing a
     * getPrev.
     */
    public void testPhantomDeleteGetPrevCommit()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDeleteGetPrevCommit",
	      "F", "F", "G", "C",
	      false, false, true));
    }

    /**
     * Phantom test deleting and aborting an edge element while doing a
     * getPrev.
     */
    public void testPhantomDeleteGetPrevAbort()
	throws Throwable {

	phantomWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDeleteGetPrevAbort",
	      "F", "F", "G", "F",
	      false, false, false));
    }

    /**
     * Phantom Dup test inserting and committing a phantom while doing a
     * getNext.
     */
    public void testPhantomDupInsertGetNextCommit()
	throws Throwable {

        try {
            phantomDupWorker
                (new PhantomTestConfiguration
                 ("testPhantomDupInsertGetNextCommit",
                  "F", "D", "C", "D",
                  true, true, true));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Phantom Dup test inserting and aborting a phantom while doing a getNext.
     */
    public void testPhantomDupInsertGetNextAbort()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupInsertGetNextAbort",
	      "F", "D", "C", "F",
	      true, true, false));
    }

    /**
     * Phantom Dup test inserting and committing a phantom while doing a
     * getPrev.
     */
    public void testPhantomDupInsertGetPrevCommit()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupInsertGetPrevCommit",
	      "C", "F", "G", "F",
	      true, false, true));
    }

    /**
     * Phantom Dup test inserting and aborting a phantom while doing a getPrev.
     */
    public void testPhantomDupInsertGetPrevAbort()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupInsertGetPrevAbort",
	      "C", "F", "G", "C",
	      true, false, false));
    }

    /**
     * Phantom Dup test deleting and committing an edge element while doing a
     * getNext.
     */
    public void testPhantomDupDeleteGetNextCommit()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupDeleteGetNextCommit",
	      "F", "F", "C", "G",
	      false, true, true));
    }

    /**
     * Phantom Dup test deleting and aborting an edge element while doing a
     * getNext.
     */
    public void testPhantomDupDeleteGetNextAbort()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupDeleteGetNextAbort",
	      "F", "F", "C", "F",
	      false, true, false));
    }

    /**
     * Phantom Dup test deleting and committing an edge element while doing a
     * getPrev.
     */
    public void testPhantomDupDeleteGetPrevCommit()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupDeleteGetPrevCommit",
	      "F", "F", "G", "C",
	      false, false, true));
    }

    /**
     * Phantom Dup test deleting and aborting an edge element while doing a
     * getPrev.
     */
    public void testPhantomDupDeleteGetPrevAbort()
	throws Throwable {

	phantomDupWorker
	    (new PhantomTestConfiguration
	     ("testPhantomDupDeleteGetPrevAbort",
	      "F", "F", "G", "F",
	      false, false, false));
    }

    private void phantomWorker(PhantomTestConfiguration c)
	throws Throwable {

	try {
	    this.config = c;
	    setupDatabaseAndEnv(false);

	    if (config.doInsert &&
		!config.doGetNext) {

		Transaction txnDel =
		    env.beginTransaction(null, TransactionConfig.DEFAULT);

		/*
		 * Delete the first entry in the second bin so that we can
		 * reinsert it in tester1 and have it be the first entry in
		 * that bin.  If we left F and then tried to insert something
		 * to the left of F, it would end up in the first bin.
		 */
		assertEquals(OperationStatus.SUCCESS,
			     db.delete(txnDel,
				       new DatabaseEntry("F".getBytes())));
		txnDel.commit();
	    }

	    JUnitThread tester1 =
		new JUnitThread(config.testName + "1") {
		    public void testBody()
			throws Throwable {

			Cursor cursor = null;
			try {
			    Transaction txn1 =
				env.beginTransaction(null, null);
			    cursor = db.openCursor(txn1, CursorConfig.DEFAULT);
			    OperationStatus status =
				cursor.getSearchKey
				(new DatabaseEntry
				 (config.thread1EntryToLock.getBytes()),
				 new DatabaseEntry(),
				 LockMode.RMW);
			    assertEquals(OperationStatus.SUCCESS, status);
			    sequence++;  // 0 -> 1

			    while (sequence < 2) {
				Thread.yield();
			    }

			    /*
			     * Since we can't increment sequence when tester2
			     * blocks on the getNext call, all we can do is
			     * bump sequence right before the getNext, and then
			     * wait a little in this thread for tester2 to
			     * block.
			     */
			    try {
				Thread.sleep(250);
			    } catch (InterruptedException IE) {
			    }

			    if (config.doInsert) {
				status = db.put
				    (txn1,
				     new DatabaseEntry
				     (config.thread1OpArg.getBytes()),
				     new DatabaseEntry(new byte[10]));
			    } else {
				status = db.delete
				    (txn1,
				     new DatabaseEntry
				     (config.thread1OpArg.getBytes()));
			    }
			    assertEquals(OperationStatus.SUCCESS, status);
			    sequence++;     // 2 -> 3

			    try {
				Thread.sleep(1000);
			    } catch (InterruptedException IE) {
			    }

			    cursor.close();
			    cursor = null;
			    if (config.doCommit) {
				txn1.commit();
			    } else {
				txn1.abort();
			    }
			} catch (DatabaseException DBE) {
			    if (cursor != null) {
				cursor.close();
			    }
			    DBE.printStackTrace();
			    fail("caught DatabaseException " + DBE);
			}
		    }
		};

	    JUnitThread tester2 =
		new JUnitThread(config.testName + "2") {
		    public void testBody()
			throws Throwable {

			Cursor cursor = null;
			try {
			    Transaction txn2 =
				env.beginTransaction(null, null);
			    txn2.setLockTimeout(LOCK_TIMEOUT);
			    cursor = db.openCursor(txn2, CursorConfig.DEFAULT);

			    while (sequence < 1) {
				Thread.yield();
			    }

			    OperationStatus status =
				cursor.getSearchKey
				(new DatabaseEntry
				 (config.thread2Start.getBytes()),
				 new DatabaseEntry(),
				 LockMode.DEFAULT);
			    assertEquals(OperationStatus.SUCCESS, status);

			    sequence++;           // 1 -> 2
			    DatabaseEntry nextKey = new DatabaseEntry();
			    try {

				/*
				 * This will block until tester1 above commits.
				 */
				if (config.doGetNext) {
				    status =
					cursor.getNext(nextKey,
						       new DatabaseEntry(),
						       LockMode.DEFAULT);
				} else {
				    status =
					cursor.getPrev(nextKey,
						       new DatabaseEntry(),
						       LockMode.DEFAULT);
				}
			    } catch (DatabaseException DBE) {
				System.out.println("t2 caught " + DBE);
			    }
			    assertEquals(3, sequence);
			    assertEquals(config.expectedResult,
					 new String(nextKey.getData()));
			    cursor.close();
			    cursor = null;
			    txn2.commit();
			} catch (DatabaseException DBE) {
			    if (cursor != null) {
				cursor.close();
			    }
			    DBE.printStackTrace();
			    fail("caught DatabaseException " + DBE);
			}
		    }
		};

	    tester1.start();
	    tester2.start();

	    tester1.finishTest();
	    tester2.finishTest();
	} finally {
	    db.close();
	    env.close();
            env = null;
	}
    }

    private void phantomDupWorker(PhantomTestConfiguration c)
	throws Throwable {

	Cursor cursor = null;
	try {
	    this.config = c;
	    setupDatabaseAndEnv(true);

	    if (config.doInsert &&
		!config.doGetNext) {

		Transaction txnDel =
		    env.beginTransaction(null, TransactionConfig.DEFAULT);
		cursor = db.openCursor(txnDel, CursorConfig.DEFAULT);

		/*
		 * Delete the first entry in the second bin so that we can
		 * reinsert it in tester1 and have it be the first entry in
		 * that bin.  If we left F and then tried to insert something
		 * to the left of F, it would end up in the first bin.
		 */
		assertEquals(OperationStatus.SUCCESS, cursor.getSearchBoth
			     (new DatabaseEntry(DUPKEY.getBytes()),
			      new DatabaseEntry("F".getBytes()),
			      LockMode.DEFAULT));
		assertEquals(OperationStatus.SUCCESS, cursor.delete());
		cursor.close();
		cursor = null;
		txnDel.commit();
	    }

	    JUnitThread tester1 =
		new JUnitThread(config.testName + "1") {
		    public void testBody()
			throws Throwable {

			Cursor cursor = null;
			Cursor c = null;
			try {
			    Transaction txn1 =
				env.beginTransaction(null, null);
			    cursor = db.openCursor(txn1, CursorConfig.DEFAULT);
			    OperationStatus status =
				cursor.getSearchBoth
				(new DatabaseEntry(DUPKEY.getBytes()),
				 new DatabaseEntry
				 (config.thread1EntryToLock.getBytes()),
				 LockMode.RMW);
			    assertEquals(OperationStatus.SUCCESS, status);
			    cursor.close();
			    cursor = null;
			    sequence++;  // 0 -> 1

			    while (sequence < 2) {
				Thread.yield();
			    }

			    /*
			     * Since we can't increment sequence when tester2
			     * blocks on the getNext call, all we can do is
			     * bump sequence right before the getNext, and then
			     * wait a little in this thread for tester2 to
			     * block.
			     */
			    try {
				Thread.sleep(250);
			    } catch (InterruptedException IE) {
			    }

			    if (config.doInsert) {
				status = db.put
				    (txn1,
				     new DatabaseEntry(DUPKEY.getBytes()),
				     new DatabaseEntry
				     (config.thread1OpArg.getBytes()));
			    } else {
				c = db.openCursor(txn1, CursorConfig.DEFAULT);
				assertEquals(OperationStatus.SUCCESS,
					     c.getSearchBoth
					     (new DatabaseEntry
					      (DUPKEY.getBytes()),
					      new DatabaseEntry
					      (config.thread1OpArg.getBytes()),
					      LockMode.DEFAULT));
				assertEquals(OperationStatus.SUCCESS,
					     c.delete());
				c.close();
				c = null;
			    }
			    assertEquals(OperationStatus.SUCCESS, status);
			    sequence++;     // 2 -> 3

			    try {
				Thread.sleep(1000);
			    } catch (InterruptedException IE) {
			    }

			    if (config.doCommit) {
				txn1.commit();
			    } else {
				txn1.abort();
			    }
			} catch (DatabaseException DBE) {
			    if (cursor != null) {
				cursor.close();
			    }
			    if (c != null) {
				c.close();
			    }
			    DBE.printStackTrace();
			    fail("caught DatabaseException " + DBE);
			}
		    }
		};

	    JUnitThread tester2 =
		new JUnitThread("testPhantomInsert2") {
		    public void testBody()
			throws Throwable {

			Cursor cursor = null;
			try {
			    Transaction txn2 =
				env.beginTransaction(null, null);
			    txn2.setLockTimeout(LOCK_TIMEOUT);
			    cursor = db.openCursor(txn2, CursorConfig.DEFAULT);

			    while (sequence < 1) {
				Thread.yield();
			    }

			    OperationStatus status =
				cursor.getSearchBoth
				(new DatabaseEntry(DUPKEY.getBytes()),
				 new DatabaseEntry
				 (config.thread2Start.getBytes()),
				 LockMode.DEFAULT);
			    assertEquals(OperationStatus.SUCCESS, status);

			    sequence++;           // 1 -> 2
			    DatabaseEntry nextKey = new DatabaseEntry();
			    DatabaseEntry nextData = new DatabaseEntry();
			    try {

				/*
				 * This will block until tester1 above commits.
				 */
				if (config.doGetNext) {
				    status =
					cursor.getNextDup(nextKey, nextData,
							  LockMode.DEFAULT);
				} else {
				    status =
					cursor.getPrevDup(nextKey, nextData,
							  LockMode.DEFAULT);
				}
			    } catch (DatabaseException DBE) {
				System.out.println("t2 caught " + DBE);
			    }
			    assertEquals(3, sequence);
			    byte[] data = nextData.getData();
			    assertEquals(config.expectedResult,
					 new String(data));
			    cursor.close();
			    cursor = null;
			    txn2.commit();
			} catch (DatabaseException DBE) {
			    if (cursor != null) {
				cursor.close();
			    }
			    DBE.printStackTrace();
			    fail("caught DatabaseException " + DBE);
			}
		    }
		};

	    tester1.start();
	    tester2.start();

	    tester1.finishTest();
	    tester2.finishTest();
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	    db.close();
	    env.close();
            env = null;
	}
    }

    /**
     * Sets up a small database with a tree containing 2 bins, one with A, B,
     * and C, and the other with F, G, H, and I.
     */
    private void setupDatabaseAndEnv(boolean writeAsDuplicateData)
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();

        /* RepeatableRead isolation is required by this test. */
        TestUtils.clearIsolationLevel(envConfig);

	DbInternal.disableParameterValidation(envConfig);
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                 "6");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX_DUPTREE.getName(),
                                 "6");
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 "1024");
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                                 "true");
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        env = new Environment(envHome, envConfig);
        Transaction txn = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(txn, "testDB", dbConfig);

	if (writeAsDuplicateData) {
	    writeDuplicateData(db, txn);
	} else {
	    writeData(db, txn);
	}

	txn.commit();
    }

    String[] dataStrings = {
	"A", "B", "C", "F", "G", "H", "I"
    };

    private void writeData(Database db, Transaction txn)
	throws DatabaseException {

	for (int i = 0; i < dataStrings.length; i++) {
	    db.put(txn, new DatabaseEntry(dataStrings[i].getBytes()),
		   new DatabaseEntry(new byte[10]));
	}
    }

    private void writeDuplicateData(Database db, Transaction txn)
	throws DatabaseException {

	for (int i = 0; i < dataStrings.length; i++) {
	    db.put(txn, new DatabaseEntry(DUPKEY.getBytes()),
		   new DatabaseEntry(dataStrings[i].getBytes()));
	}
    }

    /**
     * Insert data over many databases.
     */
    private void insertMultiDb(int numDbs)
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();

        /* RepeatableRead isolation is required by this test. */
        TestUtils.clearIsolationLevel(envConfig);

	DbInternal.disableParameterValidation(envConfig);
        envConfig.setTransactional(true);
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_FILE_MAX.getName(), "1024");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_CHECK_LEAKS.getName(), "true");
	envConfig.setConfigParam
	    (EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
	    (EnvironmentParams.NODE_MAX_DUPTREE.getName(), "6");
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setAllowCreate(true);
        Environment env = new Environment(envHome, envConfig);

        Database[] myDb = new Database[numDbs];
        Cursor[] cursor = new Cursor[numDbs];
        Transaction txn =
	    env.beginTransaction(null, TransactionConfig.DEFAULT);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        for (int i = 0; i < numDbs; i++) {
            myDb[i] = env.openDatabase(txn, "testDB" + i, dbConfig);

            cursor[i] = myDb[i].openCursor(txn, CursorConfig.DEFAULT);
        }

        /* Insert data in a round robin fashion to spread over log. */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = NUM_RECS; i > 0; i--) {
            for (int c = 0; c < numDbs; c++) {
                key.setData(TestUtils.getTestArray(i + c));
                data.setData(TestUtils.getTestArray(i + c));
                if (DEBUG) {
                    System.out.println("i = " + i + 
                                       TestUtils.dumpByteArray(key.getData()));
                }
                cursor[c].put(key, data);
            }
        }

        for (int i = 0; i < numDbs; i++) {
            cursor[i].close();
            myDb[i].close();
        }
        txn.commit();

        assertTrue(env.verify(null, System.err));
        env.close();
        env = null;

        envConfig.setAllowCreate(false);
        env = new Environment(envHome, envConfig);

        /*
         * Before running the verifier, run the cleaner to make sure it has
         * completed.  Otherwise, the cleaner will be running when we call
         * verify, and open txns will be reported.
         */
        env.cleanLog();

        env.verify(null, System.err);

        /* Check each db in turn, using null transactions. */
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(false);
        for (int d = 0; d < numDbs; d++) {
            Database checkDb = env.openDatabase(null, "testDB" + d, 
						dbConfig);
            Cursor myCursor = checkDb.openCursor(null, CursorConfig.DEFAULT);

            OperationStatus status =
		myCursor.getFirst(key, data, LockMode.DEFAULT);

            int i = 1;
            while (status == OperationStatus.SUCCESS) {
                byte[] expectedKey = TestUtils.getTestArray(i + d);
                byte[] expectedData = TestUtils.getTestArray(i + d);

                if (DEBUG) {
                    System.out.println("Database " + d + " Key " + i +
                                       " expected = " + 
                                       TestUtils.dumpByteArray(expectedKey) +
                                       " seen = " +
                                       TestUtils.dumpByteArray(key.getData()));
                }

                assertTrue("Database " + d + " Key " + i + " expected = " + 
                           TestUtils.dumpByteArray(expectedKey) +
                           " seen = " +
                           TestUtils.dumpByteArray(key.getData()),
                           Arrays.equals(expectedKey, key.getData()));
                assertTrue("Data " + i, Arrays.equals(expectedData,
                                                      data.getData()));
                i++;

                status = myCursor.getNext(key, data, LockMode.DEFAULT);
            } 
	    myCursor.close();
            assertEquals("Number recs seen", NUM_RECS, i-1);
            checkDb.close();
        }
        env.close();
        env = null;
    }
}
