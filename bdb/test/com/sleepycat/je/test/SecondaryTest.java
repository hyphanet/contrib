/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SecondaryTest.java,v 1.38.2.2 2007/06/13 21:22:18 mark Exp $
 */

package com.sleepycat.je.test;

import java.util.Arrays;
import java.util.List;

import junit.framework.Test;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.util.TestUtils;

public class SecondaryTest extends MultiKeyTxnTestCase {

    private static final int NUM_RECS = 5;
    private static final int KEY_OFFSET = 100;

    private JUnitThread junitThread;

    private static EnvironmentConfig envConfig = TestUtils.initEnvConfig();
    static {
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                 "6");
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setLockTimeout(1); // to speed up intentional deadlocks
        envConfig.setAllowCreate(true);
    }

    public static Test suite() {

        return multiKeyTxnTestSuite(SecondaryTest.class, envConfig, null);
    }

    public void tearDown()
        throws Exception {

        super.tearDown();
        if (junitThread != null) {
            while (junitThread.isAlive()) {
                junitThread.interrupt();
                Thread.yield();
            }
            junitThread = null;
        }
    }

    public void testPutAndDelete()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();

        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry();
        OperationStatus status;
        Transaction txn = txnBegin();
        
        /* Database.put() */
        status = priDb.put(txn, entry(1), entry(2));
        assertSame(OperationStatus.SUCCESS, status);
        status = secDb.get(txn, entry(102), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(1), key);
        assertDataEquals(entry(2), data);

        /* Database.putNoOverwrite() */
        status = priDb.putNoOverwrite(txn, entry(1), entry(1));
        assertSame(OperationStatus.KEYEXIST, status);
        status = secDb.get(txn, entry(102), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(1), key);
        assertDataEquals(entry(2), data);
        
        /* Database.put() overwrite */
        status = priDb.put(txn, entry(1), entry(3));
        assertSame(OperationStatus.SUCCESS, status);
        status = secDb.get(txn, entry(102), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);
        status = secDb.get(txn, entry(103), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(1), key);
        assertDataEquals(entry(3), data);
        
        /* Database.delete() */
        status = priDb.delete(txn, entry(1));
        assertSame(OperationStatus.SUCCESS, status);
        status = priDb.delete(txn, entry(1));
        assertSame(OperationStatus.NOTFOUND, status);
        status = secDb.get(txn, entry(103), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);
        
        /* SecondaryDatabase.delete() */
        status = priDb.put(txn, entry(1), entry(1));
        assertSame(OperationStatus.SUCCESS, status);
        status = priDb.put(txn, entry(2), entry(1));
        assertSame(OperationStatus.SUCCESS, status);
        status = secDb.get(txn, entry(101), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(1), key);
        assertDataEquals(entry(1), data);
        status = secDb.delete(txn, entry(101));
        assertSame(OperationStatus.SUCCESS, status);
        status = secDb.delete(txn, entry(101));
        assertSame(OperationStatus.NOTFOUND, status);
        status = secDb.get(txn, entry(101), key, data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);
        status = priDb.get(txn, entry(1), data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);
        status = priDb.get(txn, entry(2), data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);

        /*
         * Database.putNoDupData() cannot be called since the primary cannot be
         * configured for duplicates.
         */

        /* Primary and secondary are empty now. */

        /* Get a txn for a cursor. */
        txnCommit(txn);
        txn = txnBeginCursor();

        Cursor priCursor = null;
        SecondaryCursor secCursor = null;
        try {
            priCursor = priDb.openCursor(txn, null);
            secCursor = secDb.openSecondaryCursor(txn, null);

            /* Cursor.putNoOverwrite() */
            status = priCursor.putNoOverwrite(entry(1), entry(2));
            assertSame(OperationStatus.SUCCESS, status);
            status = secCursor.getSearchKey(entry(102), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(1), key);
            assertDataEquals(entry(2), data);

            /* Cursor.putCurrent() */
            status = priCursor.putCurrent(entry(3));
            assertSame(OperationStatus.SUCCESS, status);
            status = secCursor.getSearchKey(entry(102), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);
            status = secCursor.getSearchKey(entry(103), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(1), key);
            assertDataEquals(entry(3), data);

            /* Cursor.delete() */
            status = priCursor.delete();
            assertSame(OperationStatus.SUCCESS, status);
            status = priCursor.delete();
            assertSame(OperationStatus.KEYEMPTY, status);
            status = secCursor.getSearchKey(entry(103), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);
            status = priCursor.getSearchKey(entry(1), data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /* Cursor.put() */
            status = priCursor.put(entry(1), entry(4));
            assertSame(OperationStatus.SUCCESS, status);
            status = secCursor.getSearchKey(entry(104), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(1), key);
            assertDataEquals(entry(4), data);

            /* SecondaryCursor.delete() */
            status = secCursor.delete();
            assertSame(OperationStatus.SUCCESS, status);
            status = secCursor.delete();
            assertSame(OperationStatus.KEYEMPTY, status);
            status = secCursor.getCurrent(new DatabaseEntry(), key, data,
                                          LockMode.DEFAULT);
            assertSame(OperationStatus.KEYEMPTY, status);
            status = secCursor.getSearchKey(entry(104), key, data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);
            status = priCursor.getSearchKey(entry(1), data,
                                            LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /*
             * Cursor.putNoDupData() cannot be called since the primary cannot
             * be configured for duplicates.
             */

            /* Primary and secondary are empty now. */
        } finally {
            if (secCursor != null) {
                secCursor.close();
            }
            if (priCursor != null) {
                priCursor.close();
            }
        }

        txnCommit(txn);
        secDb.close();
        priDb.close();
    }

    public void testGet()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();

        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry secKey = new DatabaseEntry();
        OperationStatus status;
        Transaction txn = txnBegin();

        /*
         * For parameters that do not require initialization with a non-null
         * data array, we set them to null to make sure this works. [#12121]
         */

        /* Add one record for each key with one data/duplicate. */
        for (int i = 0; i < NUM_RECS; i += 1) {
            status = priDb.put(txn, entry(i), entry(i));
            assertSame(OperationStatus.SUCCESS, status);
        }

        /* SecondaryDatabase.get() */
        for (int i = 0; i < NUM_RECS; i += 1) {

            data.setData(null);
            status = secDb.get(txn, entry(i + KEY_OFFSET), key,
                               data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(i), key);
            assertDataEquals(entry(i), data);
        }
        data.setData(null);
        status = secDb.get(txn, entry(NUM_RECS + KEY_OFFSET), key,
                           data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);

        /* SecondaryDatabase.getSearchBoth() */
        for (int i = 0; i < NUM_RECS; i += 1) {
            data.setData(null);
            status = secDb.getSearchBoth(txn, entry(i + KEY_OFFSET), entry(i),
                                         data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(i), data);
        }
        data.setData(null);
        status = secDb.getSearchBoth(txn, entry(NUM_RECS + KEY_OFFSET),
                                     entry(NUM_RECS), data, LockMode.DEFAULT);
        assertSame(OperationStatus.NOTFOUND, status);

        /* Get a cursor txn. */
        txnCommit(txn);
        txn = txnBeginCursor();

        SecondaryCursor cursor = secDb.openSecondaryCursor(txn, null);
        try {
            /* SecondaryCursor.getFirst()/getNext() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getFirst(secKey, key, data, LockMode.DEFAULT);
            for (int i = 0; i < NUM_RECS; i += 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getNext(secKey, key, data, LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getCurrent() (last) */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getCurrent(secKey, key, data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(NUM_RECS - 1 + KEY_OFFSET), secKey);
            assertDataEquals(entry(NUM_RECS - 1), key);
            assertDataEquals(entry(NUM_RECS - 1), data);
            assertPriLocked(priDb, key);

            /* SecondaryCursor.getLast()/getPrev() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getLast(secKey, key, data, LockMode.DEFAULT);
            for (int i = NUM_RECS - 1; i >= 0; i -= 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getPrev(secKey, key, data, LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getCurrent() (first) */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getCurrent(secKey, key, data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(0 + KEY_OFFSET), secKey);
            assertDataEquals(entry(0), key);
            assertDataEquals(entry(0), data);
            assertPriLocked(priDb, key);

            /* SecondaryCursor.getSearchKey() */
            key.setData(null);
            data.setData(null);
            status = cursor.getSearchKey(entry(KEY_OFFSET - 1), key,
                                         data, LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);
            for (int i = 0; i < NUM_RECS; i += 1) {
                key.setData(null);
                data.setData(null);
                status = cursor.getSearchKey(entry(i + KEY_OFFSET), key,
                                             data, LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key);
            }
            key.setData(null);
            data.setData(null);
            status = cursor.getSearchKey(entry(NUM_RECS + KEY_OFFSET), key,
                                         data, LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getSearchBoth() */
            data.setData(null);
            status = cursor.getSearchKey(entry(KEY_OFFSET - 1), entry(0),
                                         data, LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);
            for (int i = 0; i < NUM_RECS; i += 1) {
                data.setData(null);
                status = cursor.getSearchBoth(entry(i + KEY_OFFSET), entry(i),
                                              data, LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, entry(i));
            }
            data.setData(null);
            status = cursor.getSearchBoth(entry(NUM_RECS + KEY_OFFSET),
                                          entry(NUM_RECS), data,
                                          LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getSearchKeyRange() */
            key.setData(null);
            data.setData(null);
            status = cursor.getSearchKeyRange(entry(KEY_OFFSET - 1), key,
                                              data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(0), key);
            assertDataEquals(entry(0), data);
            assertPriLocked(priDb, key);
            for (int i = 0; i < NUM_RECS; i += 1) {
                key.setData(null);
                data.setData(null);
                status = cursor.getSearchKeyRange(entry(i + KEY_OFFSET), key,
                                                  data, LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key);
            }
            key.setData(null);
            data.setData(null);
            status = cursor.getSearchKeyRange(entry(NUM_RECS + KEY_OFFSET),
                                              key, data, LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getSearchBothRange() */
            data.setData(null);
            status = cursor.getSearchBothRange(entry(1 + KEY_OFFSET), entry(1),
                                               data, LockMode.DEFAULT);
            assertSame(OperationStatus.SUCCESS, status);
            assertDataEquals(entry(1), data);
            assertPriLocked(priDb, entry(1));
            for (int i = 0; i < NUM_RECS; i += 1) {
                data.setData(null);
                status = cursor.getSearchBothRange(entry(i + KEY_OFFSET),
                                                   entry(i), data,
                                                   LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, entry(i));
            }
            data.setData(null);
            status = cursor.getSearchBothRange(entry(NUM_RECS + KEY_OFFSET),
                                               entry(NUM_RECS), data,
                                               LockMode.DEFAULT);
            assertSame(OperationStatus.NOTFOUND, status);

            /* Add one duplicate for each key. */
            Cursor priCursor = priDb.openCursor(txn, null);
            try {
                for (int i = 0; i < NUM_RECS; i += 1) {
                    status = priCursor.put(entry(i + KEY_OFFSET), entry(i));
                    assertSame(OperationStatus.SUCCESS, status);
                }
            } finally {
                priCursor.close();
            }

            /* SecondaryCursor.getNextDup() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getFirst(secKey, key, data, LockMode.DEFAULT);
            for (int i = 0; i < NUM_RECS; i += 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getNextDup(secKey, key, data,
                                           LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i + KEY_OFFSET), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getNextDup(secKey, key, data,
                                           LockMode.DEFAULT);
                assertSame(OperationStatus.NOTFOUND, status);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getNext(secKey, key, data, LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getNextNoDup() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getFirst(secKey, key, data, LockMode.DEFAULT);
            for (int i = 0; i < NUM_RECS; i += 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getNextNoDup(secKey, key, data,
                                             LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getPrevDup() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getLast(secKey, key, data, LockMode.DEFAULT);
            for (int i = NUM_RECS - 1; i >= 0; i -= 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i + KEY_OFFSET), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getPrevDup(secKey, key, data,
                                           LockMode.DEFAULT);
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getPrevDup(secKey, key, data,
                                           LockMode.DEFAULT);
                assertSame(OperationStatus.NOTFOUND, status);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getPrev(secKey, key, data, LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);

            /* SecondaryCursor.getPrevNoDup() */
            secKey.setData(null);
            key.setData(null);
            data.setData(null);
            status = cursor.getLast(secKey, key, data, LockMode.DEFAULT);
            for (int i = NUM_RECS - 1; i >= 0; i -= 1) {
                assertSame(OperationStatus.SUCCESS, status);
                assertDataEquals(entry(i + KEY_OFFSET), secKey);
                assertDataEquals(entry(i + KEY_OFFSET), key);
                assertDataEquals(entry(i), data);
                assertPriLocked(priDb, key, data);
                secKey.setData(null);
                key.setData(null);
                data.setData(null);
                status = cursor.getPrevNoDup(secKey, key, data,
                                             LockMode.DEFAULT);
            }
            assertSame(OperationStatus.NOTFOUND, status);
        } finally {
            cursor.close();
        }

        txnCommit(txn);
        secDb.close();
        priDb.close();
    }

    public void testOpenAndClose()
        throws DatabaseException {

        Database priDb = openDatabase(false, "testDB", false);

        /* Open two secondaries as regular databases and as secondaries. */
        Database secDbDetached = openDatabase(true, "testSecDB", false);
        SecondaryDatabase secDb = openSecondary(priDb, true, "testSecDB",
                                                false, false);
        Database secDb2Detached = openDatabase(true, "testSecDB2", false);
        SecondaryDatabase secDb2 = openSecondary(priDb, true, "testSecDB2",
                                                 false, false);
        assertEquals(priDb.getSecondaryDatabases(),
                     Arrays.asList(new SecondaryDatabase[] {secDb, secDb2}));

        Transaction txn = txnBegin();

        /* Check that primary writes to both secondaries. */
        checkSecondaryUpdate(txn, priDb, 1, secDbDetached, true,
                                            secDb2Detached, true);
        
        /* New txn before closing database. */
        txnCommit(txn);
        txn = txnBegin();

        /* Close 2nd secondary. */
        secDb2.close();
        assertEquals(priDb.getSecondaryDatabases(),
                     Arrays.asList(new SecondaryDatabase[] {secDb }));

        /* Check that primary writes to 1st secondary only. */
        checkSecondaryUpdate(txn, priDb, 2, secDbDetached, true,
                                             secDb2Detached, false);
        
        /* New txn before closing database. */
        txnCommit(txn);
        txn = txnBegin();

        /* Close 1st secondary. */
        secDb.close();
        assertEquals(0, priDb.getSecondaryDatabases().size());

        /* Check that primary writes to no secondaries. */
        checkSecondaryUpdate(txn, priDb, 3, secDbDetached, false,
                                            secDb2Detached, false);

        /* Open the two secondaries again. */
        secDb = openSecondary(priDb, true, "testSecDB", false, false);
        secDb2 = openSecondary(priDb, true, "testSecDB2", false, false);
        assertEquals(priDb.getSecondaryDatabases(),
                     Arrays.asList(new SecondaryDatabase[] {secDb, secDb2}));

        /* Check that primary writes to both secondaries. */
        checkSecondaryUpdate(txn, priDb, 4, secDbDetached, true,
                                            secDb2Detached, true);

        /* Close the primary first to disassociate secondaries. */
        txnCommit(txn);
        priDb.close();
        assertNull(secDb.getPrimaryDatabase());
        assertNull(secDb2.getPrimaryDatabase());
        secDb2.close();
        secDb.close();

        secDb2Detached.close();
        secDbDetached.close();
    }

    /**
     * Check that primary put() writes to each secondary that is open.
     */
    private void checkSecondaryUpdate(Transaction txn, Database priDb, int val,
                                      Database secDb, boolean expectSecDbVal,
                                      Database secDb2, boolean expectSecDb2Val)
        throws DatabaseException {

        OperationStatus status;
        DatabaseEntry data = new DatabaseEntry();
        int secVal = KEY_OFFSET + val;

        status = priDb.put(txn, entry(val), entry(val));
        assertSame(OperationStatus.SUCCESS, status);

        status = secDb.get(txn, entry(secVal), data, LockMode.DEFAULT);
        assertSame(expectSecDbVal ? OperationStatus.SUCCESS
                                  : OperationStatus.NOTFOUND, status);


        status = secDb2.get(txn, entry(secVal), data, LockMode.DEFAULT);
        assertSame(expectSecDb2Val ? OperationStatus.SUCCESS
                                   : OperationStatus.NOTFOUND, status);

        status = priDb.delete(txn, entry(val));
        assertSame(OperationStatus.SUCCESS, status);
    }

    public void testReadOnly()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();
        OperationStatus status;
        Transaction txn = txnBegin();

        for (int i = 0; i < NUM_RECS; i += 1) {
            status = priDb.put(txn, entry(i), entry(i));
            assertSame(OperationStatus.SUCCESS, status);
        }

        /*
         * Secondaries can be opened without a key creator if the primary is
         * read only.  openSecondary will specify a null key creator if the 
         * readOnly param is false.
         */
        Database readOnlyPriDb = openDatabase(false, "testDB", true);
        SecondaryDatabase readOnlySecDb = openSecondary(readOnlyPriDb,
                                                        true, "testSecDB",
                                                        false, true);
        assertNull(readOnlySecDb.getSecondaryConfig().getKeyCreator());
        verifyRecords(txn, readOnlySecDb, NUM_RECS, true);

        txnCommit(txn);
        readOnlySecDb.close();
        readOnlyPriDb.close();
        secDb.close();
        priDb.close();
    }

    public void testPopulate()
        throws DatabaseException {

        Database priDb = openDatabase(false, "testDB", false);
        Transaction txn = txnBegin();

        /* Test population of newly created secondary database. */
        
        for (int i = 0; i < NUM_RECS; i += 1) {
            assertSame(OperationStatus.SUCCESS, 
                       priDb.put(txn, entry(i), entry(i)));
        }
        txnCommit(txn);

        SecondaryDatabase secDb = openSecondary(priDb, true, "testSecDB",
                                                true, false);
        txn = txnBegin();
        verifyRecords(txn, secDb, NUM_RECS, true);
        txnCommit(txn);

        /*
         * Clear secondary and perform populate again, to test the case where
         * an existing database is opened, and therefore a write txn will only
         * be created in order to populate it
         */
        Database secDbDetached = openDatabase(true, "testSecDB", false);
        secDb.close();
        txn = txnBegin();
        for (int i = 0; i < NUM_RECS; i += 1) {
            assertSame(OperationStatus.SUCCESS, 
                       secDbDetached.delete(txn, entry(i + KEY_OFFSET)));
        }
        verifyRecords(txn, secDbDetached, 0, true);
        txnCommit(txn);
        secDb = openSecondary(priDb, true, "testSecDB", true, false);
        txn = txnBegin();
        verifyRecords(txn, secDb, NUM_RECS, true);
        verifyRecords(txn, secDbDetached, NUM_RECS, true);

        txnCommit(txn);
        secDbDetached.close();
        secDb.close();
        priDb.close();
    }

    public void testTruncate()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();
        Transaction txn = txnBegin();
        
        for (int i = 0; i < NUM_RECS; i += 1) {
            priDb.put(txn, entry(i), entry(i));
        }
        verifyRecords(txn, priDb, NUM_RECS, false);
        verifyRecords(txn, secDb, NUM_RECS, true);
        txnCommit(txn);
        secDb.close();
        priDb.close();

        txn = txnBegin();
        assertEquals(NUM_RECS, env.truncateDatabase(txn, "testDB", true));
        assertEquals(NUM_RECS, env.truncateDatabase(txn, "testSecDB", true));
        txnCommit(txn);

        secDb = initDb();
        priDb = secDb.getPrimaryDatabase();

        txn = txnBegin();
        verifyRecords(txn, priDb, 0, false);
        verifyRecords(txn, secDb, 0, true);
        txnCommit(txn);

        secDb.close();
        priDb.close();
    }

    private void verifyRecords(Transaction txn, Database db, int numRecs,
                               boolean isSecondary)
        throws DatabaseException {

        /* We're only reading, so txn may be null. */
        Cursor cursor = db.openCursor(txn, null);
        try {
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry key = new DatabaseEntry();
            OperationStatus status;
            int count = 0;
            status = cursor.getFirst(key, data, LockMode.DEFAULT);
            while (status == OperationStatus.SUCCESS) {
                assertDataEquals(entry(count), data);
                if (isSecondary) {
                    assertDataEquals(entry(count + KEY_OFFSET), key);
                } else {
                    assertDataEquals(entry(count), key);
                }
                count += 1;
                status = cursor.getNext(key, data, LockMode.DEFAULT);
            }
            assertEquals(numRecs, count);
        } finally {
            cursor.close();
        }
    }

    public void testUniqueSecondaryKey()
        throws DatabaseException {

        Database priDb = openDatabase(false, "testDB", false);
        SecondaryDatabase secDb = openSecondary(priDb, false, "testSecDB",
                                                false, false);
        DatabaseEntry key;
        DatabaseEntry data;
        DatabaseEntry pkey = new DatabaseEntry();
        Transaction txn;

        /* Put {0, 0} */
        txn = txnBegin();
        key = entry(0);
        data = entry(0);
        priDb.put(txn, key, data);
        txnCommit(txn);
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(null, entry(0 + KEY_OFFSET),
                               pkey, data, null));
        assertEquals(0, TestUtils.getTestVal(pkey.getData()));
        assertEquals(0, TestUtils.getTestVal(data.getData()));

        /* Put {1, 1} */
        txn = txnBegin();
        key = entry(1);
        data = entry(1);
        priDb.put(txn, key, data);
        txnCommit(txn);
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(null, entry(1 + KEY_OFFSET),
                               pkey, data, null));
        assertEquals(1, TestUtils.getTestVal(pkey.getData()));
        assertEquals(1, TestUtils.getTestVal(data.getData()));

        /* Put {2, 0} */
        txn = txnBegin();
        key = entry(2);
        data = entry(0);
        try {
            priDb.put(txn, key, data);
            /* Expect exception because secondary key must be unique. */
            fail();
        } catch (DatabaseException e) {
            txnAbort(txn);
            /* Ensure that primary record was not inserted. */
            assertEquals(OperationStatus.NOTFOUND,
                         secDb.get(null, key, data, null));
            /* Ensure that secondary record has not changed. */
            assertEquals(OperationStatus.SUCCESS,
                         secDb.get(null, entry(0 + KEY_OFFSET),
                                   pkey, data, null));
            assertEquals(0, TestUtils.getTestVal(pkey.getData()));
            assertEquals(0, TestUtils.getTestVal(data.getData()));
        }

        /* Overwrite {1, 1} */
        txn = txnBegin();
        key = entry(1);
        data = entry(1);
        priDb.put(txn, key, data);
        txnCommit(txn);
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(null, entry(1 + KEY_OFFSET),
                               pkey, data, null));
        assertEquals(1, TestUtils.getTestVal(pkey.getData()));
        assertEquals(1, TestUtils.getTestVal(data.getData()));

        /* Modify secondary key to {1, 3} */
        txn = txnBegin();
        key = entry(1);
        data = entry(3);
        priDb.put(txn, key, data);
        txnCommit(txn);
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(null, entry(3 + KEY_OFFSET),
                               pkey, data, null));
        assertEquals(1, TestUtils.getTestVal(pkey.getData()));
        assertEquals(3, TestUtils.getTestVal(data.getData()));

        secDb.close();
        priDb.close();
    }

    /**
     * @deprecated use of Database.truncate
     */
    public void testOperationsNotAllowed()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();
        Transaction txn = txnBegin();

        /* Open secondary without a key creator. */
        try {
            env.openSecondaryDatabase(txn, "xxx", priDb, null);
            fail();
        } catch (NullPointerException expected) { }
        try {
            env.openSecondaryDatabase(txn, "xxx", priDb,
                                      new SecondaryConfig());
            fail();
        } catch (NullPointerException expected) { }

        /* Open secondary with both single and multi key creators. */
        SecondaryConfig config = new SecondaryConfig();
        config.setKeyCreator(new MyKeyCreator());
        config.setMultiKeyCreator
            (new SimpleMultiKeyCreator(new MyKeyCreator()));
        try {
            env.openSecondaryDatabase(txn, "xxx", priDb, config);
            fail();
        } catch (IllegalArgumentException expected) { }
        
        /* Database operations. */

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);

        try {
            secDb.getSearchBoth(txn, key, data, LockMode.DEFAULT);
            fail();
        } catch (UnsupportedOperationException expected) { }

        try {
            secDb.put(txn, key, data);
            fail();
        } catch (UnsupportedOperationException expected) { }

        try {
            secDb.putNoOverwrite(txn, key, data);
            fail();
        } catch (UnsupportedOperationException expected) { }

        try {
            secDb.putNoDupData(txn, key, data);
            fail();
        } catch (UnsupportedOperationException expected) { }

        try {
            secDb.truncate(txn, true);
            fail();
        } catch (UnsupportedOperationException expected) { }

        try {
            secDb.join(new Cursor[0], null);
            fail();
        } catch (UnsupportedOperationException expected) { }

        /* Cursor operations. */

        txnCommit(txn);
        txn = txnBeginCursor();

        SecondaryCursor cursor = null;
        try {
            cursor = secDb.openSecondaryCursor(txn, null);

            try {
                cursor.getSearchBoth(key, data, LockMode.DEFAULT);
                fail();
            } catch (UnsupportedOperationException expected) { }

            try {
                cursor.getSearchBothRange(key, data, LockMode.DEFAULT);
                fail();
            } catch (UnsupportedOperationException expected) { }

            try {
                cursor.putCurrent(data);
                fail();
            } catch (UnsupportedOperationException expected) { }

            try {
                cursor.put(key, data);
                fail();
            } catch (UnsupportedOperationException expected) { }

            try {
                cursor.putNoOverwrite(key, data);
                fail();
            } catch (UnsupportedOperationException expected) { }

            try {
                cursor.putNoDupData(key, data);
                fail();
            } catch (UnsupportedOperationException expected) { }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        txnCommit(txn);
        secDb.close();
        priDb.close();

        /* Primary with duplicates. */
        priDb = openDatabase(true, "testDBWithDups", false);
        try {
            openSecondary(priDb, true, "testSecDB", false, false);
            fail();
        } catch (IllegalArgumentException expected) {}

        priDb.close();

        /* Single secondary with two primaries.*/
        Database pri1 = openDatabase(false, "pri1", false);
        Database pri2 = openDatabase(false, "pri2", false);
        Database sec1 = openSecondary(pri1, false, "sec", false, false);
        try {
            openSecondary(pri2, false, "sec", false, false);
            fail();
        } catch (IllegalArgumentException expected) {}
        sec1.close();
        pri1.close();
        pri2.close();
    }

    /**
     * Test that null can be passed for the LockMode to all get methods.
     */
    public void testNullLockMode()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();
        Transaction txn = txnBegin();

        DatabaseEntry key = entry(0);
        DatabaseEntry data = entry(0);
        DatabaseEntry secKey = entry(KEY_OFFSET);
        DatabaseEntry found = new DatabaseEntry();
        DatabaseEntry found2 = new DatabaseEntry();
        DatabaseEntry found3 = new DatabaseEntry();

        assertEquals(OperationStatus.SUCCESS,
                     priDb.put(txn, key, data));
        assertEquals(OperationStatus.SUCCESS,
                     priDb.put(txn, entry(1), data));
        assertEquals(OperationStatus.SUCCESS,
                     priDb.put(txn, entry(2), entry(2)));

        /* Database operations. */

        assertEquals(OperationStatus.SUCCESS,
                     priDb.get(txn, key, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     priDb.getSearchBoth(txn, key, data, null));
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(txn, secKey, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secDb.get(txn, secKey, found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secDb.getSearchBoth(txn, secKey, key, found, null));

        /* Cursor operations. */

        txnCommit(txn);
        txn = txnBeginCursor();
        Cursor cursor = priDb.openCursor(txn, null);
        SecondaryCursor secCursor = secDb.openSecondaryCursor(txn, null);

        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchKey(key, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchBoth(key, data, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchKeyRange(key, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchBothRange(key, data, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getFirst(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getNext(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getPrev(found, found2, null));
        assertEquals(OperationStatus.NOTFOUND,
                     cursor.getNextDup(found, found2, null));
        assertEquals(OperationStatus.NOTFOUND,
                     cursor.getPrevDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getNextNoDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getPrevNoDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getLast(found, found2, null));

        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchKey(secKey, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchKeyRange(secKey, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getFirst(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNext(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrev(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNextDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrevDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNextNoDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrevNoDup(found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getLast(found, found2, null));

        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchKey(secKey, found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchBoth(secKey, data, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchKeyRange(secKey, found, found2, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchBothRange(secKey, data, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getFirst(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNext(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrev(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNextDup(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrevDup(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getNextNoDup(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getPrevNoDup(found, found2, found3, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getLast(found, found2, found3, null));

        secCursor.close();
        cursor.close();
        txnCommit(txn);
        secDb.close();
        priDb.close();
        env.close();
        env = null;
    }

    /**
     * Test that an exception is thrown when a cursor is used in the wrong
     * state.  No put or get is allowed in the closed state, and certain gets
     * and puts are not allowed in the uninitialized state.
     */
    public void testCursorState()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();
        Transaction txn = txnBegin();

        DatabaseEntry key = entry(0);
        DatabaseEntry data = entry(0);
        DatabaseEntry secKey = entry(KEY_OFFSET);
        DatabaseEntry found = new DatabaseEntry();
        DatabaseEntry found2 = new DatabaseEntry();

        assertEquals(OperationStatus.SUCCESS,
                     priDb.put(txn, key, data));

        txnCommit(txn);
        txn = txnBeginCursor();
        Cursor cursor = priDb.openCursor(txn, null);
        SecondaryCursor secCursor = secDb.openSecondaryCursor(txn, null);

        /* Check the uninitialized state for certain operations. */

        try {
            cursor.count();
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.delete();
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.putCurrent(data);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getCurrent(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getNextDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getPrevDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}

        try {
            secCursor.count();
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.delete();
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getCurrent(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getNextDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getPrevDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}

        /* Initialize, then close, then check all operations. */

        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchKey(key, found, null));
        assertEquals(OperationStatus.SUCCESS,
                     secCursor.getSearchKey(secKey, found, null));
        secCursor.close();
        cursor.close();

        try {
            cursor.close();
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.count();
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.delete();
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.put(key, data);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.putNoOverwrite(key, data);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.putNoDupData(key, data);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.putCurrent(data);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getCurrent(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getSearchKey(key, found, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getSearchBoth(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getSearchKeyRange(key, found, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getSearchBothRange(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getFirst(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getNext(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getPrev(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getNextDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getPrevDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getNextNoDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getPrevNoDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            cursor.getLast(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}

        try {
            secCursor.close();
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.count();
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.delete();
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getCurrent(key, data, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getSearchKey(secKey, found, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getSearchKeyRange(secKey, found, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getFirst(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getNext(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getPrev(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getNextDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getPrevDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getNextNoDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getPrevNoDup(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}
        try {
            secCursor.getLast(found, found2, null);
            fail();
        } catch (DatabaseException expected) {}

        txnCommit(txn);
        secDb.close();
        priDb.close();
        env.close();
        env = null;
    }

    /**
     * [#14966]
     */
    public void testDirtyReadPartialGet()
        throws DatabaseException {

        SecondaryDatabase secDb = initDb();
        Database priDb = secDb.getPrimaryDatabase();

        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry secKey = new DatabaseEntry();
        OperationStatus status;

        /* Put a record */
        Transaction txn = txnBegin();
        status = priDb.put(txn, entry(0), entry(0));
        assertSame(OperationStatus.SUCCESS, status);
        txnCommit(txn);

        /* Regular get */
        status = secDb.get(null, entry(0 + KEY_OFFSET), key,
                           data, LockMode.DEFAULT);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(0), key);
        assertDataEquals(entry(0), data);

        /* Dirty read returning no data */
        data.setPartial(0, 0, true);
        status = secDb.get(null, entry(0 + KEY_OFFSET), key,
                           data, LockMode.READ_UNCOMMITTED);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(0), key);
        assertEquals(0, data.getData().length);
        assertEquals(0, data.getSize());

        /* Dirty read returning partial data */
        data.setPartial(0, 1, true);
        status = secDb.get(null, entry(0 + KEY_OFFSET), key,
                           data, LockMode.READ_UNCOMMITTED);
        assertSame(OperationStatus.SUCCESS, status);
        assertDataEquals(entry(0), key);
        assertEquals(1, data.getData().length);
        assertEquals(1, data.getSize());

        secDb.close();
        priDb.close();
    }

    /**
     * Open environment, primary and secondary db
     */
    private SecondaryDatabase initDb()
        throws DatabaseException {

        Database priDb = openDatabase(false, "testDB", false);
        SecondaryDatabase secDb = openSecondary(priDb, true, "testSecDB",
                                                false, false);
        return secDb;
    }

    private Database openDatabase(boolean allowDuplicates, String name,
                                  boolean readOnly)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(allowDuplicates);
        dbConfig.setReadOnly(readOnly);
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

    private SecondaryDatabase openSecondary(Database priDb, 
                                            boolean allowDuplicates,
                                            String dbName,
                                            boolean allowPopulate,
                                            boolean readOnly)
        throws DatabaseException {

        List secListBefore = priDb.getSecondaryDatabases();
        SecondaryConfig dbConfig = new SecondaryConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(allowDuplicates);
        dbConfig.setReadOnly(readOnly);
        dbConfig.setAllowPopulate(allowPopulate);
        if (!readOnly) {
            if (useMultiKey) {
                dbConfig.setMultiKeyCreator
                    (new SimpleMultiKeyCreator(new MyKeyCreator()));
            } else {
                dbConfig.setKeyCreator(new MyKeyCreator());
            }
        }
        Transaction txn = txnBegin();
        SecondaryDatabase secDb;
        try {
            secDb = env.openSecondaryDatabase(txn, dbName, priDb, dbConfig);
        } finally {
            txnCommit(txn);
        }
        assertNotNull(secDb);

        /* Check configuration. */
        assertSame(priDb, secDb.getPrimaryDatabase());
        SecondaryConfig config2 = secDb.getSecondaryConfig();
        assertEquals(allowPopulate, config2.getAllowPopulate());
        assertEquals(dbConfig.getKeyCreator(), config2.getKeyCreator());

        /* Make sure the new secondary is added to the primary's list. */
        List secListAfter = priDb.getSecondaryDatabases();
        assertTrue(secListAfter.remove(secDb));
        assertEquals(secListBefore, secListAfter);

        return secDb;
    }

    private DatabaseEntry entry(int val) {

        return new DatabaseEntry(TestUtils.getTestArray(val));
    }

    private void assertDataEquals(DatabaseEntry e1, DatabaseEntry e2) {
        assertTrue(e1.equals(e2));
    }

    private void assertPriLocked(Database priDb, DatabaseEntry key) {
        assertPriLocked(priDb, key, null);
    }

    /**
     * Checks that the given key (or both key and data if data is non-null) is
     * locked in the primary database.  The primary record should be locked
     * whenever a secondary cursor is positioned to point to that primary
     * record. [#15573]
     */
    private void assertPriLocked(final Database priDb,
                                 final DatabaseEntry key,
                                 final DatabaseEntry data) {

        /*
         * Whether the record is locked transactionally or not in the current
         * thread, we should not be able to write lock the record
         * non-transactionally in another thread.
         */
        final StringBuffer error = new StringBuffer();
        junitThread = new JUnitThread("primary-locker") {
            public void testBody() 
                throws DatabaseException {
                try {
                    if (data != null) {
                        priDb.getSearchBoth(null, key, data, LockMode.RMW);
                    } else {
                        DatabaseEntry myData = new DatabaseEntry();
                        priDb.get(null, key, myData, LockMode.RMW);
                    }
                    error.append("Expected DeadlockException");
                } catch (DeadlockException expected) {
                }
            }
        };

        junitThread.start();
        Throwable t = null;
        try {
            junitThread.finishTest();
        } catch (Throwable e) {
            t = e;
        } finally {
            junitThread = null;
        }

        if (t != null) {
            t.printStackTrace();
            fail(t.toString());
        }
        if (error.length() > 0) {
            fail(error.toString());
        }
    }

    private static class MyKeyCreator implements SecondaryKeyCreator {

        public boolean createSecondaryKey(SecondaryDatabase secondary,
                                          DatabaseEntry key,
                                          DatabaseEntry data,
                                          DatabaseEntry result)
            throws DatabaseException {

            result.setData(
                TestUtils.getTestArray(
                    TestUtils.getTestVal(data.getData()) + KEY_OFFSET));
            return true;
        }
    }
}
