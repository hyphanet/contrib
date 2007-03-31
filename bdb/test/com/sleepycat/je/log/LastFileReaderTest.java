/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LastFileReaderTest.java,v 1.67.2.1 2007/02/01 14:50:14 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.txn.TxnAbort;
import com.sleepycat.je.util.BadFileFilter;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

public class LastFileReaderTest extends TestCase {

    private DbConfigManager configManager;
    private FileManager fileManager;
    private LogManager logManager;
    private File envHome;
    private EnvironmentImpl envImpl;
    public LastFileReaderTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws DatabaseException, IOException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        TestUtils.removeFiles(envHome, new BadFileFilter());
    }

    public void tearDown()
        throws DatabaseException, IOException {

        /*
         * Pass false to skip checkpoint, since the file manager may hold an
         * open file that we've trashed in the tests, so we don't want to
         * write to it here.
         */
        try {
            envImpl.close(false);
        } catch (DatabaseException e) {
        }

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
        TestUtils.removeFiles(envHome, new BadFileFilter());
    }

    /* Create an environment, using the default log file size. */
    private void initEnv() 
        throws DatabaseException {

        initEnv(null);
    }

    /* Create an environment, specifying the log file size. */
    private void initEnv(String logFileSize) 
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();

        /* Don't run daemons; we do some abrupt shutdowns. */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");

        envConfig.setConfigParam
	    (EnvironmentParams.NODE_MAX.getName(), "6");
	envConfig.setConfigParam
	    (EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
        if (logFileSize != null) {
	    DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam
                (EnvironmentParams.LOG_FILE_MAX.getName(), logFileSize);
        }

        /* Disable noisy UtilizationProfile database creation. */
        DbInternal.setCreateUP(envConfig, false);
        /* Don't checkpoint utilization info for this test. */
        DbInternal.setCheckpointUP(envConfig, false);

	envConfig.setAllowCreate(true);
        envImpl = new EnvironmentImpl(envHome, envConfig);
        configManager = envImpl.getConfigManager();
        fileManager = envImpl.getFileManager();
        logManager = envImpl.getLogManager();
    }

    /**
     * Run with an empty file that has a file header but no log entries.
     */
    public void testEmptyAtEnd()
        throws Throwable {

        initEnv();

        /* 
         * Make a log file with a valid header, but no data.
         */
        FileManagerTestUtils.createLogFile(fileManager, envImpl, 100);
        fileManager.clear();

        LastFileReader reader = new LastFileReader(envImpl, 1000);
        assertTrue(reader.readNextEntry());
        assertEquals(0, DbLsn.getFileOffset(reader.getLastLsn()));
    }

    /**
     * Run with an empty, 0 length file at the end.  This has caused a
     * BufferUnderflowException. [#SR 12631]
     */
    public void testLastFileEmpty()
        throws Throwable {

        initEnv("1000");
        int numIters = 10;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();

        /* 
         * Create a log with one or more files. Use only Tracer objects so we
         * can iterate through the entire log ... ?
         */
        for (int i = 0; i < numIters; i++) {
            /* Add a debug record. */
            Tracer msg = new Tracer("Hello there, rec " + (i+1));
            testObjs.add(msg);
            testLsns.add(new Long(msg.log(logManager)));
        }
        /* Flush the log, files. */
	logManager.flush();
        fileManager.clear();

        int lastFileNum = fileManager.getAllFileNumbers().length - 1;        

        /* 
         * Create an extra, totally empty file.
         */
        fileManager.syncLogEnd();
        fileManager.clear();
        String emptyLastFile = fileManager.getFullFileName(lastFileNum+1,
                                                      FileManager.JE_SUFFIX);

        RandomAccessFile file =
            new RandomAccessFile(emptyLastFile, FileManager.FileMode.
                                 READWRITE_MODE.getModeValue());
        file.close();

        assertTrue(fileManager.getAllFileNumbers().length >= 2);

        /* 
         * Try a LastFileReader. It should give us a end-of-log position in the
         * penultimate file.
         */
        LastFileReader reader = new LastFileReader(envImpl, 1000);
        while (reader.readNextEntry()) {
        }

        /* 
         * The reader should be positioned at the last, valid file, skipping
         * this 0 length file.
         */
        assertEquals("lastValid=" + DbLsn.toString(reader.getLastValidLsn()),
                     lastFileNum,
                     DbLsn.getFileNumber(reader.getLastValidLsn()));
        assertEquals(lastFileNum, DbLsn.getFileNumber(reader.getEndOfLog()));
    }

    /**
     * Corrupt the file headers of the one and only log file.
     */
    public void testBadFileHeader()
	throws Throwable {

        initEnv();

        /* 
         * Handle a log file that has data and a bad header. First corrupt the
         * existing log file. We will not be able to establish log end, but
         * won't throw away the file because it has data.
         */
        long lastFileNum = fileManager.getLastFileNum().longValue();
        String lastFile =
            fileManager.getFullFileName(lastFileNum,
                                        FileManager.JE_SUFFIX);

        RandomAccessFile file =
            new RandomAccessFile(lastFile, FileManager.FileMode.
                                 READWRITE_MODE.getModeValue());

        file.seek(15);
        file.writeBytes("putting more junk in, mess up header");
        file.close();

        /*
         * We should see an exception on this one, because we made a file that
         * looks like it has a bad header and bad data.
         */
        try {
            LastFileReader reader = new LastFileReader(envImpl, 1000);
            fail("Should see exception when creating " + reader);
        } catch (DbChecksumException e) {
            /* Eat exception, expected. */
        }

        /*
         * Now make a bad file header, but one that is less than the size of a
         * file header. This file ought to get moved aside.
         */
        file = new RandomAccessFile(lastFile, "rw");
        file.getChannel().truncate(0);
        file.writeBytes("bad");
        file.close();

        LastFileReader reader = new LastFileReader(envImpl, 1000);
        /* Nothing comes back from reader. */
        assertFalse(reader.readNextEntry()); 
        File movedFile = new File(envHome, "00000000.bad");
        assertTrue(movedFile.exists());

        /* Try a few more times, we ought to keep moving the file. */
        file = new RandomAccessFile(lastFile, "rw");
        file.getChannel().truncate(0);
        file.writeBytes("bad");
        file.close();

        reader = new LastFileReader(envImpl, 1000);
        assertTrue(movedFile.exists());
        File movedFile1 = new File(envHome, "00000000.bad.1");
        assertTrue(movedFile1.exists());
    }

    /**
     * Run with defaults.
     */
    public void testBasic()
        throws Throwable {

        initEnv();
        int numIters = 50;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();

        fillLogFile(numIters, testLsns, testObjs);
        LastFileReader reader =
            new LastFileReader(envImpl,
                               configManager.getInt
                               (EnvironmentParams.LOG_ITERATOR_READ_SIZE));

        checkLogEnd(reader, numIters, testLsns, testObjs);
    }

    /**
     * Run with very small read buffer.
     */
    public void testSmallBuffers()
        throws Throwable {

        initEnv();
        int numIters = 50;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();

        fillLogFile(numIters, testLsns, testObjs);
        LastFileReader reader = new LastFileReader(envImpl, 10);
        checkLogEnd(reader, numIters, testLsns, testObjs);
    }

    /**
     * Run with medium buffers.
     */
    public void testMedBuffers()
        throws Throwable {

        initEnv();
        int numIters = 50;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();

        fillLogFile(numIters, testLsns, testObjs);
        LastFileReader reader = new LastFileReader(envImpl, 100);
        checkLogEnd(reader, numIters, testLsns, testObjs);
    }

    /**
     * Put junk at the end of the file.
     */
    public void testJunk()
        throws Throwable {

        initEnv();
        int numIters = 50;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();

        /* Write junk into the end of the file. */
        fillLogFile(numIters, testLsns, testObjs);
        long lastFileNum = fileManager.getLastFileNum().longValue();
        String lastFile =
            fileManager.getFullFileName(lastFileNum,
                                        FileManager.JE_SUFFIX);

        RandomAccessFile file =
            new RandomAccessFile(lastFile, FileManager.FileMode.
                                 READWRITE_MODE.getModeValue());
        file.seek(file.length());
        file.writeBytes("hello, some junk");
        file.close();


        /* Read. */
        LastFileReader reader = new LastFileReader(envImpl, 100);
        checkLogEnd(reader, numIters, testLsns, testObjs);
    }

    /**
     * Make a log, then make a few extra files at the end, one empty, one with
     * a bad file header.
     */
    public void testExtraEmpty()
        throws Throwable {

        initEnv();
        int numIters = 50;
        List testObjs = new ArrayList();
        List testLsns = new ArrayList();
        int defaultBufferSize = 
            configManager.getInt(EnvironmentParams.LOG_ITERATOR_READ_SIZE);

        /* 
         * Make a valid log with data, then put a couple of extra files after
         * it. Make the file numbers non-consecutive. We should have three log
         * files.
         */
        /* Create a log */
        fillLogFile(numIters, testLsns, testObjs);

        /* First empty log file -- header, no data. */
        fileManager.bumpLsn(100000000);
        fileManager.bumpLsn(100000000);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, 10);

        /* Second empty log file -- header, no data. */
        fileManager.bumpLsn(100000000);
        fileManager.bumpLsn(100000000);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, 10);

        assertEquals(3, fileManager.getAllFileNumbers().length);

        /* 
         * Corrupt the last empty file and then search for the correct last
         * file.
         */
        long lastFileNum = fileManager.getLastFileNum().longValue();
        String lastFile =
            fileManager.getFullFileName(lastFileNum,
                                        FileManager.JE_SUFFIX);
        RandomAccessFile file =
            new RandomAccessFile(lastFile, FileManager.FileMode.
                                 READWRITE_MODE.getModeValue());
        file.getChannel().truncate(10);
        file.close();
        fileManager.clear();

        /* 
         * Make a reader, read the log. After the reader returns, we should
         * only have 2 log files.
         */
        LastFileReader reader = new LastFileReader(envImpl,
                                                   defaultBufferSize);
        checkLogEnd(reader, numIters, testLsns, testObjs);
        assertEquals(2, fileManager.getAllFileNumbers().length);

        /* 
         * Corrupt the now "last" empty file and try again. This is actually
         * the first empty file we made.
         */
        lastFileNum = fileManager.getLastFileNum().longValue();
        lastFile = fileManager.getFullFileName(lastFileNum,
                                               FileManager.JE_SUFFIX);
        file = new RandomAccessFile(lastFile, FileManager.FileMode.
                                    READWRITE_MODE.getModeValue());
        file.getChannel().truncate(10);
        file.close();

        /* 
         * Validate that we have the right number of log entries, and only one
         * valid log file.
         */
        reader = new LastFileReader(envImpl, defaultBufferSize);
        checkLogEnd(reader, numIters, testLsns, testObjs);
        assertEquals(1, fileManager.getAllFileNumbers().length);
    }


    /**
     * Write a logfile of entries, then read the end.
     */
    private void fillLogFile(int numIters, List testLsns, List testObjs)
        throws Throwable {

        /*
         * Create a log file full of LNs and Debug Records.
         */
        for (int i = 0; i < numIters; i++) {
            /* Add a debug record. */
            Tracer msg = new Tracer("Hello there, rec " + (i+1));
            testObjs.add(msg);
            testLsns.add(new Long(msg.log(logManager)));

            /* Add a txn abort */
            TxnAbort abort = new TxnAbort(10L, 200L);
            SingleItemEntry entry =
                new SingleItemEntry(LogEntryType.LOG_TXN_ABORT, abort);
            testObjs.add(abort);
            testLsns.add(new Long(logManager.log(entry))); 
        }

        /* Flush the log, files. */
	logManager.flush();
        fileManager.clear();
    }

    /**
     * Use the LastFileReader to check this file, see if the log end is set
     * right.
     */
    private void checkLogEnd(LastFileReader reader,
			     int numIters, 
                             List testLsns,
			     List testObjs)
        throws Throwable {

        reader.setTargetType(LogEntryType.LOG_ROOT);
        reader.setTargetType(LogEntryType.LOG_TXN_COMMIT);
        reader.setTargetType(LogEntryType.LOG_TXN_ABORT);
        reader.setTargetType(LogEntryType.LOG_TRACE);
        reader.setTargetType(LogEntryType.LOG_IN);
        reader.setTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);

        /* Now ask the LastFileReader to read it back. */
        while (reader.readNextEntry()) {
        }
        
        /* Truncate the file. */
        reader.setEndOfFile();

        /* 
	 * How many entries did the iterator go over? We should see
	 *   numIters * 2 + 7
	 * (the extra 7 are the root, debug records and checkpoints and file
	 * header written by recovery.
	 */
        assertEquals("should have seen this many entries", (numIters * 2) + 7,
                     reader.getNumRead());

        /* Check last used LSN. */
        int numLsns = testLsns.size();
        long lastLsn = DbLsn.longToLsn((Long) testLsns.get(numLsns - 1));
        assertEquals("last LSN", lastLsn, reader.getLastLsn());

        /* Check last offset. */
        assertEquals("prev offset", DbLsn.getFileOffset(lastLsn),
                     reader.getPrevOffset());

        /* Check next available LSN. */
        int lastSize =
            ((Loggable)testObjs.get(testObjs.size()-1)).getLogSize();
        assertEquals("next available",
                     DbLsn.makeLsn(DbLsn.getFileNumber(lastLsn),
				   DbLsn.getFileOffset(lastLsn) +
				   LogEntryHeader.MIN_HEADER_SIZE + lastSize),
                     reader.getEndOfLog());

        /* The log should be truncated to just the right size. */
        FileHandle handle =  fileManager.getFileHandle(0L);
        RandomAccessFile file = handle.getFile();
        assertEquals(DbLsn.getFileOffset(reader.getEndOfLog()),
                     file.getChannel().size());
        handle.release();
        fileManager.clear();

        /* Check the last tracked LSNs. */
        assertTrue(reader.getLastSeen(LogEntryType.LOG_ROOT) !=
		   DbLsn.NULL_LSN);
        assertTrue(reader.getLastSeen(LogEntryType.LOG_IN) == DbLsn.NULL_LSN);
        assertTrue(reader.getLastSeen(LogEntryType.LOG_LN_TRANSACTIONAL) ==
		   DbLsn.NULL_LSN);
        assertEquals(reader.getLastSeen(LogEntryType.LOG_TRACE),
                     DbLsn.longToLsn((Long) testLsns.get(numLsns-2)));
        assertEquals(reader.getLastSeen(LogEntryType.LOG_TXN_ABORT),
                     lastLsn);
    }
}
