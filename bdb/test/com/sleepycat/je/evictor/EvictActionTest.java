/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EvictActionTest.java,v 1.24.2.2 2007/07/02 19:54:55 mark Exp $
 */

package com.sleepycat.je.evictor;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.util.TestUtils;

/**
 * This tests exercises the act of eviction and determines whether the 
 * expected nodes have been evicted properly.
 */
public class EvictActionTest extends TestCase {

    private static final boolean DEBUG = false;
    private static final int NUM_KEYS = 60;
    private static final int NUM_DUPS = 30;
    private static final int BIG_CACHE_SIZE = 500000;
    private static final int SMALL_CACHE_SIZE = (int)
	MemoryBudget.MIN_MAX_MEMORY_SIZE;

    private File envHome = null;
    private Environment env = null;
    private Database db = null;
    private int actualLNs = 0;
    private int actualINs = 0;

    public EvictActionTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

	IN.ACCUMULATED_LIMIT = 0;
	Txn.ACCUMULATED_LIMIT = 0;

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        if (env != null) {
            try {
                env.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
        }

        try {
            TestUtils.removeLogFiles("TearDown", envHome, false);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        envHome = null;
        env = null;
        db = null;
    }

    public void testEvict()
        throws Throwable {

        try {
            doEvict(50, SMALL_CACHE_SIZE, true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testNoNeedToEvict()
        throws Throwable {

        try {
            doEvict(80, BIG_CACHE_SIZE, false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Evict in very controlled circumstances. Check that we first strip
     * BINs and later evict BINS.
     */
    private void doEvict(int floor,
                         int maxMem,
                         boolean shouldEvict) 
        throws Throwable {

        try {
            openEnv(floor, maxMem);
            insertData(NUM_KEYS);

            /* Evict once after insert. */
            evictAndCheck(shouldEvict, NUM_KEYS);

            /* Evict again after verification. */
            evictAndCheck(shouldEvict, NUM_KEYS);

            closeEnv();
        } catch (Throwable t) {
            t.printStackTrace();
            throw (t);
        }
    }

    public void testSetCacheSize()
        throws DatabaseException {

        /* Start with large cache size. */
        openEnv(80, BIG_CACHE_SIZE);
        EnvironmentMutableConfig config = env.getMutableConfig();
        insertData(NUM_KEYS);

        /* No need to evict. */
        verifyData(NUM_KEYS);
        evictAndCheck(false, NUM_KEYS);

        /* Set small cache size. */
        config.setCacheSize(SMALL_CACHE_SIZE);
        env.setMutableConfig(config);

        /* Expect eviction. */
        verifyData(NUM_KEYS);
        evictAndCheck(true, NUM_KEYS);

        /* Set large cache size. */
        config.setCacheSize(BIG_CACHE_SIZE);
        env.setMutableConfig(config);

        /* Expect no eviction. */
        verifyData(NUM_KEYS);
        evictAndCheck(false, NUM_KEYS);

        closeEnv();
    }

    public void testSetCachePercent()
        throws DatabaseException {

        int nKeys = NUM_KEYS * 500;

        /* Start with large cache size. */
        openEnv(80, BIG_CACHE_SIZE);
        EnvironmentMutableConfig config = env.getMutableConfig();
        config.setCacheSize(0);
        config.setCachePercent(90);
        env.setMutableConfig(config);
        insertData(nKeys);

        /* No need to evict. */
        verifyData(nKeys);
        evictAndCheck(false, nKeys);

        /* Set small cache percent. */
        config.setCacheSize(0);
        config.setCachePercent(1);
        env.setMutableConfig(config);

        /* Expect eviction. */
        verifyData(nKeys);
        evictAndCheck(true, nKeys);

        /* Set large cache percent. */
        config.setCacheSize(0);
        config.setCachePercent(90);
        env.setMutableConfig(config);

        /* Expect no eviction. */
        verifyData(nKeys);
        evictAndCheck(false, nKeys);

        closeEnv();
    }

    public void testThreadedCacheSizeChanges()
        throws DatabaseException {

        final int N_ITERS = 10;
        openEnv(80, BIG_CACHE_SIZE);
        insertData(NUM_KEYS);

        JUnitThread writer = new JUnitThread("Writer") {
            public void testBody() 
                throws DatabaseException {
                for (int i = 0; i < N_ITERS; i += 1) {
                    env.evictMemory();
                    /* insertData will update if data exists. */
                    insertData(NUM_KEYS);
                    env.evictMemory();
                    EnvironmentMutableConfig config = env.getMutableConfig();
                    config.setCacheSize(SMALL_CACHE_SIZE);
                    env.setMutableConfig(config);
                }
            }
        };

        JUnitThread reader = new JUnitThread("Reader") {
            public void testBody() 
                throws DatabaseException {
                for (int i = 0; i < N_ITERS; i += 1) {
                    env.evictMemory();
                    verifyData(NUM_KEYS);
                    env.evictMemory();
                    EnvironmentMutableConfig config = env.getMutableConfig();
                    config.setCacheSize(BIG_CACHE_SIZE);
                    env.setMutableConfig(config);
                }
            }
        };

        writer.start();
        reader.start();

        try {
            writer.finishTest();
        } catch (Throwable e) {
            try {
                reader.finishTest();
            } catch (Throwable ignore) { }
            e.printStackTrace();
            fail(e.toString());
        }

        try {
            reader.finishTest();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        }

        closeEnv();
    }

    /**
     * We now allow eviction of the root IN of a DB, whether the DB is closed
     * or not.  Check that basic root eviction works.  [#13415]
     */
    public void testRootINEviction()
        throws DatabaseException {

        DatabaseEntry entry = new DatabaseEntry(new byte[1]);
        OperationStatus status;

        openEnv(80, SMALL_CACHE_SIZE);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db1 = env.openDatabase(null, "db1", dbConfig);

        /* Root starts out null. */
        assertTrue(!isRootResident(db1));
        /* It is created when we insert the first record. */
        status = db1.put(null, entry, entry);
        assertSame(OperationStatus.SUCCESS, status);
        assertTrue(isRootResident(db1));
        /* It is evicted when necessary. */
        forceEviction();
        assertTrue(!isRootResident(db1));
        /* And fetched again when needed. */
        status = db1.get(null, entry, entry, null);
        assertSame(OperationStatus.SUCCESS, status);
        assertTrue(isRootResident(db1));

        /* Deferred write DBs have special rules. */
        dbConfig.setDeferredWrite(true);
        Database db2 = env.openDatabase(null, "db2", dbConfig);
        status = db2.put(null, entry, entry);
        assertSame(OperationStatus.SUCCESS, status);
        assertTrue(isRootResident(db2));
        /* Root eviction is disallowed if the root is dirty. */
        forceEviction();
        assertTrue(isRootResident(db2));
        db2.sync();
        forceEviction();
        assertTrue(!isRootResident(db2));

        db2.close();
        db1.close();
        closeEnv();
    }

    /**
     * We now allow eviction of the MapLN and higher level INs in the DB mappng
     * tree when DBs are closed.  Check that basic mapping tree IN eviction
     * works.  [#13415]
     */
    public void testMappingTreeEviction()
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        DatabaseEntry entry = new DatabaseEntry(new byte[1]);
        OperationStatus status;

        openEnv(80, SMALL_CACHE_SIZE);

        /* Baseline mappng tree LNs and INs. */
        final int baseLNs = 2; // Utilization DB and test DB
        final int baseINs = 2; // Root IN and BIN
        checkMappingTree(baseLNs, baseINs);
        forceEviction();
        checkMappingTree(baseLNs, baseINs);

        /*
         * Create enough DBs to fill up a BIN in the mapping DB.  NODE_MAX is
         * configured to be 4 in this test.  There are already 2 DBs open.
         */
        final int nDbs = 4;
        Database[] dbs = new Database[nDbs];
        for (int i = 0; i < nDbs; i += 1) {
            dbs[i] = env.openDatabase(null, "db" + i, dbConfig);
            status = dbs[i].put(null, entry, entry);
            assertSame(OperationStatus.SUCCESS, status);
            assertTrue(isRootResident(dbs[i]));
        }
        final int openLNs = baseLNs + nDbs; // Add 1 MapLN per open DB
        final int openINs = baseINs + 1;    // Add 1 BIN in the mapping tree
        checkMappingTree(openLNs, openINs);
        forceEviction();
        checkMappingTree(openLNs, openINs);

        /* Close DBs and force eviction. */
        for (int i = 0; i < nDbs; i += 1) {
            dbs[i].close();
        }
        forceEviction();
        checkMappingTree(baseLNs, baseINs);

        /* Re-open the DBs, opening each DB twice. */
        Database[] dbs2 = new Database[nDbs];
        for (int i = 0; i < nDbs; i += 1) {
            dbs[i] = env.openDatabase(null, "db" + i, dbConfig);
            dbs2[i] = env.openDatabase(null, "db" + i, dbConfig);
        }
        checkMappingTree(openLNs, openINs);
        forceEviction();
        checkMappingTree(openLNs, openINs);

        /* Close one handle only, MapLN eviction should not occur. */
        for (int i = 0; i < nDbs; i += 1) {
            dbs[i].close();
        }
        forceEviction();
        checkMappingTree(openLNs, openINs);

        /* Close the other handles, eviction should occur. */
        for (int i = 0; i < nDbs; i += 1) {
            dbs2[i].close();
        }
        forceEviction();
        checkMappingTree(baseLNs, baseINs);

        closeEnv();
    }

    /**
     * Check that opening a database in a transaction and then aborting the
     * transaction will decrement the database use count.  [#13415]
     */
    public void testAbortOpen()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_DB_EVICTION.getName(), "true");
        env = new Environment(envHome, envConfig);

        /* Abort the txn used to open a database. */
        Transaction txn = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        Database db1 = env.openDatabase(txn, "db1", dbConfig);
        DatabaseImpl saveDb = DbInternal.dbGetDatabaseImpl(db1);
        txn.abort();

        /* DB should not be in use and does not have to be closed. */
        assertEquals(false, saveDb.isInUse());

        /*
         * Environment.close will not throw an exception, even though the DB
         * has not been closed.  The abort took care of cleaning up the handle.
         */
        closeEnv();

        /*
         * Try a non-transactional DB open that throws an exception because we
         * create it exclusively and it already exists.  The use count should
         * be decremented.
         */
        env = new Environment(envHome, envConfig);
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        dbConfig.setTransactional(false);
        db1 = env.openDatabase(null, "db1", dbConfig);
        saveDb = DbInternal.dbGetDatabaseImpl(db1);
        try {
            env.openDatabase(null, "db1", dbConfig);
            fail();
        } catch (DatabaseException e) {
            assertTrue(e.getMessage().indexOf("already exists") >= 0);
        }
        db1.close();
        assertEquals(false, saveDb.isInUse());

        /*
         * Try a non-transactional DB open that throws an exception because we
         * change the duplicatesAllowed setting.  The use count should be
         * decremented.
         */
        dbConfig.setSortedDuplicates(true);
        dbConfig.setExclusiveCreate(false);
        try {
            env.openDatabase(null, "db1", dbConfig);
            fail();
        } catch (DatabaseException e) {
            assertTrue(e.getMessage().indexOf("duplicatesAllowed") >= 0);
        }
        assertEquals(false, saveDb.isInUse());

        closeEnv();
    }

    /**
     * Check for the expected number of nodes in the mapping DB.
     */
    private void checkMappingTree(int expectLNs, int expectINs)
        throws DatabaseException {

        IN root = DbInternal.envGetEnvironmentImpl(env)
                            .getDbMapTree()
                            .getDb(DbTree.ID_DB_ID)
                            .getTree()
                            .getRootIN(false);
        actualLNs = 0;
        actualINs = 0;
        countMappingTree(root);
        root.releaseLatch();
        assertEquals("LNs", expectLNs, actualLNs);
        assertEquals("INs", expectINs, actualINs);
    }

    private void countMappingTree(IN parent) {
        actualINs += 1;
        for (int i = 0; i < parent.getNEntries(); i += 1) {
            if (parent.getTarget(i) != null) {
                if (parent.getTarget(i) instanceof IN) {
                    countMappingTree((IN) parent.getTarget(i));
                } else {
                    actualLNs += 1;
                }
            }
        }
    }

    /**
     * Returns whether the root IN is currently resident for the given DB.
     */
    private boolean isRootResident(Database dbParam) {
        return DbInternal.dbGetDatabaseImpl(dbParam)
                         .getTree()
                         .isRootResident();
    }

    /**
     * Force eviction by inserting a large record in the pre-opened DB.
     */
    private void forceEviction()
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        MemoryBudget mb = envImpl.getMemoryBudget();
        OperationStatus status;

        /*
         * Repeat twice to cause a 2nd pass over the INList.  The second pass
         * evicts BINs that were only stripped of LNs in the first pass.
         */
        for (int i = 0; i < 2; i += 1) {
            status = db.put(null, new DatabaseEntry(new byte[1]),
                                  new DatabaseEntry(new byte[BIG_CACHE_SIZE]));
            assertSame(OperationStatus.SUCCESS, status);

            long preEvictMem = mb.getCacheMemoryUsage();
            env.evictMemory();
            long postEvictMem = mb.getCacheMemoryUsage();
            assertTrue(preEvictMem > postEvictMem);

            status = db.delete(null, new DatabaseEntry(new byte[1]));
            assertSame(OperationStatus.SUCCESS, status);
        }

        TestUtils.validateNodeMemUsage(envImpl, true);
    }

    /**
     * Open an environment and database.
     */
    private void openEnv(int floor,
                         int maxMem) 
        throws DatabaseException {

        /* Convert floor percentage into bytes. */
        long evictBytes = maxMem - ((maxMem * floor) / 100);

        /* Make a non-txnal env w/no daemons and small nodes. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 EVICTOR_EVICT_BYTES.getName(),
                                 (new Long(evictBytes)).toString());
        envConfig.setConfigParam(EnvironmentParams.
                                 MAX_MEMORY.getName(),
                                 new Integer(maxMem).toString());
        /* Don't track detail with a tiny cache size. */
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.LOG_MEM_SIZE.getName(),
				 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
        envConfig.setConfigParam(EnvironmentParams.NUM_LOG_BUFFERS.getName(),
				 "2");
        /* Enable DB (MapLN) eviction for eviction tests. */
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_DB_EVICTION.getName(), "true");

        /* 
         * Disable critical eviction, we want to test under controlled
         * circumstances.
         */
        envConfig.setConfigParam(EnvironmentParams.
                                 EVICTOR_CRITICAL_PERCENTAGE.getName(),
                                 "1000");

        /* Make small nodes */
        envConfig.setConfigParam(EnvironmentParams.
                                 NODE_MAX.getName(), "4");
        envConfig.setConfigParam(EnvironmentParams.
                                 NODE_MAX_DUPTREE.getName(), "4");
        env = new Environment(envHome, envConfig);

        /* Open a database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }

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

    private void insertData(int nKeys) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < nKeys; i++) {

            IntegerBinding.intToEntry(i, key);

            if ((i % 5) == 0) {
                for (int j = 10; j < (NUM_DUPS + 10); j++) {
                    IntegerBinding.intToEntry(j, data);
                    db.put(null, key, data);
                }
            } else {
                IntegerBinding.intToEntry(i+1, data);
                db.put(null, key, data);
            }
        }
    }

    private void verifyData(int nKeys)
        throws DatabaseException {

        /* Full scan of data, make sure we can bring everything back in. */
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry();
        
        for (int i = 0; i < nKeys; i++) {
            if ((i % 5) ==0) {
                for (int j = 10; j < (NUM_DUPS + 10); j++) {
                    assertEquals(OperationStatus.SUCCESS,
                                 cursor.getNext(key, data, LockMode.DEFAULT));
                    assertEquals(i, IntegerBinding.entryToInt(key));
                    assertEquals(j, IntegerBinding.entryToInt(data));
                }
            } else {
                assertEquals(OperationStatus.SUCCESS,
                             cursor.getNext(key, data, LockMode.DEFAULT));
                assertEquals(i, IntegerBinding.entryToInt(key));
                assertEquals(i+1, IntegerBinding.entryToInt(data));
            }
        }

        assertEquals(OperationStatus.NOTFOUND,
                     cursor.getNext(key, data, LockMode.DEFAULT));
        cursor.close();
    }

    private void evictAndCheck(boolean shouldEvict, int nKeys) 
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        MemoryBudget mb = envImpl.getMemoryBudget();

        /* 
         * The following batches are run in a single evictMemory() call:
         * 1st eviction will strip DBINs.
         * 2nd will evict DBINs
         * 3rd will evict DINs
         * 4th will strip BINs
         * 5th will evict BINs
         * 6th will evict INs
         * 7th will evict INs
         */
        long preEvictMem = mb.getCacheMemoryUsage();
        TestUtils.validateNodeMemUsage(envImpl, true);
        env.evictMemory();
        long postEvictMem = mb.getCacheMemoryUsage();

        TestUtils.validateNodeMemUsage(envImpl, true);
        if (DEBUG) {
            System.out.println("preEvict=" + preEvictMem +
                               " postEvict=" + postEvictMem);
        }

        if (shouldEvict) {
            assertTrue("preEvict=" + preEvictMem +
                       " postEvict=" + postEvictMem +
                       " maxMem=" + mb.getMaxMemory(),
                       (preEvictMem > postEvictMem));
        } else {
            assertTrue("preEvict=" + preEvictMem +
                       " postEvict=" + postEvictMem,
                       (preEvictMem == postEvictMem));
        }

        verifyData(nKeys);
        TestUtils.validateNodeMemUsage(envImpl, true);
    }
}
