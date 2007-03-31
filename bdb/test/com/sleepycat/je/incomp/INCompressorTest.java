/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INCompressorTest.java,v 1.15.2.1 2007/02/01 14:50:11 cwl Exp $
 */

package com.sleepycat.je.incomp;

import java.io.File;
import java.io.IOException;

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
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;

/**
 * Test that BIN compression occurs in the various ways it is supposed to.
 * <p>These are:</p>
 * <ul>
 * <li>transactional and non-transactional delete,</li>
 * <li>delete duplicates and non-duplicates,</li>
 * <li>removal of empty sub-trees (duplicates and non-duplicates),</li>
 * <li>compression of BIN for deleted DIN subtree.</li>
 * <li>removal of empty BIN after deleting a DIN subtree.</li>
 * <li>undo causes compression of inserted LN during abort and recovery,</li>
 * <li>redo causes compression of deleted LN during recovery,</li>
 * </ul>
 *
 * <p>Also test that compression retries occur after we attempt to compress but
 * cannot because:</p>
 * <ul>
 * <li>cursors are open on the BIN when the compressor dequeues them,</li>
 * <li>cursors are open when attempting to delete a sub-tree (dup and non-dup
 * are two separate code paths).</li>
 * <li>a deleted key is locked during compression (NOT TESTED - this is very
 * difficult to reproduce),</li>
 * </ul>
 *
 * <p>Possible problem:  When we attempt to delete a subtree because the BIN is
 * empty, we give up when NodeNotEmptyException is thrown by the search.
 * However, this is thrown not only when entries have been added but also when
 * there are cursors on the BIN; it seems like we should retry in the latter
 * case.  Or is it impossible to have a cursor on an empty BIN?</p>
 * 
 * <p>We do not test here the last ditch effort to compress to make room in
 * IN.insertEntry1; that should never happen in theory, so I dodn't think it
 * is worthwhile to try to reproduce it.</p>
 */
public class INCompressorTest extends TestCase {
    private static final boolean DEBUG = false;
    private static final int NUM_RECS = 257;

    private File envHome;
    private Environment env;
    private Database db;
    private IN in;
    private BIN bin;
    private DBIN dbin;
    private boolean hasDups;

    /* Use high keys since we fill the first BIN with low keys. */
    private DatabaseEntry entry0 = new DatabaseEntry(new byte[] {0});
    private DatabaseEntry entry1 = new DatabaseEntry(new byte[] {1});
    private DatabaseEntry entry2 = new DatabaseEntry(new byte[] {2});
    private DatabaseEntry keyFound = new DatabaseEntry();
    private DatabaseEntry dataFound = new DatabaseEntry();

    public INCompressorTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        if (env != null) {
            try { env.close(); } catch (DatabaseException ignored) { }
        }
        TestUtils.removeLogFiles("TearDown", envHome, false);
        env = null;
        db = null;
        in = null;
        bin = null;
        dbin = null;
        entry0 = null;
        entry1 = null;
        entry2 = null;
        keyFound = null;
        dataFound = null;
    }

    public void testDeleteTransactional()
        throws DatabaseException {

        /* Transactional no-dups, 2 keys. */
        openAndInit(true, false);
        OperationStatus status;

        /* Cursor appears on BIN. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Closing the cursor without commit does not compress. */
        cursor.close();
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Commit without calling compress does not compress. */
        txn.commit();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 1, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testDeleteNonTransactional()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);
        OperationStatus status;

        /* Cursor appears on BIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 1, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testDeleteDuplicate()
        throws DatabaseException {

        /* Non-transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(false, true);
        OperationStatus status;

        /* Cursor appears on DBIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(dbin, 1, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());
        checkBinEntriesAndCursors(bin, 2, 0);

        closeEnv();
    }

    public void testRemoveEmptyBIN()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);
        OperationStatus status;

        /* Cursor appears on BIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.getNext(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 0, 0);

        /* BIN is empty so parent entry should be gone also. */
        assertEquals(1, in.getNEntries());

        closeEnv();
    }

    public void testRemoveEmptyDBIN()
        throws DatabaseException {

        /* Non-transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(false, true);
        OperationStatus status;

        /* Cursor appears on DBIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.getNext(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(dbin, 0, 0);

        /* BIN parent should have one less entry. */
        assertEquals(2, in.getNEntries());
        checkBinEntriesAndCursors(bin, 1, 0);

        closeEnv();
    }

    public void testRemoveEmptyDBINandBIN()
        throws DatabaseException {

        /* Non-transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(false, true);
        OperationStatus status;

        /* Delete key 1, cursor appears on BIN, no compression yet. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getSearchKey(entry1, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Move cursor to 1st dup, cursor moves to DBIN, no compresion yet. */
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Delete the duplicates for key 0, no compression yet. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.getNext(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
	/* 
	 * Do this twice.  The test is depending on the iterator in
	 * doCompress() getting the DBINReference first and the BINReference
	 * second.  In JRockit, it's the opposite so the compress of the BIN
	 * doesn't do any good on the first time around.  So take two
	 * iterations to get the job done.
	 */
        env.compress();

        checkBinEntriesAndCursors(bin, 0, 0);
        checkBinEntriesAndCursors(dbin, 0, 0);

        /* BIN is empty so parent entry should be gone also. */
        assertEquals(1, in.getNEntries());

        closeEnv();
    }

    public void testAbortInsert()
        throws DatabaseException {

        /* Transactional no-dups, 2 keys. */
        openAndInit(true, false);
     
        /* Add key 2, cursor appears on BIN. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        cursor.put(entry2, entry0);
        checkBinEntriesAndCursors(bin, 3, 1);

        /* Closing the cursor without abort does not compress. */
        cursor.close();
        env.compress();
        checkBinEntriesAndCursors(bin, 3, 0);

        /* Abort without calling compress does not compress. */
        txn.abort();
        checkBinEntriesAndCursors(bin, 3, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testAbortInsertDuplicate()
        throws DatabaseException {

        /* Transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(true, true);

        /* Add datum 2 for key 0, cursor appears on DBIN. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        cursor.put(entry0, entry2);
        checkBinEntriesAndCursors(bin, 2, 1);
        checkBinEntriesAndCursors(dbin, 3, 1);

        /* Closing the cursor without abort does not compress. */
        cursor.close();
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 3, 0);

        /* Abort without calling compress does not compress. */
        txn.abort();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 3, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testRollBackInsert()
        throws DatabaseException {

        /* Transactional no-dups, 2 keys. */
        openAndInit(true, false);

        /* Add key 2, cursor appears on BIN. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        cursor.put(entry2, entry0);
        checkBinEntriesAndCursors(bin, 3, 1);

        /* Closing the cursor without abort does not compress. */
        cursor.close();
        env.compress();
        checkBinEntriesAndCursors(bin, 3, 0);

        /* Checkpoint to preserve internal nodes through recovery. */
        CheckpointConfig config = new CheckpointConfig();
        config.setForce(true);
        env.checkpoint(config);

        /* Abort without calling compress does not compress. */
        txn.abort();
        checkBinEntriesAndCursors(bin, 3, 0);

        /*
         * Shutdown and reopen to run recovery. This will call a checkpoint,
         * but it doesn't compress because the child is not resident.
         */
        db.close();
        DbInternal.envGetEnvironmentImpl(env).close(false);
        env = null;
        openEnv(true, false, null);
        initInternalNodes();
        checkBinEntriesAndCursors(bin, 3, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);

        closeEnv();
    }

    public void testRollBackInsertDuplicate()
        throws DatabaseException {

        /* Transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(true, true);

        /* Add datum 2 for key 0, cursor appears on DBIN. */
        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        cursor.put(entry0, entry2);
        checkBinEntriesAndCursors(bin, 2, 1);
        checkBinEntriesAndCursors(dbin, 3, 1);

        /* Closing the cursor without abort does not compress. */
        cursor.close();
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 3, 0);

        /* Checkpoint to preserve internal nodes through recovery. */
        CheckpointConfig config = new CheckpointConfig();
        config.setForce(true);
        env.checkpoint(config);

        /* Abort without calling compress does not compress. */
        txn.abort();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 3, 0);

        /* 
         * Shutdown and reopen to run recovery. This will call a checkpoint,
         * but it doesn't compress because the child is not resident.
         */
        db.close();
        DbInternal.envGetEnvironmentImpl(env).close(false);
        env = null;
        openEnv(true, true, null);
        initInternalNodes();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 3, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 0);
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testRollForwardDelete()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);
        OperationStatus status;

        /* Checkpoint to preserve internal nodes through recovery. */
        CheckpointConfig config = new CheckpointConfig();
        config.setForce(true);
        env.checkpoint(config);

        /* Cursor appears on BIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(bin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* 
         * Shutdown and reopen to run recovery. This will call a checkpoint,
         * but it doesn't compress because the child is not resident.
         */
        db.close();
        DbInternal.envGetEnvironmentImpl(env).close(false);
        openEnv(false, false, null);
        initInternalNodes();
        checkBinEntriesAndCursors(bin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(bin, 1, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());

        closeEnv();
    }

    public void testRollForwardDeleteDuplicate()
        throws DatabaseException {

        /* Non-transactional dups, 2 keys and 2 dups for 1st key. */
        openAndInit(false, true);
        OperationStatus status;

        /* Checkpoint to preserve internal nodes through recovery. */
        CheckpointConfig config = new CheckpointConfig();
        config.setForce(true);
        env.checkpoint(config);

        /* Cursor appears on DBIN. */
        Cursor cursor = db.openCursor(null, null);
        status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Delete without closing the cursor does not compress. */
        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        env.compress();
        checkBinEntriesAndCursors(dbin, 2, 1);

        /* Closing the cursor without calling compress does not compress. */
        cursor.close();
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* 
         * Shutdown and reopen to run recovery. This will call a checkpoint,
         * but it doesn't compress because the child is not resident.
         */
        db.close();
        DbInternal.envGetEnvironmentImpl(env).close(false);
        openEnv(false, true, null);
        initInternalNodes();
        checkBinEntriesAndCursors(dbin, 2, 0);

        /* Finally compress can compress. */
        env.compress();
        checkBinEntriesAndCursors(dbin, 1, 0);

        /* Should be no change in parent nodes. */
        assertEquals(2, in.getNEntries());
        checkBinEntriesAndCursors(bin, 2, 0);

        closeEnv();
    }

    /**
     * Test that we can handle cases where lazy compression runs first, but the
     * daemon handles pruning.  Testing against BINs.
     */
    public void testLazyPruning()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);

        deleteAndLazyCompress(false);

        /* Now compress, empty BIN should disappear. */
        env.compress();
        checkINCompQueueSize(0);
        assertEquals(1, in.getNEntries());

        closeEnv();
    }

    /**
     * Test that we can handle cases where lazy compression runs first, but the
     * daemon handles pruning.  Testing against DBINs.  [#11778]
     */
    public void testLazyPruningDups()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, true);

        deleteAndLazyCompress(true);

        /* Now compress, empty DBIN should disappear. */
        env.compress();
	/* Compress again. Empty BIN should disappear. */
	env.compress();
        checkINCompQueueSize(0);
        assertEquals(1, in.getNEntries());

        closeEnv();
    }

    /**
     * Scan over an empty DBIN.  [#11778]
     */
    public void testEmptyInitialDBINScan()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, true);

        deleteAndLazyCompress(true);

	/*
	 * Have IN with two entries, first entry is BIN with 1 entry.  That
	 * entry is DIN with 1 entry.  That entry is a DBIN with 0 entries.
	 * Position the cursor at the first entry so that we move over that
	 * zero-entry DBIN.
	 */
        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
	assertTrue(keyFound.getData()[0] == 64);
	cursor.close();
        closeEnv();
    }

    /**
     * Scan over an empty BIN.  This looks very similar to
     * com.sleepycat.je.test.SR11297Test. [#11778]
     */
    public void testEmptyInitialBINScan()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);

        deleteAndLazyCompress(false);

	/*
	 * Have IN with two entries, first entry is BIN with 0 entries.
	 * Position the cursor at the first entry so that we move over that
	 * zero-entry BIN.
	 */
        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
	assertTrue(keyFound.getData()[0] == 64);
	cursor.close();
        closeEnv();
    }

    /**
     * Test that we can handle cases where lazy compression runs first, but the
     * daemon handles pruning.
     */
    public void testNodeNotEmpty()
        throws DatabaseException {

        /* Non-transactional no-dups, 2 keys. */
        openAndInit(false, false);

        deleteAndLazyCompress(false);

        /*
         * We now have an entry on the compressor queue, but let's re-insert a
         * value to make pruning hit the NodeNotEmptyException case.
         */
        assertEquals(OperationStatus.SUCCESS, db.put(null, entry0, entry0));
        checkBinEntriesAndCursors(bin, 1, 0);

        env.compress();
        assertEquals(2, in.getNEntries());
        checkINCompQueueSize(0);

        closeEnv();
    }

    /* Todo: Check cursor movement across an empty bin. */

    /* Delete all records from the first bin and invoke lazy compression. */
    private void deleteAndLazyCompress(boolean doDups)
        throws DatabaseException {

        /* Position the cursor at the first BIN and delete both keys. */
        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        checkBinEntriesAndCursors(bin, 2, 1);

        status = cursor.delete();
        assertEquals(OperationStatus.SUCCESS, status);
        status = cursor.getNext(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
	status = cursor.delete();
	assertEquals(OperationStatus.SUCCESS, status);
	if (doDups) {
	    status = cursor.getNext(keyFound, dataFound, null);
	    assertEquals(OperationStatus.SUCCESS, status);
	    status = cursor.delete();
	    assertEquals(OperationStatus.SUCCESS, status);
	}
        cursor.close();

        /* 
	 * Do lazy compression, leaving behind an empty BIN (and DBIN if dups.)
	 */
        checkINCompQueueSize(doDups ? 2 : 1);
        CheckpointConfig config = new CheckpointConfig();
        config.setForce(true);
        env.checkpoint(config);
        checkBinEntriesAndCursors((doDups ? dbin : bin), 0, 0);

        /* BIN is empty but tree pruning hasn't happened. */
        assertEquals(2, in.getNEntries());
        checkINCompQueueSize(1);
    }

    /**
     * Checks for expected entry and cursor counts on the given BIN or DBIN.
     */
    private void checkBinEntriesAndCursors(BIN checkBin,
                                           int nEntries,
                                           int nCursors)
        throws DatabaseException {

        assertEquals("nEntries", nEntries, checkBin.getNEntries());
        assertEquals("nCursors", nCursors, checkBin.nCursors());
    }

    /**
     * Check expected size of the INCompressor queue. 
     */
    private void checkINCompQueueSize(int expected) 
        throws DatabaseException {

        assertEquals(expected,
           DbInternal.envGetEnvironmentImpl(env).getINCompressorQueueSize());
    }

    /**
     * Opens the environment and db and writes 2 records (3 if dups are used).
     *
     * <p>Without dups: {0,0}, {1,0}. This gives two LNs in the main tree.</p>
     *
     * <p>With dups: {0,0}, {0,1}, {1,0}. This gives one LN in the main tree,
     * and a dup tree with two LNs.</p>
     */
    private void openAndInit(boolean transactional, boolean dups)
        throws DatabaseException {

        openEnv(transactional, dups, null);

        /*
         * We need at least 2 BINs, otherwise empty BINs won't be deleted.  So
         * we add keys until the BIN splits, then delete everything in the
         * first BIN except the first two keys.  Those are the keys we'll use
         * for testing, and are key values 0 and 1.
         */
        BIN firstBin = null;
        OperationStatus status;
        for (int i = 0;; i += 1) {
            DatabaseEntry key = new DatabaseEntry(new byte[] { (byte) i });
            status = db.put(null, key, entry0);
            assertEquals(OperationStatus.SUCCESS, status);
            Cursor cursor = db.openCursor(null, null);
            status = cursor.getLast(keyFound, dataFound, null);
            assertEquals(OperationStatus.SUCCESS, status);
            BIN b = DbInternal.getCursorImpl(cursor).getBIN();
            cursor.close();
            if (firstBin == null) {
                firstBin = b;
            } else if (firstBin != b) {
                /* Now delete all but the first two keys in the first BIN. */
                while (firstBin.getNEntries() > 2) {
                    cursor = db.openCursor(null, null);
                    keyFound.setData(entry2.getData());
                    status =
			cursor.getSearchKeyRange(keyFound, dataFound, null);
                    assertEquals(OperationStatus.SUCCESS, status);
                    cursor.close();
                    status = db.delete(null, keyFound);
                    assertEquals(OperationStatus.SUCCESS, status);
                    env.compress();
                }
                break;
            }
        }

        /* Write dup records. */
        if (dups) {
            status = db.put(null, entry0, entry1);
            assertEquals(OperationStatus.SUCCESS, status);
        }

        /* Set in, bin, dbin. */
        initInternalNodes();
        assertSame(bin, firstBin);

        /* Check that all tree nodes are populated. */
        assertEquals(2, in.getNEntries());
        checkBinEntriesAndCursors(bin, 2, 0);
        if (dups) {
            checkBinEntriesAndCursors(dbin, 2, 0);
        } else {
            assertNull(dbin);
        }
    }

    /**
     * Initialize in, bin, dbin.
     */
    private void initInternalNodes()
        throws DatabaseException {

        /* Find the BIN/DBIN. */
        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getFirst(keyFound, dataFound, null);
        assertEquals(OperationStatus.SUCCESS, status);
        bin = DbInternal.getCursorImpl(cursor).getBIN();
        dbin = DbInternal.getCursorImpl(cursor).getDupBIN();
        cursor.close();

        /* Find the IN parent of the BIN. */
        bin.latch();
        in = DbInternal.dbGetDatabaseImpl(db)
                       .getTree()
                       .getParentINForChildIN(bin, true, true)
                       .parent;
        assertNotNull(in);
        in.releaseLatch();
    }

    /**
     * Opens the environment and db.
     */
    private void openEnv(boolean transactional, boolean dups, String nodeMax)
        throws DatabaseException {

        hasDups = dups;

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(transactional);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
	if (nodeMax != null) {
	    envConfig.setConfigParam
		(EnvironmentParams.NODE_MAX.getName(), nodeMax);
	    envConfig.setConfigParam
		(EnvironmentParams.NODE_MAX_DUPTREE.getName(), nodeMax);
	}
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        /* Make a db and open it. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(transactional);
        dbConfig.setSortedDuplicates(dups);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "testDB", dbConfig);
    }

    /**
     * Closes the db and environment.
     */
    private void closeEnv()
        throws DatabaseException {

        db.close();
        db = null;
        env.close();
        env = null;
    }
}
