/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BackgroundIOTest.java,v 1.4.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleOutput;
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
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.TestHook;

public class BackgroundIOTest extends TestCase {

    private static CheckpointConfig forceConfig;
    static {
        forceConfig = new CheckpointConfig();
        forceConfig.setForce(true);
    }

    private File envHome;
    private Environment env;
    private int nSleeps;

    public BackgroundIOTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        if (env != null) {
            try {
                env.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
            env = null;
        }
                
        //*
        TestUtils.removeLogFiles("TearDown", envHome, true);
        TestUtils.removeFiles("TearDown", envHome, FileManager.DEL_SUFFIX);
        //*/
    }

    public void testBackgroundIO1()
	throws DatabaseException, InterruptedException {

        doTest(10, 10, 226, 246);
    }

    public void testBackgroundIO2()
	throws DatabaseException, InterruptedException {

        doTest(10, 5, 365, 385);
    }

    public void testBackgroundIO3()
	throws DatabaseException, InterruptedException {

        doTest(5, 10, 324, 344);
    }

    public void testBackgroundIO4()
	throws DatabaseException, InterruptedException {

        doTest(5, 5, 463, 483);
    }

    private void doTest(int readLimit,
                        int writeLimit,
                        int minSleeps,
                        int maxSleeps)
	throws DatabaseException, InterruptedException {

        final int fileSize = 1000000;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_BUFFER_MAX_SIZE.getName(),
             Integer.toString(1024));
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(fileSize));
        envConfig.setConfigParam
	    (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "60");
        //*
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_BACKGROUND_READ_LIMIT.getName(),
             String.valueOf(readLimit));
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_BACKGROUND_WRITE_LIMIT.getName(),
             String.valueOf(writeLimit));
        //*/
        env = new Environment(envHome, envConfig);

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        envImpl.setBackgroundSleepHook(new TestHook() {
            public void doHook() {
                nSleeps += 1;
                assertEquals(0, LatchSupport.countLatchesHeld());
            }
            public Object getHookValue() {
        	throw new UnsupportedOperationException();
            }
            public void doIOHook() throws IOException {
                throw new UnsupportedOperationException();
            }
        });

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        Database db = env.openDatabase(null, "BackgroundIO", dbConfig);

        final int nFiles = 3;
        final int keySize = 20;
        final int dataSize = 10;
        final int recSize = keySize + dataSize + 35 /* LN overhead */;
        final int nRecords = nFiles * (fileSize / recSize);

        /*
         * Insert records first so we will have a sizeable checkpoint.  Insert
         * interleaved because sequential inserts flush the BINs, and we want
         * to defer BIN flushing until the checkpoint.
         */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[dataSize]);
        for (int i = 0; i <= nRecords; i += 2) {
            setKey(key, i, keySize);
            db.put(null, key, data);
        }
        for (int i = 1; i <= nRecords; i += 2) {
            setKey(key, i, keySize);
            db.put(null, key, data);
        }

        /* Perform a checkpoint to perform background writes. */
        env.checkpoint(forceConfig);

        /* Delete records so we will have a sizable cleaning. */
        for (int i = 0; i <= nRecords; i += 1) {
            setKey(key, i, keySize);
            db.delete(null, key);
        }

        /* Perform cleaning to perform background reading. */
        env.checkpoint(forceConfig);
        env.cleanLog();
        env.checkpoint(forceConfig);

	db.close();
	env.close();
        env = null;

        String msg;
        msg = "readLimit=" + readLimit +
              " writeLimit=" + writeLimit +
              " minSleeps=" + minSleeps +
              " maxSleeps=" + maxSleeps +
              " actualSleeps=" + nSleeps;
        //System.out.println(msg);

        //*
        assertTrue(msg, nSleeps >= minSleeps && nSleeps <= maxSleeps);
        //*/
    }

    /**
     * Outputs an integer followed by pad bytes.
     */
    private void setKey(DatabaseEntry entry, int val, int len) {
        TupleOutput out = new TupleOutput();
        out.writeInt(val);
        for (int i = 0; i < len - 4; i += 1) {
            out.writeByte(0);
        }
        TupleBase.outputToEntry(out, entry);
    }
}
