/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INFileReaderTest.java,v 1.72.2.1 2007/02/01 14:50:14 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.INDeleteInfo;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * 
 */
public class INFileReaderTest extends TestCase {

    static private final boolean DEBUG = false;

    private File envHome;
    private Environment env;
    /* 
     * Need a handle onto the true environment in order to create
     * a reader 
     */
    private EnvironmentImpl envImpl;
    private Database db;
    private long maxNodeId;
    private List checkList;

    public INFileReaderTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        Key.DUMP_BINARY = true;
    }

    public void setUp()
	throws IOException, DatabaseException {

        /*
         * Note that we use the official Environment class to make the
         * environment, so that everything is set up, but we then go a
         * backdoor route to get to the underlying EnvironmentImpl class
         * so that we don't require that the Environment.getDbEnvironment
         * method be unnecessarily public.
         */
        TestUtils.removeLogFiles("Setup", envHome, false);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
	    (EnvironmentParams.BIN_DELTA_PERCENT.getName(), "75");
        envConfig.setAllowCreate(true);

        /* Disable noisy UtilizationProfile database creation. */
        DbInternal.setCreateUP(envConfig, false);
        /* Don't checkpoint utilization info for this test. */
        DbInternal.setCheckpointUP(envConfig, false);
        /* Don't run the cleaner without a UtilizationProfile. */
        envConfig.setConfigParam
	    (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

        env = new Environment(envHome, envConfig);

        envImpl =DbInternal.envGetEnvironmentImpl(env);

    }

    public void tearDown()
	throws IOException, DatabaseException {

        envImpl = null;
        env.close();
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Test no log file
     */
    public void testNoFile()
	throws IOException, DatabaseException {

        /* Make a log file with a valid header, but no data. */
        INFileReader reader =
	    new INFileReader(envImpl, 1000, DbLsn.NULL_LSN, DbLsn.NULL_LSN, false, false,
			     DbLsn.NULL_LSN, null);
        reader.addTargetType(LogEntryType.LOG_IN);
        reader.addTargetType(LogEntryType.LOG_BIN);
        reader.addTargetType(LogEntryType.LOG_IN_DELETE_INFO);

        int count = 0;
        while (reader.readNextEntry()) {
            count += 1;
        }
        assertEquals("Empty file should not have entries", 0, count);
    }

    /**
     * Run with an empty file
     */
    public void testEmpty()
	throws IOException, DatabaseException {

        /* Make a log file with a valid header, but no data. */
        FileManager fileManager = envImpl.getFileManager();
        fileManager.bumpLsn(1000000);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, 10000);
        fileManager.clear();

        INFileReader reader =
	    new INFileReader(envImpl, 1000, DbLsn.NULL_LSN, DbLsn.NULL_LSN, false, false,
			     DbLsn.NULL_LSN, null);
        reader.addTargetType(LogEntryType.LOG_IN);
        reader.addTargetType(LogEntryType.LOG_BIN);
        reader.addTargetType(LogEntryType.LOG_IN_DELETE_INFO);

        int count = 0;
        while (reader.readNextEntry()) {
            count += 1;
        }
        assertEquals("Empty file should not have entries", 0, count);
    }

    /**
     * Run with defaults, read whole log
     */
    public void testBasic()
	throws IOException, DatabaseException {

        DbConfigManager cm = envImpl.getConfigManager();
        doTest(50,
               cm.getInt(EnvironmentParams.LOG_ITERATOR_READ_SIZE),
               0,
               false);
    }

    /**
     * Run with very small buffers and track node ids
     */
    public void testTracking()
	throws IOException, DatabaseException {

        doTest(50, // num iterations
               10, // tiny buffer
               0, // start lsn index
               true); // track node ids
    }

    /**
     * Start in the middle of the file
     */
    public void testMiddleStart()
	throws IOException, DatabaseException {

        doTest(50, 100, 40, true);
    }

    private void doTest(int numIters,
                        int bufferSize,
                        int startLsnIndex,
                        boolean trackNodeIds)
        throws IOException, DatabaseException {

        /* Fill up a fake log file. */
        createLogFile(numIters);

        /* Decide where to start. */
        long startLsn = DbLsn.NULL_LSN;
        int checkIndex = 0;
        if (startLsnIndex >= 0) {
            startLsn = ((CheckInfo) checkList.get(startLsnIndex)).lsn;
            checkIndex = startLsnIndex;
        }

        /* Use an empty utilization map for testing tracking. */
        Map fileSummaryLsns = trackNodeIds ? (new HashMap()) : null;

        INFileReader reader = new INFileReader(envImpl,
                                               bufferSize,
                                               startLsn,
					       DbLsn.NULL_LSN,
                                               trackNodeIds,
					       false,
                                               DbLsn.NULL_LSN,
                                               fileSummaryLsns);
        reader.addTargetType(LogEntryType.LOG_IN);
        reader.addTargetType(LogEntryType.LOG_BIN);
        reader.addTargetType(LogEntryType.LOG_BIN_DELTA);
        reader.addTargetType(LogEntryType.LOG_IN_DELETE_INFO);

        /* Read. */
        checkLogFile(reader, checkIndex, trackNodeIds);
    }

    /**
     * Write a logfile of entries, then read the end
     */
    private void createLogFile(int numIters)
	throws IOException, DatabaseException {

        /*
         * Create a log file full of INs, INDeleteInfo, BINDeltas and
         * Debug Records
         */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "foo", dbConfig);
        LogManager logManager = envImpl.getLogManager();
        maxNodeId = 0;

        checkList = new ArrayList();

        for (int i = 0; i < numIters; i++) {
            /* Add a debug record. */
            Tracer rec = new Tracer("Hello there, rec " + (i + 1));
            rec.log(logManager);

            /* Create, log, and save an IN. */
            byte[] data = new byte[i + 1];
            Arrays.fill(data, (byte) (i + 1));

            byte[] key = new byte[i + 1];
            Arrays.fill(key, (byte) (i + 1));

            IN in = new IN(DbInternal.dbGetDatabaseImpl(db), key, 5, 10);
	    in.latch(false);
            long lsn = in.log(logManager);
	    in.releaseLatch();
            checkList.add(new CheckInfo(lsn, in));

            if (DEBUG) {
                System.out.println("LSN " + i + " = " + lsn);
                System.out.println("IN " + i + " = " + in.getNodeId());
            }

            /* Add other types of INs. */
            BIN bin = new BIN(DbInternal.dbGetDatabaseImpl(db), key, 2, 1);
	    bin.latch(false);
            lsn = bin.log(logManager);
            checkList.add(new CheckInfo(lsn, bin));

            /* Add provisional entries, which should get ignored. */
            lsn = bin.log(logManager, 
        	          false, // allowDeltas,
        	          true,  // isProvisional,
        	          false, // proactiveMigration,
        	          false, // backgroundIO
        	          in);

	    bin.releaseLatch();

            /* Add a LN, to stress the node tracking. */
            LN ln = new LN(data);
            lsn = ln.log(envImpl,
                         DbInternal.dbGetDatabaseImpl(db).getId(),
                         key, DbLsn.NULL_LSN, 0, null, false);

            /* 
	     * Add an IN delete entry, it should get picked up by the reader.
	     */
            INDeleteInfo info =
                new INDeleteInfo(i, key, DbInternal.
				 dbGetDatabaseImpl(db).getId());
            lsn = logManager.log(
                   new SingleItemEntry(LogEntryType.LOG_IN_DELETE_INFO, info));
            checkList.add(new CheckInfo(lsn, info));

            /*
             * Add an BINDelta. Generate it by making the first, full version
             * provisional so the test doesn't pick it up, and then log a
             * delta.
             */
            BIN binDeltaBin =
		new BIN(DbInternal.dbGetDatabaseImpl(db), key, 10, 1);
            maxNodeId = binDeltaBin.getNodeId();
            binDeltaBin.latch();
            ChildReference newEntry =
                new ChildReference(null, key, DbLsn.makeLsn(0, 0));
            assertTrue(binDeltaBin.insertEntry(newEntry));

            lsn = binDeltaBin.log(logManager, 
        	            	  false, // allowDeltas,
        	          	  true,  // isProvisional,
        	          	  false, // proactiveMigration,
                                  false, // backgroundIO
        	          	  in);   // parent

            /* Modify the bin with one entry so there can be a delta. */

            byte[] keyBuf2 = new byte[2];
            Arrays.fill(keyBuf2, (byte) (i + 2));
            ChildReference newEntry2 =
                new ChildReference(null, keyBuf2,
                                   DbLsn.makeLsn(100, 101));
            assertTrue(binDeltaBin.insertEntry(newEntry2));

            assertTrue(binDeltaBin.log(logManager, 
        	                       true, // allowDeltas
        	                       false, // isProvisional
        	                       false, // proactiveMigration,
                                       false, // backgroundIO
        	                       in) ==
		       DbLsn.NULL_LSN);
            lsn = binDeltaBin.getLastDeltaVersion();
            if (DEBUG) {
                System.out.println("delta =" + binDeltaBin.getNodeId() +
                                   " at LSN " + lsn);
            }
            checkList.add(new CheckInfo(lsn, binDeltaBin));

            /* 
             * Reset the generation to 0 so this version of the BIN, which gets
             * saved for unit test comparison, will compare to the version read
             * from the log, which is initialized to 0.
             */
            binDeltaBin.setGeneration(0);
            binDeltaBin.releaseLatch();
        }

        /* Flush the log, files. */
        logManager.flush();
        envImpl.getFileManager().clear();
    }

    private void checkLogFile(INFileReader reader,
                              int checkIndex,
                              boolean checkMaxNodeId)
        throws IOException, DatabaseException {

        try {
            /* Read all the INs. */
            int i = checkIndex;

            while (reader.readNextEntry()) {
                if (DEBUG) {
                    System.out.println("i = "
                                       + i
                                       + " reader.isDeleteInfo="
                                       + reader.isDeleteInfo()
                                       + " LSN = "
                                       + reader.getLastLsn());
                }

                CheckInfo check = (CheckInfo) checkList.get(i);

                if (reader.isDeleteInfo()) {
                    assertEquals(check.info.getDeletedNodeId(),
                                 reader.getDeletedNodeId());
                    assertTrue(Arrays.equals(check.info.getDeletedIdKey(),
                                             reader.getDeletedIdKey()));
                    assertTrue(check.info.getDatabaseId().equals
                               (reader.getDatabaseId()));

                } else {

                    /* 
		     * When comparing the check data against the data from the
		     * log, make the dirty bits match so that they compare
		     * equal.
                     */
                    IN inFromLog = reader.getIN();
		    inFromLog.latch(false);
                    inFromLog.setDirty(true);
		    inFromLog.releaseLatch();
                    IN testIN = check.in;
		    testIN.latch(false);
                    testIN.setDirty(true);
		    testIN.releaseLatch();

                    /*
                     * Only check the INs we created in the test. (The others
                     * are from the map db.
                     */
                    if (reader.getDatabaseId().
			equals(DbInternal.dbGetDatabaseImpl(db).getId())) {
                        // The IN should match
                        String inFromLogString = inFromLog.toString();
                        String testINString = testIN.toString();
                        if (DEBUG) {
                            System.out.println("testIN=" + testINString);
                            System.out.println("inFromLog=" + inFromLogString);
                        }

                        assertEquals("IN "
                                     + inFromLog.getNodeId()
                                     + " at index "
                                     + i
                                     + " should match.\nTestIN=" +
                                     testIN +
                                     "\nLogIN=" +
                                     inFromLog,
                                     testINString,
                                     inFromLogString);
                    }
                }
                /* The LSN should match. */
                assertEquals
		    ("LSN " + i + " should match",
		     check.lsn,
		     reader.getLastLsn());

                i++;
            }
            assertEquals(i, checkList.size());
            if (checkMaxNodeId) {
                assertEquals(maxNodeId, reader.getMaxNodeId());
            }
        } finally {
            db.close();
        }
    }

    private class CheckInfo {
        long lsn;
        IN in;
        INDeleteInfo info;

        CheckInfo(long lsn, IN in) {
            this.lsn = lsn;
            this.in = in;
            this.info = null;
        }

        CheckInfo(long lsn, INDeleteInfo info) {
            this.lsn = lsn;
            this.in = null;
            this.info = info;
        }
    }
}
