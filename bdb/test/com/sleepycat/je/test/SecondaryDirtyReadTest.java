/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SecondaryDirtyReadTest.java,v 1.12.2.1 2007/02/01 14:50:20 cwl Exp $
 */

package com.sleepycat.je.test;

import junit.framework.Test;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.junit.JUnitMethodThread;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests for multithreading problems when using read-uncommitted with
 * secondaries.  If a primary record is updated while performing a
 * read-uncommitted (in between reading the secondary and the primary), we need
 * to be sure that we don't return inconsistent results to the user.  For
 * example, we should not return a primary data value that no longer contains
 * the secondary key.  We also need to ensure that deleting a primary record in
 * the middle of a secondary read does not appear as a corrupt secondary.  In
 * both of these cases it should appear that the record does not exist, from
 * the viewpoint of an application using a cursor.
 * 
 * <p>These tests create two threads, one reading and the other deleting or
 * updating.  The intention is for reading thread and the delete/update thread
 * to race in operating on the same key (nextKey).  If the reading thread reads
 * the secondary, then the other thread deletes the primary, then the reading
 * thread tries to read the primary, we've accomplished our goal.  Prior to
 * when we handled that case in SecondaryCursor, that situation would cause a
 * "secondary corrupt" exception.</p>
 */
public class SecondaryDirtyReadTest extends MultiKeyTxnTestCase {

    private static final int MAX_KEY = 1000;

    public static Test suite() {
        return multiKeyTxnTestSuite(SecondaryDirtyReadTest.class, null,
                                    null);
                                    //new String[] {TxnTestCase.TXN_NULL});
    }

    private int nextKey;
    private Database priDb;
    private SecondaryDatabase secDb;
    private LockMode lockMode = LockMode.READ_UNCOMMITTED;

    /**
     * Closes databases, then calls the super.tearDown to close the env.
     */
    public void tearDown()
        throws Exception {

        if (secDb != null) {
            try {
                secDb.close();
            } catch (Exception e) {}
            secDb = null;
        }
        if (priDb != null) {
            try {
                priDb.close();
            } catch (Exception e) {}
            priDb = null;
        }
        super.tearDown();
    }

    /**
     * Tests that deleting primary records does not cause secondary
     * read-uncommitted to throw a "secondary corrupt" exception.
     */
    public void testDeleteWhileReadingByKey()
	throws Throwable {

        doTest("runReadUncommittedByKey", "runPrimaryDelete");
    }

    /**
     * Same as testDeleteWhileReadingByKey but does a scan.  Read-uncommitted
     * for scan and keyed reads are implemented differently, since scanning
     * moves to the next record when a deletion is detected while a keyed read
     * returns NOTFOUND.
     */
    public void testDeleteWhileScanning()
	throws Throwable {

        doTest("runReadUncommittedScan", "runPrimaryDelete");
    }

    /**
     * Tests that updating primary records, to cause deletion of the secondary
     * key record, does not cause secondary read-uncommitted to return
     * inconsistent data (a primary datum without a secondary key value).
     */
    public void testUpdateWhileReadingByKey()
	throws Throwable {

        doTest("runReadUncommittedByKey", "runPrimaryUpdate");
    }

    /**
     * Same as testUpdateWhileReadingByKey but does a scan.
     */
    public void testUpdateWhileScanning()
	throws Throwable {

        doTest("runReadUncommittedScan", "runPrimaryUpdate");
    }

    /**
     * Runs two threads for the given method names, after populating the
     * database.
     */
    public void doTest(String method1, String method2)
	throws Throwable {

        JUnitMethodThread tester1 = new JUnitMethodThread(method1 + "-t1",
                                                          method1, this);
        JUnitMethodThread tester2 = new JUnitMethodThread(method2 + "-t2",
                                                          method2, this);
        priDb = openPrimary("testDB");
        secDb = openSecondary(priDb, "testSecDB", false);
        addRecords();
        tester1.start();
        tester2.start();
        tester1.finishTest();
        tester2.finishTest();
        secDb.close();
        secDb = null;
        priDb.close();
        priDb = null;
    }

    /**
     * Deletes the key that is being read by the other thread.
     */
    public void runPrimaryDelete() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        while (nextKey < MAX_KEY - 1) {
            Transaction txn = txnBegin();
            key.setData(TestUtils.getTestArray(nextKey));
            OperationStatus status = priDb.delete(txn, key);
            if (status != OperationStatus.SUCCESS) {
                assertEquals(OperationStatus.NOTFOUND, status);
            }
            txnCommit(txn);
        }
    }

    /**
     * Updates the record for the key that is being read by the other thread,
     * changing the datum to -1 so it will cause the secondary key record to
     * be deleted.
     */
    public void runPrimaryUpdate() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (nextKey < MAX_KEY - 1) {
            Transaction txn = txnBegin();
            key.setData(TestUtils.getTestArray(nextKey));
            data.setData(TestUtils.getTestArray(-1));
            OperationStatus status = priDb.put(txn, key, data);
            assertEquals(OperationStatus.SUCCESS, status);
            txnCommit(txn);
        }
    }

    /**
     * Does a read-uncommitted by key, retrying until it is deleted by the
     * delete/update thread, then moves to the next key.  We shouldn't get an
     * exception, just a NOTFOUND when it is deleted.
     */
    public void runReadUncommittedByKey() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry pKey = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (nextKey < MAX_KEY - 1) {
            key.setData(TestUtils.getTestArray(nextKey));
            OperationStatus status = secDb.get(null, key, pKey, data,
                                               lockMode);
            if (status != OperationStatus.SUCCESS) {
                assertEquals(OperationStatus.NOTFOUND, status);
                nextKey++;
            } else {
                assertEquals(nextKey, TestUtils.getTestVal(key.getData()));
                assertEquals(nextKey, TestUtils.getTestVal(pKey.getData()));
                assertEquals(nextKey, TestUtils.getTestVal(data.getData()));
            }
        }
    }

    /**
     * Does a read-uncommitted scan through the whole key range, but moves
     * forward only after the key is deleted by the delete/update thread.  We
     * shouldn't get an exception or a NOTFOUND, but we may skip values when a
     * key is deleted.
     */
    public void runReadUncommittedScan() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry pKey = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        SecondaryCursor cursor = secDb.openSecondaryCursor(null, null);
        while (nextKey < MAX_KEY - 1) {
            OperationStatus status = cursor.getNext(key, pKey, data,
                                                    lockMode);
            assertEquals("nextKey=" + nextKey,
                         OperationStatus.SUCCESS, status);
            int keyFound = TestUtils.getTestVal(key.getData());
            assertEquals(keyFound, TestUtils.getTestVal(pKey.getData()));
            assertEquals(keyFound, TestUtils.getTestVal(data.getData()));
            /* Let the delete/update thread catch up. */
            nextKey = keyFound;
            if (nextKey < MAX_KEY - 1) {
                while (status != OperationStatus.KEYEMPTY) {
                    assertEquals(OperationStatus.SUCCESS, status);
                    status = cursor.getCurrent(key, pKey, data,
                                               lockMode);
                }
                nextKey = keyFound + 1;
            }
        }
        cursor.close();
    }

    /**
     * Adds records for the entire key range.
     */
    private void addRecords()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        Transaction txn = txnBegin();
        for (int i = 0; i < MAX_KEY; i += 1) {
            byte[] val = TestUtils.getTestArray(i);
            key.setData(val);
            data.setData(val);
            OperationStatus status = priDb.putNoOverwrite(txn, key, data);
            assertEquals(OperationStatus.SUCCESS, status);
        }
        txnCommit(txn);
    }

    /**
     * Opens the primary database.
     */
    private Database openPrimary(String name)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        Transaction txn = txnBegin();
        Database priDb;
        try {
            priDb = env.openDatabase(txn, name, dbConfig);
        } finally {
            txnCommit(txn);
        }
        assertNotNull(priDb);
        return priDb;
    }

    /**
     * Opens the secondary database.
     */
    private SecondaryDatabase openSecondary(Database priDb, String dbName,
                                            boolean allowDuplicates)
        throws DatabaseException {

        SecondaryConfig dbConfig = new SecondaryConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(allowDuplicates);
        if (useMultiKey) {
            dbConfig.setMultiKeyCreator
                (new SimpleMultiKeyCreator(new MyKeyCreator()));
        } else {
            dbConfig.setKeyCreator(new MyKeyCreator());
        }
        Transaction txn = txnBegin();
        SecondaryDatabase secDb;
        try {
            secDb = env.openSecondaryDatabase(txn, dbName, priDb, dbConfig);
        } finally {
            txnCommit(txn);
        }
        return secDb;
    }

    /**
     * Creates secondary keys for a primary datum with a non-negative value.
     */
    private static class MyKeyCreator implements SecondaryKeyCreator {

        public boolean createSecondaryKey(SecondaryDatabase secondary,
                                          DatabaseEntry key,
                                          DatabaseEntry data,
                                          DatabaseEntry result)
            throws DatabaseException {

            int val = TestUtils.getTestVal(data.getData());
            if (val >= 0) {
                result.setData(TestUtils.getTestArray(val));
                return true;
            } else {
                return false;
            }
        }
    }
}
