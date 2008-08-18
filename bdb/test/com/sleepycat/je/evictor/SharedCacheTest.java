/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: SharedCacheTest.java,v 1.11 2008/05/30 00:46:09 mark Exp $
 */

package com.sleepycat.je.evictor;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests the shared cache feature enabled via Environment.setSharedCache(true).
 */
public class SharedCacheTest extends TestCase {

    private static final int N_ENVS = 5;
    private static final int ONE_MB = 1 << 20;
    private static final int ENV_CACHE_SIZE = ONE_MB;
    private static final int TOTAL_CACHE_SIZE = N_ENVS * ENV_CACHE_SIZE;
    private static final int LOG_BUFFER_SIZE = (ENV_CACHE_SIZE * 7) / 100;
    private static final int MIN_DATA_SIZE = 50 * 1024;
    private static final int LRU_ACCURACY_PCT = 60;
    private static final int ENTRY_DATA_SIZE = 500;
    private static final String TEST_PREFIX = "SharedCacheTest_";
    private static final StatsConfig CLEAR_CONFIG = new StatsConfig();
    static {
        CLEAR_CONFIG.setClear(true);
    }

    private File envHome;
    private File[] dirs;
    private Environment[] envs;
    private Database[] dbs;
    private boolean sharedCache = true;

    public SharedCacheTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        dirs = new File[N_ENVS];
        envs = new Environment[N_ENVS];
        dbs = new Database[N_ENVS];

        for (int i = 0; i < N_ENVS; i += 1) {
            dirs[i] = new File(envHome, TEST_PREFIX + i);
            dirs[i].mkdir();
            assertTrue(dirs[i].isDirectory());
            TestUtils.removeLogFiles("Setup", dirs[i], false);
        }
    }

    public void tearDown() {
        for (int i = 0; i < N_ENVS; i += 1) {
            if (dbs[i] != null) {
                try {
                    dbs[i].close();
                } catch (Throwable e) {
                    System.out.println("tearDown: " + e);
                }
                dbs[i] = null;
            }
            if (envs[i] != null) {
                try {
                    envs[i].close();
                } catch (Throwable e) {
                    System.out.println("tearDown: " + e);
                }
                envs[i] = null;
            }
            if (dirs[i] != null) {
                try {
                    TestUtils.removeLogFiles("TearDown", dirs[i], false);
                } catch (Throwable e) {
                    System.out.println("tearDown: " + e);
                }
                dirs[i] = null;
            }
        }
        envHome = null;
        dirs = null;
        envs = null;
        dbs = null;
    }

    public void testBaseline()
        throws DatabaseException {

        /* Open all DBs in the same environment. */
        final int N_DBS = N_ENVS;
        sharedCache = false;
        openOne(0);
        DatabaseConfig dbConfig = dbs[0].getConfig();
        for (int i = 1; i < N_DBS; i += 1) {
            dbs[i] = envs[0].openDatabase(null, "foo" + i, dbConfig);
        }
        for (int i = 0; i < N_DBS; i += 1) {
            write(i, ENV_CACHE_SIZE);
        }

        for (int repeat = 0; repeat < 50; repeat += 1) {

            /* Read all DBs evenly. */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            boolean done = false;
            for (int i = 0; !done; i += 1) {
                IntegerBinding.intToEntry(i, key);
                for (int j = 0; j < N_DBS; j += 1) {
                    if (dbs[j].get(null, key, data, null) !=
                        OperationStatus.SUCCESS) {
                        done = true;
                    }
                }
            }

            /*
             * Check that each DB uses approximately equal portions of the
             * cache.
             */
            StringBuffer buf = new StringBuffer();
            long low = Long.MAX_VALUE;
            long high = 0;
            for (int i = 0; i < N_DBS; i += 1) {
                long val = getDatabaseCacheBytes(dbs[i]);
                buf.append(" db=" + i + " bytes=" + val);
                if (low > val) {
                    low = val;
                }
                if (high < val) {
                    high = val;
                }
            }
            long pct = (low * 100) / high;
            assertTrue("failed with pct=" + pct + buf,
                       pct >= LRU_ACCURACY_PCT);
        }

        for (int i = 1; i < N_DBS; i += 1) {
            dbs[i].close();
            dbs[i] = null;
        }
        closeOne(0);
    }

    private long getDatabaseCacheBytes(Database db) {
        long total = 0;
        DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        for (IN in : dbImpl.getDbEnvironment().getInMemoryINs()) {
            if (in.getDatabase() == dbImpl) {
                total += in.getInMemorySize();
            }
        }
        return total;
    }

    /**
     * Writes to each env one at a time, writing enough data in each env to
     * fill the entire cache.  Each env in turn takes up a large majority of
     * the cache.
     */
    public void testWriteOneEnvAtATime()
        throws DatabaseException {

        final int SMALL_DATA_SIZE = MIN_DATA_SIZE + (20 * 1024);
        final int SMALL_TOTAL_SIZE = SMALL_DATA_SIZE + LOG_BUFFER_SIZE;
        final int BIG_TOTAL_SIZE = ENV_CACHE_SIZE -
                                   ((N_ENVS - 1) * SMALL_TOTAL_SIZE);
        openAll();
        for (int i = 0; i < N_ENVS; i += 1) {
            write(i, TOTAL_CACHE_SIZE);
            EnvironmentStats stats = envs[i].getStats(null);
            String msg = "env=" + i +
                         " total=" + stats.getCacheTotalBytes() +
                         " shared=" + stats.getSharedCacheTotalBytes();
            assertTrue(stats.getSharedCacheTotalBytes() >= BIG_TOTAL_SIZE);
            assertTrue(msg, stats.getCacheTotalBytes() >= BIG_TOTAL_SIZE);
        }
        closeAll();
    }

    /**
     * Writes alternating records to each env, writing enough data to fill the
     * entire cache.  Each env takes up roughly equal portions of the cache.
     */
    public void testWriteAllEnvsEvenly()
        throws DatabaseException {

        openAll();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[ENTRY_DATA_SIZE]);
        for (int i = 0; i < ENV_CACHE_SIZE / ENTRY_DATA_SIZE; i += 1) {
            IntegerBinding.intToEntry(i, key);
            for (int j = 0; j < N_ENVS; j += 1) {
                dbs[j].put(null, key, data);
            }
            checkStatsConsistency();
        }
        checkEvenCacheUsage();
        closeAll();
    }

    /**
     * Checks that the cache usage changes appropriately as environments are
     * opened and closed.
     */
    public void testOpenClose()
        throws DatabaseException {

        openAll();
        int nRecs = 0;
        for (int i = 0; i < N_ENVS; i += 1) {
            int n = write(i, TOTAL_CACHE_SIZE);
            if (nRecs < n) {
                nRecs = n;
            }
        }
        closeAll();
        openAll();
        readEvenly(nRecs);
        /* Close only one. */
        for (int i = 0; i < N_ENVS; i += 1) {
            closeOne(i);
            readEvenly(nRecs);
            openOne(i);
            readEvenly(nRecs);
        }
        /* Close all but one. */
        for (int i = 0; i < N_ENVS; i += 1) {
            for (int j = 0; j < N_ENVS; j += 1) {
                if (j != i) {
                    closeOne(j);
                }
            }
            readEvenly(nRecs);
            for (int j = 0; j < N_ENVS; j += 1) {
                if (j != i) {
                    openOne(j);
                }
            }
            readEvenly(nRecs);
        }
        closeAll();
    }

    /**
     * Checks that an environment with hot data uses more of the cache.
     */
    public void testHotness()
        throws DatabaseException {

        final int HOT_CACHE_SIZE = (int) (1.5 * ENV_CACHE_SIZE);
        openAll();
        int nRecs = Integer.MAX_VALUE;
        for (int i = 0; i < N_ENVS; i += 1) {
            int n = write(i, TOTAL_CACHE_SIZE);
            if (nRecs > n) {
                nRecs = n;
            }
        }
        readEvenly(nRecs);
        /* Keep one env "hot". */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        for (int i = 0; i < N_ENVS; i += 1) {
            for (int j = 0; j < N_ENVS; j += 1) {
                for (int k = 0; k < nRecs; k += 1) {
                    IntegerBinding.intToEntry(k, key);
                    dbs[i].get(null, key, data, null);
                    dbs[j].get(null, key, data, null);
                }
                checkStatsConsistency();
                EnvironmentStats iStats = envs[i].getStats(null);
                EnvironmentStats jStats = envs[j].getStats(null);

                if (iStats.getCacheTotalBytes() < HOT_CACHE_SIZE ||
                    jStats.getCacheTotalBytes() < HOT_CACHE_SIZE) {

                    StringBuilder msg = new StringBuilder();
                    msg.append("Hot cache size is below " + HOT_CACHE_SIZE +
                               " for env " + i + " or " + j);
                    for (int k = 0; k < N_ENVS; k += 1) {
                        msg.append("\n**** ENV " + k + " ****\n");
                        msg.append(envs[k].getStats(null));
                    }
                    fail(msg.toString());
                }
            }
        }
        closeAll();
    }

    /**
     * Tests changing the cache size.
     */
    public void testMutateCacheSize()
        throws DatabaseException {

        final int HALF_CACHE_SIZE = TOTAL_CACHE_SIZE / 2;
        openAll();
        int nRecs = 0;
        for (int i = 0; i < N_ENVS; i += 1) {
            int n = write(i, ENV_CACHE_SIZE);
            if (nRecs < n) {
                nRecs = n;
            }
        }
        /* Full cache size. */
        readEvenly(nRecs);
        EnvironmentStats stats = envs[0].getStats(null);
        assertTrue(Math.abs
                   (TOTAL_CACHE_SIZE - stats.getSharedCacheTotalBytes())
                   < (TOTAL_CACHE_SIZE / 10));
        /* Halve cache size. */
        EnvironmentMutableConfig config = envs[0].getMutableConfig();
        config.setCacheSize(HALF_CACHE_SIZE);
        envs[0].setMutableConfig(config);
        readEvenly(nRecs);
        stats = envs[0].getStats(null);
        assertTrue(Math.abs
                   (HALF_CACHE_SIZE - stats.getSharedCacheTotalBytes())
                   < (HALF_CACHE_SIZE / 10));
        /* Full cache size. */
        config = envs[0].getMutableConfig();
        config.setCacheSize(TOTAL_CACHE_SIZE);
        envs[0].setMutableConfig(config);
        readEvenly(nRecs);
        stats = envs[0].getStats(null);
        assertTrue(Math.abs
                   (TOTAL_CACHE_SIZE - stats.getSharedCacheTotalBytes())
                   < (TOTAL_CACHE_SIZE / 10));
        closeAll();
    }

    private void openAll()
        throws DatabaseException {

        for (int i = 0; i < N_ENVS; i += 1) {
            openOne(i);
        }
    }

    private void openOne(int i)
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setSharedCache(sharedCache);
        envConfig.setCacheSize(TOTAL_CACHE_SIZE);
        envConfig.setConfigParam("je.tree.minMemory",
                                 String.valueOf(MIN_DATA_SIZE));
        envConfig.setConfigParam("je.env.runCleaner", "false");
        envConfig.setConfigParam("je.env.runCheckpointer", "false");
        envConfig.setConfigParam("je.env.runINCompressor", "false");

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        envs[i] = new Environment(dirs[i], envConfig);
        dbs[i] = envs[i].openDatabase(null, "foo", dbConfig);
    }

    private void closeAll()
        throws DatabaseException {

        for (int i = 0; i < N_ENVS; i += 1) {
            closeOne(i);
        }
    }

    private void closeOne(int i)
        throws DatabaseException {

        if (dbs[i] != null) {
            dbs[i].close();
            dbs[i] = null;
        }
        if (envs[i] != null) {
            envs[i].close();
            envs[i] = null;
        }
    }

    /**
     * Writes enough records in the given envIndex environment to cause at
     * least minSizeToWrite bytes to be used in the cache.
     */
    private int write(int envIndex, int minSizeToWrite)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[ENTRY_DATA_SIZE]);
        int i;
        for (i = 0; i < minSizeToWrite / ENTRY_DATA_SIZE; i += 1) {
            IntegerBinding.intToEntry(i, key);
            dbs[envIndex].put(null, key, data);
        }
        checkStatsConsistency();
        return i;
    }

    /**
     * Reads alternating records from each env, reading all records from each
     * env.  Checks that all environments use roughly equal portions of the
     * cache.
     */
    private void readEvenly(int nRecs)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Repeat reads twice to give the LRU a fighting chance. */
        for (int repeat = 0; repeat < 2; repeat += 1) {
            for (int i = 0; i < nRecs; i += 1) {
                IntegerBinding.intToEntry(i, key);
                for (int j = 0; j < N_ENVS; j += 1) {
                    if (dbs[j] != null) {
                        dbs[j].get(null, key, data, null);
                    }
                }
                checkStatsConsistency();
            }
        }
        checkEvenCacheUsage();
    }

    /**
     * Checks that each env uses approximately equal portions of the cache.
     * How equal the portions are depends on the accuracy of the LRU.
     */
    private void checkEvenCacheUsage()
        throws DatabaseException {

        StringBuffer buf = new StringBuffer();
        long low = Long.MAX_VALUE;
        long high = 0;
        for (int i = 0; i < N_ENVS; i += 1) {
            if (envs[i] != null) {
                EnvironmentStats stats = envs[i].getStats(null);
                long val = stats.getCacheTotalBytes();
                buf.append(" env=" + i + " bytes=" + val);
                if (low > val) {
                    low = val;
                }
                if (high < val) {
                    high = val;
                }
            }
        }
        long pct = (low * 100) / high;
        assertTrue("failed with pct=" + pct + buf, pct >= LRU_ACCURACY_PCT);
    }

    /**
     * Checks that the sum of all env cache usages is the total cache usage,
     * and other self-consistency checks.
     */
    private void checkStatsConsistency()
        throws DatabaseException {

        if (!sharedCache) {
            return;
        }
        long total = 0;
        long sharedTotal = -1;
        int nShared = 0;
        EnvironmentStats stats = null;

        for (int i = 0; i < N_ENVS; i += 1) {
            if (envs[i] != null) {
                stats = envs[i].getStats(null);
                total += stats.getCacheTotalBytes();
                nShared += 1;
                if (sharedTotal == -1) {
                    sharedTotal = stats.getSharedCacheTotalBytes();
                } else {
                    assertEquals(sharedTotal, stats.getSharedCacheTotalBytes());
                }
            }
        }
        assertEquals(sharedTotal, total);
        assertTrue(sharedTotal < TOTAL_CACHE_SIZE + (TOTAL_CACHE_SIZE / 10));
        assertEquals(nShared, stats.getNSharedCacheEnvironments());
    }
}
