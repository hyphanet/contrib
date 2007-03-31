/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogManagerTest.java,v 1.68.2.1 2007/02/01 14:50:15 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;
  
/**
 * Test basic log management.
 */
public class LogManagerTest extends TestCase {

    static private final boolean DEBUG = false;

    private FileManager fileManager;
    private LogManager logManager;
    private File envHome;
    private EnvironmentImpl env;

    public LogManagerTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws DatabaseException, IOException  {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }
    
    public void tearDown()
	throws IOException, DatabaseException {

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Log and retrieve objects, with log in memory
     */
    public void testBasicInMemory()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_FILE_MAX.getName(), "1000");

        envConfig.setAllowCreate(true);
        env = new EnvironmentImpl(envHome, envConfig);
        fileManager = env.getFileManager();
        logManager = env.getLogManager();

        logAndRetrieve();
        env.close();
    }

    /**
     * Log and retrieve objects, with log completely flushed to disk
     */
    public void testBasicOnDisk()
	throws Throwable {

        try {

            /* 
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. Each file
             * only holds one test item (each test item is 50 bytes).
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	    DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam(
                            EnvironmentParams.LOG_MEM_SIZE.getName(),
			    EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam(
                            EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam(
                            EnvironmentParams.LOG_FILE_MAX.getName(), "79");
            envConfig.setConfigParam(
                            EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setConfigParam
		(EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
            
            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /* 
             * Don't run any daemons, those emit trace messages and other log
             * entries and mess up our accounting.
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);

            /* 
	     * Recreate the file manager and log manager w/different configs.
	     */
            env = new EnvironmentImpl(envHome, envConfig);
            fileManager = env.getFileManager();
            logManager = env.getLogManager();

            logAndRetrieve();

            /*
             * Expect 13 je files, 7 to hold logged records, 1 to hold root, 3
             * to hold recovery messages, 2 for checkpoint records
             */
            String[] names = fileManager.listFiles(FileManager.JE_SUFFIXES);
            assertEquals("Should be 13 files on disk", 13, names.length);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log and retrieve objects, with some of log flushed to disk, some of log
     * in memory.
     */
    public void testComboDiskMemory()
	throws Throwable {

        try {

            /* 
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. Each file
             * only holds one test item (each test item is 50 bytes)
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam
		(EnvironmentParams.LOG_MEM_SIZE.getName(),
		 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam
		(EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
            envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                     "142");
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     "6");
            
            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /* 
             * Don't run the cleaner or the checkpointer daemons, those create
             * more log entries and mess up our accounting
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);

            env = new EnvironmentImpl(envHome, envConfig);
            fileManager = env.getFileManager();
            logManager = env.getLogManager();

            logAndRetrieve();

            /* 
	     * Expect 8 je files, 3 for records, 1 for root, 2 for recovery
             * message, 2 for checkpoints.
	     */
            String[] names = fileManager.listFiles(FileManager.JE_SUFFIXES);
            assertEquals("Should be 8 files on disk", 8, names.length);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log and retrieve objects, with some of log flushed to disk, some
     * of log in memory. Force the read buffer to be very small
     */
    public void testFaultingIn()
	throws Throwable {

        try {

            /* 
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. We read in 32
             * byte chunks, will have to re-read only holds one test item (each
             * test item is 50 bytes)
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	    DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam
		(EnvironmentParams.LOG_MEM_SIZE.getName(),
		 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam
		(EnvironmentParams.LOG_FILE_MAX.getName(), "200");
            envConfig.setConfigParam
		(EnvironmentParams.LOG_FAULT_READ_SIZE.getName(), "32");
            envConfig.setConfigParam
		(EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setAllowCreate(true);
            env = new EnvironmentImpl(envHome, envConfig);
            fileManager = env.getFileManager();
            logManager = env.getLogManager();

            logAndRetrieve();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log several objects, retrieve them.
     */
    private void logAndRetrieve()
	throws DatabaseException {

        /* Make test loggable objects. */

        List testRecs = new ArrayList();
        for (int i = 0; i < 10; i++) {
            testRecs.add(new Tracer("Hello there, rec " + (i+1)));
        }

        /* Log three of them, remember their LSNs. */
        List testLsns = new ArrayList();

        for (int i = 0; i < 3; i++) {
            long lsn = ((Tracer)testRecs.get(i)).log(logManager);
            if (DEBUG) {
                System.out.println("i = " + i + " test LSN: file = " +
                                   DbLsn.getFileNumber(lsn) +
                                   " offset = " + 
                                   DbLsn.getFileOffset(lsn));
            }
            testLsns.add(new Long(lsn));
        }

        /* Ask for them back, out of order. */
        assertEquals((Tracer) testRecs.get(2),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(2))));
        assertEquals((Tracer) testRecs.get(0),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(0))));
        assertEquals((Tracer) testRecs.get(1),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(1))));

        /* Intersperse logging and getting. */
        testLsns.add(new Long(((Tracer)testRecs.get(3)).log(logManager)));
        testLsns.add(new Long(((Tracer)testRecs.get(4)).log(logManager)));

        assertEquals((Tracer) testRecs.get(2),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(2))));
        assertEquals((Tracer) testRecs.get(4),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(4))));

        /* Intersperse logging and getting. */
        testLsns.add(new Long(((Tracer)testRecs.get(5)).log(logManager)));
        testLsns.add(new Long(((Tracer)testRecs.get(6)).log(logManager)));
        testLsns.add(new Long(((Tracer)testRecs.get(7)).log(logManager)));

        assertEquals((Tracer) testRecs.get(7),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(7))));
        assertEquals((Tracer) testRecs.get(0),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(0))));
        assertEquals((Tracer) testRecs.get(6),
                     (Tracer) logManager.get
		     (DbLsn.longToLsn((Long) testLsns.get(6))));
    }

    private void turnOffDaemons(EnvironmentConfig envConfig) {
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CLEANER.getName(),
                      "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
                       "false");
    }

    /**
     * Log a few items, then hit exceptions. Make sure LSN state is correctly
     * maintained and that items logged after the exceptions are at the correct
     * locations on disk.
     */
    public void testExceptions()
	throws Throwable {
        
        int logBufferSize = ((int) EnvironmentParams.LOG_MEM_SIZE_MIN) / 3;
        int numLogBuffers = 5;
        int logBufferMemSize = logBufferSize * numLogBuffers;
        int logFileMax = 1000;
        int okCounter = 0;
        
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	    DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam(EnvironmentParams.LOG_MEM_SIZE.getName(),
                                     new Integer(logBufferMemSize).toString());
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), 
		 new Integer(numLogBuffers).toString());
            envConfig.setConfigParam
		(EnvironmentParams.LOG_FILE_MAX.getName(), 
		 new Integer(logFileMax).toString());
            envConfig.setConfigParam(
                            EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setConfigParam
		(EnvironmentParams.JE_LOGGING_LEVEL.getName(), "SEVERE");
            
            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /* 
             * Don't run any daemons, those emit trace messages and other log
             * entries and mess up our accounting.
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);
            env = new EnvironmentImpl(envHome, envConfig);
            fileManager = env.getFileManager();
            logManager = env.getLogManager();

            /* Keep track of items logged and their LSNs. */
            ArrayList testRecs = new ArrayList();
            ArrayList testLsns = new ArrayList();

            /*
             * Intersperse:
             * - log successfully
             * - log w/failure because the item doesn't fit in the log buffer
             * - have I/O failures writing out the log
             * Verify that all expected items can be read. Some will come 
             * from the log buffer pool.
             * Then close and re-open the environment, to verify that
             * all log items are faulted from disk
             */
            
            /* Successful log. */
            addOkayItem(logManager, okCounter++,
                        testRecs, testLsns, logBufferSize);
            
            /* Item that's too big for the log buffers. */
            attemptTooBigItem(logManager, logBufferSize, testRecs, testLsns);

            /* Successful log. */
            addOkayItem(logManager, okCounter++,
                        testRecs, testLsns, logBufferSize);
            
            /* 
             * This verify read the items from the log buffers. Note before SR
             * #12638 existed (LSN state not restored properly after exception
             * because of too-small log buffer), this verify hung.
             */
            verifyOkayItems(logManager, testRecs, testLsns, true);

            /* More successful logs, along with a few too-big items. */

            for (;okCounter < 23; okCounter++) {
                addOkayItem(logManager, okCounter, testRecs,
                            testLsns, logBufferSize);

                if ((okCounter % 4) == 0) {
                    attemptTooBigItem(logManager, logBufferSize,
				      testRecs, testLsns);
                }
                /* 
                 * If we verify in the loop, sometimes we'll read from disk and
                 * sometimes from the log buffer pool.
                 */
                verifyOkayItems(logManager, testRecs, testLsns, true);
            }

            /* 
             * Test the case where we flip files and write the old write buffer
             * out before we try getting a log buffer for the new item. We need
             * to
             * 
             * - hit a log-too-small exceptin
             * - right after, we need to log an item that is small enough
             *   to fit in the log buffer but big enough to require that
             *   we flip to a new file.
             */
            long nextLsn = fileManager.getNextLsn();
            long fileOffset = DbLsn.getFileOffset(nextLsn);

            assertTrue((logFileMax - fileOffset ) < logBufferSize);
            attemptTooBigItem(logManager, logBufferSize, testRecs, testLsns);
            addOkayItem(logManager, okCounter++,
                        testRecs, testLsns, logBufferSize,
                        ((int)(logFileMax - fileOffset)));
            verifyOkayItems(logManager, testRecs, testLsns, true);
            
            /* Invoke some i/o exceptions. */
            for (;okCounter < 50; okCounter++) {
                attemptIOException(logManager, fileManager, testRecs,
                                   testLsns, false);
                addOkayItem(logManager, okCounter,
                            testRecs, testLsns, logBufferSize);
                verifyOkayItems(logManager, testRecs, testLsns, false);
            }

            /* 
             * Finally, close this environment and re-open, and read all
             * expected items from disk.
             */
            env.close();
            envConfig.setAllowCreate(false);
            env = new EnvironmentImpl(envHome, envConfig);
            fileManager = env.getFileManager();
            logManager = env.getLogManager();
            verifyOkayItems(logManager, testRecs, testLsns, false);

            /* Check that we read these items off disk. */
            EnvironmentStats stats = new EnvironmentStats();
            StatsConfig config = new StatsConfig();
            logManager.loadStats(config, stats);
            assertTrue(stats.getNNotResident() >= testRecs.size());

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    private void addOkayItem(LogManager logManager,
                             int tag,
                             List testRecs,
                             List testLsns,
                             int logBufferSize,
                             int fillerLen) 
        throws DatabaseException {

        String filler = new String(new byte[fillerLen]);
        Tracer t = new Tracer("okay" + filler + tag );
        assertTrue(logBufferSize > t.getLogSize());
        testRecs.add(t);
        long lsn = t.log(logManager);
        testLsns.add(new Long(lsn));
    }

    private void addOkayItem(LogManager logManager,
                             int tag,
                             List testRecs,
                             List testLsns,
                             int logBufferSize) 
        throws DatabaseException {

        addOkayItem(logManager, tag, testRecs, testLsns, logBufferSize, 0);
    }

    private void attemptTooBigItem(LogManager logManager,
                                   int logBufferSize,
                                   Tracer big,
				   List testRecs,
				   List testLsns) {
        assertTrue(big.getLogSize() > logBufferSize);

        try {
            long lsn = big.log(logManager);
	    testLsns.add(new Long(lsn));
	    testRecs.add(big);
        } catch (DatabaseException expected) {
            fail("Should not have hit exception.");
        }
    }
    private void attemptTooBigItem(LogManager logManager,
                                   int logBufferSize,
				   List testRecs,
				   List testLsns) {
        String stuff = "12345679890123456798901234567989012345679890";
	while (stuff.length() < EnvironmentParams.LOG_MEM_SIZE_MIN) {
	    stuff += stuff;
	}
        Tracer t = new Tracer(stuff);
        attemptTooBigItem(logManager, logBufferSize, t, testRecs, testLsns);
    }

    private void attemptIOException(LogManager logManager, 
                                    FileManager fileManager,
                                    List testRecs,
                                    List testLsns,
                                    boolean forceFlush) {
        Tracer t = new Tracer("ioException");
        FileManager.IO_EXCEPTION_TESTING = true;
        try {

            /*
             * This object might get flushed to disk -- depend on whether
             * the ioexception happened before or after the copy into the
             * log buffer. Both are valid, but the test doesn't yet
             * know how to differentiate the cases.

               testLsns.add(new Long(fileManager.getNextLsn()));
               testRecs.add(t);
            */
            logManager.logForceFlush(
                           new SingleItemEntry(LogEntryType.LOG_TRACE, t),
                           true);
            fail("expect io exception");
        } catch (DatabaseException expected) {
        } finally {
            FileManager.IO_EXCEPTION_TESTING = false;
        }
    }

    private void verifyOkayItems(LogManager logManager,
                                 ArrayList testRecs,
                                 ArrayList testLsns,
                                 boolean checkOrder) 
        throws DatabaseException {

        /* read forwards. */
        for (int i = 0; i < testRecs.size(); i++) {
            assertEquals((Tracer) testRecs.get(i),
                         (Tracer) logManager.get
                         (DbLsn.longToLsn((Long) testLsns.get(i))));

        }

        /* Make sure LSNs are adjacent */
        assertEquals(testLsns.size(), testRecs.size());

        if (checkOrder) {

            /* 
	     * TODO: sometimes an ioexception entry will make it into the write
	     * buffer, and sometimes it won't. It depends on whether the IO
	     * exception was thrown when before or after the logabble item was
	     * written into the buffer.  I haven't figure out yet how to tell
	     * the difference, so for now, we don't check order in the portion
	     * of the test that issues IO exceptions.
             */
            for (int i = 1; i < testLsns.size(); i++) {
            
                long lsn = ((Long) testLsns.get(i)).longValue();
                long lsnFile = DbLsn.getFileNumber(lsn);
                long lsnOffset = DbLsn.getFileOffset(lsn);
                long prevLsn = ((Long) testLsns.get(i-1)).longValue();
                long prevFile = DbLsn.getFileNumber(prevLsn);
                long prevOffset = DbLsn.getFileOffset(prevLsn);
                if (prevFile == lsnFile) {
                    assertEquals("item " + i + "prev = " +
                                 DbLsn.toString(prevLsn) +
                                 " current=" + DbLsn.toString(lsn), 
                                 (((Tracer) testRecs.get(i-1)).getLogSize() + 
                                  LogEntryHeader.MIN_HEADER_SIZE),
                                 lsnOffset - prevOffset);
                } else {
                    assertEquals(prevFile+1, lsnFile);
                    assertEquals(FileManager.firstLogEntryOffset(),
                                 lsnOffset);
                }
            }
        }

        /* read backwards. */
        for (int i = testRecs.size() - 1; i > -1; i--) {
            assertEquals((Tracer) testRecs.get(i),
                         (Tracer) logManager.get
                         (DbLsn.longToLsn((Long) testLsns.get(i))));

        }
    }
}
