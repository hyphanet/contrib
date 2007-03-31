/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbBackupTest.java,v 1.3.2.1 2007/02/01 14:50:23 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

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
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.utilint.DbLsn;

public class DbBackupTest extends TestCase {

    private static StatsConfig CLEAR_CONFIG = new StatsConfig();
    static {
        CLEAR_CONFIG.setClear(true);
    }
    
    private static CheckpointConfig FORCE_CONFIG = new CheckpointConfig();
    static {
        FORCE_CONFIG.setForce(true);
    }

    private static final String SAVE1 = "save1";
    private static final String SAVE2 = "save2";
    private static final String SAVE3 = "save3";
    private static final int NUM_RECS = 50;

    private File envHome;
    private Environment env;
    private FileManager fileManager;

    public DbBackupTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        deleteSaveDir(SAVE1);
        deleteSaveDir(SAVE2);
        deleteSaveDir(SAVE3);
    }
    
    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("TearDown", envHome, false);
        deleteSaveDir(SAVE1);
        deleteSaveDir(SAVE2);
        deleteSaveDir(SAVE3);
    }

    /**
     * Test basic backup, make sure log cleaning isn't running.
     */
    public void testBackupVsCleaning()
        throws Throwable {

        env = createEnv(false, envHome); /* read-write env */
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        fileManager = envImpl.getFileManager();

        try {

            /* 
             * Grow files, creating obsolete entries to create cleaner
             * opportunity. 
             */
            growFiles("db1", env, 8);

            /* Start backup. */
            DbBackup backupHelper = new DbBackup(env);
            backupHelper.startBackup();

            long lastFileNum =  backupHelper.getLastFileInBackupSet();
            long checkLastFileNum = lastFileNum;
            
            /* Copy the backup set. */
            saveFiles(backupHelper, -1, lastFileNum, SAVE1);
            
            /* 
             * Try to clean and checkpoint. Check that the logs grew as
             * a result.
             */
            batchClean(0);
            long newLastFileNum = (fileManager.getLastFileNum()).longValue();
            assertTrue(checkLastFileNum < newLastFileNum);
            checkLastFileNum = newLastFileNum;
 
            /* Copy the backup set after attempting cleaning */
            saveFiles(backupHelper, -1, lastFileNum, SAVE2);

            /* Insert more data. */
            growFiles("db2", env, 8);

            /* 
             * Try to clean and checkpoint. Check that the logs grew as
             * a result.
             */
            batchClean(0);
            newLastFileNum = fileManager.getLastFileNum().longValue();
            assertTrue(checkLastFileNum < newLastFileNum);
            checkLastFileNum = newLastFileNum;

            /* Copy the backup set after inserting more data */
            saveFiles(backupHelper, -1, lastFileNum, SAVE3);

            /* Check the membership of the saved set. */
            long lastFile =  backupHelper.getLastFileInBackupSet();
            String [] backupSet = backupHelper.getLogFilesInBackupSet();
            assertEquals((lastFile + 1), backupSet.length);

            /* End backup. */
            backupHelper.endBackup();

            /*
             * Run cleaning, and verify that quite a few files are deleted.
             */
            long numCleaned = batchClean(100);
            assertTrue(numCleaned > 5);
            env.close();
            env = null;

            /* Verify backups. */
            TestUtils.removeLogFiles("Verify", envHome, false);
            verifyDb1(SAVE1, true);
            TestUtils.removeLogFiles("Verify", envHome, false);
            verifyDb1(SAVE2, true);
            TestUtils.removeLogFiles("Verify", envHome, false);
            verifyDb1(SAVE3, true);
        } finally {
            if (env != null) {
                env.close();
            }
        }
    }

    /**
     * Test multiple backup passes
     */
    public void testIncrementalBackup()
        throws Throwable {

        env = createEnv(false, envHome); /* read-write env */
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        fileManager = envImpl.getFileManager();

        try {

            /* 
             * Grow files, creating obsolete entries to create cleaner
             * opportunity. 
             */
            growFiles("db1", env, 8);

            /* Backup1. */
            DbBackup backupHelper = new DbBackup(env);
            backupHelper.startBackup();
            long b1LastFile =  backupHelper.getLastFileInBackupSet();
            saveFiles(backupHelper, -1, b1LastFile, SAVE1);
            String lastName = fileManager.getFullFileName(b1LastFile,
                                                 FileManager.JE_SUFFIX);
            File f = new File(lastName);
            long savedLength = f.length();
            backupHelper.endBackup();

            /* 
             * Add more data. Check that the file did flip, and is not modified
             * by the additional data. 
             */
            growFiles("db2", env, 8);
            checkFileLen(b1LastFile, savedLength);

            /* Backup2. */
            backupHelper.startBackup();
            long b2LastFile =  backupHelper.getLastFileInBackupSet();
            saveFiles(backupHelper, b1LastFile, b2LastFile, SAVE2);
            backupHelper.endBackup();

            env.close();
            env = null;

            /* Verify backups. */
            TestUtils.removeLogFiles("Verify", envHome, false);
            verifyDb1(SAVE1, false);
            TestUtils.removeLogFiles("Verify", envHome, false);
            verifyBothDbs(SAVE1, SAVE2);
        } finally {
            if (env != null) {
                env.close();
            }
        }
    }

    public void testBadUsage() 
        throws Exception {

        Environment env = createEnv(false, envHome); /* read-write env */

        try {
            DbBackup backup = new DbBackup(env);

            /* end can only be called after start. */
            try {
                backup.endBackup();
                fail("should fail");
            } catch (DatabaseException expected) {
            }

            /* start can't be called twice. */
            backup.startBackup();
            try {
                backup.startBackup();
                fail("should fail");
            } catch (DatabaseException expected) {
            }

            /* 
             * You can only get the backup set when you're in between start 
             * and end.
             */
            backup.endBackup();
        
            try {
                backup.getLastFileInBackupSet();
                fail("should fail");
            } catch (DatabaseException expected) {
            }

            try {
                backup.getLogFilesInBackupSet();
                fail("should fail");
            } catch (DatabaseException expected) {
            }

            try {
                backup.getLogFilesInBackupSet(0);
                fail("should fail");
            } catch (DatabaseException expected) {
            }
        } finally {
            env.close();
        }
    }

    /*
     * This test can't be run by default, because it makes a directory 
     * read/only, and Java doesn't support a way to make it writable again
     * except in Mustang. There's no way to clean up a read-only directory.
     */
    public void xtestReadOnly() 
        throws Exception {

        /* Make a read-only handle on a read-write environment directory.*/
        Environment env = createEnv(true, envHome); 

        try {
            DbBackup backup = new DbBackup(env);
            fail("Should fail because env is read/only.");
        } catch (DatabaseException expected) {
        }

        env.close();

        /* 
         * Make a read-only handle on a read-only environment directory. Use a
         * new environment directory because we're going to set it read0nly and
         * there doesn't seem to be a way of undoing that.
         */
        File tempEnvDir = new File(envHome, SAVE1);
        assertTrue(tempEnvDir.mkdirs());
        env = createEnv(false, tempEnvDir); 
        growFiles("db1", env, 8);
        env.close();
        //assertTrue(tempEnvDir.setReadOnly());

        env = createEnv(true, tempEnvDir);

        DbBackup backupHelper = new DbBackup(env);
        backupHelper.startBackup();

        FileManager fileManager =
            DbInternal.envGetEnvironmentImpl(env).getFileManager();
        long lastFile = fileManager.getLastFileNum().longValue();
        assertEquals(lastFile, backupHelper.getLastFileInBackupSet());

        backupHelper.endBackup();
        env.close();
        assertTrue(tempEnvDir.delete());
    }

    private Environment createEnv(boolean readOnly, File envDir) 
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setAllowCreate(true);
        envConfig.setReadOnly(readOnly);
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 "400");
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                                 "false");

        Environment env = new Environment(envDir, envConfig);

        return env;
    }

    private long growFiles(String dbName,
                           Environment env,
                           int minNumFiles) 
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, dbName, dbConfig);
        FileManager fileManager =
            DbInternal.envGetEnvironmentImpl(env).getFileManager();
        long startLastFileNum =
            DbLsn.getFileNumber(fileManager.getLastUsedLsn());

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        /* Update twice, in order to create plenty of cleaning opportunity. */
        for (int i = 0; i < NUM_RECS; i++) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }

        for (int i = 0; i < NUM_RECS; i++) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i+5, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }

        db.close();

        long endLastFileNum =
            DbLsn.getFileNumber(fileManager.getLastUsedLsn());
        assertTrue((endLastFileNum -
                    startLastFileNum) >= minNumFiles);
        return endLastFileNum;
    }

    private int batchClean(int expectedDeletions) 
        throws DatabaseException {

        EnvironmentStats stats = env.getStats(CLEAR_CONFIG);
        while (env.cleanLog() > 0) {
        }
        env.checkpoint(FORCE_CONFIG);
        stats = env.getStats(CLEAR_CONFIG);
        assertTrue(stats.getNCleanerDeletions() <= expectedDeletions);

        return stats.getNCleanerDeletions();
    }

    private void saveFiles(DbBackup backupHelper, 
                           long lastFileFromPrevBackup,
                           long lastFileNum,
                           String saveDirName)
        throws IOException, DatabaseException {

        /* Check that the backup set contains only the files it should have. */
        String [] fileList =
            backupHelper.getLogFilesInBackupSet(lastFileFromPrevBackup);
        assertEquals(lastFileNum,
                     fileManager.getNumFromName(fileList[fileList.length-1]).
                     longValue());

        /* Make a new save directory. */
        File saveDir = new File(envHome, saveDirName);
        assertTrue(saveDir.mkdir());
        copyFiles(envHome, saveDir, fileList);
    }

    private void copyFiles(File sourceDir, File destDir, String [] fileList) 
        throws DatabaseException {

        try {
            for (int i = 0; i < fileList.length; i++) {
                File source = new File(sourceDir, fileList[i]);
                FileChannel sourceChannel =
                    new FileInputStream(source).getChannel();
                File save = new File(destDir, fileList[i]);
                FileChannel saveChannel =
                    new FileOutputStream(save).getChannel();

                saveChannel.transferFrom(sourceChannel, 0,
                                         sourceChannel.size());
    
                // Close the channels
                sourceChannel.close();
                saveChannel.close();
            }
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Delete all the contents and the directory itself.
     */
    private void deleteSaveDir(String saveDirName) 
        throws IOException {

        File saveDir = new File(envHome, saveDirName);
        if (saveDir.exists()) {
            String [] savedFiles = saveDir.list();
            if (savedFiles != null) {
            for (int i = 0; i < savedFiles.length; i++) {
                File f = new File(saveDir, savedFiles[i]);
                assertTrue(f.delete());
            }
            assertTrue(saveDir.delete());
            }
        }   
    }

    /**
     * Copy the saved files in, check values.
     */
    private void verifyDb1(String saveDirName, boolean rename) 
        throws DatabaseException {

        File saveDir = new File(envHome, saveDirName);
        String [] savedFiles = saveDir.list();
        if (rename){
            for (int i = 0; i < savedFiles.length; i++) {
                File saved = new File(saveDir, savedFiles[i]);
                File dest = new File(envHome, savedFiles[i]);
                assertTrue(saved.renameTo(dest));
            }
        } else {
            /* copy. */
            copyFiles(saveDir, envHome, savedFiles);
        }
        env = createEnv(false, envHome);
        try {
            checkDb("db1");

            /* Db 2 should not exist. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            try {
                Database db = env.openDatabase(null, "db2", dbConfig);
                fail("db2 should not exist");
            } catch (DatabaseException expected) {
            }

        } finally {
            env.close();
            env = null;
        }
    }

    /**
     * Copy the saved files in, check values.
     */
    private void verifyBothDbs(String saveDirName1, String saveDirName2) 
        throws DatabaseException {

        File saveDir = new File(envHome, saveDirName1);
        String [] savedFiles = saveDir.list();
        for (int i = 0; i < savedFiles.length; i++) {
            File saved = new File(saveDir, savedFiles[i]);
            File dest = new File(envHome, savedFiles[i]);
            assertTrue(saved.renameTo(dest));
        }

        saveDir = new File(envHome, saveDirName2);
        savedFiles = saveDir.list();
        for (int i = 0; i < savedFiles.length; i++) {
            File saved = new File(saveDir, savedFiles[i]);
            File dest = new File(envHome, savedFiles[i]);
            assertTrue(saved.renameTo(dest));
        }

        env = createEnv(false, envHome);
        try {
            checkDb("db1");
            checkDb("db2");
        } finally {
            env.close();
            env = null;
        }
    }

    private void checkDb(String dbName) 
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        Database db = env.openDatabase(null, dbName, dbConfig);
        Cursor c = null;
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            c = db.openCursor(null, null);

            for (int i = 0; i < NUM_RECS; i++) {
                assertEquals(OperationStatus.SUCCESS,
                             c.getNext(key, data, LockMode.DEFAULT));
                assertEquals(i, IntegerBinding.entryToInt(key));
                assertEquals(i + 5, IntegerBinding.entryToInt(data));
            }
            assertEquals(OperationStatus.NOTFOUND,
                         c.getNext(key, data, LockMode.DEFAULT));
        } finally {
            if (c != null) 
                c.close();
            db.close();
        }
    }

    private void checkFileLen(long fileNum, long length) 
        throws IOException {
        String fileName = fileManager.getFullFileName(fileNum, 
                                                      FileManager.JE_SUFFIX);
        File f = new File(fileName);
        assertEquals(length, f.length());
    }
}


