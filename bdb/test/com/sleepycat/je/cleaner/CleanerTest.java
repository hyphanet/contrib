/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CleanerTest.java,v 1.87.2.3 2007/05/31 21:55:33 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.cleaner.Cleaner;
import com.sleepycat.je.cleaner.UtilizationProfile;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;

public class CleanerTest extends TestCase {

    private static final int N_KEYS = 300;
    private static final int N_KEY_BYTES = 10;

    /* 
     * Make the log file size small enough to allow cleaning, but large enough
     * not to generate a lot of fsyncing at the log file boundaries.
     */
    private static final int FILE_SIZE = 10000;  
    protected File envHome = null;
    protected Database db = null;
    private Environment exampleEnv;
    private Database exampleDb;
    private CheckpointConfig forceConfig;

    public CleanerTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        forceConfig = new CheckpointConfig();
        forceConfig.setForce(true);
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    private void initEnv(boolean createDb, boolean allowDups)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 Integer.toString(FILE_SIZE));
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.CLEANER_REMOVE.getName(),
                                 "false");
        envConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam(EnvironmentParams.BIN_DELTA_PERCENT.getName(),
                                 "75");

        /* Don't use detail tracking in this test. */
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");

        exampleEnv = new Environment(envHome, envConfig);

        String databaseName = "cleanerDb";
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(createDb);
        dbConfig.setSortedDuplicates(allowDups);
        exampleDb = exampleEnv.openDatabase(null, databaseName, dbConfig);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        if (exampleEnv != null) {
            try {
                exampleEnv.close();
            } catch (DatabaseException e) {
                System.out.println("tearDown: " + e);
            }
        }
        exampleDb = null;
        exampleEnv = null;
                
        //*
        TestUtils.removeLogFiles("TearDown", envHome, true);
        TestUtils.removeFiles("TearDown", envHome, FileManager.DEL_SUFFIX);
        //*/
    }

    private void closeEnv()
        throws DatabaseException {

        if (exampleDb != null) {
            exampleDb.close();
            exampleDb = null;
        }

        if (exampleEnv != null) {
            exampleEnv.close();
            exampleEnv = null;
        }
    }

    public void testCleanerNoDupes()
        throws Throwable {

        initEnv(true, false);
        try {
            doCleanerTest(N_KEYS, 1);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testCleanerWithDupes()
        throws Throwable {

        initEnv(true, true);
        try {
            doCleanerTest(2, 500);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void doCleanerTest(int nKeys, int nDupsPerKey)
        throws DatabaseException {

        EnvironmentImpl environment =
            DbInternal.envGetEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();
        Map expectedMap = new HashMap();
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        Long lastNum = fileManager.getLastFileNum();

        /* Read the data back. */
        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(null, null);

        while (cursor.getNext(foundKey, foundData, LockMode.DEFAULT) ==
               OperationStatus.SUCCESS) {
        }

	exampleEnv.checkpoint(forceConfig);

        for (int i = 0; i < (int) lastNum.longValue(); i++) {

            /*
             * Force clean one file.  Utilization-based cleaning won't
             * work here, since utilization is over 90%.
             */
            DbInternal.envGetEnvironmentImpl(exampleEnv).
		getCleaner().
		doClean(false, // cleanMultipleFiles
			true); // forceCleaning
        }

        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);
        assertTrue(stats.getNINsCleaned() > 0);
                
	cursor.close();
        closeEnv();

        initEnv(false, (nDupsPerKey > 1));

        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * Ensure that INs are cleaned.
     */
    public void testCleanInternalNodes()
        throws DatabaseException {

        initEnv(true, true);
        int nKeys = 200;
        
        EnvironmentImpl environment =
            DbInternal.envGetEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();
        /* Insert a lot of keys. ExpectedMap holds the expected data */
        Map expectedMap = new HashMap();
        doLargePut(expectedMap, nKeys, 1, true);

        /* Modify every other piece of data. */
        modifyData(expectedMap, 10, true);
        checkData(expectedMap);

        /* Checkpoint */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Modify every other piece of data. */
        modifyData(expectedMap, 10, true);
        checkData(expectedMap);

        /* Checkpoint -- this should obsolete INs. */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Clean */
        Long lastNum = fileManager.getLastFileNum();
        exampleEnv.cleanLog();

        /* Validate after cleaning. */
        checkData(expectedMap);
        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);

        /* Make sure we really cleaned something.*/
        assertTrue(stats.getNINsCleaned() > 0);
        assertTrue(stats.getNLNsCleaned() > 0);
                
        closeEnv();
        initEnv(false, true);
        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * See if we can clean in the middle of the file set.
     */
    public void testCleanFileHole()
        throws Throwable {

        initEnv(true, true);

        int nKeys = 20; // test ends up inserting 2*nKeys
        int nDupsPerKey = 30;

        EnvironmentImpl environment =
            DbInternal.envGetEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();

        /* Insert some non dup data, modify, insert dup data. */
        Map expectedMap = new HashMap();
        doLargePut(expectedMap, nKeys, 1, true);
        modifyData(expectedMap, 10, true);
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        checkData(expectedMap);

        /* 
         * Delete all the data, but abort. (Try to fill up the log
         * with entries we don't need.
         */
        deleteData(expectedMap, false, false);
        checkData(expectedMap);

        /* Do some more insertions, but abort them. */
        doLargePut(expectedMap, nKeys, nDupsPerKey, false);
        checkData(expectedMap);

        /* Do some more insertions and commit them. */
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        checkData(expectedMap);

        /* Checkpoint */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Clean */
        Long lastNum = fileManager.getLastFileNum();
        exampleEnv.cleanLog();
            
        /* Validate after cleaning. */
        checkData(expectedMap);
        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);

        /* Make sure we really cleaned something.*/
        assertTrue(stats.getNINsCleaned() > 0);
        assertTrue(stats.getNLNsCleaned() > 0);

        closeEnv();
        initEnv(false, true);
        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * Test for SR13191.  This SR shows a problem where a MapLN is initialized
     * with a DatabaseImpl that has a null EnvironmentImpl.  When the Database
     * gets used, a NullPointerException occurs in the Cursor code which
     * expects there to be an EnvironmentImpl present.  The MapLN gets init'd
     * by the Cleaner reading through a log file and encountering a MapLN which
     * is not presently in the DbTree.  As an efficiency, the Cleaner calls
     * updateEntry on the BIN to try to insert the MapLN into the BIN so that
     * it won't have to fetch it when it migrates the BIN.  But this is bad
     * since the MapLN has not been init'd properly.  The fix was to ensure
     * that the MapLN is init'd correctly by calling postFetchInit on it just
     * prior to inserting it into the BIN.
     *
     * This test first creates an environment and two databases.  The first
     * database it just adds to the tree with no data.  This will be the MapLN
     * that eventually gets instantiated by the cleaner.  The second database
     * is used just to create a bunch of data that will get deleted so as to
     * create a low utilization for one of the log files.  Once the data for
     * db2 is created, the log is flipped (so file 0 is the one with the MapLN
     * for db1 in it), and the environment is closed and reopened.  We insert
     * more data into db2 until we have enough .jdb files that file 0 is
     * attractive to the cleaner.  Call the cleaner to have it instantiate the
     * MapLN and then use the MapLN in a Database.get() call.
     */
    public void testSR13191()
	throws Throwable {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        Environment env = new Environment(envHome, envConfig);
	EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	FileManager fileManager =
	    DbInternal.envGetEnvironmentImpl(env).getFileManager();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db1 =
	    env.openDatabase(null, "db1", dbConfig);

        Database db2 =
	    env.openDatabase(null, "db2", dbConfig);

	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	IntegerBinding.intToEntry(1, key);
	data.setData(new byte[100000]);
        for (int i = 0; i < 50; i++) {
            assertEquals(OperationStatus.SUCCESS, db2.put(null, key, data));
	}
	db1.close();
	db2.close();
        assertEquals("Should have 0 as current file", 0L,
                     fileManager.getCurrentFileNum());
	envImpl.forceLogFileFlip();
	env.close();

        env = new Environment(envHome, envConfig);
	fileManager = DbInternal.envGetEnvironmentImpl(env).getFileManager();
        assertEquals("Should have 1 as current file", 1L,
                     fileManager.getCurrentFileNum());

	db2 = env.openDatabase(null, "db2", dbConfig);

        for (int i = 0; i < 250; i++) {
            assertEquals(OperationStatus.SUCCESS, db2.put(null, key, data));
	}

	db2.close();
	env.cleanLog();
	db1 = env.openDatabase(null, "db1", dbConfig);
	db1.get(null, key, data, null);
	db1.close();
	env.close();
    }

    /**
     * Tests that setting je.env.runCleaner=false stops the cleaner from
     * processing more files even if the target minUtilization is not met
     * [#15158].
     */
    public void testCleanerStop()
	throws Throwable {

        final int fileSize = 1000000;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(fileSize));
        envConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        Environment env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "CleanerStop", dbConfig);

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[fileSize]);
        for (int i = 0; i <= 10; i += 1) {
            db.put(null, key, data);
        }
        env.checkpoint(forceConfig);

        EnvironmentStats stats = env.getStats(null);
        assertEquals(0, stats.getNCleanerRuns());

        envConfig = env.getConfig();
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "true");
        env.setMutableConfig(envConfig);

        int iter = 0;
        while (stats.getNCleanerRuns() == 0) {
            iter += 1;
            if (iter == 20) {
                fail("Cleaner did not run after " + iter + " tries");
            }
            Thread.yield();
            Thread.sleep(1);
            stats = env.getStats(null);
        }

        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        env.setMutableConfig(envConfig);

        int prevNFiles = stats.getNCleanerRuns();
        stats = env.getStats(null);
        int currNFiles = stats.getNCleanerRuns();
        if (currNFiles - prevNFiles > 5) {
            fail("Expected less than 5 files cleaned," +
                 " prevNFiles=" + prevNFiles +
                 " currNFiles=" + currNFiles);
        }

        //System.out.println("Num runs: " + stats.getNCleanerRuns());

	db.close();
	env.close();
    }

    /**
     * Tests that when a file being cleaned is deleted, we ignore the error and
     * don't repeatedly try to clean it.  This is happening when we mistakedly
     * clean a file after it has been queued for deletion.  The workaround is
     * to catch LogFileNotFoundException in the cleaner and ignore the error.
     * We're testing the workaround here by forcing cleaning of deleted files.
     * [#15528]
     */
    public void testUnexpectedFileDeletion()
        throws DatabaseException, IOException {

        initEnv(true, false);
        EnvironmentMutableConfig config = exampleEnv.getMutableConfig();
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "true");
        config.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        exampleEnv.setMutableConfig(config);

        final EnvironmentImpl envImpl =
            DbInternal.envGetEnvironmentImpl(exampleEnv);
        final Cleaner cleaner = envImpl.getCleaner();

        Map expectedMap = new HashMap();
        doLargePut(expectedMap, 1000, 1, true);
        checkData(expectedMap);

        for (int i = 0; i < 100; i += 1) {
            modifyData(expectedMap, 1, true);
            checkData(expectedMap);
            cleaner.injectFileForCleaning(new Long(0));
            exampleEnv.cleanLog();
            exampleEnv.checkpoint(forceConfig);
        }
        checkData(expectedMap);

        closeEnv();
    }

    /**
     * Helper routine. Generates keys with random alpha values while data
     * is numbered numerically.
     */
    private void doLargePut(Map expectedMap,
                            int nKeys,
                            int nDupsPerKey,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);
        for (int i = 0; i < nKeys; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            TestUtils.generateRandomAlphaBytes(key);
            String keyString = new String(key);

            /*
	     * The data map is keyed by key value, and holds a hash
	     * map of all data values.
             */
            Set dataVals = new HashSet();
            if (commit) {
                expectedMap.put(keyString, dataVals);
            }
            for (int j = 0; j < nDupsPerKey; j++) {
                String dataString = Integer.toString(j);
                exampleDb.put(txn,
                              new StringDbt(keyString),
                              new StringDbt(dataString));
                dataVals.add(dataString);
            }
        }
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Increment each data value. 
     */
    private void modifyData(Map expectedMap,
                            int increment,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(txn, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        boolean toggle = true;
        while (status == OperationStatus.SUCCESS) {
            if (toggle) {

                String foundKeyString = foundKey.getString();
                String foundDataString = foundData.getString();
                int newValue = Integer.parseInt(foundDataString) + increment;
                String newDataString = Integer.toString(newValue);

                /* If committing, adjust the expected map. */
                if (commit) {
                
                    Set dataVals = (Set) expectedMap.get(foundKeyString);
                    if (dataVals == null) {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    } else if (dataVals.contains(foundDataString)) {
                        dataVals.remove(foundDataString);
                        dataVals.add(newDataString);
                    } else {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    }
                }
 
                assertEquals(OperationStatus.SUCCESS,
                             cursor.delete());
                assertEquals(OperationStatus.SUCCESS,
                             cursor.put(foundKey,
					new StringDbt(newDataString)));
                toggle = false;
            } else {
                toggle = true;
            }

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

        cursor.close();
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Delete data.
     */
    private void deleteData(Map expectedMap,
                            boolean everyOther,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(txn, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        boolean toggle = true;
        while (status == OperationStatus.SUCCESS) {
            if (toggle) {

                String foundKeyString = foundKey.getString();
                String foundDataString = foundData.getString();

                /* If committing, adjust the expected map */
                if (commit) {
                
                    Set dataVals = (Set) expectedMap.get(foundKeyString);
                    if (dataVals == null) {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    } else if (dataVals.contains(foundDataString)) {
                        dataVals.remove(foundDataString);
                        if (dataVals.size() == 0) {
			    expectedMap.remove(foundKeyString);
                        }
                    } else {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    }
                }
 
                assertEquals(OperationStatus.SUCCESS, cursor.delete());
            } 

            if (everyOther) {
                toggle = toggle? false: true;
            }

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

        cursor.close();
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Check what's in the database against what's in the expected map.
     */
    private void checkData(Map expectedMap) 
        throws DatabaseException {

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();
        Cursor cursor = exampleDb.openCursor(null, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        /* 
         * Make a copy of expectedMap so that we're free to delete out
         * of the set of expected results when we verify.
         * Also make a set of counts for each key value, to test count.
         */

        Map checkMap = new HashMap();
        Map countMap = new HashMap();
        Iterator iter = expectedMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            Set copySet = new HashSet();
            copySet.addAll((Set) entry.getValue());
            checkMap.put(entry.getKey(), copySet);
            countMap.put(entry.getKey(), new Integer(copySet.size()));
        }     

        while (status == OperationStatus.SUCCESS) {
            String foundKeyString = foundKey.getString();
            String foundDataString = foundData.getString();

            /* Check that the current value is in the check values map */
            Set dataVals = (Set) checkMap.get(foundKeyString);
            if (dataVals == null) {
                fail("Couldn't find " +
                     foundKeyString + "/" + foundDataString);
            } else if (dataVals.contains(foundDataString)) {
                dataVals.remove(foundDataString);
                if (dataVals.size() == 0) {
                    checkMap.remove(foundKeyString);
                }
            } else {
                fail("Couldn't find " +
                     foundKeyString + "/" + 
                     foundDataString +
                     " in data vals");
            }

            /* Check that the count is right. */
            int count = cursor.count();
            assertEquals(((Integer)countMap.get(foundKeyString)).intValue(),
                         count);

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

	cursor.close();

	if (checkMap.size() != 0) {
	    dumpExpected(checkMap);
	    fail("checkMapSize = " + checkMap.size());
	    		
	}
        assertEquals(0, checkMap.size());
    }

    private void dumpExpected(Map expectedMap) {
        Iterator iter = expectedMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = (String) entry.getKey();
            Iterator dataIter = ((Set) entry.getValue()).iterator();
            while (dataIter.hasNext()) {
                System.out.println("key=" + key + 
                                   " data=" + (String) dataIter.next());
            }
        }
    }

    /**
     * Tests that cleaner mutable configuration parameters can be changed and
     * that the changes actually take effect.
     */
    public void testMutableConfig()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        exampleEnv = new Environment(envHome, envConfig);
        envConfig = exampleEnv.getConfig();
        EnvironmentImpl envImpl =
            DbInternal.envGetEnvironmentImpl(exampleEnv);
        Cleaner cleaner = envImpl.getCleaner();
        UtilizationProfile profile = envImpl.getUtilizationProfile();
        MemoryBudget budget = envImpl.getMemoryBudget();
        String name;
        String val;

        /* je.cleaner.minUtilization */
        name = EnvironmentParams.CLEANER_MIN_UTILIZATION.getName();
        setParam(name, "33");
        assertEquals(33, profile.minUtilization);

        /* je.cleaner.minFileUtilization */
        name = EnvironmentParams.CLEANER_MIN_FILE_UTILIZATION.getName();
        setParam(name, "7");
        assertEquals(7, profile.minFileUtilization);

        /* je.cleaner.bytesInterval */
        name = EnvironmentParams.CLEANER_BYTES_INTERVAL.getName();
        setParam(name, "1000");
        assertEquals(1000, cleaner.cleanerBytesInterval);

        /* je.cleaner.deadlockRetry */
        name = EnvironmentParams.CLEANER_DEADLOCK_RETRY.getName();
        setParam(name, "7");
        assertEquals(7, cleaner.nDeadlockRetries);

        /* je.cleaner.lockTimeout */
        name = EnvironmentParams.CLEANER_LOCK_TIMEOUT.getName();
        setParam(name, "7000");
        assertEquals(7, cleaner.lockTimeout);

        /* je.cleaner.expunge */
        name = EnvironmentParams.CLEANER_REMOVE.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.expunge);

        /* je.cleaner.minAge */
        name = EnvironmentParams.CLEANER_MIN_AGE.getName();
        setParam(name, "7");
        assertEquals(7, profile.minAge);

        /* je.cleaner.cluster */
        name = EnvironmentParams.CLEANER_CLUSTER.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.clusterResident);
        /* Cannot set both cluster and clusterAll to true. */
        setParam(name, "false");

        /* je.cleaner.clusterAll */
        name = EnvironmentParams.CLEANER_CLUSTER_ALL.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.clusterAll);

        /* je.cleaner.maxBatchFiles */
        name = EnvironmentParams.CLEANER_MAX_BATCH_FILES.getName();
        setParam(name, "7");
        assertEquals(7, cleaner.maxBatchFiles);

        /* je.cleaner.readSize */
        name = EnvironmentParams.CLEANER_READ_SIZE.getName();
        setParam(name, "7777");
        assertEquals(7777, cleaner.readBufferSize);

        /* je.cleaner.detailMaxMemoryPercentage */
        name = EnvironmentParams.CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE.
            getName();
        setParam(name, "7");
        assertEquals((budget.getMaxMemory() * 7) / 100,
                     budget.getTrackerBudget());

        /* je.cleaner.threads */
        name = EnvironmentParams.CLEANER_THREADS.getName();
        setParam(name, "7");
        assertEquals((envImpl.isNoLocking() ? 0 : 7),
		     countCleanerThreads());

        exampleEnv.close();
        exampleEnv = null;
    }

    /**
     * Sets a mutable config param, checking that the given value is not
     * already set and that it actually changes.
     */
    private void setParam(String name, String val)
        throws DatabaseException {

        EnvironmentMutableConfig config = exampleEnv.getMutableConfig();
        String myVal = config.getConfigParam(name);
        assertTrue(!val.equals(myVal));

        config.setConfigParam(name, val);
        exampleEnv.setMutableConfig(config);

        config = exampleEnv.getMutableConfig();
        myVal = config.getConfigParam(name);
        assertTrue(val.equals(myVal));
    }

    /**
     * Count the number of threads with the name "Cleaner#".
     */
    private int countCleanerThreads() {

        Thread[] threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);

        int count = 0;
        for (int i = 0; i < threads.length; i += 1) {
            if (threads[i] != null &&
		threads[i].getName().startsWith("Cleaner")) {
                count += 1;
            }
        }

        return count;
    }

    /**
     * Checks that the memory budget is updated properly by the
     * UtilizationTracker.  Prior to a bug fix [#15505] amounts were added to
     * the budget but not subtraced when two TrackedFileSummary objects were
     * merged.  Merging occurs when a local tracker is added to the global
     * tracker.  Local trackers are used during recovery, checkpoints, lazy
     * compression, and reverse splits.
     */
    public void testTrackerMemoryBudget()
        throws DatabaseException {

        /* Open environmnet. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_CHECK_LEAKS.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        exampleEnv = new Environment(envHome, envConfig);
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(exampleEnv);
        MemoryBudget budget = envImpl.getMemoryBudget();

        /* Open database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        exampleDb = exampleEnv.openDatabase(null, "foo", dbConfig);

        /* Insert data. */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 1; i <= 200; i += 1) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            exampleDb.put(null, key, data);
        }

        /* Sav the misc budget baseline. */
        flushTrackedFiles();
        long misc = budget.getMiscMemoryUsage();

        /*
         * Nothing becomes obsolete when inserting and no INs are logged, so
         * the budget does not increase.
         */
        IntegerBinding.intToEntry(201, key);
        exampleDb.put(null, key, data);
        assertEquals(misc, budget.getMiscMemoryUsage());
        flushTrackedFiles();
        assertEquals(misc, budget.getMiscMemoryUsage());

        /*
         * Update a record and expect the budget to increase because the old
         * LN becomes obsolete.
         */
        exampleDb.put(null, key, data);
        assertTrue(misc < budget.getMiscMemoryUsage());
        flushTrackedFiles();
        assertEquals(misc, budget.getMiscMemoryUsage());

        /*
         * Delete all records and expect the budget to increase because LNs
         * become obsolete.
         */
        for (int i = 1; i <= 201; i += 1) {
            IntegerBinding.intToEntry(i, key);
            exampleDb.delete(null, key);
        }
        assertTrue(misc < budget.getMiscMemoryUsage());
        flushTrackedFiles();
        assertEquals(misc, budget.getMiscMemoryUsage());

        /*
         * Compress and expect no change to the budget.  Prior to the fix for
         * [#15505] the assertion below failed because the baseline misc budget
         * was not restored.
         */
        exampleEnv.compress();
        flushTrackedFiles();
        assertEquals(misc, budget.getMiscMemoryUsage());

        closeEnv();
    }

    /**
     * Flushes all tracked files to subtract tracked info from the misc memory
     * budget.
     */
    private void flushTrackedFiles()
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(exampleEnv);
        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        UtilizationProfile profile = envImpl.getUtilizationProfile();

        TrackedFileSummary[] files = tracker.getTrackedFiles();
        for (int i = 0; i < files.length; i += 1) {
            profile.flushFileSummary(files[i]);
        }
    }
}
