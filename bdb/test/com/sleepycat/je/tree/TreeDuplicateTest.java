/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TreeDuplicateTest.java,v 1.41.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.dbi.NullCursor;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.util.TestUtils;

public class TreeDuplicateTest extends TreeTestBase {

    public TreeDuplicateTest() {
	super();
    }

    private static final int N_DUPLICATES_PER_KEY = N_KEYS;
    private static final int N_TOP_LEVEL_KEYS = 10;

    /**
     * Rudimentary insert/retrieve test.
     */
    public void testSimpleTreeCreation()
	throws Throwable {

        try {

            initEnv(true);
            Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
            NullCursor cursor = new NullCursor(tree.getDatabase(), txn);

            try {
                byte[][] keys = new byte[N_TOP_LEVEL_KEYS][];
                LN[][] lns = new LN[N_TOP_LEVEL_KEYS][];
                for (int i = 0; i < N_TOP_LEVEL_KEYS; i++) {
                    byte[] key = new byte[N_KEY_BYTES];
                    keys[i] = key;
                    lns[i] = new LN[N_DUPLICATES_PER_KEY];
                    TestUtils.generateRandomAlphaBytes(key);
                    for (int j = 0; j < N_DUPLICATES_PER_KEY; j++) {
                        byte[] data = new byte[N_KEY_BYTES];
                        TestUtils.generateRandomAlphaBytes(data);
                        LN ln = new LN(data);
                        lns[i][j] = ln;
                        insertAndRetrieveDuplicate(key, ln, cursor);
                    }
                }

                for (int i = 0; i < N_TOP_LEVEL_KEYS; i++) {
                    byte[] key = keys[i];
                    for (int j = 0; j < N_DUPLICATES_PER_KEY; j++) {
                        LN ln = lns[i][j];
                        retrieveDuplicateLN(key, ln);
                    }
                }
            } finally {
                txn.operationEnd();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Make sure that IllegalArgumentException is returned from search(null).
     */
    public void testNullRoot()
	throws DatabaseException {

        initEnv(false);
        assertTrue(tree.search(null, Tree.SearchType.NORMAL, -1, null, true) ==
                   null);
        TestUtils.checkLatchCount();
    }

    /**
     * Insert a bunch of keys.  Validate that getFirstNode and
     * getLastNode return the right values.
     */
    public void testGetFirstLast()
	throws DatabaseException {

        initEnv(true);
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        NullCursor cursor = new NullCursor(tree.getDatabase(), txn);

	/* Make sure IllegalArgumentException is thrown for null args. */
        try {
            TestUtils.checkLatchCount();
            tree.getFirstNode(null);
            fail("Tree.getFirstNode didn't throw IllegalArgumentException");
        } catch (IllegalArgumentException IAE) {
        }
        TestUtils.checkLatchCount();

        try {
            TestUtils.checkLatchCount();
            tree.getLastNode(null);
            fail("Tree.getLastNode didn't throw IllegalArgumentException");
        } catch (IllegalArgumentException IAE) {
        }
        TestUtils.checkLatchCount();

        byte[][] keys = new byte[N_TOP_LEVEL_KEYS][];
        LN[][] lns = new LN[N_TOP_LEVEL_KEYS][];
	byte[][] minKeys = new byte[N_TOP_LEVEL_KEYS][];
	byte[][] maxKeys = new byte[N_TOP_LEVEL_KEYS][];
        for (int i = 0; i < N_TOP_LEVEL_KEYS; i++) {
            byte[] key = new byte[N_KEY_BYTES];
	    byte[] minKey = null;
	    byte[] maxKey = null;
            keys[i] = key;
	    lns[i] = new LN[N_DUPLICATES_PER_KEY];
            TestUtils.generateRandomAlphaBytes(key);
	    for (int j = 0; j < N_DUPLICATES_PER_KEY; j++) {
		byte[] data = new byte[N_KEY_BYTES];
		TestUtils.generateRandomAlphaBytes(data);
		byte[] dupKey = data;

		if (minKey == null) {
		    minKey = dupKey;
		} else if (Key.compareKeys(dupKey, minKey, null) < 0) {
		    minKey = dupKey;
		}

		if (maxKey == null) {
		    maxKey = dupKey;
		} else if (Key.compareKeys(maxKey, dupKey, null) < 0) {
		    maxKey = dupKey;
		}

		LN ln = new LN(data);
		lns[i][j] = ln;
		insertAndRetrieveDuplicate(key, ln, cursor);
	    }
	    minKeys[i] = minKey;
	    maxKeys[i] = maxKey;
        }

        for (int i = 0; i < N_TOP_LEVEL_KEYS; i++) {
	    byte[] key = keys[i];
	    for (int j = 0; j < N_DUPLICATES_PER_KEY; j++) {
		validateFirstLast(key, minKeys[i], maxKeys[i]);
	    }
        }
        txn.operationEnd();
    }

    /**
     * Find the first and last dup for key and make sure they match the
     * minKey and maxKey args.
     */
    private void validateFirstLast(byte[] key, byte[] minKey, byte[] maxKey)
	throws DatabaseException {

        TestUtils.checkLatchCount();

	/* find the top of the dup tree. */
	IN dr = tree.search(key, Tree.SearchType.NORMAL, -1, null, true);
	if (!(dr instanceof BIN)) {
	    fail("search didn't return a BIN for key: " + key);
	}
	BIN topBin = (BIN) dr;
	int index = topBin.findEntry(key, false, true);
	if (index == -1) {
	    fail("Didn't read back key: " + key);
	}
	Node dupEntry = topBin.getTarget(index);
	if (!(dupEntry instanceof DIN)) {
	    fail("Didn't find a DIN");
	}
	topBin.releaseLatch();
	DIN duplicateRoot = (DIN) dupEntry;
	duplicateRoot.latch();

	DBIN leftMostNode = tree.getFirstNode(duplicateRoot);

        assertTrue(leftMostNode instanceof DBIN);
        leftMostNode.releaseLatch();
        assertTrue(Key.compareKeys(leftMostNode.getKey(0), minKey, null) == 0);

	duplicateRoot.latch();
	DBIN rightMostNode = tree.getLastNode(duplicateRoot);

        assertTrue(rightMostNode instanceof DBIN);
        rightMostNode.releaseLatch();
        assertTrue(Key.compareKeys
            (rightMostNode.getKey(rightMostNode.getNEntries() - 1), maxKey,
             null) == 0);

        TestUtils.checkLatchCount();
    }

    private void insertAndRetrieveDuplicate(byte[] key, LN ln, NullCursor cursor)
	throws DatabaseException {

        TestUtils.checkLatchCount();
        assertTrue(tree.insert(ln, key, true, cursor,
			       new LockResult(null, null)));
        TestUtils.checkLatchCount();
        assertTrue(retrieveDuplicateLN(key, ln) == ln);
    }

    /**
     * Helper routine to read the duplicate LN referred to by key.
     */
    private LN retrieveDuplicateLN(byte[] key, LN ln)
	throws DatabaseException {

        TreeLocation location = new TreeLocation();
        try {
            assertTrue(tree.getParentBINForChildLN(location,
                                                   key,
                                                   ln.getData(),
                                                   ln,
                                                   false,
						   false,
						   false,
                                                   true));

            return (LN) location.bin.getTarget(location.index);
        } finally {
            location.bin.releaseLatch();
            TestUtils.checkLatchCount();
        }
    }
}
