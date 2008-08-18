/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EvictNNodesStatsTest.java,v 1.3 2008/03/18 01:17:44 cwl Exp $
 */

package com.sleepycat.je.evictor;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.util.TestUtils;

/**
 * This tests exercises the act of eviction and determines whether the
 * expected nodes have been evicted properly.
 */
public class EvictNNodesStatsTest extends TestCase {

    private static final boolean DEBUG = false;
    private static final int BIG_CACHE_SIZE = 500000;
    private static final int SMALL_CACHE_SIZE = (int)
    MemoryBudget.MIN_MAX_MEMORY_SIZE;

    private File envHome = null;
    private Environment env = null;
    private Database db = null;
    private int actualLNs = 0;
    private int actualINs = 0;

    public EvictNNodesStatsTest() {
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

    /**
     * Check that the counters of evicted MapLNs in the DB mapping tree and
     * the counter of evicted BINs in a regular DB eviction works.  [#13415]
     */
    public void testRegularDB()
        throws DatabaseException {

        /* Initialize an environment and open a test DB. */
        openEnv(80, SMALL_CACHE_SIZE);

        EnvironmentStats stats = new EnvironmentStats();
        StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        DatabaseEntry entry = new DatabaseEntry(new byte[1]);
        OperationStatus status;

        /* Baseline mapping tree LNs and INs. */
        final int baseLNs = 2; // Utilization DB and test DB
        final int baseINs = 2; // Root IN and BIN
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
        checkMappingTree(baseLNs + nDbs /*Add 1 MapLN per open DB*/,
                         baseINs + 1 /*Add 1 BIN in the mapping tree*/);

        /* Close DBs and force eviction. */
        for (int i = 0; i < nDbs; i += 1) {
            dbs[i].close();
        }

        forceEviction();
        /* Load Stats. */
        DbInternal.envGetEnvironmentImpl(env).
                   getEvictor().
                   loadStats(statsConfig, stats);
        assertEquals("Evicted MapLNs",
                     nDbs + 1, // nDbs and Utilization DB
                     stats.getNRootNodesEvicted());
        assertEquals("Evicted BINs",
                     nDbs + 4, // 2 BINs for Name DB, 1 for Mapping DB,
                               // 1 for Utilization DB and 1 per each nDb
                     stats.getNNodesExplicitlyEvicted());
        checkMappingTree(baseLNs, baseINs);

        closeEnv();
    }

    /**
     * Check that the counters of evicted MapLNs in the DB mapping tree and
     * the counter of evicted BINs in a deferred write DB eviction works.
     * [#13415]
     */
    public void testDeferredWriteDB()
        throws DatabaseException {

        /* Initialize an environment and open a test DB. */
        openEnv(80, SMALL_CACHE_SIZE);

        EnvironmentStats stats = new EnvironmentStats();
        StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        DatabaseEntry entry = new DatabaseEntry(new byte[1]);
        OperationStatus status;

        /* Baseline mapping tree LNs and INs. */
        final int baseLNs = 2; // Utilization DB and test DB
        final int baseINs = 2; // Root IN and BIN

        checkMappingTree(baseLNs, baseINs);

        /* Deferred write DBs have special rules. */
        dbConfig.setDeferredWrite(true);
        Database db2 = env.openDatabase(null, "db2", dbConfig);
        status = db2.put(null, entry, entry);
        assertSame(OperationStatus.SUCCESS, status);
        assertTrue(isRootResident(db2));
        checkMappingTree(baseLNs + 1, baseINs); // Deferred Write DB.

        /* Root eviction is disallowed if the root is dirty. */
        forceEviction();
        /* Load Stats. */
        DbInternal.envGetEnvironmentImpl(env).
                   getEvictor().
                   loadStats(statsConfig, stats);
        assertEquals("Evicted MapLNs",
                     1, // Utilization DB.
                     stats.getNRootNodesEvicted());
        assertEquals("Evicted BINs",
                     3, // 1 BIN for Name DB, 1 for Utilization DB,
                        // and 1 for Deferred Write DB.
                     stats.getNNodesExplicitlyEvicted());
        assertTrue(isRootResident(db2));
        checkMappingTree(baseLNs + 1, baseINs); // Deferred Write DB.

        db2.sync();
        forceEviction();
        /* Load Stats. */
        DbInternal.envGetEnvironmentImpl(env).
                   getEvictor().
                   loadStats(statsConfig, stats);
        assertEquals("Evicted MapLNs",
                     1, // Root eviction.
                     stats.getNRootNodesEvicted());
        assertEquals("Evicted BINs",
                     0,
                     stats.getNNodesExplicitlyEvicted());
        assertTrue(!isRootResident(db2));
        checkMappingTree(baseLNs + 1, baseINs); // Deferred Write DB.

        db2.close();
        forceEviction();
        /* Load Stats. */
        DbInternal.envGetEnvironmentImpl(env).
                   getEvictor().
                   loadStats(statsConfig, stats);
        assertEquals("Evicted MapLNs",
                     1, // Root eviction.
                     stats.getNRootNodesEvicted());
        assertEquals("Evicted BINs",
                     0,
                     stats.getNNodesExplicitlyEvicted());

        checkMappingTree(baseLNs + 1, baseINs); // Deferred Write DB.

        closeEnv();
    }

    private void forceEviction()
        throws DatabaseException {

        OperationStatus status;

        /*
         * Repeat twice to cause a 2nd pass over the INList.  The second pass
         * evicts BINs that were only stripped of LNs in the first pass.
         */
        for (int i = 0; i < 2; i += 1) {
            /* Fill up cache so as to call eviction. */
            status = db.put(null, new DatabaseEntry(new byte[1]),
                                  new DatabaseEntry(new byte[BIG_CACHE_SIZE]));
            assertSame(OperationStatus.SUCCESS, status);

            /* Do a manual call eviction. */
            env.evictMemory();

            status = db.delete(null, new DatabaseEntry(new byte[1]));
            assertSame(OperationStatus.SUCCESS, status);
        }
    }

    /**
     * Check for the expected number of nodes in the mapping DB.
     */
    private void checkMappingTree(int expectLNs, int expectINs)
        throws DatabaseException {

        IN root = DbInternal.envGetEnvironmentImpl(env).
            getDbTree().getDb(DbTree.ID_DB_ID).getTree().
            getRootIN(CacheMode.UNCHANGED);
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
        return DbInternal.dbGetDatabaseImpl(dbParam).
                          getTree().
                          isRootResident();
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
        /* Enable DB (MapLN) eviction for eviction tests. */
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_DB_EVICTION.getName(), "true");

        /* Make small nodes */
        envConfig.setConfigParam(EnvironmentParams.
                                 NODE_MAX.getName(), "4");
        envConfig.setConfigParam(EnvironmentParams.
                                 NODE_MAX_DUPTREE.getName(), "4");
        if (DEBUG) {
            envConfig.setConfigParam(EnvironmentParams.
                                     JE_LOGGING_CONSOLE.getName(), "true");
            envConfig.setConfigParam(EnvironmentParams.
                                     JE_LOGGING_LEVEL_EVICTOR.getName(),
                                     "SEVERE");
        }
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
}
