/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TreeTest.java,v 1.86.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.IOException;

import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseStats;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.dbi.NullCursor;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.util.TestUtils;

public class TreeTest extends TreeTestBase {

    public TreeTest() {
	super();
    }

    /**
     * Rudimentary insert/retrieve test.
     */
    public void testSimpleTreeCreation()
	throws DatabaseException {
        initEnv(false);
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);
        insertAndRetrieve(cursor, "aaaaa".getBytes(),
                          new LN((byte[]) null));
        insertAndRetrieve(cursor, "aaaab".getBytes(),
                          new LN((byte[]) null));
        insertAndRetrieve(cursor, "aaaa".getBytes(),
                          new LN((byte[]) null));
        insertAndRetrieve(cursor, "aaa".getBytes(),
                          new LN((byte[]) null));
        txn.operationEnd();
    }

    /**
     * Slightly less rudimentary test inserting a handfull of keys and LN's.
     */
    public void testMultipleInsertRetrieve0()
	throws DatabaseException {

        /* 
	 * Set the seed to reproduce a specific problem found while debugging:
         * IN.split was splitting with the identifier key being on the right
         * side.
	 */
        initEnv(false);
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);
        for (int i = 0; i < 21; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            TestUtils.generateRandomAlphaBytes(key);
            insertAndRetrieve(cursor, key, new LN((byte[]) null));
        }
        txn.operationEnd();
    }

    /**
     * Insert a bunch of keys and test that they retrieve back ok.  While we
     * insert, maintain the highest and lowest keys inserted.  Verify that
     * getFirstNode and getLastNode return those two entries.  Lather, rinse,
     * repeat.
     */
    public void testMultipleInsertRetrieve1()
	throws DatabaseException, IOException {

        initEnv(false);
	doMultipleInsertRetrieve1();
    }

    /**
     * Helper routine for above.
     */
    private void doMultipleInsertRetrieve1()
	throws DatabaseException {

        byte[][] keys = new byte[N_KEYS][];
        LN[] lns = new LN[N_KEYS];
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);
        for (int i = 0; i < N_KEYS; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            keys[i] = key;
            lns[i] = new LN((byte[]) null);
            TestUtils.generateRandomAlphaBytes(key);
            insertAndRetrieve(cursor, key, lns[i]);
        }

        for (int i = 0; i < N_KEYS; i++) {
            assertTrue(retrieveLN(keys[i]) == lns[i]);
        }

        TestUtils.checkLatchCount();
        IN leftMostNode = tree.getFirstNode();

        assertTrue(leftMostNode instanceof BIN);
        BIN lmn = (BIN) leftMostNode;
        lmn.releaseLatch();
        TestUtils.checkLatchCount();
        assertTrue(Key.compareKeys(lmn.getKey(0), minKey, null) == 0);

        TestUtils.checkLatchCount();
        IN rightMostNode = tree.getLastNode();

        assertTrue(rightMostNode instanceof BIN);
        BIN rmn = (BIN) rightMostNode;
        rmn.releaseLatch();
        TestUtils.checkLatchCount();
        assertTrue(Key.compareKeys
            (rmn.getKey(rmn.getNEntries() - 1), maxKey, null) == 0);
        TreeStats ts = tree.getTreeStats();
        assertTrue(ts.nRootSplits > 1);

        txn.operationEnd();
    }

    /**
     * Create a tree.  After creation, walk the bins forwards using getNextBin
     * counting the keys and validating that the keys are being returned in
     * ascending order.  Ensure that the correct number of keys were returned.
     */
    public void testCountAndValidateKeys() 
	throws DatabaseException, IOException {

        initEnv(false);
	doCountAndValidateKeys();
    }

    /**
     * Helper routine for above test.
     */
    private void doCountAndValidateKeys()
	throws DatabaseException {
        byte[][] keys = new byte[N_KEYS][];
        LN[] lns = new LN[N_KEYS];
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);

        for (int i = 0; i < N_KEYS; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            keys[i] = key;
            lns[i] = new LN((byte[]) null);
            TestUtils.generateRandomAlphaBytes(key);
            insertAndRetrieve(cursor, key, lns[i]);
        }
        assertTrue(countAndValidateKeys(tree) == N_KEYS);
        txn.operationEnd();
    }

    /**
     * Create a tree.  After creation, walk the bins backwards using getPrevBin
     * counting the keys and validating that the keys are being returned in
     * descending order.  Ensure that the correct number of keys were returned.
     */
    public void testCountAndValidateKeysBackwards()
	throws DatabaseException, IOException {

        initEnv(false);
	doCountAndValidateKeysBackwards();
    }

    /**
     * Helper routine for above test.
     */
    public void doCountAndValidateKeysBackwards()
	throws DatabaseException {

        byte[][] keys = new byte[N_KEYS][];
        LN[] lns = new LN[N_KEYS];
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);
        for (int i = 0; i < N_KEYS; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            keys[i] = key;
            lns[i] = new LN((byte[]) null);
            TestUtils.generateRandomAlphaBytes(key);
            insertAndRetrieve(cursor, key, lns[i]);
        }
        assertTrue(countAndValidateKeysBackwards(tree) == N_KEYS);
        txn.operationEnd();
    }

    public void testAscendingInsertBalance()
	throws DatabaseException {

        initEnv(false);
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);

        /* Fill up a db with data */
        for (int i = 0; i < N_KEYS; i++) {
            byte[] keyBytes = new byte[4];
            TestUtils.putUnsignedInt(keyBytes, TestUtils.alphaKey(i));
            insertAndRetrieve(cursor, keyBytes,
                              new LN((byte[]) null));
        }
        
        TestUtils.checkLatchCount();

        /* Count the number of levels on the left. */
        IN leftMostNode = tree.getFirstNode();
        assertTrue(leftMostNode instanceof BIN);
        int leftSideLevels = 0;
        do {
            SearchResult result =
		tree.getParentINForChildIN(leftMostNode, true, true);
            leftMostNode = result.parent;
            leftSideLevels++;
        } while (leftMostNode != null);
        TestUtils.checkLatchCount();

        /* Count the number of levels on the right. */
        IN rightMostNode = tree.getLastNode();
        assertTrue(rightMostNode instanceof BIN);
        int rightSideLevels = 0;
        do {
            SearchResult result =
		tree.getParentINForChildIN(rightMostNode, true, true);
            rightMostNode = result.parent;
            rightSideLevels++;
        } while (rightMostNode != null);
        TestUtils.checkLatchCount();
        if (leftSideLevels > 10 ||
            rightSideLevels > 10) {
            fail("Levels too high (" +
                 leftSideLevels +
                 "/" +
                 rightSideLevels +
                 ") on descending insert");
        }
        txn.operationEnd();
    }

    public void testDescendingInsertBalance()
	throws DatabaseException {
        initEnv(false);
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);

        for (int i = N_KEYS; i >= 0; --i) {
            byte[] keyBytes = new byte[4];
            TestUtils.putUnsignedInt(keyBytes, TestUtils.alphaKey(i));
            insertAndRetrieve(cursor, keyBytes,
                              new LN((byte[]) null));
        }
        
        TestUtils.checkLatchCount();
        IN leftMostNode = tree.getFirstNode();

        assertTrue(leftMostNode instanceof BIN);
        int leftSideLevels = 0;
        do {
            SearchResult result =
		tree.getParentINForChildIN(leftMostNode, true, true);
            leftMostNode = result.parent;
            leftSideLevels++;
        } while (leftMostNode != null);
        TestUtils.checkLatchCount();

        IN rightMostNode = tree.getLastNode();

        assertTrue(rightMostNode instanceof BIN);
        int rightSideLevels = 0;
        do {
            SearchResult result =
		tree.getParentINForChildIN(rightMostNode, true, true);
            rightMostNode = result.parent;
            rightSideLevels++;
        } while (rightMostNode != null);
        TestUtils.checkLatchCount();
        if (leftSideLevels > 10 ||
            rightSideLevels > 10) {
            fail("Levels too high (" +
                 leftSideLevels +
                 "/" +
                 rightSideLevels +
                 ") on descending insert");
        }
        txn.operationEnd();
    }

    /**
     * Insert a bunch of keys.  Call verify and validate the results.
     */
    public void testVerify()
	throws DatabaseException {

        initEnv(false);
	byte[][] keys = new byte[N_KEYS][];
	LN[] lns = new LN[N_KEYS];
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);
        
	for (int i = 0; i < N_KEYS; i++) {
	    byte[] key = new byte[N_KEY_BYTES];
	    keys[i] = key;
	    lns[i] = new LN((byte[]) new byte[1]);
	    TestUtils.generateRandomAlphaBytes(key);
	    insertAndRetrieve(cursor, key, lns[i]);
	}

        /* 
         * Note that verify will attempt to continue past errors, so
         * assertTrue on the status return.
         */
        assertTrue(env.verify(new VerifyConfig(), System.err));
	DatabaseStats stats = db.verify(new VerifyConfig());
	BtreeStats btStats = (BtreeStats) stats;

	assertTrue(btStats.getInternalNodeCount() <
		   btStats.getBottomInternalNodeCount());
	assertTrue(btStats.getBottomInternalNodeCount() <
		   btStats.getLeafNodeCount() +
		   btStats.getDeletedLeafNodeCount());
	assertTrue(btStats.getLeafNodeCount() +
		   btStats.getDeletedLeafNodeCount() ==
		   N_KEYS);
        txn.operationEnd();

        /* Now intentionally create LogFileNotFoundExceptions */
        /*
          db.close();
          env.close();

          This is disabled until the method for flipping files is
          introduced. It's too hard to create a LogFileNotFoundException
          by brute force deleting a file; often recovery doesn't work.
          Instead, use a flipped file later on.

        String [] jeFiles =
            FileManager.listFiles(envHome,
                                  new String[] {FileManager.JE_SUFFIX});
        int targetIdx = jeFiles.length / 2;
        assertTrue(targetIdx > 0);
        File targetFile = new File(envHome, jeFiles[targetIdx]);
        assertTrue(targetFile.delete());

        initEnv(false);
        assertFalse(env.verify(new VerifyConfig(), System.err));
        */
    }
}
