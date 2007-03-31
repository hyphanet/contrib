/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileSelectionTest.java,v 1.31.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

public class FileSelectionTest extends TestCase {

    private static final int DATA_SIZE = 100;
    private static final int FILE_SIZE = 4096 * 10;
    private static final int INITIAL_FILES = 5;
    private static final byte[] MAIN_KEY_FOR_DUPS = {0, 1, 2, 3, 4, 5};

    private static final EnvironmentConfig envConfig = initConfig();
    private static final EnvironmentConfig highUtilizationConfig =
                                                                initConfig();
    private static final EnvironmentConfig steadyStateAutoConfig =
								initConfig();
    static {
        highUtilizationConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(),
             String.valueOf(90));

        steadyStateAutoConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "true");
    }

    static EnvironmentConfig initConfig() {
        EnvironmentConfig config = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(config);
        config.setTransactional(true);
        config.setAllowCreate(true);
        config.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        config.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                              Integer.toString(FILE_SIZE));
        config.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                              "false");
        config.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                              "false");
        config.setConfigParam(EnvironmentParams.CLEANER_REMOVE.getName(),
                              "false");
        config.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        config.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_FILES_TO_DELETE.getName(), "1");
        config.setConfigParam
	    (EnvironmentParams.CLEANER_LOCK_TIMEOUT.getName(), "1");
        config.setConfigParam
	    (EnvironmentParams.CLEANER_MAX_BATCH_FILES.getName(), "1");
        return config;
    }

    private static final CheckpointConfig forceConfig = new CheckpointConfig();
    static {
        forceConfig.setForce(true);
    }

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private boolean dups;
    
    /* The index is the file number, the value is the first key in the file. */
    private List firstKeysInFiles;

    /* Set of keys that should exist. */
    private Set existingKeys;

    public FileSelectionTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
                
        //*
        try {
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
        //*/

        db = null;
        env = null;
        envImpl = null;
        envHome = null;
        firstKeysInFiles = null;
    }

    private void openEnv()
        throws DatabaseException {

        openEnv(envConfig);
    }

    private void openEnv(EnvironmentConfig config)
        throws DatabaseException {

        env = new Environment(envHome, config);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        db = env.openDatabase(null, "cleanerFileSelection", dbConfig);
    }

    private void closeEnv()
        throws DatabaseException {

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    /**
     * Tests that the test utilities work.
     */
    public void testBaseline()
        throws DatabaseException {

        openEnv();
        writeData();
        verifyData();
        verifyDeletedFiles(null);
        closeEnv();
        openEnv();
        verifyData();
        verifyDeletedFiles(null);
        closeEnv();
    }

    public void testBaselineDups()
        throws DatabaseException {

        dups = true;
        testBaseline();
    }

    /**
     * Tests that the expected files are selected for cleaning.
     */
    public void testBasic()
        throws DatabaseException {

        openEnv();
        writeData();
        verifyDeletedFiles(null);

        /*
         * The first file should be the first to be cleaned because it has
         * relatively few LNs.
         */
        forceCleanOne();
        verifyDeletedFiles(new int[] {0});
        verifyData();

        /*
         * Delete most of the LNs in two middle files.  They should be the next
         * two files cleaned.
         */
        int fileNum = INITIAL_FILES / 2;
        int firstKey = ((Integer) firstKeysInFiles.get(fileNum)).intValue();
        int nextKey = ((Integer) firstKeysInFiles.get(fileNum + 1)).intValue();
        int count = nextKey - firstKey - 4;
        deleteData(firstKey, count);

        fileNum += 1;
        firstKey = ((Integer) firstKeysInFiles.get(fileNum)).intValue();
        nextKey = ((Integer) firstKeysInFiles.get(fileNum + 1)).intValue();
        count = nextKey - firstKey - 4;
        deleteData(firstKey, count);

        forceCleanOne();
        forceCleanOne();
        verifyDeletedFiles(new int[] {0, fileNum - 1, fileNum});
        verifyData();

        closeEnv();
    }

    public void testBasicDups()
        throws DatabaseException {

        dups = true;
        testBasic();
    }

    /*
     * testCleaningMode, testTruncateDatabase, and testRemoveDatabase and are
     * not tested with dups=true because with duplicates the total utilization
     * after calling writeData() is 47%, so cleaning will occur and the tests
     * don't expect that.
     */

    /**
     * Tests that routine cleaning does not clean when it should not.
     */
    public void testCleaningMode()
        throws DatabaseException {

        int nextFile = -1;
        int nCleaned;

        /*
         * Nothing is cleaned with routine cleaning, even after reopening the
         * environment.
         */
        openEnv();
        writeData();

        nCleaned = cleanRoutine();
        assertEquals(0, nCleaned);
        nextFile = getNextDeletedFile(nextFile);
        assertTrue(nextFile == -1);

        verifyData();
        closeEnv();
        openEnv();
        verifyData();

        nCleaned = cleanRoutine();
        assertEquals(0, nCleaned);
        nextFile = getNextDeletedFile(nextFile);
        assertTrue(nextFile == -1);

        verifyData();

        closeEnv();
    }

    /**
     * Test retries after cleaning fails because an LN was write-locked.
     */
    public void testRetry()
        throws DatabaseException {

        openEnv(highUtilizationConfig);
        writeData();
        verifyData();

        /*
         * The first file is full of LNs.  Delete all but the last record to
         * cause it to be selected next for cleaning.
         */
        int firstKey = ((Integer) firstKeysInFiles.get(1)).intValue();
        int nextKey = ((Integer) firstKeysInFiles.get(2)).intValue();
        int count = nextKey - firstKey - 1;
        deleteData(firstKey, count);
        verifyData();

        /* Write-lock the last record to cause cleaning to fail. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;
        if (dups) {
            key.setData(MAIN_KEY_FOR_DUPS);
            data.setData(TestUtils.getTestArray(nextKey - 1));
            status = cursor.getSearchBoth(key, data, LockMode.RMW);
        } else {
            key.setData(TestUtils.getTestArray(nextKey - 1));
            status = cursor.getSearchKey(key, data, LockMode.RMW);
        }
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);


        /* Cleaning should fail. */
        forceCleanOne();
        verifyDeletedFiles(null);
        forceCleanOne();
        verifyDeletedFiles(null);

        /* Release the write-lock. */
        cursor.close();
        txn.abort();
        verifyData();

        /* Cleaning should succeed, with all files deleted. */
        forceCleanOne();
        verifyDeletedFiles(new int[] {0, 1, 2});
        verifyData();

        closeEnv();
    }

    /**
     * Tests that the je.cleaner.minFileUtilization property works as expected.
     */
    public void testMinFileUtilization()
        throws DatabaseException {

        /* Open with minUtilization=10 and minFileUtilization=0. */
        EnvironmentConfig myConfig = initConfig();
        myConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(),
             String.valueOf(10));
        myConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_FILE_UTILIZATION.getName(),
             String.valueOf(0));
        openEnv(myConfig);

        /* Write data and delete two thirds of the LNs in the middle file. */
        writeData();
        verifyDeletedFiles(null);
        int fileNum = INITIAL_FILES / 2;
        int firstKey = ((Integer) firstKeysInFiles.get(fileNum)).intValue();
        int nextKey = ((Integer) firstKeysInFiles.get(fileNum + 1)).intValue();
        int count = ((nextKey - firstKey) * 2) / 3;
        deleteData(firstKey, count);

        /* The file should not be deleted. */
        env.cleanLog();
        env.checkpoint(forceConfig);
        verifyDeletedFiles(null);

        /* Change minFileUtilization=50 */
        myConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_FILE_UTILIZATION.getName(),
             String.valueOf(50));
        env.setMutableConfig(myConfig);

        /* The file should now be deleted. */
        env.cleanLog();
        env.checkpoint(forceConfig);
        verifyDeletedFiles(new int[] {fileNum});
        verifyData();

        closeEnv();
    }

    private void printFiles(String msg) {
        System.out.print(msg);
        Long lastNum = envImpl.getFileManager().getLastFileNum();
        for (int i = 0; i <= (int) lastNum.longValue(); i += 1) {
            String name = envImpl.getFileManager().
                                  getFullFileName(i, FileManager.JE_SUFFIX);
            if (new File(name).exists()) {
                System.out.print(" " + i);
            }
        }
        System.out.println("");
    }

    public void testRetryDups()
        throws DatabaseException {

        dups = true;
        testRetry();
    }

    /**
     * Steady state should occur with normal (50% utilization) configuration
     * and automatic checkpointing and cleaning.
     */
    public void testSteadyStateAutomatic()
        throws DatabaseException {

        doSteadyState(steadyStateAutoConfig, false, 13);
    }

    public void testSteadyStateAutomaticDups()
        throws DatabaseException {

        dups = true;
        testSteadyStateAutomatic();
    }

    /**
     * Steady state utilization with manual checkpointing and cleaning.
     */
    public void testSteadyStateManual()
        throws DatabaseException {

        doSteadyState(envConfig, true, 13);
    }

    public void testSteadyStateManualDups()
        throws DatabaseException {

        dups = true;
        testSteadyStateManual();
    }

    /**
     * Steady state should occur when utilization is at the maximum.
     */
    public void testSteadyStateHighUtilization()
        throws DatabaseException {

        doSteadyState(highUtilizationConfig, true, 9);
    }

    public void testSteadyStateHighUtilizationDups()
        throws DatabaseException {

        dups = true;
        testSteadyStateHighUtilization();
    }

    /**
     * Tests that we quickly reach a steady state of disk usage when updates
     * are made but no net increase in data occurs.
     *
     * @param manualCleaning is whether to run cleaning manually every
     * iteration, or to rely on the cleaner thread.
     *
     * @param maxFileCount the maximum number of files allowed for this test.
     */
    private void doSteadyState(EnvironmentConfig config,
                               boolean manualCleaning,
                               int maxFileCount)
        throws DatabaseException {

        openEnv(config);
        writeData();
        verifyData();

        final int iterations = 100;

        for (int i = 0; i < iterations; i += 1) {
            updateData(100, 100);
            int cleaned = -1;
            if (manualCleaning) {
                cleaned = cleanRoutine();
            } else {
	        /* Need to delay a bit for the cleaner to keep up. */
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {}
            }

	    /*
             * Checkpoints need to occur often for the cleaner to keep up.
             * and to delete files that were cleaned.
             */
	    env.checkpoint(forceConfig);
            verifyData();
            int fileCount =
                envImpl.getFileManager().getAllFileNumbers().length;
            assertTrue("fileCount=" + fileCount +
                       " maxFileCount=" + maxFileCount,
                       fileCount <= maxFileCount);
            if (false) {
                System.out.println("fileCount=" + fileCount +
                                   " cleaned=" + cleaned);
            }
        }
        closeEnv();
    }

    /**
     * Tests that truncate causes cleaning.
     * @deprecated use of Database.truncate
     */
    public void testTruncateDatabase()
        throws DatabaseException {

        int nCleaned;

        openEnv();
        writeData();

        nCleaned = cleanRoutine();
        assertEquals(0, nCleaned);

        db.truncate(null, false);
        nCleaned = cleanRoutine();
        assertEquals(4, nCleaned);

        closeEnv();
    }

    /**
     * Tests that remove causes cleaning.
     */
    public void testRemoveDatabase()
        throws DatabaseException {

        int nCleaned;

        openEnv();
        writeData();

        String dbName = db.getDatabaseName();
        db.close();
        db = null;

        nCleaned = cleanRoutine();
        assertEquals(0, nCleaned);

        env.removeDatabase(null, dbName);
        nCleaned = cleanRoutine();
        assertEquals(4, nCleaned);

        closeEnv();
    }

    public void testForceCleanFiles()
        throws DatabaseException {

        /* No files force cleaned. */
        EnvironmentConfig myConfig = initConfig();
        openEnv(myConfig);
        writeData();
        verifyData();
        env.cleanLog();
        env.checkpoint(forceConfig);
        verifyDeletedFiles(null);
        closeEnv();

        /* Force cleaning: 3 */
        myConfig.setConfigParam
            (EnvironmentParams.CLEANER_FORCE_CLEAN_FILES.getName(),
             "3");
        openEnv(myConfig);
        forceCleanOne();
        verifyDeletedFiles(new int[] {3});
        closeEnv();

        /* Force cleaning: 0 - 1 */
        myConfig.setConfigParam
            (EnvironmentParams.CLEANER_FORCE_CLEAN_FILES.getName(),
             "0-1");
        openEnv(myConfig);
        forceCleanOne();
        forceCleanOne();
        verifyDeletedFiles(new int[] {0, 1, 3});
        closeEnv();
    }

    /**
     * Force cleaning of one file.
     */
    private void forceCleanOne()
        throws DatabaseException {

        envImpl.getCleaner().doClean(false, // cleanMultipleFiles
                                     true); // forceCleaning
        /* To force file deletion a checkpoint is necessary. */
        env.checkpoint(forceConfig);
    }

    /**
     * Do routine cleaning just as normally done via the cleaner daemon, and
     * return the number of files cleaned.
     */
    private int cleanRoutine()
        throws DatabaseException {

        return env.cleanLog();
    }

    /**
     * Writes data to create INITIAL_FILES number of files, storing the first
     * key for each file in the firstKeysInFiles list.  One extra file is
     * actually created, to ensure that the firstActiveLSN is not in any of
     * INITIAL_FILES files.
     */
    private void writeData()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[DATA_SIZE]);
        firstKeysInFiles = new ArrayList();
        existingKeys = new HashSet();

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        int fileNum = -1;

        for (int nextKey = 0; fileNum < INITIAL_FILES; nextKey += 1) {
            
            OperationStatus status;
            if (dups) {
                key.setData(MAIN_KEY_FOR_DUPS);
                data.setData(TestUtils.getTestArray(nextKey));
                status = cursor.putNoDupData(key, data);
            } else {
                key.setData(TestUtils.getTestArray(nextKey));
                data.setData(new byte[DATA_SIZE]);
                status = cursor.putNoOverwrite(key, data);
            }

            assertEquals(OperationStatus.SUCCESS, status);
            existingKeys.add(new Integer(nextKey));

            long lsn = getLsn(cursor);
            if (DbLsn.getFileNumber(lsn) != fileNum) {
                assertTrue(fileNum < DbLsn.getFileNumber(lsn));
                fileNum = (int) DbLsn.getFileNumber(lsn);
                assertEquals(fileNum, firstKeysInFiles.size());
                firstKeysInFiles.add(new Integer(nextKey));
            }
        }

        cursor.close();
        txn.commit();
	env.checkpoint(forceConfig);

        long firstActiveLsn = envImpl.getCheckpointer().getFirstActiveLsn();
        assertTrue(firstActiveLsn != DbLsn.NULL_LSN);
        assertTrue(DbLsn.getFileNumber(firstActiveLsn) >= INITIAL_FILES);
    }

    /**
     * Deletes the specified keys.
     */
    private void deleteData(int firstKey, int keyCount)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);

        for (int i = 0; i < keyCount; i += 1) {
            int nextKey = firstKey + i;
            OperationStatus status;
            if (dups) {
                key.setData(MAIN_KEY_FOR_DUPS);
                data.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchBoth(key, data, null);
            } else {
                key.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchKey(key, data, null);
            }
            assertEquals(OperationStatus.SUCCESS, status);
            status = cursor.delete();
            assertEquals(OperationStatus.SUCCESS, status);
            existingKeys.remove(new Integer(nextKey));
        }

        cursor.close();
        txn.commit();
    }

    /**
     * Updates the specified keys.
     */
    private void updateData(int firstKey, int keyCount)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);

        for (int i = 0; i < keyCount; i += 1) {
            int nextKey = firstKey + i;
            OperationStatus status;
            if (dups) {
                key.setData(MAIN_KEY_FOR_DUPS);
                data.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchBoth(key, data, null);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(MAIN_KEY_FOR_DUPS.length, key.getSize());
                assertEquals(nextKey, TestUtils.getTestVal(data.getData()));
            } else {
                key.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchKey(key, data, null);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(nextKey, TestUtils.getTestVal(key.getData()));
                assertEquals(DATA_SIZE, data.getSize());
            }
            status = cursor.putCurrent(data);
            assertEquals(OperationStatus.SUCCESS, status);
        }

        cursor.close();
        txn.commit();
    }

    /**
     * Verifies that the data written by writeData can be read.
     */
    private void verifyData()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);

        for (Iterator i = existingKeys.iterator(); i.hasNext();) {
            int nextKey = ((Integer) i.next()).intValue();
            OperationStatus status;
            if (dups) {
                key.setData(MAIN_KEY_FOR_DUPS);
                data.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchBoth(key, data, null);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(MAIN_KEY_FOR_DUPS.length, key.getSize());
                assertEquals(nextKey, TestUtils.getTestVal(data.getData()));
            } else {
                key.setData(TestUtils.getTestArray(nextKey));
                status = cursor.getSearchKey(key, data, null);
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(nextKey, TestUtils.getTestVal(key.getData()));
                assertEquals(DATA_SIZE, data.getSize());
            }
        }

        cursor.close();
        txn.commit();
    }

    /**
     * Checks that all log files exist except those specified.
     */
    private void verifyDeletedFiles(int[] shouldNotExist) {
        Long lastNum = envImpl.getFileManager().getLastFileNum();
        for (int i = 0; i <= (int) lastNum.longValue(); i += 1) {
            boolean shouldExist = true;
            if (shouldNotExist != null) {
                for (int j = 0; j < shouldNotExist.length; j += 1) {
                    if (i == shouldNotExist[j]) {
                        shouldExist = false;
                        break;
                    }
                }
            }
            String name = envImpl.getFileManager().
                                  getFullFileName(i, FileManager.JE_SUFFIX);
            assertEquals(name, shouldExist, new File(name).exists());
        }
    }

    /**
     * Returns the first deleted file number or -1 if none.
     */
    private int getNextDeletedFile(int afterFile) {
        Long lastNum = envImpl.getFileManager().getLastFileNum();
        for (int i = afterFile + 1; i <= (int) lastNum.longValue(); i += 1) {
            String name = envImpl.getFileManager().
                                  getFullFileName(i, FileManager.JE_SUFFIX);
            if (!(new File(name).exists())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets the LSN at the cursor position, using internal methods.
     */
    private long getLsn(Cursor cursor)
        throws DatabaseException {

        CursorImpl impl = DbTestProxy.dbcGetCursorImpl(cursor);
        BIN bin;
        int index;
        if (dups) {
            bin = impl.getDupBIN();
            index = impl.getDupIndex();
            if (bin == null) {
                bin = impl.getBIN();
                index = impl.getIndex();
                assertNotNull(bin);
            }
        } else {
            assertNull(impl.getDupBIN());
            bin = impl.getBIN();
            index = impl.getIndex();
            assertNotNull(bin);
        }
        assertTrue(index >= 0);
        long lsn = bin.getLsn(index);
        assertTrue(lsn != DbLsn.NULL_LSN);
        return lsn;
    }
}
