/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: GetParentNodeTest.java,v 1.41.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;

public class GetParentNodeTest extends TestCase {
    static private final boolean DEBUG = false;

    private File envHome;
    private Environment env;
    private Database db;
    private IN rootIN;
    private IN firstLevel2IN;
    private BIN firstBIN;
    private DBIN firstDBIN;

    public GetParentNodeTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() 
        throws Exception {
        TestUtils.removeLogFiles("Setup", envHome, false);
        initEnv();
    }

    public void tearDown() 
        throws Exception {
        try {
            db.close();
            env.close();
        } catch (DatabaseException E) {
        }
                
        TestUtils.removeLogFiles("TearDown", envHome, true);
    }

    private void initEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "4");
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        String databaseName = "testDb";
        Transaction txn = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        db = env.openDatabase(txn, databaseName, dbConfig);
        txn.commit();
    }

    /**
     * Test getParentINForChildIN and GetParentBINForChildLN painstakingly on a
     * hand constructed tree.
     */
    public void testBasic()
        throws Exception {

        try {
            /* 
             * Make a tree w/3 levels in the main tree and a single dup
             * tree. The dupTree has two levels. The tree looks like this:
	     *
             *            root(key=a)
             *             |
             *      +---------------------------+
             *    IN(key=a)                   IN(key=e)
             *     |                            |
             *  +------------------+       +--------+--------+
             * BIN(key=a)       BIN(c)    BIN(e)   BIN(g)  BIN(i)
             *   |   |            | |      | |       | |     | | |
             *  LNa DINb        LNc,d    LNe,f     LNg,h   LNi,j,k
             *       |
             *       +----------+-------------+
             *       |          |             |
             *   DBIN(data1) DBIN(data3)  DBIN(data5)
             *    LN LN         LN LN      LN LN LN
             */
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("a"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("c"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("d"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("e"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("f"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("g"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("h"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("i"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("j"),
                                new StringDbt("data1")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("k"),
                                new StringDbt("data1")));

            /* Add one dup tree. */
            byte[] dupKey = "b".getBytes();
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data2")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data3")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data4")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data5")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("b"),
                                new StringDbt("data6")));
            assertEquals(OperationStatus.SUCCESS, 
                         db.put(null, new StringDbt("b"),
				new StringDbt("data7")));

            /* 
             * Test exact matches.
             */
            checkTreeUsingExistingNodes(dupKey, true);
            checkTreeUsingExistingNodes(dupKey, false);

            /* Test potential matches. */
            checkTreeUsingPotentialNodes();

            /* Test deletes. */
	    checkTreeWithDeletedBins(true);
	    checkTreeWithDeletedBins(false);

            /* Should be no latches held. */
            assertEquals(0, LatchSupport.countLatchesHeld());
        } catch (Throwable t) {
            t.printStackTrace();
            throw new Exception(t);
        }
    }

    private void checkTreeUsingExistingNodes(byte[] dupKey,
                                             boolean requireExactMatch) 
        throws DatabaseException {

        /* Start at the root. */
        DatabaseImpl database = DbInternal.dbGetDatabaseImpl(db);
        Tree tree = database.getTree();

        if (DEBUG) {
            tree.dump();
        }

        rootIN = tree.withRootLatchedShared
	    (new GetRoot(DbInternal.dbGetDatabaseImpl(db)));
        rootIN.latch();
        SearchResult result = tree.getParentINForChildIN(rootIN, true, true);
        assertFalse(result.exactParentFound);
        assertEquals(rootIN.getNEntries(), 2);
   
        /* Second and third level. */
        BIN dupTreeBin = null;
        DIN dupTreeDINRoot = null;
        firstBIN = null;
        int dupIndex = -1;
        for (int i = 0; i < rootIN.getNEntries(); i++) {
            /* Each level 2 IN. */
            IN in = (IN) rootIN.fetchTarget(i);
            if (i == 0) {
                firstLevel2IN = in;
            }
            checkMatch(tree, in, rootIN, i, requireExactMatch);

            /* For each BIN, find its parent, and then find its LNs. */
            for (int j = 0; j < in.getNEntries(); j++) {
                BIN bin = (BIN) in.fetchTarget(j);
                if (firstBIN == null) {
                    firstBIN = bin;
                }
                checkMatch(tree, bin, in, j, requireExactMatch);

                for (int k = 0; k < bin.getNEntries(); k++) {
                    Node n = bin.fetchTarget(k);
                    if (n instanceof LN) {
                        checkMatch(tree, (LN) n, bin, bin.getKey(k),
                                   null, k, bin.getLsn(k));
                    }
                }
                    
                int findIndex = bin.findEntry(dupKey, false, true);
                if (findIndex > 0) {
                    dupIndex = findIndex;
                    dupTreeDINRoot =
                        (DIN) bin.fetchTarget(dupIndex);
                    dupTreeBin = bin;
                }
            }
        }

        /* Check dup tree, assumes a 2 level tree. */
        assertTrue(dupTreeBin != null);
        assertTrue(dupTreeDINRoot != null);
        checkMatch(tree, dupTreeDINRoot, dupTreeBin, dupIndex,
                   requireExactMatch);
        assertTrue(dupTreeDINRoot.getNEntries() > 0);

        for (int i = 0; i < dupTreeDINRoot.getNEntries(); i++) {
            IN in = (IN) dupTreeDINRoot.fetchTarget(i);
            checkMatch(tree, in, dupTreeDINRoot, i, requireExactMatch);
            if (firstDBIN == null) {
                firstDBIN = (DBIN)in;
            }

            for (int k = 0; k < in.getNEntries(); k++) {
                Node n = in.fetchTarget(k);
                LN ln = (LN) n;
                checkMatch(tree, ln, (BIN)in, dupKey,
                           ln.getData(),
                           k, in.getLsn(k));
            }
        }
    }
            
    /*
     * Do a parent search, expect to find the parent, check that we do.
     */
    private void checkMatch(Tree tree,
                            IN target,
                            IN parent,
                            int index,
                            boolean requireExactMatch) 
        throws DatabaseException {

        target.latch();
        SearchResult result = tree.getParentINForChildIN
            (target, requireExactMatch, true);
        assertTrue(result.exactParentFound);
        assertEquals("Target=" + target + " parent=" + parent,
                     index, result.index);
        assertEquals(parent, result.parent);
        parent.releaseLatch();
    }

    /*
     * Search for the BIN for this LN.
     */
    private void checkMatch(Tree tree,
			    LN target,
			    BIN parent,
			    byte[] mainKey,
                            byte[] dupKey,
			    int index,
			    long expectedLsn) 
        throws DatabaseException {
        TreeLocation location = new TreeLocation();

        
        assertTrue
	    (tree.getParentBINForChildLN(location, mainKey, dupKey, target,
					 false, true, false, true));
        location.bin.releaseLatch();
        assertEquals(parent, location.bin);
        assertEquals(index, location.index);
        assertEquals(expectedLsn, location.childLsn);

        assertTrue
	    (tree.getParentBINForChildLN(location, mainKey, dupKey, target,
					 true, false, true, true));
        location.bin.releaseLatch();
        assertEquals(parent, location.bin);
        assertEquals(index, location.index);
        assertEquals(expectedLsn, location.childLsn);

        assertTrue
	    (tree.getParentBINForChildLN(location, mainKey, dupKey, target,
					 true, true, false, true));
        location.bin.releaseLatch();
        assertEquals(parent, location.bin);
        assertEquals(index, location.index);
        assertEquals(expectedLsn, location.childLsn);
    }

    private class GetRoot implements WithRootLatched {

        private DatabaseImpl db;
        
        GetRoot(DatabaseImpl db) {
	    this.db = db;
        }

        public IN doWork(ChildReference root)
            throws DatabaseException {

            return (IN) root.fetchTarget(db, null);
        }
    }

    /**
     * Make up non-existent nodes and see where they'd fit in. This exercises
     * recovery type processing and cleaning.
     */
    private void checkTreeUsingPotentialNodes() 
        throws DatabaseException {

        DatabaseImpl database = DbInternal.dbGetDatabaseImpl(db);
        Tree tree = database.getTree();

        /* 
         * Make an IN with the key "ab". Its potential parent should be the
         * first level 2 IN.
         */
        IN inAB = new IN(database, "ab".getBytes(), 4, 2);
        checkPotential(tree, inAB, firstLevel2IN);

        /* 
         * Make an BIN with the key "x". Its potential parent should be the
         * first level 2 IN.
         */
        BIN binAB =
	    new BIN(database, "ab".getBytes(), 4, 1);
        checkPotential(tree, binAB, firstLevel2IN);

        /*
         * Make a DIN with the key "a". Its potential parent should be BIN(c).
         */
        DIN dinA = new DIN(database,
                           "data1".getBytes(),
                           4,
                           "a".getBytes(),
                           null, 3);
        checkPotential(tree, dinA, firstBIN);

        /*
         * Make an LN with the key "ab". It's potential parent should be the 
         * BINa.
         */
        LN LNab = new LN("foo".getBytes());
        byte[] mainKey = "ab".getBytes();
        checkPotential(tree, LNab, firstBIN, mainKey,
                       LNab.getData(), mainKey);

        /**
         * Make a dup LN with the key b. Its potential parent should be DBINb.
         */
        LN LNdata = new LN("data".getBytes());
        mainKey = "b".getBytes();
        byte[] dupKey = LNdata.getData();
        checkPotential(tree, LNdata, firstDBIN, mainKey, dupKey, dupKey);
    }

    private void checkPotential(Tree tree, IN potential, IN expectedParent) 
        throws DatabaseException {

        /* Try an exact match, expect a failure, then try an inexact match. */
        potential.latch();
        SearchResult result = tree.getParentINForChildIN
            (potential, true, true);
        assertFalse(result.exactParentFound);
        assertTrue(result.parent == null);

        potential.latch();
        result = tree.getParentINForChildIN(potential, false, true);
        assertFalse(result.exactParentFound);
        assertEquals("expected = " + expectedParent.getNodeId() +
                     " got" + result.parent.getNodeId(),
                     expectedParent, result.parent);
        result.parent.releaseLatch();
    }

    private void checkPotential(Tree tree, LN potential, BIN expectedParent,
                                byte[] mainKey, byte[] dupKey, byte[] expectedKey)
        throws DatabaseException {

        /* Try an exact match, expect a failure, then try an inexact match. */
        TreeLocation location = new TreeLocation();
        assertFalse(tree.getParentBINForChildLN(location, 
						mainKey, dupKey,
						potential, false,
						false, true, true));
        location.bin.releaseLatch();
        assertEquals(location.bin, expectedParent);
        assertEquals(expectedKey, location.lnKey);
    }

    private void checkTreeWithDeletedBins(boolean requireExactMatch)
        throws DatabaseException {

	/**
	 * Mark all refs from the IN's to the BIN's as "known deleted".  Start
	 * at the root.
	 */
        DatabaseImpl database = DbInternal.dbGetDatabaseImpl(db);
        Tree tree = database.getTree();

        rootIN = tree.withRootLatchedShared
	    (new GetRoot(DbInternal.dbGetDatabaseImpl(db)));
   
        /* Second and third level. */
        for (int i = 0; i < rootIN.getNEntries(); i++) {
            /* Each level 2 IN. */
            IN in = (IN) rootIN.fetchTarget(i);
            for (int j = 0; j < in.getNEntries(); j++) {
                BIN bin = (BIN) in.getTarget(j);
		in.setKnownDeleted(j);
		checkDeletedMatch(tree, bin, in, j, requireExactMatch);
            }
	}
    }

    /*
     * Do a parent search, expect to find the parent, check that we do.
     */
    private void checkDeletedMatch(Tree tree,
				   IN target,
				   IN parent,
				   int index,
				   boolean requireExactMatch) 
        throws DatabaseException {

        target.latch();
        SearchResult result = tree.getParentINForChildIN
            (target, requireExactMatch, true);
        assertFalse(result.exactParentFound);
        assertEquals("Target=" + target + " parent=" + parent,
                     index, result.index);
	if (requireExactMatch) {
	    assertEquals(null, result.parent);
	} else {
	    assertEquals(parent, result.parent);
	    parent.releaseLatch();
	}
    }
}
