/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DeferredWriteTest.java,v 1.5.2.3 2007/05/01 19:32:05 mark Exp $
 */

package com.sleepycat.je.test;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import junit.framework.TestCase;

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
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.Tracer;

public class DeferredWriteTest extends TestCase {
    private static boolean DEBUG = false;
    private static String DBNAME = "foo";

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

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.err.println("TearDown: " + e);
            }
        }
        env = null;
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    private EnvironmentConfig getEnvConfig(boolean transactional) {
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(transactional);
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "4");
        if (DEBUG) {
            envConfig.setConfigParam("java.util.logging.ConsoleHandler.on",
                                     "true");
            envConfig.setConfigParam("java.util.logging.level.cleaner",
                                     "SEVERE");
        }

        return envConfig;
    }

    private Database createDb(boolean deferredWrite) 
        throws DatabaseException {
        
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(deferredWrite);
        
        return env.openDatabase(null, DBNAME, dbConfig);   
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

	Database db = null;
	try {
	    EnvironmentConfig envConfig = getEnvConfig(true);
            /* Disable compressor for test predictability. */
            envConfig.setConfigParam("je.env.runINCompressor", "false");
	    env = new Environment(envHome, envConfig);
	    db = createDb(true);
            /* Insert some data to cause eviction later. */
            insert(db, 
                   null,          // txn
                   1,             // start
                   30000,         // end
                   new HashSet(), // expected
                   false);        // useRandom
            db.close();
            env.removeDatabase(null, DBNAME);

            envConfig = env.getConfig();
            /* Switch to a small cache to force eviction. */
            envConfig.setCacheSize(96 * 1024);
            env.setMutableConfig(envConfig);
            for (int i = 0; i < 10; i += 1) {
                env.evictMemory();
            }
        } finally {
            if (env != null) {
                try {
                    env.close();
                } catch (Throwable e) {
                    System.out.println("Ignored: " + e);
                }
                env = null;
            }
        }
    }

    public void testEmptyDatabaseSR14744()
	throws Throwable {

	Database db = null;
	try {
	    EnvironmentConfig envConfig = getEnvConfig(true);
	    env = new Environment(envHome, envConfig);
	    db = createDb(true);
	    db.sync();
        } finally {
            if (db != null) {
                db.close();
            }

            env.sync();
            env.close();
            env = null;
        }
    }

    /**
     * Check that deferred write db re-opens at expected state.
     */
    public void testCloseOpen()
        throws Throwable {

        HashSet expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        1,      /* starting value */
                        new HashSet()); /* initial ExpectedSet */
        expectedSet =
            doCloseOpen(false,  /* useDeferredWrites */
                        100,    /* starting value */
                        expectedSet);
        expectedSet =
            doCloseOpen(true,   /* useDeferredWrites */
                        200,    /* starting value */
                        expectedSet);
        
    }

    /**
     * Check that deferred write and durable databases re-open at expected
     * state.  
     */
    private HashSet doCloseOpen(boolean useDeferredWrite, 
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

        try {

            /* 
             * Insert non-random values in two batches. Don't use random
             * inserts in order to be sure we have a set of non-conflicting
             * values for the test. 
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
            db.close();
            db = createDb(useDeferredWrite);
            checkExactContentMatch(db, expectedBatch2);

            /* 
             * Recover the environment. Whether the batch2 changes show up 
             * depend on whether the db was deferred write, and whether
             * a sync was done. 
             */

            db.close();
            db = null;

            env.sync(); 
            env.close();
            env = null;
            env = new Environment(envHome, envConfig);

            db = createDb(useDeferredWrite);

            if (useDeferredWrite) {
                finalExpectedSet = expectedBatch1;
            } else {
                finalExpectedSet = expectedBatch2;
            }

            checkExactContentMatch(db, finalExpectedSet);
            db.close();
            db = null;
        } finally {
            if (db != null) {
                db.close();
            }

            env.sync();
            env.close();
            env = null;
        }
        return finalExpectedSet;
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
                  3000,    /* numRecords */
                  false,  /* syncBeforeRecovery. */
                  true);  /* expectEviction */
    }

    public void testRecoverSyncEvict()
        throws Throwable {

        EnvironmentConfig envConfig = getEnvConfig(true);
        envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
        doRecover(envConfig, 
                  3000,    /* numRecords */
                  true,  /* syncBeforeRecovery. */
                  true);  /* expectEviction */
    }

    public void doRecover(EnvironmentConfig envConfig,
                          int numRecords,
                          boolean syncBeforeRecovery,
                          boolean expectEviction) 
        throws DatabaseException {
	
        env = new Environment(envHome, envConfig);
        Database db = createDb(true);
        HashSet expected = new HashSet();

        try {
            /* Insert */
            EnvironmentStats stats = env.getStats(STATS_CLEAR_CONFIG);
            insert(db, null, 1, numRecords, expected, true);
            checkForEvictionActivity(expectEviction, /* evict activity */
                                     expectEviction); /* cache miss */
            checkExactContentMatch(db, expected);
            checkForEvictionActivity(expectEviction, /* evict activity */
                                     expectEviction); /* cache miss */

            /* checkpoint, optional sync. */
            env.checkpoint(CHECKPOINT_FORCE_CONFIG);
            DatabaseConfig saveConfig = db.getConfig();
            if (syncBeforeRecovery) {
                db.sync();
            }

            /* 
             * Close db, checkpoint, close underlying envImp to force recovery.
             */
            db.close(); 
            env.checkpoint(CHECKPOINT_FORCE_CONFIG);
            DbInternal.envGetEnvironmentImpl(env).close(false);
            env = null;

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

        } finally {
            db.close();
        }
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

        /* Insert {0,0} and {0,1}. */
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

        try {
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
            assertTrue(pStats.getNBINsLoaded() > 50);
            assertTrue(pStats.getNINsLoaded() > 50);
            assertTrue(pStats.getNLNsLoaded() > 50);

            checkExactContentMatch(db, expected);

            DatabaseConfig saveConfig = db.getConfig();
            if (syncBeforeRecovery) {
                db.sync();
            }

            /* Close db, close env */
            db.close(); 
            DbInternal.envGetEnvironmentImpl(env).close(false);
            env = null;

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
                assertTrue(pStats.getNBINsLoaded() > 50);
                assertTrue(pStats.getNINsLoaded() > 50);
                assertTrue(pStats.getNLNsLoaded() > 50);
            } else {
                useExpected = new HashSet();
                assertEquals(0, pStats.getNBINsLoaded());
                assertEquals(0, pStats.getNINsLoaded());
                assertEquals(0, pStats.getNLNsLoaded());
            }
                
            checkExactContentMatch(db, useExpected);

        } finally {
            db.close();
        }
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

            assertTrue(stats.getNNodesSelected() > 50);
            assertTrue(stats.getNBINsStripped() > 50);
            assertTrue(stats.getNNodesExplicitlyEvicted() > 50);
        }

        if (expectCacheMiss) {
            assertTrue(stats.getNCacheMiss()>50);
        }
    }

    public void testBadConfigurations() 
        throws Throwable {

        env = new Environment(envHome, getEnvConfig(true));

        DatabaseConfig dbConfigDeferred = new DatabaseConfig();
        dbConfigDeferred.setAllowCreate(true);
        dbConfigDeferred.setDeferredWrite(true);

        DatabaseConfig dbConfigNoDeferred = new DatabaseConfig();
        dbConfigNoDeferred.setAllowCreate(true);

        /* A txnal deferred database is not possible */
        try {
            dbConfigDeferred.setTransactional(true);
            Database db = env.openDatabase(null, "foo", dbConfigDeferred);
            fail("No support yet for txnal, deferred-write databases");
        } catch (DatabaseException expected) {
            if (DEBUG) {
                System.out.println("expected=" + expected);
            }
        }
        dbConfigDeferred.setTransactional(false);

        /* 
         * Open a db first with deferred write, then secondly without deferred
         * write, should fail.
         */
        Database db1 = env.openDatabase(null, "foo", dbConfigDeferred);
        try {
            Database db2 = env.openDatabase(null, "foo", dbConfigNoDeferred);
            fail("Database already opened with deferred write");
        } catch (DatabaseException expected) {
            if (DEBUG) {
                System.out.println("expected=" + expected);
            }
        }
        db1.close();

        /* 
         * Open a db first without deferred write, then secondly with deferred
         * write, should fail.
         */
        db1 = env.openDatabase(null, "foo", dbConfigNoDeferred);
        try {
            Database db2 = env.openDatabase(null, "foo", dbConfigDeferred);
            fail("Database already opened with out deferred write");
        } catch (DatabaseException expected) {
            if (DEBUG) {
                System.out.println("expected=" + expected);
            }
        }
        db1.close();

        /* Sync is only allowed for deferred-write databases. */
        Database db = null;
        try {
            db = env.openDatabase(null, "foo", dbConfigNoDeferred);
            db.sync();
            fail("Sync not permitted");
        } catch (DatabaseException expected) {
            if (DEBUG) {
                System.out.println("expected=" + expected);
            }
        } finally {
            db.close();
        }
    }

    public void testCleaning5000() 
        throws Throwable {

        doCleaning("90", "5000"); /* log file size. */
    }

    public void testCleaning2000() 
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
        HashSet finalExpectedSet = null;

        int batch1Size = 100;
        int batch2Size = 100;

        try {

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
                db.close();
                db = null;

                env.close();
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
            db = null;
        } finally {
            if (db != null) {
                db.close();
            }

            env.close();
            env = null;
        }
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
            assertEquals(OperationStatus.SUCCESS,
                         db.put(txn, entry, entry));
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

        try {
            while (c.getNext(key, data, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
                int value = IntegerBinding.entryToInt(key);

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
        } finally {
            c.close();
        }
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
