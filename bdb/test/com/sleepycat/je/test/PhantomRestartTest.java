/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: PhantomRestartTest.java,v 1.11.2.1 2007/02/01 14:50:20 cwl Exp $
 */

package com.sleepycat.je.test;

import java.io.File;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests read operation restarts that are the by product of phantom prevention
 * (range locking) added in SR [#10477].
 */
public class PhantomRestartTest extends TestCase {

    /*
     * Spec Parameters: Oper name, InsertKey1, InsertKey2, Oper instance
     *
     * A- InsertKey1 is inserted in transaction T0 and committed.
     * B- T1 starts and performs Oper passing InsertKey1; it finishes the
     *    operation, but doesn't commit.
     * C- T2 starts and attempts to insert InsertKey2, but is blocked by T1.
     * D- T3 starts and performs Oper passing InsertKey2, but is restarted
     *    because it is blocked by T2.
     * E- T1 is committed, allowing T2 and T3 to finish also.
     * F- T4 performs Oper a final time passing InsertKey2.
     *
     * For each Spec below the Lock owners and waiters are described in between
     * steps D and E above.  This state describes the condition where the read
     * operation (Oper) is performing restarts because it is blocked by a
     * RANGE_INSERT.
     *
     * To understand how read operation restarts work consider the "First"
     * Spec below.  When T1 releases K2, T2 should finish, and T3 should read
     * K1.  If restart were not implemented in the lock manager, T3 would read
     * K2 instead of K1; K1 would then be a phantom with respect to T3.  If
     * search restarts were not implemented, a RangeRestartException would
     * surface at the user level.  These errors were observed when running this
     * test before search restarts were fully implemented.
     */
    private static Spec[] SPECS = {

        /*
         * T1 calls getFirst -- owns RANGE_READ on K2.
         * T2 inserts K1 -- waits for RANGE_INSERT on K2.
         * T3 calls getFirst -- requests RANGE_READ on K2: restarts.
         */
        new Spec("First", 2, 1, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                status = cursor.getFirst(key, data, null);
                checkStatus(OperationStatus.SUCCESS);
                checkKey(insertedKey);
            }
        }),

        /*
         * T1 calls getLast -- owns RANGE_READ on EOF.
         * T2 inserts K2 -- waits for RANGE_INSERT on EOF.
         * T3 calls getLast -- requests RANGE_READ on EOF: restarts.
         */
        new Spec("Last", 1, 2, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                status = cursor.getLast(key, data, null);
                checkStatus(OperationStatus.SUCCESS);
                checkKey(insertedKey);
            }
        }),

        /*
         * T1 calls getSearchKey on K1 -- owns RANGE_READ on K2.
         * T2 inserts K1 -- waits for RANGE_INSERT on K2.
         * T3 calls getSearchKey on K1 -- requests RANGE_READ on K2: restarts.
         */
        new Spec("Search", 2, 1, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                setKey(1);
                status = dups ? cursor.getSearchBoth(key, data, null)
                              : cursor.getSearchKey(key, data, null);
                checkStatus((insertedKey == 1) ? OperationStatus.SUCCESS
                                               : OperationStatus.NOTFOUND);
            }
        }),

        /*
         * T1 calls getSearchKeyRange on K0 -- owns RANGE_READ on K2.
         * T2 inserts K1 -- waits for RANGE_INSERT on K2.
         * T3 calls getSearchKeyRange on K0 -- requests RANGE_READ on K2:
         * restarts.
         */
        new Spec("SearchRange", 2, 1, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                setKey(0);
                status = dups ? cursor.getSearchBothRange(key, data, null)
                              : cursor.getSearchKeyRange(key, data, null);
                checkStatus(OperationStatus.SUCCESS);
                checkKey(insertedKey);
            }
        }),

        /*
         * T1 calls getNext from K1 -- owns RANGE_READ on EOF.
         * T2 inserts K2 -- waits for RANGE_INSERT on EOF.
         * T3 calls getNext from K1 -- requests RANGE_READ on EOF: restarts.
         */
        new Spec("Next", 1, 2, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                status = cursor.getFirst(key, data, null);
                checkStatus(OperationStatus.SUCCESS);
                checkKey(1);
                status = cursor.getNext(key, data, null);
                checkStatus((insertedKey == 2) ? OperationStatus.SUCCESS
                                               : OperationStatus.NOTFOUND);
            }
        }),

        /*
         * T1 calls getPrev from K2 -- owns RANGE_READ on K2.
         * T2 inserts K1 -- waits for RANGE_INSERT on K2.
         * T3 calls getPrev from K2 -- requests RANGE_READ on K2: restarts.
         */
        new Spec("Prev", 2, 1, new Oper() {
            void doOper(int insertedKey) throws DatabaseException {
                status = cursor.getLast(key, data, null);
                checkStatus(OperationStatus.SUCCESS);
                checkKey(2);
                status = cursor.getPrev(key, data, null);
                checkStatus((insertedKey == 1) ? OperationStatus.SUCCESS
                                               : OperationStatus.NOTFOUND);
            }
        }),

        /*
         * NextDup, NextNoDup, PrevDup and PrevNoDup are not tested here.
         * Restarts for these operations are implemented together with Next and
         * Prev operations, so testing was skipped.
         */
    };

    private static abstract class Oper {

        PhantomRestartTest test;
        boolean dups;
        Cursor cursor;
        DatabaseEntry key;
        DatabaseEntry data;
        OperationStatus status;

        void init(PhantomRestartTest test, Cursor cursor) {
            this.test = test;
            this.cursor = cursor;
            this.dups = test.dups;
            this.key = new DatabaseEntry();
            this.data = new DatabaseEntry();
            this.status = null;
        }

        void checkStatus(OperationStatus expected) {
            TestCase.assertEquals(expected, status);
        }

        void setKey(int val) {
            if (dups) {
                IntegerBinding.intToEntry(100, key);
                IntegerBinding.intToEntry(val, data);
            } else {
                IntegerBinding.intToEntry(val, key);
            }
        }

        void checkKey(int expected) {
            if (dups) {
                TestCase.assertEquals(100, IntegerBinding.entryToInt(key));
                TestCase.assertEquals
                    (expected, IntegerBinding.entryToInt(data));
            } else {
                TestCase.assertEquals
                    (expected, IntegerBinding.entryToInt(key));
            }
        }

        abstract void doOper(int insertedKey)
            throws DatabaseException;
    }

    private static class Spec {

        String name;
        int insertKey1;
        int insertKey2;
        Oper oper;

        Spec(String name, int insertKey1, int insertKey2, Oper oper) {
            this.name = name;
            this.insertKey1 = insertKey1;
            this.insertKey2 = insertKey2;
            this.oper = oper;
        }
    }

    public static Test suite()
        throws Exception {

        TestSuite suite = new TestSuite();
        for (int i = 0; i < SPECS.length; i += 1) {
            for (int j = 0; j < 2; j += 1) {
                boolean dups = (j != 0);
                suite.addTest(new PhantomRestartTest(SPECS[i], dups));
            }
        }
        return suite;
    }

    private static final int MAX_INSERT_MILLIS = 5000;

    private File envHome;
    private Environment env;
    private Database db;
    private JUnitThread writerThread;
    private JUnitThread readerThread;
    private boolean dups;
    private Spec spec;

    public PhantomRestartTest(Spec spec, boolean dups) {
        super(spec.name + (dups ? "-Dups" : ""));
        this.spec = spec;
        this.dups = dups;
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }
    
    public void tearDown()
        throws Exception {

        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
                
        try {
            //*
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
            //*/
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        envHome = null;
        env = null;
        db = null;

        if (writerThread != null) {
            while (writerThread.isAlive()) {
                writerThread.interrupt();
                Thread.yield();
            }
            writerThread = null;
        }

        if (readerThread != null) {
            while (readerThread.isAlive()) {
                readerThread.interrupt();
                Thread.yield();
            }
            readerThread = null;
        }
    }

    /**
     * Opens the environment and database.
     */
    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setTxnSerializableIsolation(true);
        
        /* Disable the daemons so the don't interfere with stats. */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
       
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(dups);
        db = env.openDatabase(null, "PhantomRestartTest", dbConfig);
    }

    /**
     * Closes the environment and database.
     */
    private void closeEnv()
        throws DatabaseException {

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    public void runTest()
        throws DatabaseException, InterruptedException {

        openEnv();

        /* T0 inserts first key. */
        if (dups) {

            /*
             * Create a dup tree and delete it to avoid deadlocking.  Note that
             * we have the compressor disabled to make this work.  Deadlocking
             * occurs without a dup tree because insertion locks the sibling
             * key when creating a dup tree from a single LN.  This extra
             * locking throws off our test.
             */
            insert(100, 0);
            insert(100, 1);
            DatabaseEntry key = new DatabaseEntry();
            IntegerBinding.intToEntry(100, key);
            db.delete(null, key);

            /* Insert the dup key we're testing with. */
            insert(100, spec.insertKey1);
        } else {
            insert(spec.insertKey1, 0);
        }

        /* T1 performs Oper. */
        Transaction readerTxn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(readerTxn, null);
        spec.oper.init(this, cursor);
        spec.oper.doOper(spec.insertKey1);

        /* T2 starts to insert second key, waits on T1. */
        if (dups) {
            startInsert(100, spec.insertKey2);
        } else {
            startInsert(spec.insertKey2, 0);
        }

        /* T3 performs Oper. */
        startReadOper(spec.insertKey2);

        /* Close T1 to allow T2 and T3 to finish. */
        cursor.close();
        readerTxn.commitNoSync();
        waitForInsert();
        waitForReadOper();

        /* T4 performs Oper again in this thread as a double-check. */
        readerTxn = env.beginTransaction(null, null);
        cursor = db.openCursor(readerTxn, null);
        spec.oper.init(this, cursor);
        spec.oper.doOper(spec.insertKey2);
        cursor.close();
        readerTxn.commit();

        closeEnv();
    }

    /**
     * Inserts the given key and data in a new transaction and commits it.
     */
    private void insert(int keyVal, int dataVal)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(keyVal, key);
        IntegerBinding.intToEntry(dataVal, data);
        OperationStatus status;
        Transaction writerTxn = env.beginTransaction(null, null);
        try {
            if (dups) {
                status = db.putNoDupData(writerTxn, key, data);
            } else {
                status = db.putNoOverwrite(writerTxn, key, data);
            }
        } catch (DeadlockException e) {
            writerTxn.abort();
            throw e;
        }
        assertEquals(OperationStatus.SUCCESS, status);
        writerTxn.commitNoSync();
    }

    /**
     * Starts writer thread and waits for it to start the insert.
     */
    private void startInsert(final int keyVal, final int dataVal)
        throws DatabaseException, InterruptedException {

        LockStats origStats = env.getLockStats(null);

        writerThread = new JUnitThread("Writer") {
            public void testBody() 
                throws DatabaseException {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                IntegerBinding.intToEntry(keyVal, key);
                IntegerBinding.intToEntry(dataVal, data);
                Transaction writerTxn = env.beginTransaction(null, null);
                OperationStatus status;
                if (dups) {
                    status = db.putNoDupData(writerTxn, key, data);
                } else {
                    status = db.putNoOverwrite(writerTxn, key, data);
                }
                assertEquals(OperationStatus.SUCCESS, status);
                writerTxn.commitNoSync();
            }
        };

        writerThread.start();
        waitForBlock(origStats);
    }

    /**
     * Waits for the writer thread to finish.
     */
    private void waitForInsert() {

        try {
            writerThread.finishTest();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        } finally {
            writerThread = null;
        }
    }

    /**
     * Starts reader thread and waits for it to start the read operation.
     */
    private void startReadOper(final int operKeyParam)
        throws DatabaseException, InterruptedException {

        LockStats origStats = env.getLockStats(null);

        readerThread = new JUnitThread("Reader") {
            public void testBody() 
                throws DatabaseException {
                Transaction readerTxn = env.beginTransaction(null, null);
                Cursor cursor = db.openCursor(readerTxn, null);
                spec.oper.init(PhantomRestartTest.this, cursor);
                spec.oper.doOper(operKeyParam);
                cursor.close();
                readerTxn.commitNoSync();
            }
        };

        readerThread.start();
        waitForBlock(origStats);
    }

    /**
     * Waits for a new locker to block waiting for a lock.
     */
    private void waitForBlock(LockStats origStats)
        throws DatabaseException, InterruptedException {

        long startTime = System.currentTimeMillis();
        while (true) {

            /* Give some time to the thread. */
            Thread.yield();
            Thread.sleep(10);
            if (System.currentTimeMillis() - startTime > MAX_INSERT_MILLIS) {
                fail("Timeout");
            }

            /* Wait for the operation to block. */
            LockStats stats = env.getLockStats(null);
            if (stats.getNWaiters() > origStats.getNWaiters()) {
                break;
            }
        }
    }

    /**
     * Waits for the reader thread to finish.
     */
    private void waitForReadOper() {

        try {
            readerThread.finishTest();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        } finally {
            readerThread = null;
        }
    }
}
