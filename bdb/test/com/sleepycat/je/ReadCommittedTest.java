/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ReadCommittedTest.java,v 1.5.2.1 2007/02/01 14:50:05 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests the read-committed (degree 2) isolation level.
 */
public class ReadCommittedTest extends TestCase {

    private File envHome;
    private Environment env;
    private Database db;

    public ReadCommittedTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.out.println("tearDown: " + e);
            }
        }

        env = null;
        db = null;

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    private void open()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        /* Control over isolation level is required by this test. */
        TestUtils.clearIsolationLevel(envConfig);

        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        db = env.openDatabase(null, "foo", dbConfig);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        for (int i = 100; i <= 200; i += 100) {
            for (int j = 1; j <= 5; j += 1) {
                IntegerBinding.intToEntry(i + j, key);
                IntegerBinding.intToEntry(0, data);
                db.put(null, key, data);
            }
        }
    }

    private void close()
        throws DatabaseException {

        db.close();
        db = null;
        env.close();
        env = null;
    }

    public void testIllegalConfig()
        throws DatabaseException {

        open();

        CursorConfig cursConfig;
        TransactionConfig txnConfig;

        /* Disallow transaction ReadCommitted and Serializable. */
        txnConfig = new TransactionConfig();
        txnConfig.setReadCommitted(true);
        txnConfig.setSerializableIsolation(true);
        try {
            env.beginTransaction(null, txnConfig);
            fail();
        } catch (IllegalArgumentException expected) {}

        /* Disallow transaction ReadCommitted and ReadUncommitted. */
        txnConfig = new TransactionConfig();
        txnConfig.setReadCommitted(true);
        txnConfig.setReadUncommitted(true);
        try {
            env.beginTransaction(null, txnConfig);
            fail();
        } catch (IllegalArgumentException expected) {}

        /* Disallow cursor ReadCommitted and ReadUncommitted. */
        cursConfig = new CursorConfig();
        cursConfig.setReadCommitted(true);
        cursConfig.setReadUncommitted(true);
        Transaction txn = env.beginTransaction(null, null);
        try {
            db.openCursor(txn, cursConfig);
            fail();
        } catch (IllegalArgumentException expected) {}
        txn.abort();

        close();
    }

    public void testWithTransactionConfig()
        throws DatabaseException {

        open();

        TransactionConfig config = new TransactionConfig();
        config.setReadCommitted(true);
        Transaction txn = env.beginTransaction(null, config);
        Cursor cursor = db.openCursor(txn, null);

        checkReadCommitted(cursor, 100, true);

        cursor.close();
        txn.commit();
        close();
    }

    public void testWithCursorConfig()
        throws DatabaseException {

        open();

        Transaction txn = env.beginTransaction(null, null);
        CursorConfig config = new CursorConfig();
        config.setReadCommitted(true);
        Cursor cursor = db.openCursor(txn, config);
        Cursor degree3Cursor = db.openCursor(txn, null);

        checkReadCommitted(cursor, 100, true);
        checkReadCommitted(degree3Cursor, 200, false);

        degree3Cursor.close();
        cursor.close();
        txn.commit();
        close();
    }

    public void testWithLockMode()
        throws DatabaseException {

        open();

        Transaction txn = env.beginTransaction(null, null);

        checkReadCommitted(txn, LockMode.READ_COMMITTED, 100, true);
        checkReadCommitted(txn, null, 200, false);

        txn.commit();
        close();
    }

    /**
     * Checks that the given cursor provides the given
     * expectReadLocksAreReleased behavior.
     */
    private void checkReadCommitted(Cursor cursor,
                                    int startKey,
                                    boolean expectReadLocksAreReleased)
        throws DatabaseException {

        LockStats baseStats = env.getLockStats(null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        checkNReadLocks(baseStats, 0);
        for (int i = 1; i <= 5; i += 1) {
            IntegerBinding.intToEntry(startKey + i, key);
            OperationStatus status = cursor.getSearchKey(key, data, null);
            assertEquals(OperationStatus.SUCCESS, status);
            if (expectReadLocksAreReleased) {
                /* Read locks are released as the cursor moves. */
                checkNReadLocks(baseStats, 1);
            } else {
                /* Read locks are not released. */
                checkNReadLocks(baseStats, i);
            }
        }

        checkNWriteLocks(baseStats, 0);
        for (int i = 1; i <= 5; i += 1) {
            IntegerBinding.intToEntry(startKey + i, key);
            IntegerBinding.intToEntry(0, data);
            cursor.put(key, data);
            /* Write locks are not released. */
            checkNWriteLocks(baseStats, i);
        }

        if (expectReadLocksAreReleased) {
            /* The last read lock was released by the put() call above. */
            checkNReadLocks(baseStats, 0);
        }
    }

    /**
     * Checks that the given lock mode provides the given
     * expectReadLocksAreReleased behavior.
     */
    private void checkReadCommitted(Transaction txn,
                                    LockMode lockMode,
                                    int startKey,
                                    boolean expectReadLocksAreReleased)
        throws DatabaseException {

        LockStats baseStats = env.getLockStats(null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        checkNReadLocks(baseStats, 0);
        for (int i = 1; i <= 5; i += 1) {
            IntegerBinding.intToEntry(startKey + i, key);
            OperationStatus status = db.get(txn, key, data, lockMode);
            assertEquals(OperationStatus.SUCCESS, status);
            if (expectReadLocksAreReleased) {
                /* Read locks are released when the cursor is closed. */
                checkNReadLocks(baseStats, 0);
            } else {
                /* Read locks are not released. */
                checkNReadLocks(baseStats, i);
            }
        }

        checkNWriteLocks(baseStats, 0);
        for (int i = 1; i <= 5; i += 1) {
            IntegerBinding.intToEntry(startKey + i, key);
            IntegerBinding.intToEntry(0, data);
            db.put(txn, key, data);
            /* Write locks are not released. */
            checkNWriteLocks(baseStats, i);
        }

        if (expectReadLocksAreReleased) {
            /* The last read lock was released by the put() call above. */
            checkNReadLocks(baseStats, 0);
        }
    }

    private void checkNReadLocks(LockStats baseStats, int nReadLocksExpected)
        throws DatabaseException {

        LockStats stats = env.getLockStats(null);
        assertEquals
            ("Read locks -- ",
             nReadLocksExpected,
             stats.getNReadLocks() - baseStats.getNReadLocks());
    }

    private void checkNWriteLocks(LockStats baseStats, int nWriteLocksExpected)
        throws DatabaseException {

        LockStats stats = env.getLockStats(null);
        assertEquals
            ("Write locks -- ",
             nWriteLocksExpected,
             stats.getNWriteLocks() - baseStats.getNWriteLocks());
    }
}
