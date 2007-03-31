/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TreeTestBase.java,v 1.53.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.NullCursor;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.util.TestUtils;

public class TreeTestBase extends TestCase {
    static protected final boolean DEBUG = true;

    static protected int N_KEY_BYTES = 10;
    static protected int N_ITERS = 1;
    static protected int N_KEYS = 10000;
    static protected int MAX_ENTRIES_PER_NODE = 6;

    protected Tree tree = null;
    protected byte[] minKey = null;
    protected byte[] maxKey = null;
    protected Database db = null;
    protected Environment env = null;
    protected File envHome = null;

    public TreeTestBase() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException  {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    void initEnv(boolean duplicatesAllowed) 
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                 Integer.toString(MAX_ENTRIES_PER_NODE));
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(duplicatesAllowed);
        db = env.openDatabase(null, "foo", dbConfig);

        tree = DbInternal.dbGetDatabaseImpl(db).getTree();
        minKey = null;
        maxKey = null;
    }

    public void tearDown()
	throws DatabaseException, IOException {

        db.close();
        if (env != null) {
            env.close();
        }
        env = null;
        db = null;
        tree = null;
        minKey = null;
        maxKey = null;
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    protected IN makeDupIN(IN old) {
        IN ret = new IN(DbInternal.dbGetDatabaseImpl(db),
			old.getIdentifierKey(),
                        MAX_ENTRIES_PER_NODE, 2);
        ret.setNodeId(old.getNodeId());
        ret.setIsRoot(old.isRoot());
        for (int i = 0; i < old.getNEntries(); i++) {
            ret.setEntry(i, old.getTarget(i), old.getKey(i),
			 old.getLsn(i), old.getState(i));
        }

        return ret;
    }

    /**
     * Helper routine to insert a key and immediately read it back.
     */
    protected void insertAndRetrieve(NullCursor cursor, byte[] key, LN ln)
        throws DatabaseException {

        if (minKey == null) {
            minKey = key;
        } else if (Key.compareKeys(key, minKey, null) < 0) {
            minKey = key;
        }

        if (maxKey == null) {
            maxKey = key;
        } else if (Key.compareKeys(maxKey, key, null) < 0) {
            maxKey = key;
        }

        TestUtils.checkLatchCount();
        assertTrue(tree.insert(ln, key, false, cursor,
			       new LockResult(null, null)));
        TestUtils.checkLatchCount();
        assertTrue(retrieveLN(key) == ln);
    }

    /**
     * Helper routine to read the LN referred to by key.
     */
    protected LN retrieveLN(byte[] key)
        throws DatabaseException {

        TestUtils.checkLatchCount();
        IN n = tree.search(key, Tree.SearchType.NORMAL, -1, null, true);
        if (!(n instanceof BIN)) {
            fail("search didn't return a BIN for key: " + key);
        }
        BIN bin = (BIN) n;
        try {
            int index = bin.findEntry(key, false, true);
            if (index == -1) {
                fail("Didn't read back key: " + key);
            } else {
                Node node = bin.getTarget(index);
                if (node instanceof LN) {
                    return (LN) node;
                } else {
                    fail("Didn't read back LN for: " + key);
                }
            }
            /* We never get here, but the compiler doesn't know that. */
            return null;
        } finally {
            bin.releaseLatch();
            TestUtils.checkLatchCount();
        }
    }

    /**
     * Using getNextBin, count all the keys in the database.  Ensure that
     * they're returned in ascending order.
     */
    protected int countAndValidateKeys(Tree tree)
        throws DatabaseException {

        TestUtils.checkLatchCount();
        BIN nextBin = (BIN) tree.getFirstNode();
        byte[] prevKey = { 0x00 };

        int cnt = 0;

        while (nextBin != null) {
            for (int i = 0; i < nextBin.getNEntries(); i++) {
                byte[] curKey = nextBin.getKey(i);
                if (Key.compareKeys(curKey, prevKey, null) <= 0) {
                    throw new InconsistentNodeException
                        ("keys are out of order");
                }
                cnt++;
                prevKey = curKey;
            }
            nextBin = tree.getNextBin(nextBin, false /* traverseWithinDupTree */);
        }
        TestUtils.checkLatchCount();
        return cnt;
    }

    /**
     * Using getPrevBin, count all the keys in the database.  Ensure that
     * they're returned in descending order.
     */
    protected int countAndValidateKeysBackwards(Tree tree)
        throws DatabaseException {

        TestUtils.checkLatchCount();
        BIN nextBin = (BIN) tree.getLastNode();
        byte[] prevKey = null;

        int cnt = 0;

        while (nextBin != null) {
            for (int i = nextBin.getNEntries() - 1; i >= 0; i--) {
                byte[] curKey = nextBin.getKey(i);
                if (prevKey != null &&
                    Key.compareKeys(prevKey, curKey, null) <= 0) {
                    throw new InconsistentNodeException
                        ("keys are out of order");
                }
                cnt++;
                prevKey = curKey;
            }
            nextBin = tree.getPrevBin(nextBin, false /* traverseWithinDupTree */);
        }
        return cnt;
    }
}
