/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EvictSelectionTest.java,v 1.14.2.2 2007/07/02 19:54:55 mark Exp $
 */

package com.sleepycat.je.evictor;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;

public class EvictSelectionTest extends TestCase {
    private static boolean DEBUG = false;
    private static String DB_NAME = "EvictionSelectionTestDb";
    private File envHome;
    private int scanSize = 5;
    private Environment env;
    private EnvironmentImpl envImpl;

    public EvictSelectionTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
	throws Exception {

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }


    public void testEvictPass() 
        throws Throwable {

        /* Create an environment, database, and insert some data. */
        try {
            initialize(true);

            EnvironmentStats stats = new EnvironmentStats();
            StatsConfig statsConfig = new StatsConfig();
            statsConfig.setClear(true);

            /* 
             * Set up the test w/a number of INs that doesn't divide evenly
             * into scan sets.
             */
            int startingNumINs = envImpl.getInMemoryINs().getSize();
            assertTrue((startingNumINs % scanSize) != 0);

            Evictor evictor = envImpl.getEvictor();
            evictor.loadStats(statsConfig, stats);

            /* 
             * Test evictBatch, where each batch only evicts one node because
             * we are passing one byte for the currentRequiredEvictBytes
             * parameter.  To predict the evicted nodes when more than one
             * target is selected, we would have to simulate eviction and
             * maintain a parallel IN tree, which is too complex.
             */
            for (int batch = 1;; batch += 1) {

                List expectedCandidates = new ArrayList();
                int expectedNScanned = getExpectedCandidates
                    (envImpl, evictor, expectedCandidates);

                evictor.evictBatch("test", false, 1);

                evictor.loadStats(statsConfig, stats);
                assertEquals(1, stats.getNEvictPasses());
                assertEquals(expectedNScanned, stats.getNNodesScanned());

                List candidates = evictor.evictProfile.getCandidates();
                assertEquals(expectedCandidates, candidates);

                /* Stop when no more nodes are evictable. */
                if (expectedCandidates.isEmpty()) {
                    break;
                }
            }

            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw (t);
        }
    }

    /* 
     * We might call evict on an empty INList if the cache is set very low
     * at recovery time.
     */
    public void testEmptyINList() 
        throws Throwable {

        /* Create an environment, database, and insert some data. */
        try {
            initialize(true);

            env.close();
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
            env = new Environment(envHome, envConfig);
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw (t);
        }
    }

    /* 
     * Create an environment, database, and insert some data.
     */
    private void initialize(boolean makeDatabase) 
        throws DatabaseException {

        /* Environment */

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_EVICTOR.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_CLEANER.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_CHECKPOINTER.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 ENV_RUN_INCOMPRESSOR.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.
                                 NODE_MAX.getName(), "4");
        envConfig.setConfigParam(EnvironmentParams.
                                 EVICTOR_NODES_PER_SCAN.getName(), "5");
        if (DEBUG) {
            envConfig.setConfigParam(EnvironmentParams.
                                     JE_LOGGING_CONSOLE.getName(), "true");
            envConfig.setConfigParam(EnvironmentParams.
                                     JE_LOGGING_LEVEL_EVICTOR.getName(),
                                     "SEVERE");
        }
        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        if (makeDatabase) {
            /* Database */

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            Database db = env.openDatabase(null, "foo", dbConfig);
        
            /* Insert enough keys to get an odd number of nodes */

            DatabaseEntry keyAndData = new DatabaseEntry();
            for (int i = 0; i < 110; i++) {
                IntegerBinding.intToEntry(i, keyAndData);
                db.put(null, keyAndData, keyAndData);
            }

            db.close();
        }
    }

    /**
     * Returns the number of INs selected (examined) and fills the expected
     * list with the selected targets.  Currently only one target is selected.
     */
    private int getExpectedCandidates(EnvironmentImpl envImpl,
                                      Evictor evictor,
                                      List expected)
        throws DatabaseException {

        boolean evictByLruOnly = envImpl.getConfigManager().getBoolean
            (EnvironmentParams.EVICTOR_LRU_ONLY);

        INList inList = envImpl.getInMemoryINs();
        inList.latchMajor();

        IN nextNode = evictor.getNextNode();
        if (nextNode == null) {
            nextNode = (IN) inList.first();
        }
        Iterator inIter = inList.tailSet(nextNode).iterator();

        long targetGeneration = Long.MAX_VALUE;
        int targetLevel = Integer.MAX_VALUE;
        boolean targetDirty = true;
        IN target = null;
        
        boolean wrapped = false;
        int nScanned = 0;
        int nCandidates = 0;
        while (nCandidates < scanSize) {

            if (!inIter.hasNext()) {
                if (wrapped) {
                    break;
                } else {
                    inIter = inList.tailSet(inList.first()).iterator();
                    wrapped = true;
                }
            }

            IN in = (IN) inIter.next();
            nScanned += 1;

            if (in.getDatabase() == null || in.getDatabase().isDeleted()) {
                continue;
            }

            int evictType = in.getEvictionType();
            if (evictType == IN.MAY_NOT_EVICT) {
                continue;
            }

            if (evictByLruOnly) {
                if (in.getGeneration() < targetGeneration) {
                    targetGeneration = in.getGeneration();
                    target = in;
                }
            } else {
                int level = evictor.normalizeLevel(in, evictType);
                if (targetLevel != level) {
                    if (targetLevel > level) {
                        targetLevel = level;
                        targetDirty = in.getDirty();
                        targetGeneration = in.getGeneration();
                        target = in;
                    }
                } else if (targetDirty != in.getDirty()) {
                    if (targetDirty) {
                        targetDirty = false;
                        targetGeneration = in.getGeneration();
                        target = in;
                    }
                } else {
                    if (targetGeneration > in.getGeneration()) {
                        targetGeneration = in.getGeneration();
                        target = in;
                    }
                }
            }

            nCandidates++;
        }

        inList.releaseMajorLatch();

        expected.clear();
        if (target != null) {
            expected.add(new Long(target.getNodeId()));
        }
        return nScanned;
    }
}
