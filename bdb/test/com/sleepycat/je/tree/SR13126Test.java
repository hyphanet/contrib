/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2007 Oracle.  All rights reserved.
 *
 * $Id: SR13126Test.java,v 1.9.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.collections.CurrentTransaction;
import com.sleepycat.collections.TransactionRunner;
import com.sleepycat.collections.TransactionWorker;
import com.sleepycat.compat.DbCompat;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.util.TestUtils;

/**
 */
public class SR13126Test extends TestCase {

    private File envHome;
    private Environment env;
    private Database db;
    private long maxMem;

    public SR13126Test() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        try {
            if (env != null) {
		env.close();
            }
        } catch (Exception e) {
            System.out.println("During tearDown: " + e);
        }

        env = null;
        db = null;

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    private boolean open()
	throws DatabaseException {

        maxMem = MemoryBudget.getRuntimeMaxMemory();
        if (maxMem == -1) {
            System.out.println
                ("*** Warning: not able to run this test because the JVM " +
                 "heap size is not available");
            return false;
        }

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        /* Do not run the daemons to avoid timing considerations. */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        db = env.openDatabase(null, "foo", dbConfig);

        return true;
    }

    private void close()
	throws DatabaseException {

        db.close();
        db = null;

        env.close();
        env = null;
    }

    public void testSR13126()
	throws DatabaseException {

        if (!open()) {
            return;
        }

        Transaction txn = env.beginTransaction(null, null);

        try {
            insertUntilOutOfMemory(txn);
            fail("Expected OutOfMemoryError");
        } catch (RunRecoveryException expected) {}

        verifyDataAndClose();
    }

    public void testTransactionRunner()
	throws Exception {

        if (!open()) {
            return;
        }

        final CurrentTransaction currentTxn =
            CurrentTransaction.getInstance(env);

        TransactionRunner runner = new TransactionRunner(env);
	/* Don't print exception stack traces during test runs. */
	DbCompat.TRANSACTION_RUNNER_PRINT_STACK_TRACES = false;
        try {
            runner.run(new TransactionWorker() {
                public void doWork()
                    throws Exception {

                    insertUntilOutOfMemory(currentTxn.getTransaction());
                }
            });
            fail("Expected OutOfMemoryError");
        } catch (RunRecoveryException expected) { }

        /*
         * If TransactionRunner does not abort the transaction, this thread
         * will be left with a transaction attached.
         */
        assertNull(currentTxn.getTransaction());

        verifyDataAndClose();
    }

    private void insertUntilOutOfMemory(Transaction txn)
	throws DatabaseException, OutOfMemoryError {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry();

        int startMem = (int) (maxMem / 3);
        int bumpMem = (int) ((maxMem - maxMem / 3) / 5);

        /* Insert larger and larger LNs until an OutOfMemoryError occurs. */
        for (int memSize = startMem;; memSize += bumpMem) {

            /*
             * If the memory error occurs when we do "new byte[]" below, this
             * is not a test of the bug in question, so the test fails.
             */
            data.setData(new byte[memSize]);
            try {
                db.put(null, key, data);
            } catch (OutOfMemoryError e) {
                //System.err.println("Error during write " + memSize);
                throw e;
            }
        }
    }

    private void verifyDataAndClose()
	throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /*
         * If a NULL_LSN is present in a BIN entry because of an incomplete
         * insert, an assertion will fire during the checkpoint when writing
         * the BIN.
         */
        env.close();
        env = null;

        /*
         * If the NULL_LSN was written above because assertions are disabled,
         * check that we don't get an exception when fetching it.
         */
        open();
        Cursor c = db.openCursor(null, null);
        while (c.getNext(key, data, null) == OperationStatus.SUCCESS) {}
        c.close();
        close();
    }
}
