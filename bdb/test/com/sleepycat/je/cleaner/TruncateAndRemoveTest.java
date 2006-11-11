/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: TruncateAndRemoveTest.java,v 1.14 2006/09/12 19:17:14 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.nio.ByteBuffer;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.DumpFileReader;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;

public class TruncateAndRemoveTest extends TestCase {

    private static final String DB_NAME1 = "foo";
    private static final String DB_NAME2 = "bar";
    private static final long RECORD_COUNT = 100;

    private static final CheckpointConfig FORCE_CHECKPOINT =
        new CheckpointConfig();
    static {
        FORCE_CHECKPOINT.setForce(true);
    }
   
    private static final boolean DEBUG = false;

    private File envHome;
    private Environment env;
    private Database db;
    private JUnitThread junitThread;

    public TruncateAndRemoveTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    public void tearDown()
        throws IOException, DatabaseException {
  
        if (junitThread != null) {
            while (junitThread.isAlive()) {
                junitThread.interrupt();
                Thread.yield();
            }
            junitThread = null;
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
        envHome = null;
    }

    /**
     * Opens the environment.
     */
    private void openEnv(boolean transactional)
        throws DatabaseException {

        EnvironmentConfig config = TestUtils.initEnvConfig();
        config.setTransactional(transactional);
        config.setAllowCreate(true);
        /* Do not run the daemons since they interfere with LN counting. */
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        config.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");

        /* Use small nodes to test the post-txn scanning. */
        config.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(), "10");
        config.setConfigParam
            (EnvironmentParams.NODE_MAX_DUPTREE.getName(), "10");

        /* Use small files to ensure that there is cleaning. */
        config.setConfigParam("je.cleaner.minUtilization", "90");
        DbInternal.disableParameterValidation(config);
        config.setConfigParam("je.log.fileMax", "4000");

        env = new Environment(envHome, config);
    }

    /**
     * Opens that database.
     */
    private void openDb(Transaction useTxn, String dbName)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        EnvironmentConfig envConfig = env.getConfig();
        dbConfig.setTransactional(envConfig.getTransactional());
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(useTxn, dbName, dbConfig);
    }

    /**
     * Closes the environment and database.
     */
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
     * Test that truncate generates the right number of obsolete LNs.
     */
    public void testTruncate()
        throws Exception {

        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;

        Transaction txn = env.beginTransaction(null, null);
        truncate(txn, true);
        ObsoleteCounts beforeCommit = getObsoleteCounts();
        txn.commit();

        verifyUtilization(beforeCommit, 
                          RECORD_COUNT + 2, // LNs, + MapLN + previous NameLN
                          15);              // 1 root, 2 INs, 12 BINs

        closeEnv();
        batchCleanAndVerify(saveId);
    }

    /**
     * Test that aborting truncate generates the right number of obsolete LNs.
     */
    public void testTruncateAbort()
        throws Exception {

        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        db.close();
        db = null;

        Transaction txn = env.beginTransaction(null, null);
        truncate(txn, true);
        ObsoleteCounts beforeAbort = getObsoleteCounts();
        txn.abort();

        /* 
         * The obsolete count should include the records inserted after
         * the truncate.
         */
        verifyUtilization(beforeAbort,
                          /* 1 new nameLN, 2 copies of MapLN for new db */
                           3, 
                           0); 

        /* Reopen, db should be populated. */
        openDb(null, DB_NAME1);
        assertEquals(RECORD_COUNT, countRecords(null));
        closeEnv();
    }

    /**
     * Test that aborting truncate generates the right number of obsolete LNs.
     */
    public void testTruncateRepopulateAbort()
        throws Exception {

        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        db.close();
        db = null;

        Transaction txn = env.beginTransaction(null, null);
        truncate(txn, true);

        /* populate the database with some more records. */
        openDb(txn, DB_NAME1);
        writeAndCountRecords(txn, RECORD_COUNT/4);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;
        ObsoleteCounts beforeAbort = getObsoleteCounts();
        txn.abort();

        /* 
         * The obsolete count should include the records inserted after
         * the truncate.
         */
        verifyUtilization(beforeAbort,
                          /* newly inserted LNs, 1 new nameLN, 
                           * 2 copies of MapLN for new db */
                          (RECORD_COUNT/4) + 3, 
                          5); 

        /* Reopen, db should be populated. */
        openDb(null, DB_NAME1);
        assertEquals(RECORD_COUNT, countRecords(null));

        closeEnv();
        batchCleanAndVerify(saveId);
    }

    /**
     * Test that remove generates the right number of obsolete LNs.
     */
    public void testRemove()
        throws Exception {

        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;

        Transaction txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME1);
        ObsoleteCounts beforeCommit = getObsoleteCounts();
        txn.commit();

        verifyUtilization(beforeCommit,
                          /* LNs + old NameLN, old MapLN, delete MapLN */
                          RECORD_COUNT + 3,
                          15);

        openDb(null, DB_NAME1);
        assertEquals(0, countRecords(null));

        closeEnv();
        batchCleanAndVerify(saveId);
    }

    /**
     * Test that remove generates the right number of obsolete LNs.
     */
    public void testNonTxnalRemove()
        throws Exception {

        openEnv(false);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;
        ObsoleteCounts beforeOperation = getObsoleteCounts();
        env.removeDatabase(null, DB_NAME1);

        verifyUtilization(beforeOperation,
                          /* LNs + new NameLN, old NameLN, old MapLN, delete
                             MapLN */
                          RECORD_COUNT + 4,
                          15);

        openDb(null, DB_NAME1);
        assertEquals(0, countRecords(null));

        closeEnv();
        batchCleanAndVerify(saveId);
    }

    /**
     * Test that aborting remove generates the right number of obsolete LNs.
     */
    public void testRemoveAbort()
        throws Exception {

        /* Create database, populate, remove, abort the remove. */
        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        db.close();
        db = null;
        Transaction txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME1);
        ObsoleteCounts beforeAbort = getObsoleteCounts();
        txn.abort();

        verifyUtilization(beforeAbort, 0, 0);

        /* All records should be there. */
        openDb(null, DB_NAME1);
        assertEquals(RECORD_COUNT, countRecords(null));

        closeEnv();

        /* 
         * Batch clean and then check the record count again, just to make sure
         * we don't lose any valid data.
         */
        openEnv(true);
        while (env.cleanLog() > 0) {
        }
        CheckpointConfig force = new CheckpointConfig();
        force.setForce(true);
        env.checkpoint(force);
        closeEnv();

        openEnv(true);
        openDb(null, DB_NAME1);
        assertEquals(RECORD_COUNT, countRecords(null));
        closeEnv();
    }

    /**
     * Test that we can properly account for a non-resident database.
     */
    public void testRemoveNotResident()
        throws Exception {

        /* Create a database, populate. */
        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;
        env.close();
        env = null;

        /* 
         * Open the environment and remove the database. The
         * database is not resident at all.
         */
        openEnv(true);
        Transaction txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME1);
        ObsoleteCounts beforeCommit = getObsoleteCounts();
        txn.commit();

        verifyUtilization(beforeCommit,
                          /* LNs + old NameLN, old MapLN, delete MapLN */
                          RECORD_COUNT + 3,
                          /* 15 IN for data tree, 
                             2 for re-logged INs */
                          15 + 2);

        /* check record count. */
        openDb(null, DB_NAME1);
        assertEquals(0, countRecords(null));

        closeEnv();
        batchCleanAndVerify(saveId);
    }

    /**
     * Test that we can properly account for partially resident tree.
     */
    public void testRemovePartialResident()
        throws Exception {

        /* Create a database, populate. */
        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndCountRecords(null, RECORD_COUNT);
        DatabaseId saveId = DbInternal.dbGetDatabaseImpl(db).getId();
        db.close();
        db = null;
        env.close();
        env = null;

        /* 
         * Open the environment and remove the database. Pull 1 BIN in.
         */
        openEnv(true);
        openDb(null, DB_NAME1);
        Cursor c = db.openCursor(null, null);
        assertEquals(OperationStatus.SUCCESS,
                     c.getFirst(new DatabaseEntry(), new DatabaseEntry(), 
                                LockMode.DEFAULT));
        c.close();
        db.close();
        db = null;

        Transaction txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME1);
        ObsoleteCounts beforeCommit = getObsoleteCounts();
        txn.commit();

        verifyUtilization(beforeCommit,
                          /* LNs + old NameLN, old MapLN, delete MapLN */
                          RECORD_COUNT + 3,
                          /* 15 IN for data tree, 2 for file summary db */
                          15 + 2);

        /* check record count. */
        openDb(null, DB_NAME1);
        assertEquals(0, countRecords(null));

        closeEnv();
        batchCleanAndVerify(saveId);
    }
  
    /**
     * Tests that a log file is not deleted by the cleaner when it contains
     * entries in a database that is pending deletion.
     */
    public void testDBPendingDeletion()
        throws DatabaseException, InterruptedException {

        doDBPendingTest(RECORD_COUNT, false /*deleteAll*/, 7);
    }

    /**
     * Like testDBPendingDeletion but creates a scenario where only a single
     * log file is cleaned, and that log file contains only known obsolete
     * log entries.  This reproduced a bug where we neglected to adding
     * pending deleted DBs to the cleaner's pending DB set if all entries in
     * the log file were known obsoleted. [#13333]
     */
    public void testObsoleteLogFile()
        throws DatabaseException, InterruptedException {

        doDBPendingTest(40, true /*deleteAll*/, 1);
    }
    
    private void doDBPendingTest(long recordCount,
                                 boolean deleteAll,
                                 int expectFilesCleaned)
        throws DatabaseException, InterruptedException {

        /* Create a database, populate, close. */
        Set logFiles = new HashSet();
        openEnv(true);
        openDb(null, DB_NAME1);
        writeAndMakeWaste(recordCount, logFiles, deleteAll);
        long remainingRecordCount = deleteAll ? 0 : recordCount;
        env.checkpoint(FORCE_CHECKPOINT);
        ObsoleteCounts obsoleteCounts = getObsoleteCounts();
        DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db); 
        db.close();
        db = null;
        assertTrue(!dbImpl.isDeleteFinished());
        assertTrue(!dbImpl.isDeleted());

        /* Make sure that we wrote a full file's worth of LNs. */
        assertTrue(logFiles.size() >= 3);
        assertTrue(logFilesExist(logFiles));

        /* Remove the database but do not commit yet. */
        final Transaction txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME1);

        /* The obsolete count should be <= 1 (for the NameLN). */
        obsoleteCounts = verifyUtilization(obsoleteCounts, 1, 0);

        junitThread = new JUnitThread("Committer") {
            public void testBody() 
                throws DatabaseException {
                try {
                    txn.commit();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };

        /*
         * Set a hook to cause the commit to block.  The commit is done in a
         * separate thread.  The commit will set the DB state to pendingDeleted
         * and will then wait for the hook to return.
         */
        final Object lock = new Object();

        dbImpl.setPendingDeletedHook(new TestHook() {
            public void doIOHook()
                throws IOException {
                throw new UnsupportedOperationException();
            }
            public void doHook() {
                synchronized (lock) {
                    try {
                        lock.notify();
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e.toString());
                    }
                }
            }
            public Object getHookValue() {
        	return null;
            }
        });

        /* Start the committer thread; expect the pending deleted state. */
        synchronized (lock) {
            junitThread.start();
            lock.wait();
        }
        assertTrue(!dbImpl.isDeleteFinished());
        assertTrue(dbImpl.isDeleted());

        /* Expect obsolete LNs: NameLN */
        obsoleteCounts = verifyUtilization(obsoleteCounts, 1, 0);

        /* The DB deletion is pending; the log file should still exist. */
        int filesCleaned = env.cleanLog();
        assertEquals(expectFilesCleaned, filesCleaned);
        assertTrue(filesCleaned > 0);
        env.checkpoint(FORCE_CHECKPOINT);
        env.checkpoint(FORCE_CHECKPOINT);
        assertTrue(logFilesExist(logFiles));

        /*
         * When the commiter thread finishes, the DB deletion will be
         * complete and the DB state will change to deleted.
         */
        synchronized (lock) {
            lock.notify();
        }
        try {
            junitThread.finishTest();
            junitThread = null;
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        }
        assertTrue(dbImpl.isDeleteFinished());
        assertTrue(dbImpl.isDeleted());

        /* Expect obsolete LNs: recordCount + MapLN + FSLNs (apprx). */
        verifyUtilization(obsoleteCounts, remainingRecordCount + 6, 0);

        /* The DB deletion is complete; the log file should be deleted. */
        env.checkpoint(FORCE_CHECKPOINT);
        env.checkpoint(FORCE_CHECKPOINT);
        assertTrue(!logFilesExist(logFiles));
    }

    private void writeAndCountRecords(Transaction txn, long count)
        throws DatabaseException {

        for (int i = 1; i <= count; i += 1) {
            DatabaseEntry entry = new DatabaseEntry(TestUtils.getTestArray(i));

            db.put(txn, entry, entry);
        }

        /* Insert and delete some records, insert and abort some records. */
        DatabaseEntry entry =
            new DatabaseEntry(TestUtils.getTestArray((int)count+1));
        db.put(txn, entry, entry);
        db.delete(txn, entry);

        EnvironmentConfig envConfig = env.getConfig();
        if (envConfig.getTransactional()) {
            entry = new DatabaseEntry(TestUtils.getTestArray(0));
            Transaction txn2 = env.beginTransaction(null, null);
            db.put(txn2, entry, entry);
            txn2.abort();
            txn2 = null;
        }

        assertEquals(count, countRecords(txn));
    }

    /**
     * Writes the specified number of records to db.  Check the number of
     * records, and return the number of obsolete records.  Returns a set of
     * the file numbers that are written to.
     *
     * Makes waste (obsolete records):  If doDelete=true, deletes records as
     * they are added; otherwise does updates to produce obsolete records
     * interleaved with non-obsolete records.
     */
    private void writeAndMakeWaste(long count,
                                   Set logFilesWritten,
                                   boolean doDelete)
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        for (int i = 0; i < count; i += 1) {
            DatabaseEntry entry = new DatabaseEntry(TestUtils.getTestArray(i));
            cursor.put(entry, entry);
            /* Add log file written. */
            long file = CleanerTestUtils.getLogFile(this, cursor);
            logFilesWritten.add(new Long(file));
            /* Make waste. */
            if (!doDelete) {
                cursor.put(entry, entry);
                cursor.put(entry, entry);
            }
        }
        if (doDelete) {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status;
            for (status = cursor.getFirst(key, data, null);
                 status == OperationStatus.SUCCESS;
                 status = cursor.getNext(key, data, null)) {
                /* Make waste. */
                cursor.delete();
                /* Add log file written. */
                long file = CleanerTestUtils.getLogFile(this, cursor);
                logFilesWritten.add(new Long(file));
            }
        }
        cursor.close();
        txn.commit();
        assertEquals(doDelete ? 0 : count, countRecords(null));
    }

    /* Truncate database and check the count. */
    private void truncate(Transaction useTxn,
                          boolean getCount)
        throws DatabaseException {

        long nTruncated = env.truncateDatabase(useTxn, DB_NAME1, getCount);

        if (getCount) {
            assertEquals(RECORD_COUNT, nTruncated);
        }

        assertEquals(0, countRecords(useTxn));
    }
                          
    /**
     * Returns how many records are in the database.
     */
    private int countRecords(Transaction useTxn)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        if (db == null) {
            openDb(useTxn, DB_NAME1);
        }
        Cursor cursor = db.openCursor(useTxn, null);
        try {
            int count = 0;
            OperationStatus status = cursor.getFirst(key, data, null);
            while (status == OperationStatus.SUCCESS) {
                count += 1;
                status = cursor.getNext(key, data, null);
            }
            return count;
        } finally {
            cursor.close();
        }
    }

    /**
     * Return the total number of obsolete node counts according to the
     * UtilizationProfile and UtilizationTracker.
     */
    private ObsoleteCounts getObsoleteCounts()
        throws DatabaseException {

        FileSummary[] files = (FileSummary[])
            DbInternal.envGetEnvironmentImpl(env)
                      .getUtilizationProfile()
                      .getFileSummaryMap(true)
                      .values().toArray(new FileSummary[0]);
        int lnCount = 0;
        int inCount = 0;
        for (int i = 0; i < files.length; i += 1) {
            lnCount += files[i].obsoleteLNCount;
            inCount += files[i].obsoleteINCount;
        }

        return new ObsoleteCounts(lnCount, inCount);
    }

    private class ObsoleteCounts {
        int obsoleteLNs;
        int obsoleteINs;

        ObsoleteCounts(int obsoleteLNs, int obsoleteINs) {
            this.obsoleteLNs = obsoleteLNs;
            this.obsoleteINs = obsoleteINs;
        }

        public String toString() {
            return "lns=" + obsoleteLNs + " ins=" + obsoleteINs;
        }
    }

    /* 
     * Check obsolete counts. If the expected IN count is negative, don't
     * check.
     */
    private ObsoleteCounts verifyUtilization(ObsoleteCounts prev,
                                             long expectedLNs,
                                             int expectedINs) 
        throws DatabaseException {
	
        ObsoleteCounts now = getObsoleteCounts();
        String beforeAndAfter = "before: " + prev + " now: " + now;
        if (DEBUG) {
            System.out.println(beforeAndAfter);
        }

        assertEquals(beforeAndAfter, expectedLNs,
                     now.obsoleteLNs - prev.obsoleteLNs);
        if (expectedINs > 0) {
            assertEquals(beforeAndAfter, expectedINs,
                         now.obsoleteINs - prev.obsoleteINs);
        }

        return now;
    }

    /**
     * Returns true if all files exist, or false if any file is deleted.
     */
    private boolean logFilesExist(Set fileNumbers) {

        Iterator iter = fileNumbers.iterator();
        while (iter.hasNext()) {
            long fileNum = ((Long) iter.next()).longValue();
            File file = new File
                (envHome,
                 FileManager.getFileName(fileNum, FileManager.JE_SUFFIX));
            if (!file.exists()) {
                return false;
            }
        }
        return true;
    }

    /*
     * Run batch cleaning and verify that there are no files with these
     * log entries.
     */ 
    private void batchCleanAndVerify(DatabaseId dbId) 
        throws Exception {

        /* 
         * Open the environment, flip the log files to reduce mixing of new
         * records and old records and add more records to force the
         * utilization level of the removed records down.
         */
        openEnv(true);
        openDb(null, DB_NAME2);
        long lsn = DbInternal.envGetEnvironmentImpl(env).forceLogFileFlip();
        CheckpointConfig force = new CheckpointConfig();
        force.setForce(true);
        env.checkpoint(force);

        writeAndCountRecords(null, RECORD_COUNT * 3);
        env.checkpoint(force);

        db.close();
        db = null;

        /* Check log files, there should be entries with this database. */
        CheckReader checker = new CheckReader(env, dbId, true);
        while (checker.readNextEntry()) {
        }

        if (DEBUG) {
            System.out.println("entries for this db =" + checker.getCount());
        }

        assertTrue(checker.getCount() > 0);

        /* batch clean. */
        boolean anyCleaned = false;
        while (env.cleanLog() > 0) {
            anyCleaned = true;
        }

        assertTrue(anyCleaned);

        if (anyCleaned) {
            env.checkpoint(force);
        }

        /* Check log files, there should be no entries with this database. */
        checker = new CheckReader(env, dbId, false);
        while (checker.readNextEntry()) {
        }

        closeEnv();
        
    }

    class CheckReader extends DumpFileReader{

        private DatabaseId dbId;
        private boolean expectEntries;
        private int count;

        /*
         * @param databaseId we're looking for log entries for this database.
         * @param expectEntries if false, there should be no log entries
         * with this database id. If true, the log should have entries
         * with this database id.
         */
        CheckReader(Environment env, DatabaseId dbId, boolean expectEntries)
            throws DatabaseException, IOException {
            
            super(DbInternal.envGetEnvironmentImpl(env),
        	  1000, DbLsn.NULL_LSN, DbLsn.NULL_LSN,
                  null, null, false);
            this.dbId = dbId;
            this.expectEntries = expectEntries;
        }
        
        protected boolean processEntry(ByteBuffer entryBuffer) 
            throws DatabaseException {
            
            /* Figure out what kind of log entry this is */
            LogEntryType lastEntryType =
                LogEntryType.findType(currentEntryTypeNum,
                                      currentEntryTypeVersion);
            boolean isNode = LogEntryType.isNodeType(currentEntryTypeNum,
                                                     currentEntryTypeVersion);


            /* Read the entry. */
            LogEntry entry = lastEntryType.getSharedLogEntry();
            entry.readEntry(entryBuffer, currentEntrySize,
                            currentEntryTypeVersion, true);

            long lsn = getLastLsn();
            if (isNode) {
                boolean found = false;
                if (entry instanceof INLogEntry) {
                    INLogEntry inEntry = (INLogEntry) entry;
                    found = dbId.equals(inEntry.getDbId());
                } else {
                    LNLogEntry lnEntry = (LNLogEntry) entry;
                    found = dbId.equals(lnEntry.getDbId());
                }
                if (found) {
                    if (expectEntries) {
                        count++;
                    } else {
                        StringBuffer sb = new StringBuffer();
                        entry.dumpEntry(sb, false);
                        fail("lsn=" + DbLsn.getNoFormatString(lsn) +
                             " dbId = " + dbId +
                             " entry= " + sb.toString());
                    }
                }
            }


            return true;
        }

        /* Num entries with this database id seen by reader. */
        int getCount() {
            return count;
        }
    }
}
