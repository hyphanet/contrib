/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryCheckpointTest.java,v 1.37.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Hashtable;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
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
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.util.TestUtils;

public class RecoveryCheckpointTest extends RecoveryTestBase {

    volatile int sequence = 0;

    public void setExtraProperties()
	throws DatabaseException {

        /*
         * Make sure that the environments in this unit test always run with
         * checkpointing off, so we can call it explcitly.
         */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
	/*
        envConfig.setConfigParam
            (EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
	*/
    }

    /**
     * Run checkpoints on empty dbs.
     */
    public void testEmptyCheckpoint()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);

        try {

            /*
	     * Run checkpoint on empty environment. Should be the second one
	     * run, the first was run by recovery when the environment was
	     * opened.
	     */
            env.checkpoint(forceConfig);
            EnvironmentStats stats = env.getStats(TestUtils.FAST_STATS);
            assertEquals(2, stats.getNCheckpoints());
            assertEquals(2, stats.getLastCheckpointId());

            /* Shutdown, recover. */
            Hashtable expectedData = new Hashtable(); // expected values
            closeEnv();  
            recoverAndVerify(expectedData, NUM_DBS); // 0 checkpoints

            /* Another checkpoint. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
	    envConfig.setConfigParam
		(EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
            env = new Environment(envHome, envConfig);
            env.checkpoint(forceConfig);
            stats = env.getStats(TestUtils.FAST_STATS);
            assertEquals(2, stats.getNCheckpoints());
            assertEquals(4, stats.getLastCheckpointId());

            /* Shutdown, recover. */
            env.close();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Run checkpoints on empty dbs.
     */
    public void testNoCheckpointOnOpenSR11861()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);

        try {

            EnvironmentStats stats = env.getStats(TestUtils.FAST_STATS);
            assertEquals(1, stats.getNCheckpoints());
            assertEquals(1, stats.getLastCheckpointId());

            /* Shutdown, recover. */
            Hashtable expectedData = new Hashtable(); // expected values
            Transaction txn = env.beginTransaction(null, null);
	    insertData(txn, 0, 1, expectedData, 1, true, NUM_DBS);
	    txn.commit();
            closeEnv();   // closes without a checkpoint
            recoverAndVerify(expectedData, NUM_DBS); // 1 checkpoint

            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            env = new Environment(envHome, envConfig);
            stats = env.getStats(TestUtils.FAST_STATS);
            assertEquals(0, stats.getNCheckpoints());
            assertEquals(2, stats.getLastCheckpointId());
	    env.close();
            env = new Environment(envHome, envConfig);
            stats = env.getStats(TestUtils.FAST_STATS);
            assertEquals(0, stats.getNCheckpoints());
            assertEquals(2, stats.getLastCheckpointId());

            /* Shutdown, recover. */
            env.close();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test checkpoints that end up using BINDeltas -- the recovery must work.
     */
    public void testBinDelta()
	throws Throwable {

        doTestBinDelta(true);
    }

    /**
     * Same as testBinDelta but disallows deltas, to ensure that the
     * setMinimizeRecoveryTime API works.
     */
    public void testNoBinDelta()
	throws Throwable {

        doTestBinDelta(false);
    }

    private void doTestBinDelta(boolean useDeltas)
	throws Throwable {

	createEnvAndDbs(1 << 20, false, NUM_DBS);

        StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);

        CheckpointConfig deltaConfig = new CheckpointConfig();
        deltaConfig.setForce(true);
        deltaConfig.setMinimizeRecoveryTime(!useDeltas);

        try {
            
            /*
             * Insert 4 records (nodeMax is 6), checkpoint, then insert 1
             * record.  The 1 record insertion will qualify for a delta,
             * because the threshold percentage is 25%, and 25% of 4 is 1.
             */
            int numRecs = 4;
            Hashtable expectedData = new Hashtable();
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs, expectedData, 1, true, NUM_DBS);
            env.checkpoint(forceConfig);
            insertData(txn, numRecs+1, numRecs+2, expectedData,
		       1, true, NUM_DBS);
            txn.commit();
            
            /* 
             * If useDeltas is true, this next checkpoint will end up using a
             * BINDelta to log the last inserted record. It will have
             * practically nothing but the root in the checkpoint.
             */
            EnvironmentStats stats = env.getStats(statsConfig);
            env.checkpoint(deltaConfig);
            stats = env.getStats(statsConfig);
            if (useDeltas) {
                assertTrue(stats.getNDeltaINFlush() > 0);
            } else {
                assertTrue(stats.getNDeltaINFlush() == 0);
            }

            /* Shutdown, recover from a checkpoint that uses BINDeltas. */
            closeEnv();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /** 
     * Test the rollback of transactions that are active during a checkpoint.
     */
    public void testActiveWhileCheckpointing() 
        throws Throwable {

	createEnvAndDbs(1 << 20, true, NUM_DBS);

        try {
            int numRecs = 1;
            Hashtable expectedData = new Hashtable();
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs, expectedData, 1, false, NUM_DBS);

            /* Now run a checkpoint while this operation hasn't finished. */
            env.checkpoint(forceConfig);
            txn.abort();
            
            /* Shutdown, recover. */
            closeEnv();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testSR11293()
	throws Throwable {

	createEnv(1 << 20, false);

	Transaction dbTxn = env.beginTransaction(null, null);
	EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	final DbTree dbTree = envImpl.getDbMapTree();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
	final Database db = env.openDatabase(dbTxn, "foo", dbConfig);
	dbTxn.commit();
	final Transaction txn = env.beginTransaction(null, null);
	sequence = 0;

	/**
	 * The sequence between the two tester threads is:
	 *
	 * tester2: write 1/1 into the database.  This causes the initial tree
	 * to be created (IN/BIN/LN).  Flush that out to the disk with a full
	 * checkpoint.  Signal tester1 and wait.
	 *
	 * tester1: Lock the MapLN for "foo" db.  Signal tester2 and wait.
	 *
	 * tester2: Add 2/2 to the tree which causes the BIN to be dirtied.
	 * Signal tester1 to continue, perform a full checkpoint which will
	 * causes the root IN to be dirtied and flushed.  DbTree.modifyDbRoot
	 * will block on the MapLN lock held by tester1.
	 *
	 * tester1: while tester2 is blocking on the MapLN lock, this thread is
	 * sleeping.  When it wakes up, it releases the MapLN lock by aborting
	 * the transaction.
	 *
	 * tester2: modifyDbRoot finally acquires the write lock on foo-db's
	 * MapLN write lock, performs the update to the DbTree and returns from
	 * the sync().
	 */
	JUnitThread tester1 =
	    new JUnitThread("testSR11293DbTreeLocker") {
		    public void testBody() {
			try {
			    /* Wait for tester2. */
			    while (sequence < 1) {
				Thread.yield();
			    }

			    /* Lock the MapLN for the database. */
			    DatabaseId fooId =
				DbInternal.dbGetDatabaseImpl(db).getId();
			    DatabaseImpl fooDb = dbTree.getDb(fooId, 500000L);
			    assert fooDb != null;

			    sequence++;

			    /* Wait for tester2. */
			    while (sequence < 3) {
				Thread.yield();
			    }

			    try {
				Thread.sleep(3000);
			    } catch (Exception E) {
			    }

			    try {
				txn.abort();
				db.close();
				env.close();
			    } catch (DatabaseException DBE) {
				DBE.printStackTrace();
				fail("unexpected exception: " + DBE);
			    }
			} catch (DatabaseException DBE) {
			    DBE.printStackTrace();
			    fail("caught DatabaseException " + DBE);
			}
		    }
		};

	JUnitThread tester2 =
	    new JUnitThread("testSR11293DbWriter") {
		    public void testBody() {
			try {
			    DatabaseEntry key =
				new DatabaseEntry(new byte[] { 1 });
			    DatabaseEntry data =
				new DatabaseEntry(new byte[] { 1 });
			    assertEquals(OperationStatus.SUCCESS,
					 db.put(null, key, data));
			    env.sync();

			    sequence++;
			    while (sequence < 2) {
				Thread.yield();
			    }

			    key.setData(new byte[] { 2 });
			    data.setData(new byte[] { 2 });
			    assertEquals(OperationStatus.SUCCESS,
					 db.put(null, key, data));
			    sequence++;
			    env.sync();
			} catch (DatabaseException DBE) {
			    DBE.printStackTrace();
			    fail("unexpected exception: " + DBE);
			}
		    }
		};

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();

        EnvironmentConfig recoveryConfig = TestUtils.initEnvConfig();

        recoveryConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
	recoveryConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
	recoveryConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");

        env = new Environment(envHome, recoveryConfig);
	dbConfig.setAllowCreate(false);
	dbConfig.setTransactional(false);
	Database db2 = env.openDatabase(null, "foo", dbConfig);
	Cursor c = db2.openCursor(null, null);
	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	assertEquals(OperationStatus.SUCCESS,
		     c.getNext(key, data, LockMode.DEFAULT));
	assertEquals((key.getData())[0], 1);
	assertEquals((data.getData())[0], 1);

	assertEquals(OperationStatus.SUCCESS,
		     c.getNext(key, data, LockMode.DEFAULT));
	assertEquals((key.getData())[0], 2);
	assertEquals((data.getData())[0], 2);
	assertEquals(OperationStatus.NOTFOUND,
		     c.getNext(key, data, LockMode.DEFAULT));

	c.close();
	db2.close();
	env.close();
    }

    /* 
     * See what happens if someone calls checkpoint on a read only environment.
     */
    public void testReadOnlyCheckpoint() 
        throws DatabaseException {
        /* Create an environment, close. */
        EnvironmentConfig c = TestUtils.initEnvConfig();
        c.setAllowCreate(true);
        Environment e = new Environment(envHome, c);
        e.close();

        /* Now open read only. */
        c.setAllowCreate(false);
        c.setReadOnly(true);
        e = new Environment(envHome, c);
        try {
            CheckpointConfig ckptConfig = new CheckpointConfig();
            ckptConfig.setForce(true);
            e.checkpoint(ckptConfig);
        } finally {
            e.close();
        }
    }
}
