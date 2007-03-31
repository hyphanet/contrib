/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ReadOnlyLockingTest.java,v 1.9.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 * Verifies that opening an environment read-only will prevent cleaned files
 * from being deleted in a read-write environment.  Uses the ReadOnlyProcess
 * class to open the environment read-only in a separate process.
 */
public class ReadOnlyLockingTest extends TestCase {

    private static final int FILE_SIZE = 4096;
    private static final int READER_STARTUP_SECS = 30;

    private static final CheckpointConfig forceConfig = new CheckpointConfig();
    static {
        forceConfig.setForce(true);
    }

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private Process readerProcess;

    private static File getProcessFile() {
        return new File(System.getProperty(TestUtils.DEST_DIR),
                        "ReadOnlyProcessFile");
    }

    private static void deleteProcessFile() {
        File file = getProcessFile();
        file.delete();
        TestCase.assertTrue(!file.exists());
    }

    static void createProcessFile()
        throws IOException {
            
        File file = getProcessFile();
        TestCase.assertTrue(file.createNewFile());
        TestCase.assertTrue(file.exists());
    }

    public ReadOnlyLockingTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        deleteProcessFile();

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        deleteProcessFile();

        try {
            stopReaderProcess();
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
                
        try {
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        db = null;
        env = null;
        envImpl = null;
        envHome = null;
        readerProcess = null;
    }

    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(FILE_SIZE));
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");

        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "ReadOnlyLockingTest", dbConfig);
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
     * Tests that cleaned files are deleted when there is no reader process.
     */
    public void testBaseline()
        throws DatabaseException {

        openEnv();
        writeAndDeleteData();
        env.checkpoint(forceConfig);

        int nFilesCleaned = env.cleanLog();
        assertTrue(nFilesCleaned > 0);
        assertTrue(!areAnyFilesDeleted());

        /* Files are deleted during the checkpoint. */
        env.checkpoint(forceConfig);
        assertTrue(areAnyFilesDeleted());

        closeEnv();
    }

    /**
     * Tests that cleaned files are not deleted when there is a reader process.
     */
    public void testReadOnlyLocking()
        throws Exception {

        openEnv();
        writeAndDeleteData();
        env.checkpoint(forceConfig);
        int nFilesCleaned = env.cleanLog();
        assertTrue(nFilesCleaned > 0);
        assertTrue(!areAnyFilesDeleted());

        /*
         * No files are deleted after cleaning when the reader process is
         * running.
         */
        startReaderProcess();
        env.cleanLog();
        env.checkpoint(forceConfig);
        assertTrue(!areAnyFilesDeleted());

        /*
         * Files are deleted when a checkpoint occurs after the reader
         * process stops.
         */
        stopReaderProcess();
        env.cleanLog();
        env.checkpoint(forceConfig);
        assertTrue(areAnyFilesDeleted());

        closeEnv();
    }

    private void writeAndDeleteData()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[FILE_SIZE]);
        for (int i = 0; i < 5; i += 1) {
            db.put(null, key, data);
        }
    }

    private boolean areAnyFilesDeleted() {
        long lastNum = envImpl.getFileManager().getLastFileNum().longValue();
        for (long i = 0; i <= lastNum; i += 1) {
            String name = envImpl.getFileManager().getFullFileName
                (i, FileManager.JE_SUFFIX);
            if (!(new File(name).exists())) {
                return true;
            }
        }
        return false;
    }

    private void startReaderProcess()
        throws Exception {

        String[] cmd = {
            "java",
            "-cp",
            System.getProperty("java.class.path"),
            "-D" + TestUtils.DEST_DIR + '=' +
                System.getProperty(TestUtils.DEST_DIR),
            ReadOnlyProcess.class.getName(),
        };

        /* Start it and wait for it to open the environment. */
        readerProcess = Runtime.getRuntime().exec(cmd);
        long startTime = System.currentTimeMillis();
        boolean running = false;
        while (!running &&
               ((System.currentTimeMillis() - startTime) <
                (READER_STARTUP_SECS * 1000))) {
            if (getProcessFile().exists()) {
                running = true;
            } else {
                Thread.sleep(10);
            }
        }
        //printReaderStatus();
        assertTrue("ReadOnlyProcess did not start after " +
                   READER_STARTUP_SECS + " + secs",
                   running);
    }

    private void stopReaderProcess()
        throws Exception {

        if (readerProcess != null) {
            printReaderErrors();
            readerProcess.destroy();
            Thread.sleep(2000);
            readerProcess = null;
        }
    }

    private void printReaderStatus()
        throws IOException {

        try {
            int status = readerProcess.exitValue();
            System.out.println("Process status=" + status);
        } catch (IllegalThreadStateException e) {
            System.out.println("Process is still running");
        }
    }

    private void printReaderErrors()
        throws IOException {

        InputStream err = readerProcess.getErrorStream();
        int len = err.available();
        if (len > 0) {
            byte[] data = new byte[len];
            err.read(data);
            System.out.println("[ReadOnlyProcess] " + new String(data));
        }
    }
}
