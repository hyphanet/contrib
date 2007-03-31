/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: AtomicPutTest.java,v 1.13.2.1 2007/02/01 14:50:19 cwl Exp $
 */

package com.sleepycat.je.test;

import junit.framework.Test;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.junit.JUnitMethodThread;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests put() (overwrite) and putNoOverwrite() to check that they work
 * atomically under concurrent access.  These tests were added after put()
 * and putNoOverwrite() were changed to work atomically.  The history of the
 * bugs is below.
 *
 *  Old Algorithm
 *  -------------
 *  put(X, Y):
 *      if duplicates:
 *          return insertDup(X, Y)
 *      else:
 *          search(X)
 *          if SUCCESS:
 *              putCurrent(Y)
 *              return SUCCESS
 *          else:
 *              return insert(X,Y)
 *
 *  putNoOverwrite(X, Y):
 *      search(X)
 *      if SUCCESS:
 *          return KEYEXIST
 *      else:
 *          if duplicates:
 *              insertDup(X, Y)
 *          else:
 *              insert(X, Y)
 *
 *  Bug #1: In put with duplicates: Returned KEYEXIST when trying to overwrite
 *  a duplicate duplicate.
 *
 *  Bug #2: In put without duplicates: Returned KEYEXIST if another thread
 *  inserted in between a search that returned NOTFOUND and the insert().
 *
 *  Bug #3: In putNoOverwrite with duplicates:  Added a duplicate if another
 *  thread inserted in between a search that returned NOTFOUND and the
 *  insert().
 *
 *  New Algorithm
 *  -------------
 *  put(X, Y):
 *      if duplicates:
 *          insertDup(X, Y)
 *      else:
 *          insert(X, Y)
 *      if KEYEXIST:
 *          putCurrent(Y)
 *      return SUCCESS
 *
 *  putNoOverwrite(X, Y):
 *      return insert(X, Y)
 *
 *  Potential Bug #4: In put, if the lock is not acquired: Another thread may
 *  overwrite in between the insert and the putCurrent.  But then putCurrent
 *  wouldn't be able to get a write lock, right?  I can't think of how a
 *  problem could occur.

 *  Potential Bug #5: In putNoOverwrite, if we need to lock an existing record
 *  in order to return KEYEXIST, we may cause more deadlocks than is necessary.
 *
 *  Low level operations
 *  --------------------
 *  insert(X, Y):    insert if key is not present, else return KEYEXIST
 *  insertDup(X, Y): insert if key and data are not present, else return
 *  KEYEXIST
 *
 *  Both insert methods obtain a lock on the existing record when returning
 *  KEYEXIST, to support overwrite.
 */
public class AtomicPutTest extends TxnTestCase {

    private static final int MAX_KEY = 400; //50000; 

    public static Test suite() {
        return txnTestSuite(AtomicPutTest.class, null,
                            //null);
                            new String[] {TxnTestCase.TXN_USER});
    }

    private int nextKey;
    private Database db;

    /**
     * Closes databases, then calls the super.tearDown to close the env.
     */
    public void tearDown()
        throws Exception {

        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {}
            db = null;
        }
        super.tearDown();
    }

    /**
     * Tests that put (overwrite), with no duplicates allowed, never causes a
     * KEYEXIST status return.
     */
    public void testOverwriteNoDuplicates()
	throws Throwable {

        String method = "runOverwriteNoDuplicates";
        JUnitMethodThread tester1 = new JUnitMethodThread(method + "-t1",
                                                          method, this);
        JUnitMethodThread tester2 = new JUnitMethodThread(method + "-t2",
                                                          method, this);
	db = openDb("foo", false);
        tester1.start();
        tester2.start();
        finishTests(new JUnitThread[] { tester1, tester2 });
        db.close();
        db = null;
    }

    /**
     * The old put() implementation first did a search, then inserted if
     * NOTFOUND was returned by the search.  This test tries to create the
     * situation where one thread does a search on a key that returns NOTFOUND
     * and another thread immediately afterwards inserts the same key, before
     * the first thread has a chance to start the insert.  Before the fix to
     * make put() atomic, the first thread would have returned KEYEXIST from
     * put(), and that should never happen.
     */
    public void runOverwriteNoDuplicates() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (nextKey < MAX_KEY) {
            /*
             * Attempt to insert the same key as was just inserted by the other
             * thread.  We need to keep incrementing the key, since the error
             * only occurs for a non-existing key value.
             */
            int val = nextKey++ / 2;
            Transaction txn = txnBegin();
            key.setData(TestUtils.getTestArray(val));
            data.setData(TestUtils.getTestArray(val));
	    boolean commit = true;
	    try {
		OperationStatus status = db.put(txn, key, data);
		assertEquals("Key=" + val, OperationStatus.SUCCESS, status);
	    } catch (DeadlockException DE) {
		commit = false;
	    }
	    if (commit) {
		txnCommit(txn);
	    } else {
		txnAbort(txn);
	    }
        }
    }

    /**
     * Tests that putNoOverwrite, with duplicates allowed, never inserts a
     * duplicate.
     */
    public void testNoOverwriteWithDuplicates()
	throws Throwable {

        String method = "runNoOverwriteWithDuplicates";
        JUnitMethodThread tester1 = new JUnitMethodThread(method + "-t1",
                                                          method, this);
        JUnitMethodThread tester2 = new JUnitMethodThread(method + "-t2",
                                                          method, this);
	db = openDb("foo", true);
        tester1.start();
        tester2.start();
        finishTests(new JUnitThread[] { tester1, tester2 });
        db.close();
        db = null;
    }

    /**
     * The old putNoOverwrite() inserted a duplicate after a search returned
     * NOTFOUND, when duplicates were configured.  This test tries to create
     * the situation where the second thread inserting with a given key inserts
     * a duplicate, which should never happen since we're using
     * putNoOverwrite().
     */
    public void runNoOverwriteWithDuplicates()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (nextKey < MAX_KEY) {
            /*
             * Attempt to insert a duplicate for the same key as was just
             * inserted by the other thread.  Each thread uses a different data
             * value (modulo 2) so to avoid a duplicate-duplicate, which would
             * not be inserted.
             */
            int val = nextKey++;
            int keyVal = val / 2;
            int dataVal = val % 2;
            key.setData(TestUtils.getTestArray(keyVal));
            data.setData(TestUtils.getTestArray(dataVal));
            while (true) {
                Transaction txn = txnBegin();
                boolean commit = true;
                try {
                    db.putNoOverwrite(txn, key, data);
                } catch (DeadlockException DE) {
                    commit = false;
                }
                if (commit) {
                    txnCommit(txn);
                    break;
                } else {
                    txnAbort(txn);
                }
            }
            Cursor cursor = db.openCursor(null, null);
            try {
                OperationStatus status = cursor.getSearchKey(key, data,
                                                             LockMode.DEFAULT);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(1, cursor.count());
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * Opens a database.
     */
    private Database openDb(String name, boolean dups)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);

        Transaction txn = txnBegin();
        try {
            return env.openDatabase(txn, name, dbConfig);
        } finally {
            txnCommit(txn);
        }
    }

    /**
     * When one thread throws an assertion, the other threads need to be
     * stopped, otherwise we will see side effects that mask the real problem.
     */
    private void finishTests(JUnitThread[] threads)
	throws Throwable {

        Throwable ex = null;
        for (int i = 0; i < threads.length; i += 1) {
            try {
                threads[i].finishTest();
            } catch (Throwable e) {
                if (ex == null) {
                    ex = e;
                }
            }
        }
        if (ex != null) {
            throw ex;
        }
    }
}
