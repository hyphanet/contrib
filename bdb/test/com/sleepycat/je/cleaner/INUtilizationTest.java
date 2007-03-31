/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INUtilizationTest.java,v 1.21.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;

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
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.SearchFileReader;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.DbLsn;

public class INUtilizationTest extends TestCase {

    private static final String DB_NAME = "foo";

    private static final CheckpointConfig forceConfig = new CheckpointConfig();
    static {
        forceConfig.setForce(true);
    }

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private Transaction txn;
    private Cursor cursor;
    private boolean dups = false;
    private DatabaseEntry keyEntry = new DatabaseEntry();
    private DatabaseEntry dataEntry = new DatabaseEntry();

    public INUtilizationTest() {
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
                
        try {
            //*
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
            //*/
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        envHome = null;
        env = null;
        envImpl = null;
        db = null;
        txn = null;
        cursor = null;
        keyEntry = null;
        dataEntry = null;
    }

    /**
     * Opens the environment and database.
     */
    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig config = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(config);
        config.setTransactional(true);
        config.setTxnNoSync(true);
        config.setAllowCreate(true);
        /* Do not run the daemons. */
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        config.setConfigParam
	    (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        /* Use a tiny log file size to write one node per file. */
        config.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                              Integer.toString(64));
        env = new Environment(envHome, config);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        /* Speed up test that uses lots of very small files. */
        envImpl.getFileManager().setSyncAtFileEnd(false);

        openDb();
    }

    /**
     * Opens the database.
     */
    private void openDb()
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        db = env.openDatabase(null, DB_NAME, dbConfig);
    }

    private void closeEnv(boolean doCheckpoint)
        throws DatabaseException {

        closeEnv(doCheckpoint,
                 true,  // expectAccurateObsoleteLNCount
                 true); // expectAccurateObsoleteLNSize
    }

    private void closeEnv(boolean doCheckpoint,
                          boolean expectAccurateObsoleteLNCount)
        throws DatabaseException {

        closeEnv(doCheckpoint,
                 expectAccurateObsoleteLNCount,
                 expectAccurateObsoleteLNCount);
    }

    /**
     * Closes the environment and database.
     *
     * @param expectAccurateObsoleteLNCount should be false if
     * performPartialCheckpoint was called previously.
     */
    private void closeEnv(boolean doCheckpoint,
                          boolean expectAccurateObsoleteLNCount,
                          boolean expectAccurateObsoleteLNSize)
        throws DatabaseException {

        /*
         * Verify utilization using UtilizationFileReader.
         * expectAccurateObsoleteLNCount is false if performPartialCheckpoint
         * was called previously -- see CleanerTestUtils.verifyUtilization.
         * expectAccurateObsoleteLNSize is false for truncate and remove tests.
         */
        CleanerTestUtils.verifyUtilization
            (envImpl, expectAccurateObsoleteLNCount,
             expectAccurateObsoleteLNSize);

        if (db != null) {
            db.close();
            db = null;
        }
        if (envImpl != null) {
            envImpl.close(doCheckpoint);
            envImpl = null;
            env = null;
        }
    }

    /**
     * Initial setup for all tests -- open env, put one record (or two for
     * dups) and sync.
     */
    private void openAndWriteDatabase()
        throws DatabaseException {

        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Put one record. */
        IntegerBinding.intToEntry(0, keyEntry);
        IntegerBinding.intToEntry(0, dataEntry);
        cursor.put(keyEntry, dataEntry);

        /* Add a duplicate but move cursor back to the first record. */
        if (dups) {
            IntegerBinding.intToEntry(1, dataEntry);
            cursor.put(keyEntry, dataEntry);
            cursor.getFirst(keyEntry, dataEntry, null);
        }

        /* Checkpoint to the root so nothing is dirty. */
        env.sync();

        /* Expect that BIN and parent IN files are not obsolete. */
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);
    }

    /**
     * Tests that BIN and IN utilization counting works.
     */
    public void testBasic()
        throws DatabaseException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Update to make BIN dirty. */
        cursor.put(keyEntry, dataEntry);

        /* Checkpoint */
        env.checkpoint(forceConfig);

        if (!dups) {
            /* After checkpoint, expect BIN file is obsolete but not IN. */
            expectObsolete(binFile, true);
            expectObsolete(inFile, false);
            assertTrue(binFile != getBINFile(cursor));
            assertEquals(inFile, getINFile(cursor));

            /* After second checkpoint, IN file becomes obsolete also. */
            env.checkpoint(forceConfig);
        }

        /* Both BIN and IN are obsolete. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);
        assertTrue(binFile != getBINFile(cursor));
        assertTrue(inFile != getINFile(cursor));

        /* Expect that new files are not obsolete. */
        long binFile2 = getBINFile(cursor);
        long inFile2 = getINFile(cursor);
        expectObsolete(binFile2, false);
        expectObsolete(inFile2, false);

        cursor.close();
        txn.commit();
        closeEnv(true);
    }

    /**
     * Performs testBasic with duplicates.
     */
    public void testBasicDup()
        throws DatabaseException {

        dups = true;
        testBasic();
    }

    /**
     * Similar to testBasic, but logs INs explicitly and performs recovery to
     * ensure utilization recovery works.
     */
    public void testRecovery()
        throws DatabaseException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Close normally and reopen. */
        cursor.close();
        txn.commit();
        closeEnv(true);
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect BIN and IN files have not changed. */
        assertEquals(binFile, getBINFile(cursor));
        assertEquals(inFile, getINFile(cursor));
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);

        /*
         * Log explicitly since we have no way to do a partial checkpoint.
         * The BIN is logged provisionally and the IN non-provisionally.
         */
        TestUtils.logBINAndIN(env, cursor);

        /* Expect to obsolete the BIN and IN. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);
        assertTrue(binFile != getBINFile(cursor));
        assertTrue(inFile != getINFile(cursor));

        /* Save current BIN and IN files. */
        long binFile2 = getBINFile(cursor);
        long inFile2 = getINFile(cursor);
        expectObsolete(binFile2, false);
        expectObsolete(inFile2, false);

        /* Shutdown without a checkpoint and reopen. */
        cursor.close();
        txn.commit();
        closeEnv(false);
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect that recovery counts BIN and IN as obsolete. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);
        assertTrue(binFile != getBINFile(cursor));
        assertTrue(inFile != getINFile(cursor));

        /*
         * Even though it is provisional, expect that current BIN is not
         * obsolete because it is not part of partial checkpoint.  This is
         * similar to what happens with a split.  The current IN is not
         * obsolete either (nor is it provisional).
         */
        assertTrue(binFile2 == getBINFile(cursor));
        assertTrue(inFile2 == getINFile(cursor));
        expectObsolete(binFile2, false);
        expectObsolete(inFile2, false);

        /* Update to make BIN dirty. */
        cursor.put(keyEntry, dataEntry);

        /* Check current BIN and IN files. */
        assertTrue(binFile2 == getBINFile(cursor));
        assertTrue(inFile2 == getINFile(cursor));
        expectObsolete(binFile2, false);
        expectObsolete(inFile2, false);

        /* Close normally and reopen to cause checkpoint of dirty BIN/IN. */
        cursor.close();
        txn.commit();
        closeEnv(true);
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect BIN and IN were checkpointed during close. */
        assertTrue(binFile2 != getBINFile(cursor));
        assertTrue(inFile2 != getINFile(cursor));
        expectObsolete(binFile2, true);
        expectObsolete(inFile2, true);

        cursor.close();
        txn.commit();
        closeEnv(true);
    }

    /**
     * Performs testRecovery with duplicates.
     */
    public void testRecoveryDup()
        throws DatabaseException {

        dups = true;
        testRecovery();
    }

    /**
     * Tests that in a partial checkpoint (CkptStart with no CkptEnd) all
     * provisional INs are counted as obsolete.
     */
    public void testPartialCheckpoint()
        throws DatabaseException, IOException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Close with partial checkpoint and reopen. */
        cursor.close();
        txn.commit();
        performPartialCheckpoint(true); // truncateFileSummariesAlso
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect BIN and IN files have not changed. */
        assertEquals(binFile, getBINFile(cursor));
        assertEquals(inFile, getINFile(cursor));
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);

        /* Update to make BIN dirty. */
        cursor.put(keyEntry, dataEntry);

        /* Check current BIN and IN files. */
        assertTrue(binFile == getBINFile(cursor));
        assertTrue(inFile == getINFile(cursor));
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);

        /* Close with partial checkpoint and reopen. */
        cursor.close();
        txn.commit();
        performPartialCheckpoint(true,   // truncateFileSummariesAlso
                                 false); // expectAccurateObsoleteLNCount
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect BIN and IN files are obsolete. */
        assertTrue(binFile != getBINFile(cursor));
        assertTrue(inFile != getINFile(cursor));
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        /*
         * Expect that the current BIN is obsolete because it was provisional,
         * and provisional nodes following CkptStart are counted obsolete
         * even if that is sometimes incorrect.  The parent IN file is not
         * obsolete if dups are not configured, because it is not provisonal;
         * it is provisional if dups are configured, because we dirty farther
         * up the tree.
         */
        long binFile2 = getBINFile(cursor);
        long inFile2 = getINFile(cursor);
        expectObsolete(binFile2, true);
        expectObsolete(inFile2, dups);

        /*
         * Now repeat the test above but do not truncate the FileSummaryLNs.
         * The counting will be accurate because the FileSummaryLNs override
         * what is counted manually during recovery.
         */

        /* Update to make BIN dirty. */
        cursor.put(keyEntry, dataEntry);

        /* Close with partial checkpoint and reopen. */
        cursor.close();
        txn.commit();
        performPartialCheckpoint(false,  // truncateFileSummariesAlso
                                 false); // expectAccurateObsoleteLNCount
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* The prior BIN and IN files are now double-counted as obsolete. */
        assertTrue(binFile2 != getBINFile(cursor));
        assertTrue(inFile2 != getINFile(cursor));
        expectObsolete(binFile2, 2);
        expectObsolete(inFile2, dups ? 2 : 1);

        /* Expect current BIN and IN files are not obsolete. */
        binFile2 = getBINFile(cursor);
        inFile2 = getINFile(cursor);
        expectObsolete(binFile2, false);
        expectObsolete(inFile2, false);

        cursor.close();
        txn.commit();
        closeEnv(true,   // doCheckpoint
                 false); // expectAccurateObsoleteLNCount
    }

    /**
     * Performs testPartialCheckpoint with duplicates.
     */
    public void testPartialCheckpointDup()
        throws DatabaseException, IOException {

        dups = true;
        testPartialCheckpoint();
    }

    /**
     * Tests that deleting a subtree (by deleting the last LN in a BIN) is
     * counted correctly.
     */
    public void testDelete()
        throws DatabaseException, IOException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Close normally and reopen. */
        cursor.close();
        txn.commit();
        closeEnv(true);
        openEnv();
        txn = env.beginTransaction(null, null);
        cursor = db.openCursor(txn, null);

        /* Position cursor to load BIN and IN. */
        cursor.getSearchKey(keyEntry, dataEntry, null);

        /* Expect BIN and IN are still not obsolete. */
        assertEquals(binFile, getBINFile(cursor));
        assertEquals(inFile, getINFile(cursor));
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);

        if (dups) {
            /* Delete both records. */
            cursor.delete();
            cursor.getNext(keyEntry, dataEntry, null);
            cursor.delete();
        } else {

            /*
             * Add records until we move to the next BIN, so that the
             * compressor would not need to delete the root in order to delete
             * the BIN (deleting the root is not configured by default).
             */
            int keyVal = 0;
            while (binFile == getBINFile(cursor)) {
                keyVal += 1;
                IntegerBinding.intToEntry(keyVal, keyEntry);
                cursor.put(keyEntry, dataEntry);
            }
            binFile = getBINFile(cursor);
            inFile = getINFile(cursor);

            /* Delete all records in the last BIN. */
            while (binFile == getBINFile(cursor)) {
                cursor.delete();
                cursor.getLast(keyEntry, dataEntry, null);
            }
        }

        /* Compressor daemon is not running -- they're not obsolete yet. */
        expectObsolete(binFile, false);
        expectObsolete(inFile, false);

        /* Close cursor and compress. */
        cursor.close();
        txn.commit();
        env.compress();

        /*
         * Now expect BIN and IN to be obsolete. 
         */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        /* Close with partial checkpoint and reopen. */
        performPartialCheckpoint(true); // truncateFileSummariesAlso
        openEnv();

        /*
         * Expect both files to be obsolete after recovery, since the IN will
         * have been dirtied and rewritten.
         */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        closeEnv(true,   // doCheckpoint
                 false); // expectAccurateObsoleteLNCount
    }

    /**
     * Performs testDelete with duplicates.
     */
    public void testDeleteDup()
        throws DatabaseException, IOException {

        dups = true;
        testDelete();
    }

    /**
     * Tests that truncating a database is counted correctly.
     * Tests recovery also.
     * @deprecated use of Database.truncate
     */
    public void testTruncate()
        throws DatabaseException, IOException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Close normally and reopen. */
        cursor.close();
        txn.commit();
        closeEnv(true,   // doCheckpoint
                 true,   // expectAccurateObsoleteLNCount
                 false); // expectAccurateObsoleteLNSize
        openEnv();

        /* Truncate. */
        txn = env.beginTransaction(null, null);
        db.truncate(txn, false);
        txn.commit();

        /* Expect BIN and IN are obsolete. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        /* Close with partial checkpoint and reopen. */
        performPartialCheckpoint(true,   // truncateFileSummariesAlso
                                 true,   // expectAccurateObsoleteLNCount
                                 false); // expectAccurateObsoleteLNSize
        openEnv();

        /* Expect BIN and IN are counted obsolete during recovery. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        closeEnv(true,   // doCheckpoint
                 false); // expectAccurateObsoleteLNCount
    }

    /**
     * Tests that truncating a database is counted correctly.
     * Tests recovery also.
     */
    public void testRemove()
        throws DatabaseException, IOException {

        openAndWriteDatabase();
        long binFile = getBINFile(cursor);
        long inFile = getINFile(cursor);

        /* Close normally and reopen. */
        cursor.close();
        txn.commit();
        closeEnv(true,   // doCheckpoint
                 true,   // expectAccurateObsoleteLNCount
                 false); // expectAccurateObsoleteLNSize
        openEnv();

        /* Remove. */
        db.close();
        db = null;
        txn = env.beginTransaction(null, null);
        env.removeDatabase(txn, DB_NAME);
        txn.commit();

        /* Expect BIN and IN are obsolete. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        /* Close with partial checkpoint and reopen. */
        performPartialCheckpoint(true,   // truncateFileSummariesAlso
                                 true,   // expectAccurateObsoleteLNCount
                                 false); // expectAccurateObsoleteLNSize
        openEnv();

        /* Expect BIN and IN are counted obsolete during recovery. */
        expectObsolete(binFile, true);
        expectObsolete(inFile, true);

        closeEnv(true,   // doCheckpoint
                 false); // expectAccurateObsoleteLNCount
    }

    private void expectObsolete(long file, boolean obsolete)
        throws DatabaseException {

        FileSummary summary = getSummary(file);
        assertEquals("totalINCount",
                     1, summary.totalINCount);
        assertEquals("obsoleteINCount",
                     obsolete ? 1 : 0, summary.obsoleteINCount);
    }

    private void expectObsolete(long file, int obsoleteCount)
        throws DatabaseException {

        FileSummary summary = getSummary(file);
        assertEquals("totalINCount",
                     1, summary.totalINCount);
        assertEquals("obsoleteINCount",
                     obsoleteCount, summary.obsoleteINCount);
    }

    private long getINFile(Cursor cursor)
        throws DatabaseException {

        IN in = TestUtils.getIN(TestUtils.getBIN(cursor));
        long lsn = in.getLastFullVersion();
        assertTrue(lsn != DbLsn.NULL_LSN);
        return DbLsn.getFileNumber(lsn);
    }

    private long getBINFile(Cursor cursor)
        throws DatabaseException {

        long lsn = TestUtils.getBIN(cursor).getLastFullVersion();
        assertTrue(lsn != DbLsn.NULL_LSN);
        return DbLsn.getFileNumber(lsn);
    }

    /**
     * Returns the utilization summary for a given log file.
     */
    private FileSummary getSummary(long file)
        throws DatabaseException {

	return (FileSummary) envImpl.getUtilizationProfile()
                                    .getFileSummaryMap(true)
                                    .get(new Long(file));
    }

    private void performPartialCheckpoint(boolean truncateFileSummariesAlso)
        throws DatabaseException, IOException {

        performPartialCheckpoint(truncateFileSummariesAlso,
                                 true,  // expectAccurateObsoleteLNCount
                                 true); // expectAccurateObsoleteLNSize
    }

    private void performPartialCheckpoint(boolean truncateFileSummariesAlso,
                                          boolean
                                          expectAccurateObsoleteLNCount)
        throws DatabaseException, IOException {

        performPartialCheckpoint(truncateFileSummariesAlso,
                                 expectAccurateObsoleteLNCount,
                                 expectAccurateObsoleteLNCount);
    }

    /**
     * Performs a checkpoint and truncates the log before the last CkptEnd.  If
     * truncateFileSummariesAlso is true, truncates before the FileSummaryLNs
     * that appear at the end of the checkpoint.  The environment should be
     * open when this method is called, and it will be closed when it returns.
     */
    private void performPartialCheckpoint
                    (boolean truncateFileSummariesAlso,
                     boolean expectAccurateObsoleteLNCount,
                     boolean expectAccurateObsoleteLNSize)
        throws DatabaseException, IOException {

        /* Do a normal checkpoint. */
        env.checkpoint(forceConfig);
        long eofLsn = envImpl.getFileManager().getNextLsn();
        long lastLsn = envImpl.getFileManager().getLastUsedLsn();
        long truncateLsn;

        /* Searching backward from end, find last CkptEnd. */
        SearchFileReader searcher = 
            new SearchFileReader(envImpl, 1000, false, lastLsn, eofLsn,
                                 LogEntryType.LOG_CKPT_END);
        assertTrue(searcher.readNextEntry());
        long ckptEnd = searcher.getLastLsn();

        if (truncateFileSummariesAlso) {

            /* Searching backward from CkptEnd, find last CkptStart. */
            searcher = 
                new SearchFileReader(envImpl, 1000, false, ckptEnd, eofLsn,
                                     LogEntryType.LOG_CKPT_START);
            assertTrue(searcher.readNextEntry());
            long ckptStart = searcher.getLastLsn();

            /* Searching forward from CkptStart, find first FileSummaryLN. */
            searcher = 
                new SearchFileReader(envImpl, 1000, true, ckptStart, eofLsn,
                                     LogEntryType.LOG_FILESUMMARYLN);
            assertTrue(searcher.readNextEntry());
            truncateLsn = searcher.getLastLsn();
        } else {
            truncateLsn = ckptEnd;
        }
        
        /*
         * Close without another checkpoint, although it doesn't matter since
         * we would truncate before it.
         */
        closeEnv(false, // doCheckpoint
                 expectAccurateObsoleteLNCount,
                 expectAccurateObsoleteLNSize);

        /* Truncate the log. */
        EnvironmentImpl cmdEnv =
	    CmdUtil.makeUtilityEnvironment(envHome, false);
        cmdEnv.getFileManager().truncateLog(DbLsn.getFileNumber(truncateLsn),
                                            DbLsn.getFileOffset(truncateLsn));
        cmdEnv.close(false);

        /* Delete files following the truncated file. */
        String[] fileNames = envHome.list();
        for (int i = 0; i < fileNames.length; i += 1) {
            String name = fileNames[i];
            if (name.endsWith(".jdb")) {
                String numStr = name.substring(0, name.length() - 4);
                long fileNum = Long.parseLong(numStr, 16);
                if (fileNum > DbLsn.getFileNumber(truncateLsn)) {
                    assertTrue(new File(envHome, name).delete());
                }
            }
        }
    }
}
