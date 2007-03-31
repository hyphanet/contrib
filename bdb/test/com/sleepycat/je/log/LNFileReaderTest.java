/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LNFileReaderTest.java,v 1.86.2.1 2007/02/01 14:50:14 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * Test the LNFileReader
 */
public class LNFileReaderTest extends TestCase {
    static private final boolean DEBUG = false;

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private List checkList;

    public LNFileReaderTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        /*
         * Note that we use the official Environment class to make the
         * environment, so that everything is set up, but we then go a backdoor
         * route to get to the underlying EnvironmentImpl class so that we
         * don't require that the Environment.getDbEnvironment method be
         * unnecessarily public.
         */
        TestUtils.removeLogFiles("Setup", envHome, false);
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
	envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_FILE_MAX.getName(), "1024");
        envConfig.setAllowCreate(true);
	envConfig.setTransactional(true);
        env = new Environment(envHome, envConfig);

        envImpl = DbInternal.envGetEnvironmentImpl(env);
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
        LNFileReader reader =
            new LNFileReader(envImpl,
                             1000,             // read buffer size
                             DbLsn.NULL_LSN,   // start lsn
                             true,             // redo
                             DbLsn.NULL_LSN,   // end of file lsn
                             DbLsn.NULL_LSN,   // finish lsn
                             null);            // single file
        reader.addTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);
        reader.addTargetType(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
        assertFalse("Empty file should not have entries",
                    reader.readNextEntry());
    }

    /**
     * Run with an empty file.
     */
    public void testEmpty()
        throws IOException, DatabaseException {

        /* Make a log file with a valid header, but no data. */
        FileManager fileManager = envImpl.getFileManager();
        FileManagerTestUtils.createLogFile(fileManager, envImpl, 1000);
        fileManager.clear();

        LNFileReader reader =
            new LNFileReader(envImpl, 
                             1000,             // read buffer size
                             DbLsn.NULL_LSN,   // start lsn
                             true,             // redo
                             DbLsn.NULL_LSN,   // end of file lsn
                             DbLsn.NULL_LSN,   // finish lsn
                             null);            // single file
        reader.addTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);
        reader.addTargetType(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
        assertFalse("Empty file should not have entries",
                    reader.readNextEntry());
    }

    /**
     * Run with defaults, read whole log for redo, going forwards.
     */
    public void testBasicRedo()
        throws Throwable {

        try {
            DbConfigManager cm =  envImpl.getConfigManager();
            doTest(50,
                   cm.getInt(EnvironmentParams.LOG_ITERATOR_READ_SIZE),
                   0,
                   false,
                   true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Run with defaults, read whole log for undo, going backwards.
     */
    public void testBasicUndo()
        throws Throwable {

        try {
            DbConfigManager cm =  envImpl.getConfigManager();
            doTest(50, 
                   cm.getInt(EnvironmentParams.LOG_ITERATOR_READ_SIZE),
                   0,
                   false,
                   false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Run with very small read buffer for redo, and track LNs.
     */
    public void testSmallBuffersRedo()
        throws IOException, DatabaseException {

        doTest(50, 10, 0, true, true);
    }

    /**
     * Run with very small read buffer for undo and track LNs.
     */
    public void testSmallBuffersUndo()
        throws IOException, DatabaseException {

        doTest(50, 10, 0, true, false);
    }


    /**
     * Run with medium buffers for redo.
     */
    public void testMedBuffersRedo()
        throws IOException, DatabaseException {

        doTest(50, 100, 0, false, true);
    }

    /**
     * Run with medium buffers for undo.
     */
    public void testMedBuffersUndo()
        throws IOException, DatabaseException {

        doTest(50, 100, 0, false, false);
    }

    /**
     * Start in the middle of the file for redo.
     */
    public void testMiddleStartRedo()
        throws IOException, DatabaseException {

        doTest(50, 100, 20, true, true);
    }

    /**
     * Start in the middle of the file for undo.
     */
    public void testMiddleStartUndo()
        throws IOException, DatabaseException {

        doTest(50, 100, 20, true, false);
    }

    /**
     * Create a log file, create the reader, read the log file
     * @param numIters each iteration makes 3 log entries (debug record, ln
     *           and mapLN
     * @param bufferSize to pass to reader
     * @param checkIndex where in the test data to start
     * @param trackLNs true if we're tracking LNS, false if we're tracking 
     *           mapLNs
     */
    private void doTest(int numIters,
			int bufferSize,
			int checkIndex,
                        boolean trackLNs,
			boolean redo)
        throws IOException, DatabaseException {

        checkList = new ArrayList();

        /* Fill up a fake log file. */
        long endOfFileLsn = createLogFile(numIters, trackLNs, redo);

        if (DEBUG) {
            System.out.println("eofLsn = " + endOfFileLsn);
        }

        /* Decide where to start. */
        long startLsn = DbLsn.NULL_LSN;
        long finishLsn = DbLsn.NULL_LSN;
        if (redo) {
            startLsn = ((CheckInfo) checkList.get(checkIndex)).lsn;
        } else {
            /* Going backwards. Start at last check entry. */
            int lastEntryIdx = checkList.size() - 1;
            startLsn = ((CheckInfo) checkList.get(lastEntryIdx)).lsn;
            finishLsn = ((CheckInfo) checkList.get(checkIndex)).lsn;
        }

        LNFileReader reader =
	    new LNFileReader(envImpl, bufferSize, startLsn, redo, endOfFileLsn,
			     finishLsn, null);
        if (trackLNs) {
            reader.addTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);
            reader.addTargetType(LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
        } else {
            reader.addTargetType(LogEntryType.LOG_MAPLN_TRANSACTIONAL);
        }

        if (!redo) {
            reader.addTargetType(LogEntryType.LOG_TXN_COMMIT);
        }
        
        /* read. */
        checkLogFile(reader, checkIndex, redo);
    }

    /**
     * Write a logfile of entries, put the entries that we expect to
     * read into a list for later verification.
     * @return end of file LSN.
     */
    private long createLogFile(int numIters, boolean trackLNs, boolean redo)
        throws IOException, DatabaseException {

        /*
         * Create a log file full of LNs, DeletedDupLNs, MapLNs and Debug
         * Records
         */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "foo", dbConfig);
        LogManager logManager = envImpl.getLogManager();

        long lsn;
        Txn userTxn = new Txn(envImpl, new TransactionConfig());
        long txnId = userTxn.getId();

        for (int i = 0; i < numIters; i++) {
            /* Add a debug record just to be filler. */
            Tracer rec = new Tracer("Hello there, rec " + (i+1));
            rec.log(logManager);
            
            /* Make a transactional LN, we expect it to be there. */
            byte[] data = new byte[i+1];
            Arrays.fill(data, (byte)(i+1));
            LN ln = new LN(data);
            byte[] key = new byte[i+1];
            Arrays.fill(key, (byte)(i+10));
            
            /* 
	     * Log an LN. If we're tracking LNs add it to the verification
	     * list.
	     */
            userTxn.lock
                (ln.getNodeId(), LockType.WRITE, false,
                 DbInternal.dbGetDatabaseImpl(db));
            lsn = ln.log(envImpl,
                         DbInternal.dbGetDatabaseImpl(db).getId(),
                         key,
                         DbLsn.NULL_LSN,
                         0,
                         userTxn,
                         false);

            if (trackLNs) {
                checkList.add(new CheckInfo(lsn, ln, key,
                                            ln.getData(), txnId));
            }

            /* Log a deleted duplicate LN. */
            LN deleteLN = new LN(data);
            byte[] dupKey = new byte[i+1];
            Arrays.fill(dupKey, (byte)(i+2));

            userTxn.lock
                (deleteLN.getNodeId(), LockType.WRITE, false,
                 DbInternal.dbGetDatabaseImpl(db));
            lsn = deleteLN.delete(DbInternal.dbGetDatabaseImpl(db),
                                  key, 
                                  dupKey,
                                  DbLsn.NULL_LSN,
                                  userTxn);
            if (trackLNs) {
                checkList.add(new CheckInfo(lsn, deleteLN,
                                            dupKey, key, txnId));
            }

            /* 
	     * Make a non-transactional LN. Shouldn't get picked up by reader.
	     */
            LN nonTxnalLN = new LN(data);
            nonTxnalLN.log(envImpl,
			   DbInternal.dbGetDatabaseImpl(db).getId(),
			   key, DbLsn.NULL_LSN, 0, null, false);

            /* Add a MapLN. */
            MapLN mapLN = new MapLN(DbInternal.dbGetDatabaseImpl(db));
            userTxn.lock
                (mapLN.getNodeId(), LockType.WRITE, false,
                 DbInternal.dbGetDatabaseImpl(db));
            lsn = mapLN.log(envImpl,
                            DbInternal.dbGetDatabaseImpl(db).getId(),
                            key, DbLsn.NULL_LSN, 0, userTxn, false);
            if (!trackLNs) {
                checkList.add(new CheckInfo(lsn, mapLN, key,
                                            mapLN.getData(),
                                            txnId));
            }
        }

        long commitLsn = userTxn.commit(Txn.TXN_SYNC);

        /* We only expect checkpoint entries to be read in redo passes. */
        if (!redo) {
            checkList.add(new CheckInfo(commitLsn, null, null, null, txnId));
        }

        /* Make a marker log entry to pose as the end of file. */
        Tracer rec = new Tracer("Pretend this is off the file");
        long lastLsn = rec.log(logManager);
        db.close();
        logManager.flush();
        envImpl.getFileManager().clear();
        return lastLsn;
    }


    private void checkLogFile(LNFileReader reader,
                              int checkIndex,
                              boolean redo)
        throws IOException, DatabaseException {

        LN lnFromLog;
        byte[] keyFromLog;
                
        /* Read all the LNs. */
        int i;
        if (redo) {
            /* start where indicated. */
            i = checkIndex;
        } else {
            /* start at the end. */
            i = checkList.size() - 1;
        }
        while (reader.readNextEntry()) {
            CheckInfo expected = (CheckInfo) checkList.get(i);

            /* Check LSN. */
            assertEquals("LSN " + i + " should match",
                         expected.lsn,
                         reader.getLastLsn());

            if (reader.isLN()) {

                /* Check the LN. */
                lnFromLog = reader.getLN();
                LN expectedLN = expected.ln;
                assertEquals("Should be the same type of object",
                             expectedLN.getClass(),
                             lnFromLog.getClass());

                if (DEBUG) {
                    if (!expectedLN.toString().equals(lnFromLog.toString())) {
                        System.out.println("expected = " +
                                           expectedLN.toString()+
                                           "lnFromLog = " +
                                           lnFromLog.toString());
                    }
                }
                assertEquals("LN " + i + " should match",
                             expectedLN.toString(),
                             lnFromLog.toString());

                /* Check the key. */
                keyFromLog = reader.getKey();
                byte[] expectedKey = expected.key;
                if (DEBUG) {
                    if (!Arrays.equals(expectedKey, keyFromLog)) {
                        System.out.println("expectedKey=" + expectedKey +
                                           " logKey=" + keyFromLog);
                    }
                }
                    
                assertTrue("Key " + i + " should match",
                           Arrays.equals(expectedKey, keyFromLog));
                
                /* Check the dup key. */
                byte[] dupKeyFromLog = reader.getDupTreeKey();
                byte[] expectedDupKey = expected.dupKey;
                assertTrue(Arrays.equals(expectedDupKey, dupKeyFromLog));

                assertEquals(expected.txnId,
                             reader.getTxnId().longValue());
                
            } else {
                /* Should be a txn commit record. */
                assertEquals(expected.txnId,
                             reader.getTxnCommitId());
            }

            if (redo) {
                i++;
            } else {
                i--;
            }
        }
        int expectedCount = checkList.size() - checkIndex;
        assertEquals(expectedCount, reader.getNumRead());
    }

    private class CheckInfo {
        long lsn;
        LN ln;
        byte[] key;
        byte[] dupKey;
        long txnId;
        
        CheckInfo(long lsn, LN ln, byte[] key, byte[] dupKey, long txnId) {
            this.lsn = lsn;
            this.ln = ln;
            this.key = key;
            this.dupKey = dupKey;
            this.txnId = txnId;
        }
    }
}
