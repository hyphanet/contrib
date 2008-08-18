/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TwoPCTest.java,v 1.10 2008/05/15 09:44:35 chao Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;

import javax.transaction.xa.XAResource;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogUtils.XidImpl;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;

/*
 * Simple 2PC transaction testing.
 */
public class TwoPCTest extends TestCase {
    private File envHome;
    private XAEnvironment env;
    private Database db;

    public TwoPCTest()
        throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new XAEnvironment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        db.close();
        env.close();
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Basic Two Phase Commit calls.
     */
    public void testBasic2PC()
        throws Throwable {

        try {
        TransactionStats stats =
            env.getTransactionStats(TestUtils.FAST_STATS);
        int numBegins = 2; // 1 for setting up XA env and 1 for open db
        int numCommits = 2;
        int numXAPrepares = 0;
        int numXACommits = 0;
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());

        Transaction txn = env.beginTransaction(null, null);
        stats = env.getTransactionStats(TestUtils.FAST_STATS);
        numBegins++;
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());
        assertEquals(1, stats.getNActive());

        XidImpl xid = new XidImpl(1, "TwoPCTest1".getBytes(), null);
        env.setXATransaction(xid, txn);
        stats = env.getTransactionStats(TestUtils.FAST_STATS);
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());
        assertEquals(1, stats.getNActive());

        StringDbt key = new StringDbt("key");
        StringDbt data = new StringDbt("data");
        db.put(txn, key, data);
        stats = env.getTransactionStats(TestUtils.FAST_STATS);
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());
        assertEquals(1, stats.getNActive());

        env.prepare(xid);
        numXAPrepares++;
        stats = env.getTransactionStats(TestUtils.FAST_STATS);
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());
        assertEquals(1, stats.getNActive());
         
        env.commit(xid, false);
        numCommits++;
        numXACommits++;
        stats = env.getTransactionStats(TestUtils.FAST_STATS);
        assertEquals(numBegins, stats.getNBegins());
        assertEquals(numCommits, stats.getNCommits());
        assertEquals(numXAPrepares, stats.getNXAPrepares());
        assertEquals(numXACommits, stats.getNXACommits());
        assertEquals(0, stats.getNActive());
        } catch (Exception E) {
            System.out.println("caught " + E);
        }
    }

    /**
     * Basic readonly-prepare.
     */
    public void testROPrepare()
        throws Throwable {

        try {
            Transaction txn = env.beginTransaction(null, null);
            XidImpl xid = new XidImpl(1, "TwoPCTest1".getBytes(), null);
            env.setXATransaction(xid, txn);

            assertEquals(XAResource.XA_RDONLY, env.prepare(xid));
        } catch (Exception E) {
            System.out.println("caught " + E);
        }
    }

    /**
     * Test calling prepare twice (should throw exception).
     */
    public void testTwicePreparedTransaction()
        throws Throwable {

        Transaction txn = env.beginTransaction(null, null);
        XidImpl xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
        env.setXATransaction(xid, txn);
        StringDbt key = new StringDbt("key");
        StringDbt data = new StringDbt("data");
        db.put(txn, key, data);

        try {
            env.prepare(xid);
            env.prepare(xid);
            fail("should not be able to prepare twice");
        } catch (Exception E) {
            env.commit(xid, false);
        }
    }

    /**
     * Test calling rollback(xid) on an unregistered xa txn.
     */
    public void testRollbackNonExistent()
        throws Throwable {

        Transaction txn = env.beginTransaction(null, null);
        StringDbt key = new StringDbt("key");
        StringDbt data = new StringDbt("data");
        db.put(txn, key, data);
        XidImpl xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);

        try {
            env.rollback(xid);
            fail("should not be able to call rollback on an unknown xid");
        } catch (Exception E) {
        }
        txn.abort();
    }

    /**
     * Test calling commit(xid) on an unregistered xa txn.
     */
    public void testCommitNonExistent()
        throws Throwable {

        Transaction txn = env.beginTransaction(null, null);
        StringDbt key = new StringDbt("key");
        StringDbt data = new StringDbt("data");
        db.put(txn, key, data);
        XidImpl xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);

        try {
            env.commit(xid, false);
            fail("should not be able to call commit on an unknown xid");
        } catch (Exception E) {
        }
        txn.abort();
    }
}
