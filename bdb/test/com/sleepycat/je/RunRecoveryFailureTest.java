/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RunRecoveryFailureTest.java,v 1.34.2.2 2007/06/13 03:55:37 mark Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

public class RunRecoveryFailureTest extends TestCase {

    private Environment env;
    private File envHome;

    public RunRecoveryFailureTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
        openEnv();

    }
    
    public void tearDown()
        throws Exception {

        /* 
	 * Close down environments in case the unit test failed so that the log
	 * files can be removed.
         */
        try {
            if (env != null) {
                env.close();
                env = null;
            }
        } catch (RunRecoveryException e) {
            /* ok, the test hosed it. */
            return;
        } catch (DatabaseException e) {
            /* ok, the test closed it */
	}

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    private void openEnv() 
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);

        /* 
         * Run with tiny log buffers, so we can go to disk more (and see the
         * checksum errors)
         */
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam
	    (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_MEM_SIZE.getName(),
	     EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_FILE_MAX.getName(), "1024");
	envConfig.setConfigParam
	    (EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
    }

    /*
     * Corrupt an environment while open, make sure we get a
     * RunRecoveryException.
     */
    public void testInvalidateEnvMidStream()
        throws Throwable {

        try {
        
            /* Make a new db in this env and flush the file. */
            Transaction txn =
		env.beginTransaction(null, TransactionConfig.DEFAULT);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database db = env.openDatabase(txn, "foo", dbConfig);
            DatabaseEntry key = new DatabaseEntry(new byte[1000]);
            DatabaseEntry data = new DatabaseEntry(new byte[1000]);
            for (int i = 0; i < 100; i += 1) {
                db.put(txn, key, data);
            }

            env.getEnvironmentImpl().getLogManager().flush();
            env.getEnvironmentImpl().getFileManager().clear();

            /* 
	     * Corrupt the file, then abort the txn in order to force it to
             * re-read. Should get a checksum error, which should invalidate
             * the environment.
             */
            long currentFile = DbInternal.envGetEnvironmentImpl(env)
                                         .getFileManager()
                                         .getCurrentFileNum();
            for (int fileNum = 0; fileNum <= currentFile; fileNum += 1) {
                File file = new File
                    (envHome, "0000000" + fileNum + FileManager.JE_SUFFIX);
                RandomAccessFile starterFile =
                    new RandomAccessFile(file, "rw");
                FileChannel channel = starterFile.getChannel();
                long fileSize = channel.size();
                if (fileSize > FileManager.firstLogEntryOffset()) {
                    ByteBuffer junkBuffer = ByteBuffer.allocate
                        ((int) fileSize - FileManager.firstLogEntryOffset());
                    int written = channel.write
                        (junkBuffer, FileManager.firstLogEntryOffset());
                    assertTrue(written > 0);
                    starterFile.close();
                }
            }

            try {
                txn.abort();
                fail("Should see a run recovery exception");
            } catch (RunRecoveryException e) {
            }

            try {
                env.getDatabaseNames();
                fail("Should see a run recovery exception again");
            } catch (RunRecoveryException e) {
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
