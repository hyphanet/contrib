/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: APILockoutTest.java,v 1.10 2008/04/16 01:56:18 linda Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.sleepycat.je.APILockedException;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.util.TestUtils;

public class APILockoutTest extends TestCase {
    private File envHome;

    private JUnitThread tester1 = null;
    private JUnitThread tester2 = null;

    private volatile int flag = 0;

    public APILockoutTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() throws IOException, DatabaseException {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown() throws IOException, DatabaseException {
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testBasic()
	throws Throwable {

	EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	envConfig.setTransactional(true);
	envConfig.setAllowCreate(true);
	envConfig.setConfigParam
	    (EnvironmentParams.ENV_LOCKOUT_TIMEOUT.getName(), "1000");
	Environment env = new Environment(envHome, envConfig);
	final EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	envImpl.setupAPILock();
	envImpl.acquireAPIWriteLock(1, TimeUnit.SECONDS);

	tester1 =
	    new JUnitThread("testWait-Thread1") {
		public void testBody()
		    throws DatabaseException {

		    BasicLocker bl = BasicLocker.
			createBasicLocker(envImpl, false /*noWait*/,
					  true /*noAPIReadLock*/);
		    try {
			envImpl.acquireAPIReadLock(bl);
			fail("expected timeout");
		    } catch (Exception E) {
			assertTrue(E instanceof APILockedException);
		    }
		    bl.operationEnd(false);
		    try {
			bl = BasicLocker.
			    createBasicLocker(envImpl,
					      false /*noWait*/,
					      false /*noAPIReadLock*/);
			fail("expected timeout");
		    } catch (Exception E) {
			// expected
		    }
		    flag = 1;
		}
	    };

	tester2 =
	    new JUnitThread("testWait-Thread2") {
		public void testBody()
		    throws DatabaseException {

		    while (flag < 2) {
			Thread.yield();
		    }
		    BasicLocker bl =
			BasicLocker.createBasicLocker(envImpl,
						      false /*noWait*/,
						      true /*noAPIReadLock*/);
		    try {
			envImpl.acquireAPIReadLock(bl);
		    } catch (Exception E) {
			E.printStackTrace();
			fail("expected success");
		    }

		    envImpl.releaseAPIReadLock(bl);

		    /* Second release should succeed -- we're not checking. */
		    try {
			envImpl.releaseAPIReadLock(bl);
		    } catch (IllegalMonitorStateException IMSE) {
			fail("expected success");
		    }
		    bl.operationEnd(true);
		}
	    };

	tester1.start();
	tester2.start();
	/* Wait for acquireAPIReadLock to complete. */
	while (flag < 1) {
	    Thread.yield();
	}

	/*
	 * Make sure that write locking thread (main) can't read lock, too.
	 */
	try {
	    BasicLocker bl =
		BasicLocker.createBasicLocker(envImpl, false /*noWait*/,
					      true /*noAPIReadLock*/);
	    envImpl.acquireAPIReadLock(bl);
	    fail("expected exception");
	} catch (DatabaseException DE) {
	    /* ignore */
	}

	envImpl.releaseAPIWriteLock();
	flag = 2;
	tester1.finishTest();
	tester2.finishTest();
	try {

	    /*
	     * Expect an IllegalMonitorStateException saying that environment
	     * is not currently locked.
	     */
	    envImpl.releaseAPIWriteLock();
	    fail("expected exception");
	} catch (IllegalMonitorStateException IMSE) {
	    /* Ignore */
	}
	env.close();
    }

    enum BlockingOperation {
	TRANSACTION, CURSOR_OPEN, TRANSACTION_WITH_CURSOR,
	NON_TRANSACTIONAL_DB_PUT, TRANSACTIONAL_DB_PUT, CURSOR_PUT
    };

    public void testTransactionBlocking()
	throws Throwable {

	doBlockingTest(BlockingOperation.TRANSACTION);
    }

    public void testCursorWithNullTransactionBlocking()
	throws Throwable {

	doBlockingTest(BlockingOperation.CURSOR_OPEN);
    }

    public void testCursorWithTransactionBlocking()
	throws Throwable {

	doBlockingTest(BlockingOperation.TRANSACTION_WITH_CURSOR);
    }

    public void testDbPutWithNullTransactionBlocking()
	throws Throwable {

	doBlockingTest(BlockingOperation.NON_TRANSACTIONAL_DB_PUT);
    }

    public void testDbPutWithTransactionBlocking()
	throws Throwable {

	doBlockingTest(BlockingOperation.TRANSACTIONAL_DB_PUT);
    }

    public void testCursorPut()
	throws Throwable {

	doBlockingTest(BlockingOperation.CURSOR_PUT);
    }

    private void doBlockingTest(final BlockingOperation operation)
	throws Throwable {

	EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	envConfig.setTransactional(true);
	envConfig.setAllowCreate(true);
	envConfig.setConfigParam
	    (EnvironmentParams.ENV_LOCKOUT_TIMEOUT.getName(), "1000");
	final Environment env = new Environment(envHome, envConfig);
	final EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	DatabaseConfig dbConf = new DatabaseConfig();
	dbConf.setTransactional(true);
	dbConf.setAllowCreate(true);

	envImpl.setupAPILock();

	final Database db = env.openDatabase(null, "foo", dbConf);

	envImpl.acquireAPIWriteLock(1, TimeUnit.SECONDS);

	tester1 =
	    new JUnitThread("testWait-Thread1") {
		public void testBody()
		    throws DatabaseException {

		    Transaction txn = null;
		    Cursor cursor = null;
		    DatabaseEntry key = new DatabaseEntry();
		    DatabaseEntry data = new DatabaseEntry();
		    key.setData(new byte[] { 0, 1 });
		    data.setData(new byte[] { 0, 1 });
		    /* Try to do opn. while api is locked.  Should fail. */
		    try {
			switch (operation) {
			case CURSOR_OPEN:
			    cursor = db.openCursor(null, null);
			    break;

			case TRANSACTION:
			case TRANSACTION_WITH_CURSOR:
			    txn = env.beginTransaction(null, null);
			    break;

			case NON_TRANSACTIONAL_DB_PUT:
			    db.put(null, key, data);
			    break;

			case TRANSACTIONAL_DB_PUT:
			case CURSOR_PUT:
			    throw new DatabaseException("fake DE");
			}
			fail("expected timeout");
		    } catch (DatabaseException DE) {
			/* Ignore. */
		    }

		    flag = 1;

		    /* Wait for main to unlock the API, then do operation. */
		    while (flag < 2) {
			Thread.yield();
		    }
		    try {
			switch (operation) {
			case CURSOR_OPEN:
			    cursor = db.openCursor(null, null);
			    break;

			case TRANSACTION:
			case TRANSACTION_WITH_CURSOR:
			case CURSOR_PUT:
			    txn = env.beginTransaction(null, null);
			    if (operation ==
				BlockingOperation.TRANSACTION_WITH_CURSOR ||
				operation ==
				BlockingOperation.CURSOR_PUT) {
				cursor = db.openCursor(txn, null);
				if (operation ==
				    BlockingOperation.CURSOR_PUT) {
				    cursor.put(key, data);
				}
			    }
			    break;

			case NON_TRANSACTIONAL_DB_PUT:
			    db.put(null, key, data);
			    break;

			case TRANSACTIONAL_DB_PUT:
			    txn = env.beginTransaction(null, null);
			    db.put(txn, key, data);
			}
		    } catch (Exception E) {
			fail("expected success");
		    }

		    /* Return control to main. */
		    flag = 3;

		    /* Wait for main to attempt lock on the API (and fail). */
		    while (flag < 4) {
			Thread.yield();
		    }
		    try {
			switch (operation) {
			case CURSOR_OPEN:
			    cursor.close();
			    break;

			case TRANSACTION:
			case TRANSACTION_WITH_CURSOR:
			case TRANSACTIONAL_DB_PUT:
			case CURSOR_PUT:
			    if (operation ==
				BlockingOperation.TRANSACTION_WITH_CURSOR ||
				operation ==
				BlockingOperation.CURSOR_PUT) {
				cursor.close();
			    }
			    if (txn == null) {
				fail("txn is null");
			    }
			    txn.abort();
			    break;

			case NON_TRANSACTIONAL_DB_PUT:
			    /* Do nothing. */
			    break;
			}
		    } catch (Exception E) {
			fail("expected success");
		    }

		    flag = 5;
		}
	    };

	tester1.start();
	/* Wait for acquireAPIReadLock to complete. */
	while (flag < 1) {
	    Thread.yield();
	}
	envImpl.releaseAPIWriteLock();
	flag = 2;

	/* Wait for tester1 to begin a txn. */
	while (flag < 3) {
	    Thread.yield();
	}

	if (operation == BlockingOperation.TRANSACTION ||
	    operation == BlockingOperation.TRANSACTION_WITH_CURSOR) {
	    /* Attempt lock.  Should timeout. */
	    try {
		envImpl.acquireAPIWriteLock(1, TimeUnit.SECONDS);
		fail("expected timeout.");
	    } catch (DatabaseException DE) {
		/* Ignore.  Expect timeout. */
	    }
	}

	/* Back to tester1 to end the txn. */
	flag = 4;
	while (flag < 5) {
	    Thread.yield();
	}

	/* Attempt lock.  Should complete. */
	try {
	    envImpl.acquireAPIWriteLock(1, TimeUnit.SECONDS);
	} catch (DatabaseException DE) {
	    fail("expected success.");
	}

	tester1.finishTest();
	envImpl.releaseAPIWriteLock();
	db.close();
	env.close();
    }
}
