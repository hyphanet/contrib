/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LRUTest.java,v 1.3 2008/03/18 01:17:44 cwl Exp $
 */

package com.sleepycat.je.evictor;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Arrays;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests that the LRU algorithm is accurate.
 */
public class LRUTest extends TestCase {

    private static final int N_DBS = 5;
    private static final int ONE_MB = 1 << 20;
    private static final int DB_CACHE_SIZE = ONE_MB;
    private static final int ENV_CACHE_SIZE = N_DBS * DB_CACHE_SIZE;
    private static final int MIN_DATA_SIZE = 50 * 1024;
    private static final int LRU_ACCURACY_PCT = 60;
    private static final int ENTRY_DATA_SIZE = 500;

    private File envHome;
    private Environment env;
    private Database[] dbs = new Database[N_DBS];

    public LRUTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown() {
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
        dbs = null;
    }

    private void open()
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setCacheSize(ENV_CACHE_SIZE);
        envConfig.setConfigParam("je.tree.minMemory",
                                 String.valueOf(MIN_DATA_SIZE));
        envConfig.setConfigParam("je.env.runCleaner", "false");
        envConfig.setConfigParam("je.env.runCheckpointer", "false");
        envConfig.setConfigParam("je.env.runINCompressor", "false");

        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        for (int i = 0; i < dbs.length; i += 1) {
            dbs[i] = env.openDatabase(null, "foo-" + i, dbConfig);
        }
    }

    private void close()
        throws DatabaseException {

        for (int i = 0; i < N_DBS; i += 1) {
            if (dbs[i] != null) {
                dbs[i].close();
                dbs[i] = null;
            }
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    public void testBaseline()
        throws DatabaseException {

        open();
        for (int i = 0; i < N_DBS; i += 1) {
            write(dbs[i], DB_CACHE_SIZE);
        }
        long[] results = new long[100];
        for (int repeat = 0; repeat < 100; repeat += 1) {

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
            results[repeat] = pct;
        }
        Arrays.sort(results);
        //System.out.println(Arrays.toString(results));

        close();
    }

    public void testCacheMode_KEEP_HOT()
        throws DatabaseException {

        open();
        for (int i = 0; i < N_DBS; i += 1) {
            write(dbs[i], DB_CACHE_SIZE);
        }
        long[] results = new long[100];
        for (int repeat = 0; repeat < 100; repeat += 1) {

            /* Read all DBs evenly. */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            boolean done = false;
            Cursor[] cursors = new Cursor[N_DBS];
            for (int j = 0; j < N_DBS; j++) {
                cursors[j] = dbs[j].openCursor(null, null);
            }
            cursors[0].setCacheMode(CacheMode.KEEP_HOT);
            cursors[1].setCacheMode(CacheMode.KEEP_HOT);
            cursors[2].setCacheMode(CacheMode.DEFAULT);
            cursors[3].setCacheMode(CacheMode.DEFAULT);
            cursors[4].setCacheMode(CacheMode.DEFAULT);
            for (int i = 0; !done; i += 1) {
                IntegerBinding.intToEntry(i, key);
                for (int j = 0; j < N_DBS; j += 1) {
                    if (cursors[j].getSearchKey(key, data, null) !=
                        OperationStatus.SUCCESS) {
                        done = true;
                    }
                }
            }

            for (int j = 0; j < N_DBS; j++) {
                cursors[j].close();
            }

            /*
             * Check that db[0] and db[1] use more than the other three.
             */
            StringBuffer buf = new StringBuffer();
            long[] dbBytes = new long[N_DBS];
            for (int i = 0; i < N_DBS; i += 1) {
                dbBytes[i] = getDatabaseCacheBytes(dbs[i]);
                buf.append(" db=" + i + " bytes=" + dbBytes[i]);
            }
            assertTrue(dbBytes[0] > dbBytes[2]);
            assertTrue(dbBytes[0] > dbBytes[3]);
            assertTrue(dbBytes[0] > dbBytes[4]);
            assertTrue(dbBytes[1] > dbBytes[2]);
            assertTrue(dbBytes[1] > dbBytes[3]);
            assertTrue(dbBytes[1] > dbBytes[4]);
        }
        Arrays.sort(results);
        //System.out.println(Arrays.toString(results));

        close();
    }

    public void testCacheMode_UNCHANGED()
        throws DatabaseException {

        open();
        for (int i = 0; i < N_DBS; i += 1) {
            write(dbs[i], DB_CACHE_SIZE);
        }
        long[] results = new long[100];
        for (int repeat = 0; repeat < 100; repeat += 1) {

            /* Read all DBs evenly. */
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            boolean done = false;
            Cursor[] cursors = new Cursor[N_DBS];
            for (int j = 0; j < N_DBS; j++) {
                cursors[j] = dbs[j].openCursor(null, null);
            }
            cursors[0].setCacheMode(CacheMode.UNCHANGED);
            cursors[1].setCacheMode(CacheMode.UNCHANGED);
            cursors[2].setCacheMode(CacheMode.DEFAULT);
            cursors[3].setCacheMode(CacheMode.DEFAULT);
            cursors[4].setCacheMode(CacheMode.DEFAULT);
            for (int i = 0; !done; i += 1) {
                IntegerBinding.intToEntry(i, key);
                for (int j = 0; j < N_DBS; j += 1) {
                    if (cursors[j].getSearchKey(key, data, null) !=
                        OperationStatus.SUCCESS) {
                        done = true;
                    }
                }
            }

            for (int j = 0; j < N_DBS; j++) {
                cursors[j].close();
            }

            /*
             * Check that db[0] and db[1] use more than the other three.
             */
            StringBuffer buf = new StringBuffer();
            long[] dbBytes = new long[N_DBS];
            for (int i = 0; i < N_DBS; i += 1) {
                dbBytes[i] = getDatabaseCacheBytes(dbs[i]);
                buf.append(" db=" + i + " bytes=" + dbBytes[i]);
            }
            assertTrue(dbBytes[0] < dbBytes[2]);
            assertTrue(dbBytes[0] < dbBytes[3]);
            assertTrue(dbBytes[0] < dbBytes[4]);
            assertTrue(dbBytes[1] < dbBytes[2]);
            assertTrue(dbBytes[1] < dbBytes[3]);
            assertTrue(dbBytes[1] < dbBytes[4]);
            //System.out.println(buf);
        }
        Arrays.sort(results);
        //System.out.println(Arrays.toString(results));

        close();
    }

    private long getDatabaseCacheBytes(Database db)
        throws DatabaseException {

        long total = 0;
        DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        INList ins = dbImpl.getDbEnvironment().getInMemoryINs();
        Iterator i = ins.iterator();
        while (i.hasNext()) {
            IN in = (IN) i.next();
            if (in.getDatabase() == dbImpl) {
                total += in.getInMemorySize();
            }
        }
        return total;
    }

    /**
     * Writes enough records in the given envIndex environment to cause at
     * least minSizeToWrite bytes to be used in the cache.
     */
    private int write(Database db, int minSizeToWrite)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[ENTRY_DATA_SIZE]);
        int i;
        for (i = 0; i < minSizeToWrite / ENTRY_DATA_SIZE; i += 1) {
            IntegerBinding.intToEntry(i, key);
            db.put(null, key, data);
        }
        return i;
    }
}
