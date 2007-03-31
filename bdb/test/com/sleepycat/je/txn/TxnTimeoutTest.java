/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnTimeoutTest.java,v 1.29.2.1 2007/02/01 14:50:22 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/*
 * Test transaction and lock timeouts.
 */
public class TxnTimeoutTest extends TestCase {
    
    private Environment env;
    private File envHome;
   
    public TxnTimeoutTest()
	throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() 
        throws IOException, DatabaseException {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        if (env != null) {
            env.close();
        }
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
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

    /**
     * Test timeout set at txn level.
     */
    public void testTxnTimeout() 
        throws Throwable {

        try {
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
                assertTrue(TestUtils.skipVersion(e).startsWith("Transaction "));
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
                /* Good, expect this exception */
                txnB.abort();
            } catch (Exception e) {
                e.printStackTrace();
                fail("Should not get another kind of exception");
            }

            txnA.abort();
            LockStats stats = env.getLockStats(TestUtils.FAST_STATS);
            assertEquals(2, stats.getNWaits());

        } catch (Throwable t) {

            /*
             * Print stack trace before trying to clean up je files in
             * teardown.
             */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Use Txn.setTimeOut(), expect a txn timeout.
     */
    public void testPerTxnTimeout() 
        throws Throwable {
        doEnvTimeout(false, true, true, 300000, 9000000, false);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeOut(), expect a txn timeout.
     */
    public void testEnvTxnTimeout() 
        throws Throwable {
        doEnvTimeout(true, true, true, 300000, 9000000, false);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeOut(), use
     * EnvironmentConfig.setLockTimeout(0), expect a txn timeout.
     */
    public void testEnvNoLockTimeout() 
        throws Throwable {
        doEnvTimeout(true, true, true, 300000, 0, false);
    }

    /**
     * Use Txn.setLockTimeout(), expect a lock timeout.
     */
    public void testPerLockTimeout() 
        throws Throwable {
        doEnvTimeout(false, false, true, 0, 100000, true);
    }

    /**
     * Use EnvironmentConfig.setTxnTimeOut(0), Use
     * EnvironmentConfig.setLockTimeout(xxx), expect a lcok timeout.
     */
    public void testEnvLockTimeout() 
        throws Throwable {
        doEnvTimeout(true, false, true, 0, 100000, true);
    }

    /**
     * @param setEnvConfigTimeout
     * if true, use EnvironmentConfig.set{Lock,Txn}TimeOut
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
        throws Throwable {

        try {
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
            if (!setEnvConfigTimeout) {
                if (setPerTxnTimeout) {
                    txnB.setTxnTimeout(300000);
                }
                if (setPerLockTimeout) {
                    txnB.setLockTimeout(9000000);
                }
            }

            Thread.sleep(400);

            try {
                env.openDatabase(txnB, "foo", dbConfig);
                fail("Should time out");
            } catch (DeadlockException e) {
                if (expectLockException) {
                    assertTrue(TestUtils.skipVersion(e).startsWith("Lock "));
                } else {
                    assertTrue(TestUtils.skipVersion(e).startsWith("Transaction "));
                }

                /* Good, expect this exception */
                txnB.abort();
            } catch (Exception e) {
                e.printStackTrace();
                fail("Should not get another kind of exception");
            }

            dbA.close();
            txnA.abort();
        } catch (Throwable t) {

            /*
             * Print stack trace before trying to clean up je files in
             * teardown.
             */
            t.printStackTrace();
            throw t;
        }
    }
}
