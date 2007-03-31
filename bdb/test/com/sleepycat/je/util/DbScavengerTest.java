/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbScavengerTest.java,v 1.16.2.1 2007/02/01 14:50:23 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.DbScavenger;

public class DbScavengerTest extends TestCase {

    private static final int TRANSACTIONAL = 1 << 0;
    private static final int WRITE_MULTIPLE = 1 << 1;
    private static final int PRINTABLE = 1 << 2;
    private static final int ABORT_BEFORE = 1 << 3;
    private static final int ABORT_AFTER = 1 << 4;
    private static final int CORRUPT_LOG = 1 << 5;
    private static final int DELETE_DATA = 1 << 6;
    private static final int AGGRESSIVE = 1 << 7;

    private static final int N_DBS = 3;
    private static final int N_KEYS = 100;
    private static final int N_DATA_BYTES = 100;
    private static final int LOG_SIZE = 10000;

    private String envHomeName;
    private File envHome;
    
    private Environment env;

    private Database[] dbs = new Database[N_DBS];

    private boolean duplicatesAllowed = true;

    public DbScavengerTest() {
	envHomeName = System.getProperty(TestUtils.DEST_DIR);
        envHome = new File(envHomeName);
    }

    public void setUp()
	throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, ".dump");
    }
    
    public void tearDown()
	throws IOException {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.out.println("TearDown: " + e);
            }
            env = null;
        }
        TestUtils.removeLogFiles("TearDown", envHome, false);
        TestUtils.removeFiles("Teardown", envHome, ".dump");
    }

    public void testScavenger1()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE | TRANSACTIONAL |
			    ABORT_BEFORE | ABORT_AFTER);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger2()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE | TRANSACTIONAL | ABORT_BEFORE);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger3()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE | TRANSACTIONAL | ABORT_AFTER);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger4()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE | TRANSACTIONAL);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger5()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE | WRITE_MULTIPLE | TRANSACTIONAL);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger6()
        throws Throwable {

	try {
	    doScavengerTest(PRINTABLE);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	    throw T;
	}
    }

    public void testScavenger7()
        throws Throwable {

	try {
	    doScavengerTest(TRANSACTIONAL | ABORT_BEFORE | ABORT_AFTER);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger8()
        throws Throwable {

	try {
	    doScavengerTest(TRANSACTIONAL | ABORT_BEFORE);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger9()
        throws Throwable {

	try {
	    doScavengerTest(TRANSACTIONAL);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger10()
        throws Throwable {

	try {
	    doScavengerTest(TRANSACTIONAL | ABORT_AFTER);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger11()
        throws Throwable {

	try {
	    doScavengerTest(0);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger12()
        throws Throwable {

	try {
	    doScavengerTest(CORRUPT_LOG);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger13()
        throws Throwable {

	try {
	    doScavengerTest(DELETE_DATA);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavenger14()
        throws Throwable {

	try {
	    doScavengerTest(AGGRESSIVE);
	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    public void testScavengerAbortedDbLevelOperations()
        throws Throwable {

	try {
	    createEnv(true, true);
	    boolean doAbort = true;
	    byte[] dataBytes = new byte[N_DATA_BYTES];
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry data = new DatabaseEntry(dataBytes);
	    IntegerBinding.intToEntry(1, key);
	    TestUtils.generateRandomAlphaBytes(dataBytes);
	    for (int i = 0; i < 2; i++) {
		Transaction txn = env.beginTransaction(null, null);
		for (int dbCnt = 0; dbCnt < N_DBS; dbCnt++) {
		    String databaseName = null;
		    if (doAbort) {
			databaseName = "abortedDb" + dbCnt;
		    } else {
			databaseName = "simpleDb" + dbCnt;
		    }
		    DatabaseConfig dbConfig = new DatabaseConfig();
		    dbConfig.setAllowCreate(true);
		    dbConfig.setSortedDuplicates(duplicatesAllowed);
		    dbConfig.setTransactional(true);
		    if (dbs[dbCnt] != null) {
			throw new DatabaseException("database already open");
		    }
		    Database db =
			env.openDatabase(txn, databaseName, dbConfig);
		    dbs[dbCnt] = db;
		    db.put(txn, key, data);
		}
		if (doAbort) {
		    txn.abort();
		    dbs = new Database[N_DBS];
		} else {
		    txn.commit();
		}
		doAbort = !doAbort;
	    }

	    closeEnv();
	    createEnv(false, false);
	    openDbs(false, false, duplicatesAllowed, null);
	    dumpDbs(false, false);

	    /* Close the environment, delete it completely from the disk. */
	    closeEnv();
	    TestUtils.removeLogFiles("doScavengerTest", envHome, false);

	    /* Recreate and reload the environment from the scavenger files. */
	    createEnv(true, true);
	    loadDbs();

	    /* Verify that the data is the same as when it was created. */
	    for (int dbCnt = 0; dbCnt < N_DBS; dbCnt++) {
		String databaseName = "abortedDb" + dbCnt;
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.setAllowCreate(false);
		try {
		    env.openDatabase(null, databaseName, dbConfig);
		    fail("expected DatabaseNotFoundException");
		} catch (DatabaseNotFoundException DNFE) {
		    /* Expected. */
		}
	    }
	    closeEnv();

	} catch (Throwable T) {
	    System.out.println("caught " + T);
	    T.printStackTrace();
	}
    }

    private void doScavengerTest(int config)
	throws DatabaseException, IOException {

	boolean printable = (config & PRINTABLE) != 0;
	boolean transactional = (config & TRANSACTIONAL) != 0;
	boolean writeMultiple = (config & WRITE_MULTIPLE) != 0;
	boolean abortBefore = (config & ABORT_BEFORE) != 0;
	boolean abortAfter = (config & ABORT_AFTER) != 0;
	boolean corruptLog = (config & CORRUPT_LOG) != 0;
	boolean deleteData = (config & DELETE_DATA) != 0;
	boolean aggressive = (config & AGGRESSIVE) != 0;

	assert transactional ||
	    (!abortBefore && !abortAfter);

	Map[] dataMaps = new Map[N_DBS];
	Set lsnsToCorrupt = new HashSet();
	/* Create the environment and some data. */
	createEnvAndDbs(dataMaps,
			writeMultiple,
			transactional,
			abortBefore,
			abortAfter,
			corruptLog,
			lsnsToCorrupt,
			deleteData);
	closeEnv();
	createEnv(false, false);
	if (corruptLog) {
	    corruptFiles(lsnsToCorrupt);
	}
	openDbs(false, false, duplicatesAllowed, null);
	dumpDbs(printable, aggressive);

	/* Close the environment, delete it completely from the disk. */
	closeEnv();
        TestUtils.removeLogFiles("doScavengerTest", envHome, false);

	/* Recreate the environment and load it from the scavenger files. */
	createEnv(true, transactional);
	loadDbs();

	/* Verify that the data is the same as when it was created. */
	openDbs(false, false, duplicatesAllowed, null);
	verifyDbs(dataMaps);
	closeEnv();
    }

    private void closeEnv()
	throws DatabaseException {

	for (int i = 0; i < N_DBS; i++) {
	    if (dbs[i] != null) {
		dbs[i].close();
		dbs[i] = null;
	    }
	}

	env.close();
        env = null;
    }

    private void createEnv(boolean create, boolean transactional)
	throws DatabaseException {

	EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
				 "false");
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
				 "" + LOG_SIZE);
	envConfig.setTransactional(transactional);
	envConfig.setAllowCreate(create);
	env = new Environment(envHome, envConfig);
    }

    private void createEnvAndDbs(Map[] dataMaps,
				 boolean writeMultiple,
				 boolean transactional,
				 boolean abortBefore,
				 boolean abortAfter,
				 boolean corruptLog,
				 Set lsnsToCorrupt,
				 boolean deleteData)
	throws DatabaseException {

	createEnv(true, transactional);
	Transaction txn = null;
	if (transactional) {
	    txn = env.beginTransaction(null, null);
	}

	openDbs(true, transactional, duplicatesAllowed, txn);

	if (transactional) {
	    txn.commit();
	}

	long lastCorruptedFile = -1;
	for (int dbCnt = 0; dbCnt < N_DBS; dbCnt++) {
	    Map dataMap = new HashMap();
	    dataMaps[dbCnt] = dataMap;
	    Database db = dbs[dbCnt];

	    for (int i = 0; i < N_KEYS; i++) {
		byte[] dataBytes = new byte[N_DATA_BYTES];
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry(dataBytes);
		IntegerBinding.intToEntry(i, key);
		TestUtils.generateRandomAlphaBytes(dataBytes);

		boolean corruptedThisEntry = false;

		if (transactional) {
		    txn = env.beginTransaction(null, null);
		}

		if (transactional &&
		    abortBefore) {
		    assertEquals(OperationStatus.SUCCESS,
				 db.put(txn, key, data));
		    txn.abort();
		    txn = env.beginTransaction(null, null);
		}

		assertEquals(OperationStatus.SUCCESS,
			     db.put(txn, key, data));
		if (corruptLog) {
		    long currentLsn = getLastLsn();
		    long fileNumber = DbLsn.getFileNumber(currentLsn);
		    long fileOffset = DbLsn.getFileOffset(currentLsn);
		    if (fileOffset > (LOG_SIZE >> 1) &&
			/* We're writing in the second half of the file. */
			fileNumber > lastCorruptedFile) {
			/* Corrupt this file. */
			lsnsToCorrupt.add(new Long(currentLsn));
			lastCorruptedFile = fileNumber;
			corruptedThisEntry = true;
		    }
		}

		if (writeMultiple) {
		    assertEquals(OperationStatus.SUCCESS,
				 db.delete(txn, key));
		    assertEquals(OperationStatus.SUCCESS,
				 db.put(txn, key, data));
		}

		if (deleteData) {
		    assertEquals(OperationStatus.SUCCESS,
				 db.delete(txn, key));
		    /* overload this for deleted data. */
		    corruptedThisEntry = true;
		}

		if (!corruptedThisEntry) {
		    dataMap.put(new Integer(i), new String(dataBytes));
		}

		if (transactional) {
		    txn.commit();
		}

		if (transactional &&
		    abortAfter) {
		    txn = env.beginTransaction(null, null);
		    assertEquals(OperationStatus.SUCCESS,
				 db.put(txn, key, data));
		    txn.abort();
		}
	    }
	}
    }

    private void openDbs(boolean create,
			 boolean transactional,
			 boolean duplicatesAllowed,
			 Transaction txn)
	throws DatabaseException {

	for (int dbCnt = 0; dbCnt < N_DBS; dbCnt++) {
	    String databaseName = "simpleDb" + dbCnt;
	    DatabaseConfig dbConfig = new DatabaseConfig();
	    dbConfig.setAllowCreate(create);
	    dbConfig.setSortedDuplicates(duplicatesAllowed);
	    dbConfig.setTransactional(transactional);
	    if (dbs[dbCnt] != null) {
		throw new DatabaseException("database already open");
	    }
	    dbs[dbCnt] = env.openDatabase(txn, databaseName, dbConfig);
	}
    }

    private void dumpDbs(boolean printable, boolean aggressive)
	throws DatabaseException {

	try {
	    DbScavenger scavenger =
                new DbScavenger(env, null, envHomeName, printable, aggressive,
                                false /* verbose */);
	    scavenger.dump();
	} catch (IOException IOE) {
	    throw new DatabaseException(IOE);
	}
    }

    private void loadDbs()
	throws DatabaseException {

	try {
	    String dbNameBase = "simpleDb";
	    for (int i = 0; i < N_DBS; i++) {
		DbLoad loader = new DbLoad();
		File file = new File(envHomeName, dbNameBase + i + ".dump");
		FileInputStream is = new FileInputStream(file);
		BufferedReader reader =
		    new BufferedReader(new InputStreamReader(is));
		loader.setEnv(env);
		loader.setInputReader(reader);
		loader.setNoOverwrite(false);
		loader.setDbName(dbNameBase + i);
		loader.load();
		is.close();
	    }
        } catch (IOException IOE) {
	    throw new DatabaseException(IOE);
	}
    }

    private void verifyDbs(Map[] dataMaps)
	throws DatabaseException {

	for (int i = 0; i < N_DBS; i++) {
	    Map dataMap = dataMaps[i];
	    Cursor cursor = dbs[i].openCursor(null, null);
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry data = new DatabaseEntry();
	    while (cursor.getNext(key, data, null) ==
		   OperationStatus.SUCCESS) {
		Integer keyInt =
		    new Integer(IntegerBinding.entryToInt(key));
		String databaseString = new String(data.getData());
		String originalString = (String) dataMap.get(keyInt);
		if (originalString == null) {
		    fail("couldn't find " + keyInt);
		} else if (databaseString.equals(originalString)) {
		    dataMap.remove(keyInt);
		} else {
		    fail(" Mismatch: key=" + keyInt +
			 " Expected: " + originalString +
			 " Found: " + databaseString);
		}
	    }

	    if (dataMap.size() > 0) {
		fail("entries still remain");
	    }

	    cursor.close();
	}
    }

    private static DumpFileFilter dumpFileFilter = new DumpFileFilter();

    static class DumpFileFilter implements FilenameFilter {

	/**
	 * Accept files of this format:
	 * *.dump
	 */
	public boolean accept(File dir, String name) {
	    StringTokenizer tokenizer = new StringTokenizer(name, ".");
	    /* There should be two parts. */
	    if (tokenizer.countTokens() == 2) {
		String fileName = tokenizer.nextToken();
		String fileSuffix = tokenizer.nextToken();

		/* Check the length and the suffix. */
		if (fileSuffix.equals("dump")) {
		    return true;
		}
	    }

	    return false;
	}
    }

    private long getLastLsn()
	throws DatabaseException {

	return DbInternal.envGetEnvironmentImpl(env).
	    getFileManager().getLastUsedLsn();
    }

    private void corruptFiles(Set lsnsToCorrupt)
	throws DatabaseException {

	Iterator iter = lsnsToCorrupt.iterator();
	while (iter.hasNext()) {
	    long lsn = ((Long) iter.next()).longValue();
	    corruptFile(DbLsn.getFileNumber(lsn),
			DbLsn.getFileOffset(lsn));
	}
    }

    private void corruptFile(long fileNumber, long fileOffset)
	throws DatabaseException {

	String fileName = DbInternal.envGetEnvironmentImpl(env).
	    getFileManager().getFullFileName(fileNumber,
					     FileManager.JE_SUFFIX);
	/*
	System.out.println("corrupting 1 byte at " +
			   DbLsn.makeLsn(fileNumber, fileOffset));
	*/
	try {
	    RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
	    raf.seek(fileOffset);
	    int current = raf.read();
	    raf.seek(fileOffset);
	    raf.write(current + 1);
	    raf.close();
	} catch (IOException IOE) {
	    throw new DatabaseException(IOE);
	}
    }
}
