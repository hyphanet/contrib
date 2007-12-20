/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: IOExceptionTest.java,v 1.10.2.5 2007/12/13 00:12:11 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

public class IOExceptionTest extends TestCase {

    private Environment env;
    private Database db;
    private File envHome;

    public IOExceptionTest()
        throws Exception {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

	FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
	if (db != null) {
	    db.close();
	}

	if (env != null) {
	    env.close();
	}

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testIOExceptionOnRead()
	throws Exception {

	try {
	    createDatabase(MemoryBudget.MIN_MAX_MEMORY_SIZE, 0);
	
	    final int N_RECS = 25;

	    CheckpointConfig chkConf = new CheckpointConfig();
	    chkConf.setForce(true);
	    Transaction txn = env.beginTransaction(null, null);
	    int keyInt = 0;
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data =
		    new DatabaseEntry(("d" + keyStr).getBytes());
		try {
		    assertTrue(db.put(txn, key, data) ==
			       OperationStatus.SUCCESS);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException");
		    break;
		}
	    }

	    try {
		env.checkpoint(chkConf);
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    txn.commit();

	    db.close();
	    forceCloseEnvOnly();
	    createDatabase(0, 0);

	    EnvironmentStats stats = env.getStats(null);
	    assertTrue((stats.getNFullINFlush() +
			stats.getNFullBINFlush()) > 0);

	    txn = env.beginTransaction(null, null);
	    FileManager.IO_EXCEPTION_TESTING_ON_READ = true;
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data = new DatabaseEntry();
		try {
		    assertTrue(db.get(txn, key, data, null) ==
			       OperationStatus.SUCCESS);
		    assertEquals(new String(data.getData()), "d" + keyStr);
		} catch (DatabaseException DE) {
		    // ignore
		}
	    }

	    FileManager.IO_EXCEPTION_TESTING_ON_READ = false;

	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data = new DatabaseEntry();
		try {
		    assertTrue(db.get(txn, key, data, null) ==
			       OperationStatus.SUCCESS);
		    assertEquals(new String(data.getData()), "d" + keyStr);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException on read");
		}
	    }

	    /*
	     * Now we have some IN's in the log buffer and there have been
	     * IOExceptions that will later force rewriting that buffer.
	     */
	    try {
		txn.commit();
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }
	} catch (Throwable E) {
	    E.printStackTrace();
	}
    }

    public void testRunRecoveryExceptionOnWrite()
	throws Exception {

	try {
	    createDatabase(200000, 0);
	
	    final int N_RECS = 25;

	    CheckpointConfig chkConf = new CheckpointConfig();
	    chkConf.setForce(true);
	    Transaction txn = env.beginTransaction(null, null);
	    int keyInt = 0;
	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data =
		    new DatabaseEntry(("d" + keyStr).getBytes());
		if (i == (N_RECS - 1)) {
		    FileManager.THROW_RRE_FOR_UNIT_TESTS = true;
		    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;
		}
		try {
		    assertTrue(db.put(txn, key, data) ==
			       OperationStatus.SUCCESS);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException");
		    break;
		}
	    }

	    try {
		txn.commit();
		fail("expected DatabaseException");
	    } catch (RunRecoveryException DE) {
	    }
	    forceCloseEnvOnly();

	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
	    FileManager.THROW_RRE_FOR_UNIT_TESTS = false;
	    db = null;
	    env = null;
	} catch (Throwable E) {
	    E.printStackTrace();
	}
    }

    public void testIOExceptionNoRecovery()
	throws Throwable {

	doIOExceptionTest(false);
    }

    public void testIOExceptionWithRecovery()
	throws Throwable {

	doIOExceptionTest(true);
    }

    public void testEviction()
	throws Exception {

	try {
	    createDatabase(200000, 0);
	
	    final int N_RECS = 25;

	    CheckpointConfig chkConf = new CheckpointConfig();
	    chkConf.setForce(true);
	    Transaction txn = env.beginTransaction(null, null);
	    int keyInt = 0;
	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data =
		    new DatabaseEntry(("d" + keyStr).getBytes());
		try {
		    assertTrue(db.put(txn, key, data) ==
			       OperationStatus.SUCCESS);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException");
		    break;
		}
	    }

	    try {
		env.checkpoint(chkConf);
		fail("expected DatabaseException");
	    } catch (DatabaseException DE) {
	    }

	    EnvironmentStats stats = env.getStats(null);
	    assertTrue((stats.getNFullINFlush() +
			stats.getNFullBINFlush()) > 0);

	    /* Read back the data and make sure it all looks ok. */
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(keyInt);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data = new DatabaseEntry();
		try {
		    assertTrue(db.get(txn, key, data, null) ==
			       OperationStatus.SUCCESS);
		    assertEquals(new String(data.getData()), "d" + keyStr);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException");
		    break;
		}
	    }

	    /*
	     * Now we have some IN's in the log buffer and there have been
	     * IOExceptions that will later force rewriting that buffer.
	     */
	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
	    try {
		txn.commit();
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }
	} catch (Exception E) {
	    E.printStackTrace();
	}
    }

    /*
     * Test for SR 13898.  Write out some records with
     * IO_EXCEPTION_TESTING_ON_WRITE true thereby forcing some commits to be
     * rewritten as aborts.  Ensure that the checksums are correct on those
     * rewritten records by reading them back with a file reader.
     */
    public void testIOExceptionReadBack()
	throws Exception {

	try {
	    createDatabase(100000, 1000);
	
	    final int N_RECS = 25;

	    CheckpointConfig chkConf = new CheckpointConfig();
	    chkConf.setForce(true);
	    Transaction txn = env.beginTransaction(null, null);
	    int keyInt = 0;
	    for (int i = 0; i < N_RECS; i++) {
		String keyStr = Integer.toString(i);
		DatabaseEntry key =
		    new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data =
		    new DatabaseEntry(new byte[100]);
		try {
		    assertTrue(db.put(txn, key, data) ==
			       OperationStatus.SUCCESS);
		} catch (DatabaseException DE) {
		    fail("unexpected DatabaseException");
		    break;
		}
		try {
		    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;
		    txn.commit();
		    fail("expected DatabaseException");
		} catch (DatabaseException DE) {
		}
		FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
		txn = env.beginTransaction(null, null);
	    }

	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
	    try {
		env.checkpoint(chkConf);
	    } catch (DatabaseException DE) {
		DE.printStackTrace();
		fail("unexpected DatabaseException");
	    }

	    EnvironmentStats stats = env.getStats(null);
	    assertTrue((stats.getNFullINFlush() +
			stats.getNFullBINFlush()) > 0);
	    long lastCheckpointLsn = stats.getLastCheckpointStart();

	    try {
		txn.commit();
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    FileReader reader = new FileReader
		(DbInternal.envGetEnvironmentImpl(env),
		 4096, true, 0, null, DbLsn.NULL_LSN, DbLsn.NULL_LSN) {
		    protected boolean processEntry(ByteBuffer entryBuffer)
			throws DatabaseException {

			entryBuffer.position(entryBuffer.position() +
					     currentEntryHeader.getItemSize());
			return true;
		    }
		};

	    while (reader.readNextEntry()) {
	    }
	} catch (Throwable E) {
	    E.printStackTrace();
	}
    }

    public void testLogBufferOverflowAbortNoDupes()
	throws Exception {

	doLogBufferOverflowTest(false, false);
    }

    public void testLogBufferOverflowCommitNoDupes()
	throws Exception {

	doLogBufferOverflowTest(true, false);
    }

    public void testLogBufferOverflowAbortDupes()
	throws Exception {

	doLogBufferOverflowTest(false, true);
    }

    public void testLogBufferOverflowCommitDupes()
	throws Exception {

	doLogBufferOverflowTest(true, true);
    }

    private void doLogBufferOverflowTest(boolean abort, boolean dupes)
	throws Exception {

	try {
	    EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	    envConfig.setTransactional(true);
	    envConfig.setAllowCreate(true);
	    envConfig.setCacheSize(100000);
	    envConfig.setConfigParam("java.util.logging.level", "OFF");
	    env = new Environment(envHome, envConfig);

	    String databaseName = "ioexceptiondb";
	    DatabaseConfig dbConfig = new DatabaseConfig();
	    dbConfig.setAllowCreate(true);
	    dbConfig.setSortedDuplicates(true);
	    dbConfig.setTransactional(true);
	    db = env.openDatabase(null, databaseName, dbConfig);
	
	    Transaction txn = env.beginTransaction(null, null);
	    DatabaseEntry oneKey =
		(dupes ?
		 new DatabaseEntry("2".getBytes()) :
		 new DatabaseEntry("1".getBytes()));
	    DatabaseEntry oneData =
		new DatabaseEntry(new byte[10]);
	    DatabaseEntry twoKey =
		new DatabaseEntry("2".getBytes());
	    DatabaseEntry twoData =
		new DatabaseEntry(new byte[100000]);
	    if (dupes) {
		DatabaseEntry temp = oneKey;
		oneKey = oneData;
		oneData = temp;
		temp = twoKey;
		twoKey = twoData;
		twoData = temp;
	    }

	    try {
		assertTrue(db.put(txn, oneKey, oneData) ==
			   OperationStatus.SUCCESS);
		db.put(txn, twoKey, twoData);
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    /* Read back the data and make sure it all looks ok. */
	    try {
		assertTrue(db.get(txn, oneKey, oneData, null) ==
			   OperationStatus.SUCCESS);
		assertTrue(oneData.getData().length == (dupes ? 1 : 10));
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    try {
		assertTrue(db.get(txn, twoKey, twoData, null) ==
			   OperationStatus.SUCCESS);
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    try {
		if (abort) {
		    txn.abort();
		} else {
		    txn.commit();
		}
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    /* Read back the data and make sure it all looks ok. */
	    try {
		assertTrue(db.get(null, oneKey, oneData, null) ==
			   (abort ?
			    OperationStatus.NOTFOUND :
			    OperationStatus.SUCCESS));
		assertTrue(oneData.getData().length == (dupes ? 1 : 10));
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	    try {
		assertTrue(db.get(null, twoKey, twoData, null) ==
			   (abort ?
			    OperationStatus.NOTFOUND :
			    OperationStatus.SUCCESS));
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }

	} catch (Exception E) {
	    E.printStackTrace();
	}
    }

    public void testPutTransactionalWithIOException()
	throws Throwable {

	try {
	    createDatabase(100000, 0);

	    Transaction txn = env.beginTransaction(null, null);
	    int keyInt = 0;
	    String keyStr;
	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;

	    /* Fill up the buffer until we see an IOException. */
	    while (true) {
		keyStr = Integer.toString(++keyInt);
		DatabaseEntry key = new DatabaseEntry(keyStr.getBytes());
		DatabaseEntry data =
		    new DatabaseEntry(("d" + keyStr).getBytes());
		try {
		    assertTrue(db.put(txn, key, data) ==
			       OperationStatus.SUCCESS);
		} catch (DatabaseException DE) {
		    break;
		}
	    }

	    /* Buffer still hasn't been written.  This should also fail. */
	    try {
		db.put(txn,
		       new DatabaseEntry("shouldFail".getBytes()),
		       new DatabaseEntry("shouldFailD".getBytes()));
		fail("expected DatabaseException");
	    } catch (DatabaseException DE) {
		// expected
	    }
	    FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;

	    /* Buffer should write out ok now. */
	    try {
		db.put(txn,
		       new DatabaseEntry("shouldNotFail".getBytes()),
		       new DatabaseEntry("shouldNotFailD".getBytes()));
	    } catch (DatabaseException DE) {
		fail("unexpected DatabaseException");
	    }
	    txn.commit();

	    DatabaseEntry data = new DatabaseEntry();
	    assertTrue(db.get(null,
			      new DatabaseEntry("shouldNotFail".getBytes()),
			      data,
			      null) == OperationStatus.SUCCESS);
	    assertTrue(new String(data.getData()).equals("shouldNotFailD"));

	    assertTrue(db.get(null,
			      new DatabaseEntry("shouldFail".getBytes()),
			      data,
			      null) == OperationStatus.NOTFOUND);

	    assertTrue(db.get(null,
			      new DatabaseEntry("shouldFail".getBytes()),
			      data,
			      null) == OperationStatus.NOTFOUND);

	    assertTrue(db.get(null,
			      new DatabaseEntry(keyStr.getBytes()),
			      data,
			      null) == OperationStatus.NOTFOUND);

	    for (int i = --keyInt; i > 0; i--) {
		keyStr = Integer.toString(i);
		assertTrue(db.get(null,
				  new DatabaseEntry(keyStr.getBytes()),
				  data,
				  null) == OperationStatus.SUCCESS);
		assertTrue(new String(data.getData()).equals("d" + keyStr));
	    }

	} catch (Throwable T) {
	    T.printStackTrace();
	}
    }

    public void testIOExceptionDuringFileFlippingWrite()
	throws Throwable {

	doIOExceptionDuringFileFlippingWrite(8, 33, 2);
    }

    private void doIOExceptionDuringFileFlippingWrite(int numIterations,
						      int exceptionStartWrite,
						      int exceptionWriteCount)
	throws Throwable {

	try {
	    EnvironmentConfig envConfig = new EnvironmentConfig();
	    DbInternal.disableParameterValidation(envConfig);
	    envConfig.setTransactional(true);
	    envConfig.setAllowCreate(true);
	    envConfig.setConfigParam("je.log.fileMax", "1000");
	    envConfig.setConfigParam("je.log.bufferSize", "1025");
	    envConfig.setConfigParam("je.env.runCheckpointer", "false");
	    envConfig.setConfigParam("je.env.runCleaner", "false");
	    env = new Environment(envHome, envConfig);

	    EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	    DatabaseConfig dbConfig = new DatabaseConfig();
	    dbConfig.setTransactional(true);
	    dbConfig.setAllowCreate(true);
	    db = env.openDatabase(null, "foo", dbConfig);

	    String stuff = new String("..hello world ......................" +
				      "...................................");

	    /* 
	     * Put one record into the database so it gets populated w/INs and
	     * LNs, and we can fake out the RMW commits used below.
	     */
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry data = new DatabaseEntry();
	    IntegerBinding.intToEntry(5, key);
	    IntegerBinding.intToEntry(5, data);
	    db.put(null, key, data);

	    /*
	     * Now generate trace and commit log entries. The trace records 
	     * aren't forced out, but the commit records are forced.
	     */
	    FileManager.WRITE_COUNT = 0;
	    FileManager.THROW_ON_WRITE = true;
	    FileManager.STOP_ON_WRITE_COUNT = exceptionStartWrite;
	    FileManager.N_BAD_WRITES = exceptionWriteCount;
	    for (int i = 0; i < numIterations; i++) {

		try {
		    /* Generate a non-forced record. */
		    if (i == (numIterations - 1)) {

			/*
			 * On the last iteration, write a record that is large
			 * enough to force a file flip (i.e. an fsync which
			 * succeeds) followed by the large write (which doesn't
			 * succeed due to an IOException).  In [#15754] the
			 * large write fails on Out Of Disk Space, rolling back
			 * the savedLSN to the previous file, even though the
			 * file has flipped.  The subsequent write ends up in
			 * the flipped file, but at the offset of the older
			 * file (leaving a hole in the new flipped file).
			 */
			Tracer.trace(Level.SEVERE, envImpl,
				     i + "/" + FileManager.WRITE_COUNT +
				     " " + new String(new byte[2000]));
		    } else {
			Tracer.trace(Level.SEVERE, envImpl,
				     i + "/" + FileManager.WRITE_COUNT +
				     " " + "xx");
		    }
		} catch (IllegalStateException ISE) {
		    /* Eat exception thrown by TraceLogHandler. */
		}

		/* Generated a forced record by calling commit. */
		Transaction txn = env.beginTransaction(null, null);
		db.get(txn, key, data, LockMode.RMW);
		txn.commit();
	    }
	    db.close();

	    /*
	     * Verify that the log files are ok and have no checksum errors.
	     */
	    FileReader reader =
		new FileReader(DbInternal.envGetEnvironmentImpl(env),
			       4096, true, 0, null, DbLsn.NULL_LSN,
			       DbLsn.NULL_LSN) {
		    protected boolean processEntry(ByteBuffer entryBuffer)
			throws DatabaseException {

			entryBuffer.position(entryBuffer.position() +
					     currentEntryHeader.getItemSize());
			return true;
		    }
		};

	    DbInternal.envGetEnvironmentImpl(env).getLogManager().flush();

	    while (reader.readNextEntry()) {
	    }

	    /* Make sure the reader really did scan the files. */
	    assert (DbLsn.getFileNumber(reader.getLastLsn()) == 4) :
		DbLsn.toString(reader.getLastLsn());

	    env.close();
	    env = null;
	    db = null;
	} catch (Throwable T) {
	    T.printStackTrace();
	}
    }

    private void doIOExceptionTest(boolean doRecovery)
	throws Throwable {

	Transaction txn = null;
	createDatabase(0, 0);
	writeAndVerify(null, false, "k1", "d1", doRecovery);
	writeAndVerify(null, true, "k2", "d2", doRecovery);
	writeAndVerify(null, false, "k3", "d3", doRecovery);

	txn = env.beginTransaction(null, null);
	writeAndVerify(txn, false, "k4", "d4", false);
	txn.abort();
	verify(null, true, "k4", doRecovery);
	verify(null, false, "k1", doRecovery);
	verify(null, false, "k3", doRecovery);

	txn = env.beginTransaction(null, null);
	writeAndVerify(txn, false, "k4", "d4", false);
	txn.commit();
	verify(null, false, "k4", doRecovery);

	txn = env.beginTransaction(null, null);
	writeAndVerify(txn, true, "k5", "d5", false);
	/* Ensure that writes after IOExceptions don't succeed. */
	writeAndVerify(txn, false, "k5a", "d5a", false);
	txn.abort();
	verify(null, true, "k5", doRecovery);
	verify(null, true, "k5a", doRecovery);

	txn = env.beginTransaction(null, null);
	writeAndVerify(txn, false, "k6", "d6", false);
	writeAndVerify(txn, true, "k6a", "d6a", false);

	FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;
	try {
	    txn.commit();
	    fail("expected DatabaseException");
	} catch (DatabaseException DE) {
	}
	verify(null, true, "k6", doRecovery);
	verify(null, true, "k6a", doRecovery);

	txn = env.beginTransaction(null, null);
	writeAndVerify(txn, false, "k6", "d6", false);
	writeAndVerify(txn, true, "k6a", "d6a", false);
	writeAndVerify(txn, false, "k6b", "d6b", false);

	try {
	    txn.commit();
	} catch (DatabaseException DE) {
	    fail("expected success");
	}

	/*
	 * k6a will still exist because the writeAndVerify didn't fail -- there
	 * was no write.  The write happens at commit time.
	 */
	verify(null, false, "k6", doRecovery);
	verify(null, false, "k6a", doRecovery);
	verify(null, false, "k6b", doRecovery);
    }

    private void writeAndVerify(Transaction txn,
				boolean throwIOException,
				String keyString,
				String dataString,
				boolean doRecovery)
	throws DatabaseException {

	//System.out.println("Key: " + keyString + " Data: " + dataString);
	DatabaseEntry key = new DatabaseEntry(keyString.getBytes());
	DatabaseEntry data = new DatabaseEntry(dataString.getBytes());
	FileManager.IO_EXCEPTION_TESTING_ON_WRITE = throwIOException;
	try {
	    assertTrue(db.put(txn, key, data) == OperationStatus.SUCCESS);

	    /*
	     * We don't expect an IOException if we're in a transaction because
	     * the put() only writes to the buffer, not the disk.  The write to
	     * disk doesn't happen until the commit/abort.
	     */
	    if (throwIOException && txn == null) {
		fail("didn't catch DatabaseException.");
	    }
	} catch (DatabaseException DE) {
	    if (!throwIOException) {
		fail("caught DatabaseException.");
	    }
	}
	verify(txn, throwIOException, keyString, doRecovery);
    }

    private void verify(Transaction txn,
			boolean expectFailure,
			String keyString,
			boolean doRecovery)
	throws DatabaseException {

	if (doRecovery) {
	    db.close();
	    forceCloseEnvOnly();
	    createDatabase(0, 0);
	}
	DatabaseEntry key = new DatabaseEntry(keyString.getBytes());
	DatabaseEntry returnedData = new DatabaseEntry();
	OperationStatus status =
	    db.get(txn,
		   key,
		   returnedData,
		   LockMode.DEFAULT);
	//System.out.println(keyString + " => " + status);
	assertTrue(status == ((expectFailure && txn == null) ?
			      OperationStatus.NOTFOUND :
			      OperationStatus.SUCCESS));
    }

    private void createDatabase(long cacheSize, long maxFileSize)
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
	envConfig.setConfigParam
	    (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
	envConfig.setConfigParam
	    (EnvironmentParams.LOG_MEM_SIZE.getName(),
	     EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
	if (maxFileSize != 0) {
	    DbInternal.disableParameterValidation(envConfig);
	    envConfig.setConfigParam
		(EnvironmentParams.LOG_FILE_MAX.getName(), "" + maxFileSize);
	}
	if (cacheSize != 0) {
	    envConfig.setCacheSize(cacheSize);
	    envConfig.setConfigParam("java.util.logging.level", "OFF");
	}
	env = new Environment(envHome, envConfig);

        String databaseName = "ioexceptiondb";
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
	dbConfig.setTransactional(true);
        db = env.openDatabase(null, databaseName, dbConfig);
    }

    /* Force the environment to be closed even with outstanding handles.*/
    private void forceCloseEnvOnly()
	throws DatabaseException {

	/* Close w/out checkpointing, in order to exercise recovery better.*/
	try {
	    DbInternal.envGetEnvironmentImpl(env).close(false);
	} catch (DatabaseException DE) {
	    if (!FileManager.IO_EXCEPTION_TESTING_ON_WRITE) {
		throw DE;
	    } else {
		/* Expect an exception from flushing the log manager. */
	    }
	}
	env = null;
    }
}
