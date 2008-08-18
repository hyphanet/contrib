/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ScanLogTest.java,v 1.5 2008/01/07 14:29:05 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.util.TestUtils;

/**
 * Basic database operations, excluding configuration testing.
 */
public class ScanLogTest extends TestCase {
    private static final boolean DEBUG = false;
    private static final int NUM_RECS = 3;
    private static final int NUM_DUPS = 3;

    private File envHome;
    private Environment env;
    private String testName;
    private boolean forwards;
    private boolean duplicates;
    private boolean deleteRecs;
    private boolean useZeroLsn;
    private boolean doOtherTests;

    public static Test suite()
        throws Exception {

        TestSuite suite = new TestSuite();
        for (int i = 0; i < 2; i++) {               // forward
            for (int j = 0; j < 2; j++) {           // duplicates
                for (int k = 0; k < 2; k++) {       // deleteRecs
		    for (int l = 0; l < 2; l++) {   // useZeroLsn
			suite.addTest(new ScanLogTest
				      (i == 0, j == 0, k == 0, l == 0, false));
		    }
                }
            }
        }

	suite.addTest(new ScanLogTest(true, false, false, false, true));
        return suite;
    }

    public ScanLogTest(final boolean forwards,
		       final boolean duplicates,
		       final boolean deleteRecs,
		       final boolean useZeroLsn,
		       final boolean doOtherTests) {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
	this.forwards = forwards;
	this.duplicates = duplicates;
	this.deleteRecs = deleteRecs;
	this.doOtherTests = doOtherTests;
	this.useZeroLsn = useZeroLsn;

	if (doOtherTests) {
	    testName = "ScanLogTest-other";
	} else {
	    testName = "ScanLogTest-" +
		(forwards ? "fwd" : "bwd") + "-" +
		(duplicates ? "dups" : "noDups") + "-" +
		(deleteRecs ? "del" : "nodel") + "-" +
		(useZeroLsn ? "LSN0" : "noLSN0");
	}
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        try {
            /* Close in case we hit an exception and didn't close */
            if (env != null) {
		env.close();
            }
        } catch (DatabaseException e) {
            /* Ok if already closed */
        }
        env = null; // for JUNIT, to reduce memory usage when run in a suite.
	setName(testName);
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void runTest()
        throws Throwable {

	if (doOtherTests) {
	    doTest(forwards, duplicates, deleteRecs, useZeroLsn,
		   true  /* abortScan */);
	    checkIllegalArgs();
	} else {
	    doTest(forwards, duplicates, deleteRecs, useZeroLsn,
		   false /* abortScan */);
	}
    }

    private static class ScanLogTestScanner implements LogScanner {

	private int nDeletedRecsSeen = 0;
	private int nNonDeletedRecsSeen = 0;
	private boolean forwards;
	private boolean duplicates;
	private boolean deleteRecs;
	private byte prevKey;
	private boolean abortScan;

	private ScanLogTestScanner(final boolean forwards,
				   final boolean duplicates,
				   final boolean deleteRecs,
				   final boolean abortScan) {

	    this.forwards = forwards;
	    this.duplicates = duplicates;
	    this.deleteRecs = deleteRecs;
	    this.abortScan = abortScan;

	    if (forwards) {
		prevKey = (byte) 0;
	    } else {
		prevKey = (byte) NUM_RECS;
	    }
	}

	public boolean scanRecord(final DatabaseEntry key,
				  final DatabaseEntry data,
				  final boolean deleted,
				  final String databaseName) {

	    byte keyVal = key.getData()[3];

	    assertFalse(DbTree.isReservedDbName(databaseName));
	    assertTrue(databaseName.equals("testDB"));
	    assertFalse(deleted && !deleteRecs);
	    if (deleted) {
		assertTrue(keyVal == (NUM_RECS - 1));
		nDeletedRecsSeen++;
	    } else {
		if (duplicates) {
		    /* For duplicates, data[2] will be set (so ignore it). */
		    assertTrue(key.getData()[3] == data.getData()[3]);
		} else {
		    /* If !duplicates compare all of key with all of data. */
		    assertTrue(Key.compareKeys(key.getData(),
					       data.getData(),
					       null) == 0);
		}
		nNonDeletedRecsSeen++;
	    }

	    if (forwards) {
		assertTrue(prevKey <= keyVal);
	    } else {
		assertTrue(prevKey >= keyVal);
	    }
	    prevKey = keyVal;

	    if (abortScan) {
		return false;
	    } else {
		return true;
	    }
	}

	private int getNDeletedRecsSeen() {
	    return nDeletedRecsSeen;
	}

	private int getNonDeletedRecsSeen() {
	    return nNonDeletedRecsSeen;
	}
    }

    private void doTest(final boolean forwards,
			final boolean duplicates,
			final boolean deleteRecs,
			final boolean useZeroLsn,
			final boolean abortScan)
	throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true);
	    long startLSN = (useZeroLsn ? 0 : getCurrentLSN());
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);

            /* Create some data. */
	    for (int i = 0; i < NUM_RECS; i++) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));

		if (duplicates) {
		    for (int j = 1; j < NUM_RECS; j++) {
			data.setData(TestUtils.getTestArray(i + (j << 8)));
			assertEquals(OperationStatus.SUCCESS,
				     myDb.put(txn, key, data));
		    }
		}

		if (deleteRecs &&
		    i == NUM_RECS - 1) {
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.delete(txn, key));
		}
            }

	    txn.commit();
	    long endLSN = getCurrentLSN();

	    LogScanConfig lsConf = new LogScanConfig();
	    lsConf.setForwards(forwards);

	    ScanLogTestScanner scanner =
		new ScanLogTestScanner(forwards, duplicates,
				       deleteRecs, abortScan);

	    if (lsConf.getForwards()) {
		env.scanLog(startLSN, endLSN, lsConf, scanner);
	    } else {
		env.scanLog(endLSN, startLSN, lsConf, scanner);
	    }

	    if (duplicates) {
		if (deleteRecs) {
		    int expectedNDeletedRecs = NUM_RECS;

		    /*
		     * Don't subtract off deleted recs because all recs show up
		     * regardless of whether they're deleted or not.
		     */
		    int expectedNRecs = (NUM_RECS * NUM_RECS);
		    assertTrue(expectedNDeletedRecs ==
			       scanner.getNDeletedRecsSeen());
		    assertTrue(expectedNRecs ==
			       scanner.getNonDeletedRecsSeen());
		}
	    } else {
		assertTrue(scanner.getNDeletedRecsSeen() ==
			   (deleteRecs ? 1 : 0));

		if (abortScan) {
		    assertTrue(scanner.getNonDeletedRecsSeen() == 1);
		} else {
		    assertTrue(scanner.getNonDeletedRecsSeen() == NUM_RECS);
		}
	    }

            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void checkIllegalArgs()
	throws Throwable {

        try {
            Database myDb = initEnvAndDb(true, true);
	    long startLSN = getCurrentLSN();
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
	    Transaction txn = env.beginTransaction(null, null);

            /* Create some data. */
	    for (int i = 0; i < NUM_RECS; i++) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
			     myDb.put(txn, key, data));

		if (duplicates) {
		    for (int j = 1; j < NUM_RECS; j++) {
			data.setData(TestUtils.getTestArray(i + (j << 8)));
			assertEquals(OperationStatus.SUCCESS,
				     myDb.put(txn, key, data));
		    }
		}

		if (deleteRecs &&
		    i == NUM_RECS - 1) {
		    assertEquals(OperationStatus.SUCCESS,
				 myDb.delete(txn, key));
		}
            }

	    txn.commit();
	    long endLSN = getCurrentLSN();

	    ScanLogTestScanner scanner =
		new ScanLogTestScanner(forwards, duplicates,
				       deleteRecs, false);

	    LogScanConfig lsConf = new LogScanConfig();
	    lsConf.setForwards(true);
	    /* Reverse start and end LSNs. */
	    try {
		env.scanLog(endLSN, startLSN, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

	    lsConf.setForwards(false);
	    /* Reverse start and end LSNs. */
	    try {
		env.scanLog(startLSN, endLSN, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

	    /* Use negative startLSN. */
	    try {
		env.scanLog(-1, endLSN, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

	    lsConf.setForwards(true);
	    /* Use negative startLSN. */
	    try {
		env.scanLog(startLSN, -1, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

	    lsConf.setForwards(true);
	    try {
		env.scanLog(100000, 1000000, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

	    lsConf.setForwards(false);
	    try {
		env.scanLog(1000000, 100000, lsConf, scanner);
		fail("expected failure");
	    } catch (IllegalArgumentException IAE) {
		/* ignore */
	    }

            myDb.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private long getCurrentLSN()
	throws DatabaseException {

	return env.getStats(null).getEndOfLog();
    }

    /**
     * Set up the environment and db.
     */
    private Database initEnvAndDb(boolean allowDuplicates,
				  boolean transactional)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(transactional);

        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        /* Make a db and open it. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setSortedDuplicates(allowDuplicates);
        dbConfig.setAllowCreate(true);
	dbConfig.setTransactional(transactional);
        Database myDb = env.openDatabase(null, "testDB", dbConfig);
        return myDb;
    }
}
