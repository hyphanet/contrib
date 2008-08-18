/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TxnTimeoutTest.java,v 1.34 2008/02/27 15:03:52 mark Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/*
 * Test transaction and lock timeouts.
 */
public class TxnTimeoutTest extends TestCase {

    private Environment env;
    private File envHome;

    public TxnTimeoutTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown() {
        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
        try {
            TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
        env = null;
        envHome = null;
    }

    private void createEnv(boolean setTimeout,
                           long txnTimeoutVal,
                           long lockTimeoutVal)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        if (setTimeout) {
            envConfig.setTxnTimeout(txnTimeoutVal);
            envConfig.setLockTimeout(lockTimeoutVal);
        }

        env = new Environment(envHome, envConfig);
    }

    private void closeEnv()
        throws DatabaseException {

        env.close();
        env = null;
    }

    /**
     * Test timeout set at txn level.
     */
    public void testTxnTimeout()
        throws DatabaseException, InterruptedException {

        createEnv(false, 0, 0);

        Transaction txnA = env.beginTransaction(null, null);

        /* Grab a lock */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        env.openDatabase(txnA, "foo", dbConfig);

        /* Now make a second txn so we can induce some blocking. */
        Transaction txnB = env.beginTransaction(null, null);
        txnB.setTxnTimeout(300000);  // microseconds
        txnB.setLockTimeout(9000000);
        Thread.sleep(400);

        try {
            env.openDatabase(txnB, "foo", dbConfig);
            fail("Should time out");
        } catch (DeadlockException e) {
            /* Skip the version string. */
            assertTrue
                (TestUtils.skipVersion(e).startsWith("Transaction "));
            assertEquals(300, e.getTimeoutMillis());
            /* Good, expect this exception */
            txnB.abort();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not get another kind of exception");
        }

        /* Now try a lock timeout. */
        txnB = env.beginTransaction(null, null);
        txnB.setLockTimeout(100000);

        try {
            env.openDatabase(txnB, "foo", dbConfig);
            fail("Should time out");
        } catch (DeadlockException e) {
            assertTrue(TestUtils.skipVersion(e).startsWith("Lock "));
            assertEquals(100, e.getTimeoutMillis());
            /* Good, expect this exception */
            txnB.abort();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not get another kind of exception");
        }

        txnA.abort();
        LockStats stats = env.getLockStats(TestUtils.FAST_STATS);
        assertEquals(2, stats.getNWaits());

        closeEnv();
    }

    /**
     * Use Txn.setTimeout(), expect a txn timeout.
     */
    public void testPerTxnTimeout()
        throws DatabaseException, InterruptedException {

        doEnvTimeout(false, true, true, 300000, 9000000, false);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeout(), expect a txn timeout.
     */
    public void testEnvTxnTimeout()
        throws DatabaseException, InterruptedException {

        doEnvTimeout(true, true, true, 300000, 9000000, false);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeout(), use
     * EnvironmentConfig.setLockTimeout(0), expect a txn timeout.
     */
    public void testEnvNoLockTimeout()
        throws DatabaseException, InterruptedException {

        doEnvTimeout(true, true, true, 300000, 0, false);
    }

    /**
     * Use Txn.setLockTimeout(), expect a lock timeout.
     */
    public void testPerLockTimeout()
        throws DatabaseException, InterruptedException {

        doEnvTimeout(false, false, true, 0, 100000, true);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeout(0), Use
     * EnvironmentConfig.setLockTimeout(xxx), expect a lcok timeout.
     */
    public void testEnvLockTimeout()
        throws DatabaseException, InterruptedException {

        doEnvTimeout(true, false, true, 0, 100000, true);
    }

    /**
     * @param setEnvConfigTimeout
     * if true, use EnvironmentConfig.set{Lock,Txn}Timeout
     * @param setPerTxnTimeout if true, use Txn.setTxnTimeout()
     * @param setPerLockTimeout if true, use Txn.setLockTimeout()
     * @param long txnTimeout value for txn timeout
     * @param long lockTimeout value for lock timeout
     * @param expectLockException if true, expect a LockTimoutException, if
     * false, expect a TxnTimeoutException
     */
    private void doEnvTimeout(boolean setEnvConfigTimeout,
                              boolean setPerTxnTimeout,
                              boolean setPerLockTimeout,
                              long txnTimeout,
                              long lockTimeout,
                              boolean expectLockException)
        throws DatabaseException, InterruptedException {

        createEnv(setEnvConfigTimeout, txnTimeout, lockTimeout);

        Transaction txnA = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database dbA = env.openDatabase(txnA, "foo", dbConfig);

        /*
         * Now make a second txn so we can induce some blocking. Make the
         * txn timeout environment wide.
         */
        Transaction txnB = env.beginTransaction(null, null);
        long expectTxnTimeoutMillis;
        long expectLockTimeoutMillis;
        if (setEnvConfigTimeout) {
            expectTxnTimeoutMillis = txnTimeout / 1000;
            expectLockTimeoutMillis = lockTimeout / 1000;
        } else {
            if (setPerTxnTimeout) {
                txnB.setTxnTimeout(300000);
                expectTxnTimeoutMillis = 300;
            } else {
                expectTxnTimeoutMillis = 500;
            }
            if (setPerLockTimeout) {
                txnB.setLockTimeout(9000000);
                expectLockTimeoutMillis = 9000;
            } else {
                expectLockTimeoutMillis = 500;
            }
        }

        Thread.sleep(400);

        try {
            env.openDatabase(txnB, "foo", dbConfig);
            fail("Should time out");
        } catch (DeadlockException e) {
            if (expectLockException) {
                assertTrue(TestUtils.skipVersion(e).startsWith("Lock "));
                assertEquals(expectLockTimeoutMillis,
                             e.getTimeoutMillis());
            } else {
                assertTrue
                    (TestUtils.skipVersion(e).startsWith("Transaction "));
                assertEquals(expectTxnTimeoutMillis, e.getTimeoutMillis());
            }

            /* Good, expect this exception */
            txnB.abort();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not get another kind of exception");
        }

        dbA.close();
        txnA.abort();

        closeEnv();
    }

    /**
     * Use Locker.setTxnTimeout(), expect a lock timeout.
     */
    public void testPerLockerTimeout()
        throws DatabaseException, InterruptedException {
       
        createEnv(true, 500000000, 0);
    
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);

        /* 
         * Create our Locker object and set the transaction timeout to 0.
         * 0 should mean no timeout per berkeley API docs).
         */
        Locker locker = BasicLocker.createBasicLocker(envImpl);
        locker.setTxnTimeout(0);
        /* Wait for a short period. */
        Thread.sleep(100);
        /* Set the timeout to zero and should never be timed out. */
        assertFalse(locker.isTimedOut());

        /* Set timeout to 10 milliseconds. */
        locker.setTxnTimeout(10);
        /* Wait for 100 milliseconds. */
        Thread.sleep(100);
        /* Should be timed out. */
        assertTrue(locker.isTimedOut());

        try {

            /*
             * Set timeout to a negative value, and expect a
             * IllegalArgumentException.
             */
            locker.setTxnTimeout(-1000);
            fail("should get an exception");
        } catch (IllegalArgumentException ie) {
            assertTrue(ie.
                       getMessage().
                       contains("the timeout value cannot be negative"));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not get another kind of exception");
        }

        try {

            /*
             * Set timeout to a value greater than 2^32, and expect a
             * IllegalArgumentException.
             */
            long timeout = (long) Math.pow(2, 33);
            locker.setTxnTimeout(timeout);
            fail("should get an exception");
        } catch (IllegalArgumentException ie) {
            assertTrue(ie.getMessage().contains
                    ("the timeout value cannot be greater than 2^32"));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not get another kind of exception");
        }

        closeEnv();
    }

    public void testReadCommittedTxnTimeout()
        throws DatabaseException, InterruptedException {

        doReadCommittedTimeout(true);
    }

    public void testReadCommittedLockTimeout()
        throws DatabaseException, InterruptedException {

        doReadCommittedTimeout(false);
    }

    /**
     * Tests that Transaction.setTxnTimeout and setLockTimeout work with the
     * BuddyLocker used for ReadCommitted reads.  [#16017]
     */
    private void doReadCommittedTimeout(boolean useTxnTimeout)
        throws DatabaseException, InterruptedException {
       
        createEnv(false, 0, 0);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "foo", dbConfig);

        TransactionConfig txnConfig = new TransactionConfig();
        txnConfig.setReadCommitted(true);

        Transaction txnA = null;
        Transaction txnB = null;

        try {
            /* Insert a record with txnA and keep it write-locked. */
            txnA = env.beginTransaction(null, txnConfig);
            key.setData(new byte[1]);
            data.setData(new byte[1]);
            OperationStatus status = db.put(txnA, key, data);
            assertSame(OperationStatus.SUCCESS, status);

            /*
             * An insert with txnB will block because entire range is locked by
             * txnA.
             */
            txnB = env.beginTransaction(null, txnConfig);
            if (useTxnTimeout) {
                txnB.setTxnTimeout(100 * 1000);
                txnB.setLockTimeout(9000 * 1000);
                /* Ensure txn timeout triggers before waiting. */
                Thread.sleep(150);
            } else {
                txnB.setTxnTimeout(9000 * 1000);
                txnB.setLockTimeout(100 * 1000);
            }
            key.setData(new byte[1]);
            try {
                db.get(txnB, key, data, null);
                fail();
            } catch (DeadlockException e) {
                assertTrue(e.toString(), TestUtils.skipVersion(e).startsWith
                            (useTxnTimeout ? "Transaction " : "Lock "));
                assertEquals(100, e.getTimeoutMillis());
            }
        } finally {
            if (txnB != null) {
                txnB.abort();
            }
            if (txnA != null) {
                txnA.abort();
            }
        }

        db.close();
        closeEnv();
    }

    public void testSerializableTxnTimeout()
        throws DatabaseException, InterruptedException {

        doSerializableTimeout(true);
    }

    public void testSerializableLockTimeout()
        throws DatabaseException, InterruptedException {

        doSerializableTimeout(false);
    }

    /**
     * Tests that Transaction.setTxnTimeout and setLockTimeout work with the
     * BuddyLocker used for Serializable inserts. [#16017]
     */
    private void doSerializableTimeout(boolean useTxnTimeout)
        throws DatabaseException, InterruptedException {
       
        createEnv(false, 0, 0);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "foo", dbConfig);

        TransactionConfig txnConfig = new TransactionConfig();
        txnConfig.setSerializableIsolation(true);

        Transaction txnA = null;
        Transaction txnB = null;

        try {
            /* Lock virtual EOF node with txnA by scanning an empty DB. */
            txnA = env.beginTransaction(null, txnConfig);
            Cursor c = db.openCursor(txnA, null);
            OperationStatus status = c.getFirst(key, data, null);
            assertSame(OperationStatus.NOTFOUND, status);
            c.close();

            /*
             * Insert with txnB will block because entire range is locked by
             * txnA.
             */
            txnB = env.beginTransaction(null, txnConfig);
            if (useTxnTimeout) {
                txnB.setTxnTimeout(100 * 1000);
                txnB.setLockTimeout(9000 * 1000);
                /* Ensure txn timeout triggers before waiting. */
                Thread.sleep(150);
            } else {
                txnB.setTxnTimeout(9000 * 1000);
                txnB.setLockTimeout(100 * 1000);
            }
            key.setData(new byte[1]);
            data.setData(new byte[1]);
            try {
                db.put(txnB, key, data);
                fail();
            } catch (DeadlockException e) {
                assertTrue(e.toString(), TestUtils.skipVersion(e).startsWith
                            (useTxnTimeout ? "Transaction " : "Lock "));
                assertEquals(100, e.getTimeoutMillis());
            }
        } finally {
            if (txnB != null) {
                txnB.abort();
            }
            if (txnA != null) {
                txnA.abort();
            }
        }

        db.close();
        closeEnv();
    }
}
