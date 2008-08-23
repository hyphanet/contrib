/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DeferredWriteTest.java,v 1.18 2008/06/06 17:12:29 linda Exp $
 */

package com.sleepycat.je.test;

import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.bind.tuple.IntegerBinding;
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
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.cleaner.VerifyUtils;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

public class DeferredWriteTest extends TestCase {
    private static boolean DEBUG = false;
    private static String DBNAME = "foo";
    private static String DBNAME2 = "foo2";
    private static DatabaseEntry MAIN_KEY_FOR_DUPS =
        new DatabaseEntry(new byte[10]);

    private static final CheckpointConfig CHECKPOINT_FORCE_CONFIG =
        new CheckpointConfig();

    static {
        CHECKPOINT_FORCE_CONFIG.setForce(true);
    }

    private static final StatsConfig STATS_CLEAR_CONFIG = new StatsConfig();

    static {
        STATS_CLEAR_CONFIG.setClear(true);
    }

    private File envHome;
    private Environment env;
    private boolean truncateOrRemoveDone;
    private boolean dups;

    public static Test suite() {
        TestSuite allTests = new TestSuite();
        addTests(allTests, false); // no dups
        addTests(allTests, true); // dups
        return allTests;
    }

    private static void addTests(TestSuite allTests, boolean dups) {
        TestSuite suite = new TestSuite(DeferredWriteTest.class);
        Enumeration e = suite.tests();
        while (e.hasMoreElements()) {
            DeferredWriteTest test = (DeferredWriteTest) e.nextElement();
            test.dups = dups;
            allTests.addTest(test);
        }
    }

    public DeferredWriteTest()
        throws Exception {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
	throws Exception {

        /* Set test name for reporting; cannot be done in the ctor or setUp. */
        setName(getName() + (dups ? ":dups" : ""));

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.err.println("TearDown: " + e);
            }
        }
        env = null;
        //*
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
        //*/
    }

    private EnvironmentConfig getEnvConfig(boolean transactional) {
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(transactional);
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(), "4");
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX_DUPTREE.getName(), "4");
        /* Force correct LN obsolete size calculation during recovery. */
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_FETCH_OBSOLETE_SIZE.getName(), "true");
        if (DEBUG) {
            envConfig.setConfigParam("java.util.logging.ConsoleHandler.on",
                                     "true");
            envConfig.setConfigParam("java.util.logging.level.cleaner",
                                     "SEVERE");
        }

        return envConfig;
    }

    private void closeEnv(boolean normalClose)
        throws DatabaseException {

        closeEnv(normalClose,
                 true /*expectAccurateObsoleteLNCount*/,
                 true /*expectAccurateDbUtilization*/);
    }

    /**
     * @param expectAccurateObsoleteLNCount should be false only when an LN
     * cannot be counted obsolete during recovery as explained in
     * RecoveryManager.redoUtilizationInfo.
     *
     * @param expectAccurateDbUtilization should be false only when DB info is
     * not accurate because INs are evicted and then recovered without a
     * checkpoint.  The provisional INs are counted obsolete by recovery in the
     * per-DB info because the MapLN is not flushed, but not in the per-file
     * info because the FileSummaryLNs are flushed by eviction.
     */
    private void closeEnv(boolean normalClose,
                          boolean expectAccurateObsoleteLNCount,
                          boolean expectAccurateDbUtilization)
        throws DatabaseException {

        if (env != null) {

            /* Stop daemons first to stop utilization from changing. */
            try {
                DbInternal.envGetEnvironmentImpl(env).stopDaemons();
            } catch (InterruptedException e) {
                throw new DatabaseException(e);
            }

            /*
             * We pass expectAccurateDbUtilization as false when
             * truncateOrRemoveDone, because the database utilization info for
             * that database is now gone.
             */
            VerifyUtils.verifyUtilization
                (DbInternal.envGetEnvironmentImpl(env),
                 expectAccurateObsoleteLNCount,
                 true,                   // expectAccurateObsoleteLNSize,
                 expectAccurateDbUtilization &&
                 !truncateOrRemoveDone); // expectAccurateDbUtilization

            if (normalClose) {
                env.close();
            } else {
                DbInternal.envGetEnvironmentImpl(env).abnormalClose();
            }
            env = null;
        }
    }

    private Database createDb(boolean deferredWrite)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(deferredWrite);
        dbConfig.setSortedDuplicates(dups);

        Database db = env.openDatabase(null, DBNAME, dbConfig);

        assertEquals
            (deferredWrite,
             DbInternal.dbGetDatabaseImpl(db).isDurableDeferredWrite());
        assertEquals
            (deferredWrite,
             DbInternal.dbGetDatabaseImpl(db).isDeferredWriteMode());
        assertEquals
            (false,
             DbInternal.dbGetDatabaseImpl(db).isTemporary());

        return db;
    }

    private Database createTempDb()
        throws DatabaseException {

        return createTempDb(DBNAME);
    }

    private Database createTempDb(String dbName)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTemporary(true);
        dbConfig.setSortedDuplicates(dups);

        Database db = env.openDatabase(null, dbName, dbConfig);

        assertEquals
            (false,
             DbInternal.dbGetDatabaseImpl(db).isDurableDeferredWrite());
        assertEquals
            (true,
             DbInternal.dbGetDatabaseImpl(db).isDeferredWriteMode());
        assertEquals
            (true,
             DbInternal.dbGetDatabaseImpl(db).isTemporary());

        return db;
    }

    /**
     * Check that all INs are removed from the INList for a DB that is removed
     * before it is sync'ed (or checkpointed).  Before the bug fix, INs were
     * not removed if the DB root IN was never logged (was still null).  This
     * caused a DatabaseException when evicting, because the evictor expects no
     * INs for deleted DBs on the INList.
     */
    public void testRemoveNonPersistentDbSR15317()
	throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        /* Disable compressor for test predictability. */
        envConfig.setConfigParam("je.env.runINCompressor", "false");
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);
        /* Insert some data to cause eviction later. */
        insert(db,
               null,          // txn
               1,             // start
               30000,         // end
               new HashSet(), // expected
               false);        // useRandom
        db.close();
        env.removeDatabase(null, DBNAME);
        truncateOrRemoveDone = true;

        envConfig = env.getConfig();
        /* Switch to a small cache to force eviction. */
        envConfig.setCacheSize(96 * 1024);
        env.setMutableConfig(envConfig);
        for (int i = 0; i < 10; i += 1) {
            env.evictMemory();
        }
        closeEnv(true /*normalClose*/);
    }

    public void testEmptyDatabaseSR14744()
	throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);
        db.sync();
        db.close();
        env.sync();
        closeEnv(true /*normalClose*/);
    }

    /**
     * Check that deferred write db re-opens at expected state.
     */
    public void testCloseOpen()
        throws Throwable {

        HashSet expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        true,   /* doSync */
                        1,      /* starting value */
                        new HashSet()); /* initial ExpectedSet */
        expectedSet =
            doCloseOpen(false,  /* useDeferredWrites */
                        true,   /* doSync */
                        100,    /* starting value */
                        expectedSet);
        expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        true,   /* doSync */
                        200,    /* starting value */
                        expectedSet);
    }

    /**
     * Check that after crashing without a close/sync/checkpoint, a deferred
     * write DB does not contain the unflushed data.
     */
    public void testCloseOpenNoSync()
        throws Throwable {

        HashSet expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        false,  /* doSync */
                        1,      /* starting value */
                        new HashSet()); /* initial ExpectedSet */
        expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        false,  /* doSync */
                        100,    /* starting value */
                        expectedSet);
    }

    /**
     * Check that deferred write and durable databases re-open at expected
     * state.
     */
    private HashSet doCloseOpen(boolean useDeferredWrite,
                                boolean doSync,
                                int startingValue,
                                HashSet initialSet)
        throws Throwable {

	EnvironmentConfig envConfig = getEnvConfig(true);
        env = new Environment(envHome, envConfig);
        Database db = createDb(useDeferredWrite);

        /* We'll do inserts in two batches. */
        HashSet expectedBatch1 = new HashSet();
        expectedBatch1.addAll(initialSet);
        HashSet expectedBatch2 = new HashSet();
        HashSet finalExpectedSet = null;

        int batch1Size = 40;
        int batch2Size = 50;

        /*
         * Insert non-random values in two batches. Don't use random inserts in
         * order to be sure we have a set of non-conflicting values for the
         * test.
         */
        insert(db, null, startingValue, startingValue + batch1Size,
               expectedBatch1, false);
        checkExactContentMatch(db, expectedBatch1);
        if (useDeferredWrite) {
            db.sync();
        }

        /* Insert a second batch */
        insert(db, null,
               startingValue + batch1Size,
               startingValue + batch2Size,
               expectedBatch2, false);
        expectedBatch2.addAll(expectedBatch1);
        checkExactContentMatch(db, expectedBatch2);

        /* Close/reopen, database should hold the expectedBatch2 set. */
        if (doSync) {
            db.close();
            db = createDb(useDeferredWrite);
            checkExactContentMatch(db, expectedBatch2);
        }

        /*
         * Recover the environment. batch2 changes should show up even if the
         * db was deferred write, because a sync is done when the database is
         * closed.  batch2 changes should NOT show up only when doSync is
         * false and deferred write is used.
         *
         * If a flush of INs occured followed by an abnormal close and
         * recovery, obsolete LNs will not always be counted correctly.
         */
        closeEnv(false /*normalClose*/,
                 false /*expectAccurateObsoleteLNCount*/,
                 true  /*expectAccurateDbUtilization*/);
        env = new Environment(envHome, envConfig);

        db = createDb(useDeferredWrite);

        finalExpectedSet = (useDeferredWrite && !doSync) ?
            expectedBatch1 : expectedBatch2;

        checkExactContentMatch(db, finalExpectedSet);
        db.close();
        env.sync();

        /*
         */
        closeEnv(true  /*normalClose*/,
                 false /*expectAccurateObsoleteLNCount*/,
                 true  /*expectAccurateDbUtilization*/);

        return finalExpectedSet;
    }

    /**
     * Test that a checkpoint syncs a durable deferred-write DB.
     */
    public void testCheckpoint()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig(false);
        env = new Environment(envHome, envConfig);

        Database db = createDb(true);
        HashSet expected = insertAndCheck(db);

        env.checkpoint(CHECKPOINT_FORCE_CONFIG);
        closeEnv(false /*normalClose*/);
        env = new Environment(envHome, envConfig);

        db = createDb(true);
        checkExactContentMatch(db, expected);
        db.close();

        closeEnv(true /*normalClose*/);
    }

    /**
     * Test that a checkpoint does not sync a temp DB.
     */
    public void testCheckpointTemp()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig(false);
        env = new Environment(envHome, envConfig);

        Database db = createTempDb();
        env.sync();
        EnvironmentStats stats = env.getStats(STATS_CLEAR_CONFIG);

        insertAndCheck(db);

        env.sync();
        stats = env.getStats(STATS_CLEAR_CONFIG);

        /* With a non-temp DB, more than 30 BINs are flushed. */
        assertTrue(String.valueOf(stats.getNFullBINFlush()),
                   stats.getNFullBINFlush() <= 2);
        assertTrue(String.valueOf(stats.getNFullINFlush()),
                   stats.getNFullINFlush() <= 4);
        assertTrue(String.valueOf(stats.getNDeltaINFlush()),
                   stats.getNDeltaINFlush() <= 2);

        db.close();
        closeEnv(true /*normalClose*/);
    }

    /**
     * Check that temp db works in deferred write mode.
     */
    public void testTempIsDeferredWriteMode()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig(false);
        env = new Environment(envHome, envConfig);
        Database db = createTempDb();

        long origEndOfLog = DbInternal.envGetEnvironmentImpl(env)
                                      .getFileManager()
                                      .getNextLsn();

        insertAndCheck(db);

        long endOfLog = DbInternal.envGetEnvironmentImpl(env)
                                  .getFileManager()
                                  .getNextLsn();

        /* Check that no writing occurred after inserts. */
        assertEquals("origEndOfLog=" + DbLsn.getNoFormatString(origEndOfLog) +
                     " endOfLog=" + DbLsn.getNoFormatString(endOfLog),
                     origEndOfLog, endOfLog);

        db.close();
        closeEnv(true /*normalClose*/);
    }

    /**
     * Check that temp db is removed on close and by recovery.
     */
    public void testTempRemoval()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig(false);
        env = new Environment(envHome, envConfig);

        /* Create DB and close() to remove it. */
        Database db = createTempDb(DBNAME);
        insertAndCheck(db);
        assertTrue(env.getDatabaseNames().contains(DBNAME));
        db.close();
        assertTrue(!env.getDatabaseNames().contains(DBNAME));

        /*
         * Create multiple DBs and run recovery to remove them.  Recovery keeps
         * a set of temp DBs, and we want to make sure it removes all of them.
         */
        db = createTempDb(DBNAME);
        Database db2 = createTempDb(DBNAME2);
        insertAndCheck(db);
        insertAndCheck(db2);
        assertTrue(env.getDatabaseNames().contains(DBNAME));
        assertTrue(env.getDatabaseNames().contains(DBNAME2));
        closeEnv(false /*normalClose*/);
        env = new Environment(envHome, envConfig);
        assertTrue(!env.getDatabaseNames().contains(DBNAME));
        assertTrue(!env.getDatabaseNames().contains(DBNAME2));

        /*
         * Test that recovery deletes a temp DB after several checkpoints.
         * This test requires that the MapLN for every open temp DB is logged
         * during each checkpoint interval.
         */
        db = createTempDb(DBNAME);
        insertAndCheck(db);
        assertTrue(env.getDatabaseNames().contains(DBNAME));
        env.sync();
        env.sync();
        env.sync();
        closeEnv(false /*normalClose*/);
        env = new Environment(envHome, envConfig);
        assertTrue(!env.getDatabaseNames().contains(DBNAME));

        closeEnv(true /*normalClose*/);
    }

    private HashSet insertAndCheck(Database db)
        throws DatabaseException {

        HashSet expected = new HashSet();
        insert(db, null, 1, 100, expected, false);
        checkExactContentMatch(db, expected);
        return expected;
    }

    public void testRecoverNoSync()
        throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        doRecover(envConfig,
                  30,     /* numRecords */
                  false,  /* syncBeforeRecovery. */
                  false); /* expectEviction */
    }

    public void testRecoverSync()
        throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        doRecover(envConfig,
                  30,     /* numRecords */
                  true,   /* syncBeforeRecovery. */
                  false); /* expectEviction */
    }

    public void testRecoverNoSyncEvict()
        throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
        doRecover(envConfig,
                  3000,   /* numRecords */
                  false,  /* syncBeforeRecovery. */
                  true);  /* expectEviction */
    }

    public void testRecoverSyncEvict()
        throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
        doRecover(envConfig,
                  3000,   /* numRecords */
                  true,   /* syncBeforeRecovery. */
                  true);  /* expectEviction */
    }

    private void doRecover(EnvironmentConfig envConfig,
                           int numRecords,
                           boolean syncBeforeRecovery,
                           boolean expectEviction)
        throws DatabaseException {
	
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);
        HashSet expected = new HashSet();

        /* Insert */
        EnvironmentStats stats = env.getStats(STATS_CLEAR_CONFIG);
        insert(db, null, 1, numRecords, expected, true);
        checkForEvictionActivity(expectEviction, /* evict activity */
                                 expectEviction); /* cache miss */
        checkExactContentMatch(db, expected);
        checkForEvictionActivity(expectEviction, /* evict activity */
                                 expectEviction); /* cache miss */

        /*
         * optional sync; do not checkpoint because checkpoints include a
         * sync of non-temporary DBs.
         */
        DatabaseConfig saveConfig = db.getConfig();
        if (syncBeforeRecovery) {
            db.sync();
        }

        /* Close without sync or checkpoint to force recovery.  */
        closeEnv(false /*normalClose*/);

        /* recover and re-open. */
        env = new Environment(envHome, envConfig);
        db = env.openDatabase(null, DBNAME, saveConfig);

        /* Check the contents. */
        HashSet useExpected = null;
        if (syncBeforeRecovery) {
            useExpected = expected;
        } else {
            useExpected = new HashSet();
        }

        checkExactContentMatch(db, useExpected);
        db.close();

        /*
         * When eviction precedes the abnormal close and recovery, obsolete LNs
         * and INs will not always be counted correctly.
         */
        closeEnv(true  /*normalClose*/,
                 false /*expectAccurateObsoleteLNCount*/,
                 false /*expectAccurateDbUtilization*/);
    }

    /**
     * Performs a basic check of deferred-write w/duplicates for verifying the
     * fix to duplicate logging on 3.2.x. [#15365]
     */
    public void testDups()
        throws DatabaseException {
	
        EnvironmentConfig envConfig = getEnvConfig(false);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        dbConfig.setSortedDuplicates(true);
        Database db = env.openDatabase(null, DBNAME, dbConfig);

        /* Insert {9,0} and {9,1}. */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(9, key);
        IntegerBinding.intToEntry(0, data);
        assertSame(OperationStatus.SUCCESS,
                   db.putNoDupData(null, key, data));
        IntegerBinding.intToEntry(1, data);
        assertSame(OperationStatus.SUCCESS,
                   db.putNoDupData(null, key, data));

        /* Check that both exist. */
        Cursor c = db.openCursor(null, null);
        try {
            assertSame(OperationStatus.SUCCESS,
                       c.getNext(key, data, LockMode.DEFAULT));
            assertEquals(9, IntegerBinding.entryToInt(key));
            assertEquals(0, IntegerBinding.entryToInt(data));

            assertSame(OperationStatus.SUCCESS,
                       c.getNext(key, data, LockMode.DEFAULT));
            assertEquals(9, IntegerBinding.entryToInt(key));
            assertEquals(1, IntegerBinding.entryToInt(data));

            assertSame(OperationStatus.NOTFOUND,
                       c.getNext(key, data, LockMode.DEFAULT));
        } finally {
            c.close();
        }

        /* Close without a checkpoint to redo the LNs during recovery. */
        db.sync();
        db.close();
        DbInternal.envGetEnvironmentImpl(env).close(false);
        env = null;

        /* Recover and check again. */
        env = new Environment(envHome, envConfig);
        db = env.openDatabase(null, DBNAME, dbConfig);
        c = db.openCursor(null, null);
        try {
            assertSame(OperationStatus.SUCCESS,
                       c.getNext(key, data, LockMode.DEFAULT));

            /*
             * Before fixing the problem with deferred-write duplicate logging,
             * the key read below was 0 instead of 9.  The bug was that the
             * data (0) was being logged as the main tree key.
             */
            assertEquals(9, IntegerBinding.entryToInt(key));
            assertEquals(0, IntegerBinding.entryToInt(data));

            assertSame(OperationStatus.SUCCESS,
                       c.getNext(key, data, LockMode.DEFAULT));
            assertEquals(9, IntegerBinding.entryToInt(key));
            assertEquals(1, IntegerBinding.entryToInt(data));

            assertSame(OperationStatus.NOTFOUND,
                       c.getNext(key, data, LockMode.DEFAULT));
        } finally {
            c.close();
        }

        db.close();
        env.close();
        env = null;
    }

    /**
     * Tests a fix for a bug where reusing a slot caused a non-deleted record
     * to be compressed. [#15684]
     */
    public void testCompressAfterSlotReuse()
        throws DatabaseException {
	
        EnvironmentConfig envConfig = getEnvConfig(false);
        /* Disable daemons to prevent async compression. */
        envConfig.setConfigParam("je.env.runCleaner", "false");
        envConfig.setConfigParam("je.env.runCheckpointer", "false");
        envConfig.setConfigParam("je.env.runINCompressor", "false");
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        Database db = env.openDatabase(null, DBNAME, dbConfig);

        /* Reuse slot: Insert key 0, delete 0, insert 0 */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(0, key);
        IntegerBinding.intToEntry(0, data);
        assertSame(OperationStatus.SUCCESS,
                   db.putNoOverwrite(null, key, data));
        assertSame(OperationStatus.SUCCESS,
                   db.delete(null, key));
        assertSame(OperationStatus.SUCCESS,
                   db.putNoOverwrite(null, key, data));

        /*
         * Because of the delete() above, a compressor entry is queued for key
         * 0, although it was re-inserted.  And there is no LSN for the slot
         * because it has never been logged. When we compress now, we run into
         * the BIN.compress bug where it assumes an entry is deleted if its LSN
         * is null.
         */
        env.compress();

        /*
         * Before the bug fix, the following assert would fail because the
         * entry was compressed and NOTFOUND.
         */
        assertSame(OperationStatus.SUCCESS,
                   db.get(null, key, data, null));

        db.close();
        env.close();
        env = null;
    }

    public void testPreloadNoSync()
        throws DatabaseException {

        doPreload(false); /* syncBeforeRecovery */
    }

    public void testPreloadSync()
        throws DatabaseException {

        doPreload(true); /* syncBeforeRecovery */
    }

    private void doPreload(boolean syncBeforeRecovery)
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig(false);
        envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);
        HashSet expected = new HashSet();

        int numRecords = 3000;

        /* Insert */
        EnvironmentStats stats = env.getStats(STATS_CLEAR_CONFIG);
        insert(db, null, 1, numRecords, expected, true);
        checkForEvictionActivity(true, /* evict activity */
                                 true); /* cache miss */

        /*
         * Change the cache size to the default value so a preload will
         * have enough cache to pull items in.
         */
        envConfig.setCacheSize(0);
        env.setMutableConfig(envConfig);
        if (DEBUG) {
            System.out.println("after mutable " +
                               env.getConfig().getCacheSize());
        }

        PreloadConfig pConfig = new PreloadConfig();
        pConfig.setLoadLNs(true);
        PreloadStats pStats = db.preload(pConfig);

        if (DEBUG) {
            System.out.println("first preload " + pStats);
        }
        if (dups) {
            assertTrue(String.valueOf(pStats.getNDBINsLoaded()),
                       pStats.getNDBINsLoaded() > 50);
            assertTrue(String.valueOf(pStats.getNDINsLoaded()),
                       pStats.getNDINsLoaded() > 50);
        } else {
            assertTrue(String.valueOf(pStats.getNBINsLoaded()),
                       pStats.getNBINsLoaded() > 50);
            assertTrue(String.valueOf(pStats.getNINsLoaded()),
                       pStats.getNINsLoaded() > 50);
        }
        assertTrue(String.valueOf(pStats.getNLNsLoaded()),
                   pStats.getNLNsLoaded() > 50);

        checkExactContentMatch(db, expected);

        DatabaseConfig saveConfig = db.getConfig();
        if (syncBeforeRecovery) {
            db.sync();
        }

        /* Close db and env without sync or checkpoint */
        closeEnv(false /*normalClose*/);

        /* recover and re-open. */
        env = new Environment(envHome, envConfig);
        db = env.openDatabase(null, DBNAME, saveConfig);
        pStats = db.preload(pConfig);
        if (DEBUG) {
            System.out.println("second preload " + pStats);
        }

        /* Check the contents. */
        HashSet useExpected = null;
        if (syncBeforeRecovery) {
            useExpected = expected;
            if (dups) {
                assertTrue(String.valueOf(pStats.getNDBINsLoaded()),
                           pStats.getNDBINsLoaded() > 50);
                assertTrue(String.valueOf(pStats.getNDINsLoaded()),
                           pStats.getNDINsLoaded() > 50);
            } else {
                assertTrue(String.valueOf(pStats.getNBINsLoaded()),
                           pStats.getNBINsLoaded() > 50);
                assertTrue(String.valueOf(pStats.getNINsLoaded()),
                           pStats.getNINsLoaded() > 50);
            }
            assertTrue(String.valueOf(pStats.getNLNsLoaded()),
                       pStats.getNLNsLoaded() > 50);
        } else {
            useExpected = new HashSet();
            assertEquals(0, pStats.getNBINsLoaded());
            assertEquals(0, pStats.getNINsLoaded());
            assertEquals(0, pStats.getNLNsLoaded());
        }

        checkExactContentMatch(db, useExpected);

        db.close();
    }

    private void checkForEvictionActivity(boolean expectEviction,
                                          boolean expectCacheMiss)
        throws DatabaseException {

        EnvironmentStats stats = env.getStats(STATS_CLEAR_CONFIG);
        if (DEBUG) {
            System.out.println("EvictPasses=" + stats.getNEvictPasses());
            System.out.println("Selected=" + stats.getNNodesSelected());
            System.out.println("Stripped=" + stats.getNBINsStripped());
            System.out.println("Evicted=" +
                               stats.getNNodesExplicitlyEvicted());
            System.out.println("CacheMiss=" +
                               stats.getNCacheMiss());
        }

        if (expectEviction) {

            assertTrue(String.valueOf(stats.getNNodesSelected()),
                       stats.getNNodesSelected() > 50);
            assertTrue(String.valueOf(stats.getNBINsStripped()),
                       stats.getNBINsStripped() > 50);
            assertTrue(String.valueOf(stats.getNNodesExplicitlyEvicted()),
                       stats.getNNodesExplicitlyEvicted() > 50);
        }

        if (expectCacheMiss) {
            assertTrue(String.valueOf(stats.getNCacheMiss()),
                       stats.getNCacheMiss() > 50);
        }
    }

    public void testBadConfigurations()
        throws Throwable {

        env = new Environment(envHome, getEnvConfig(true));

        DatabaseConfig dbConfigDeferred = new DatabaseConfig();
        dbConfigDeferred.setAllowCreate(true);
        dbConfigDeferred.setDeferredWrite(true);
        dbConfigDeferred.setSortedDuplicates(dups);

        DatabaseConfig dbConfigNoDeferred = new DatabaseConfig();
        dbConfigNoDeferred.setAllowCreate(true);
        dbConfigNoDeferred.setSortedDuplicates(dups);

        /* A txnal deferred database is not possible */
        try {
            dbConfigDeferred.setTransactional(true);
            @SuppressWarnings("unused")
            Database db = env.openDatabase(null, "foo", dbConfigDeferred);
            fail("No support yet for txnal, deferred-write databases");
        } catch (IllegalArgumentException expected) {
        }

        dbConfigDeferred.setTransactional(false);

        /*
         * Open a db first with deferred write, then secondly without deferred
         * write, should fail.
         */
        Database db1 = env.openDatabase(null, "foo", dbConfigDeferred);
        try {
            @SuppressWarnings("unused")
            Database db2 = env.openDatabase(null, "foo", dbConfigNoDeferred);
            fail("Database already opened with deferred write");
        } catch (IllegalArgumentException expected) {
        }
        db1.close();

        /*
         * Open a db first without deferred write, then secondly with deferred
         * write, should fail.
         */
        db1 = env.openDatabase(null, "foo", dbConfigNoDeferred);
        try {
            @SuppressWarnings("unused")
            Database db2 = env.openDatabase(null, "foo", dbConfigDeferred);
            fail("Database already opened with out deferred write");
        } catch (IllegalArgumentException expected) {
        }
        db1.close();

        /* Sync is only allowed for deferred-write databases. */
        Database db = env.openDatabase(null, "foo", dbConfigNoDeferred);
        try {
            db.sync();
            fail("Sync not permitted");
        } catch (UnsupportedOperationException expected) {
            if (DEBUG) {
                System.out.println("expected=" + expected);
            }
            db.close();
        }
    }

    public void testCleaning5000()
        throws Throwable {

        doCleaning("90", "4200"); /* log file size. */
    }

    /**
     * This test is disabled because it is very unreliable.  It works some of
     * the time.  Other times, it creates huge numbers (10s of thousands) of
     * log files.  The file size is too small.  A small file size causes many
     * FileSummaryLNs to be logged, which creates more files.  With a small
     * cache, the FileSummaryLNs are constantly evicted, creating more files.
     */
    public void XXtestCleaning2000()
        throws Throwable {

        doCleaning("90", "3000"); /* log file size. */
    }

    private void doCleaning(String minUtilization, String logFileSize)
        throws DatabaseException {

        /*
         * Run with a small cache so there's plenty of logging.  But use a
         * slightly bigger cache than the minimum so that eviction during
         * cleaning has enough working room on 64-bit systems [#15176].
         */
        long cacheSize = MemoryBudget.MIN_MAX_MEMORY_SIZE +
                        (MemoryBudget.MIN_MAX_MEMORY_SIZE / 2);
	EnvironmentConfig envConfig = getEnvConfig(true);
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setCacheSize(cacheSize);
        envConfig.setConfigParam("je.cleaner.minUtilization",
                                 minUtilization);
        envConfig.setConfigParam("je.log.fileMax", logFileSize);
        envConfig.setConfigParam("je.cleaner.expunge", "false");
        /* Disable cleaner thread so batch cleaning is predictable. [#15176] */
        envConfig.setConfigParam("je.env.runCleaner", "false");
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);

        /* We'll do inserts in two batches. */
        HashSet expectedBatch1 = new HashSet();
        HashSet expectedBatch2 = new HashSet();

        int batch1Size = 100;
        int batch2Size = 110;

        /*
         * Insert non-random values in two batches. Don't use random
         * inserts in order to be sure we have a set of non-conflicting
         * values for the test.
         */
        int startingValue = 1;
        insert(db,
               null,
               startingValue,
               startingValue + batch1Size,
               expectedBatch1,
               false); /* random */
        checkExactContentMatch(db, expectedBatch1);
        db.sync();

        /* Insert a second batch with no sync */
        insertAndUpdate(db,
                        null,
                        startingValue + batch1Size,
                        startingValue + batch2Size,
                        expectedBatch2,
                        false); /* random */
        expectedBatch2.addAll(expectedBatch1);
        checkExactContentMatch(db, expectedBatch2);
        env.checkpoint(CHECKPOINT_FORCE_CONFIG);
        Tracer.trace(Level.SEVERE,
                     DbInternal.envGetEnvironmentImpl(env),
                     "before clean");
        batchClean();

        Tracer.trace(Level.SEVERE,
                     DbInternal.envGetEnvironmentImpl(env),
                     "after clean");

        checkExactContentMatch(db, expectedBatch2);

        /*
         * Recover the environment a few times. Whether the batch2 changes
         * show up depend on whether the db was deferred write, and whether
         * a sync was done.
         */
        for (int i = 0; i < 4; i++) {
            /* Do an abnormal close, we do not want to sync the database. */
            db = null;
            closeEnv(false /*normalClose*/);
            env = new Environment(envHome, envConfig);

            db = createDb(true);
            checkContents(db,
                          expectedBatch2,
                          false); /* exact match. */

            batchClean();
            checkContents(db,
                          expectedBatch2,
                          false); /* exact match. */
        }

        db.close();
        closeEnv(true /*normalClose*/);
    }

    /**
     * Insert a set of records, record the values in the expected set.
     * @param useRandom If True, use random values.
     */
    private void insert(Database db,
                        Transaction txn,
                        int start,
                        int end,
                        Set expected,
                        boolean useRandom)
        throws DatabaseException{

        DatabaseEntry entry = new DatabaseEntry();
        Random rand = new Random();
        for (int i = start; i < end; i++) {
            int value = useRandom ? rand.nextInt() : i;

            IntegerBinding.intToEntry(value, entry);
            if (dups) {
                assertEquals(OperationStatus.SUCCESS,
                             db.putNoDupData(txn, MAIN_KEY_FOR_DUPS, entry));
            } else {
                assertEquals(OperationStatus.SUCCESS,
                             db.putNoOverwrite(txn, entry, entry));
            }
            expected.add(new Integer(value));
        }
    }

    /**
     * Insert and modify a set of records, record the values in the
     * expected set.
     * @param useRandom If True, use random values.
     */
    private void insertAndUpdate(Database db,
                                 Transaction txn,
                                 int start,
                                 int end,
                                 Set expected,
                                 boolean useRandom)
        throws DatabaseException{

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        Random rand = new Random();
        for (int i = start; i < end; i++) {
            int value = useRandom ? rand.nextInt() : i;

            IntegerBinding.intToEntry(value, key);
            if (dups) {
                OperationStatus status =
                    db.putNoDupData(txn, MAIN_KEY_FOR_DUPS, key);
                if (status == OperationStatus.SUCCESS) {
                    /* Update it */
                    db.put(txn, MAIN_KEY_FOR_DUPS, key);
                    expected.add(new Integer(value));
                }
            } else {
                IntegerBinding.intToEntry(value - 1, data);
                OperationStatus status = db.putNoOverwrite(txn, key, data);
                if (status == OperationStatus.SUCCESS) {
                    /* Update it */
                    IntegerBinding.intToEntry(value, data);
                    db.put(txn, key, data);
                    expected.add(new Integer(value));
                }
            }
        }
    }

    /**
     * The database should hold exactly the values in the expected set.
     */
    private void checkExactContentMatch(Database db, HashSet expected)
        throws DatabaseException{

        checkContents(db, expected, true);
    }

    /**
     * The database should hold only values that are in the expected set.
     * Note that this assumes that the key and data are the same value.
     * @param exactMatch if true, the database ought to hold all the values
     * in the expected set.
     */
    private void checkContents(Database db,
                               HashSet expected,
                               boolean exactMatch)
        throws DatabaseException{

        Cursor c = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Set useExpected = (Set) expected.clone();

        if (DEBUG) {
            System.err.println("Start checking");
        }

        while (c.getNext(key, data, LockMode.DEFAULT) ==
               OperationStatus.SUCCESS) {
            int value = IntegerBinding.entryToInt(dups ? data : key);

            if (DEBUG) {
                System.err.println("checkDatabase: found " + value);
            }

            assertTrue(value + " not in useExpected set. Expected size="
                       + useExpected.size(),
                       useExpected.remove(new Integer(value)));
            assertEquals(value, IntegerBinding.entryToInt(data));
        }

        if (exactMatch) {
            assertEquals(0, useExpected.size());
        } else {
            if (DEBUG) {
                System.out.println(useExpected.size() +
                                   " is leftover in expected set");
            }
        }
        c.close();
    }

    private void batchClean()
        throws DatabaseException {

        int cleaned = 0;
        int cleanedThisRound = 0;
        do {
            cleanedThisRound = env.cleanLog();
            cleaned += cleanedThisRound;
        } while (cleanedThisRound > 0);

        if (DEBUG) {
            System.out.println("numCleaned = " + cleaned);
        }

        assertTrue("cleaned must be > 0, was only " + cleaned +
                   " but may vary on machine to machine", cleaned > 0);

        if (cleaned > 0) {
            CheckpointConfig force = new CheckpointConfig();
            force.setForce(true);
            env.checkpoint(force);
        }
    }
}
