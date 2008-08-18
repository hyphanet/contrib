/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStatTest.java,v 1.23 2008/03/25 02:26:37 linda Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.util.TestUtils;

public class EnvironmentStatTest extends TestCase {

    private Environment env;
    private File envHome;

    public EnvironmentStatTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        /* Close down environments in case the unit test failed so that
         * the log files can be removed.
         */
        try {
            if (env != null) {
                env.close();
                env = null;
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Test open and close of an environment.
     */
    public void testCacheStats()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
        EnvironmentStats stat = env.getStats(TestUtils.FAST_STATS);
        env.close();
        env = null;
        assertEquals(0, stat.getNCacheMiss());
        assertEquals(0, stat.getNNotResident());

        // Try to open and close again, now that the environment exists
        envConfig.setAllowCreate(false);
        envConfig.setConfigParam
            (EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
        env = new Environment(envHome, envConfig);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "foo", dbConfig);
        db.put(null, new DatabaseEntry(new byte[0]),
                     new DatabaseEntry(new byte[0]));
        Transaction txn = env.beginTransaction(null, null);
        db.put(txn, new DatabaseEntry(new byte[0]),
                    new DatabaseEntry(new byte[0]));
        stat = env.getStats(TestUtils.FAST_STATS);
        MemoryBudget mb =
            DbInternal.envGetEnvironmentImpl(env).getMemoryBudget();

        assertEquals(mb.getCacheMemoryUsage(), stat.getCacheTotalBytes());
        assertEquals(mb.getLogBufferBudget(), stat.getBufferBytes());
        assertEquals(mb.getTreeMemoryUsage() + mb.getTreeAdminMemoryUsage(),
                     stat.getDataBytes());
        assertEquals(mb.getLockMemoryUsage(), stat.getLockBytes());
        assertEquals(mb.getAdminMemoryUsage(), stat.getAdminBytes());

        assertTrue(stat.getBufferBytes() > 0);
        assertTrue(stat.getDataBytes() > 0);
        assertTrue(stat.getLockBytes() > 0);
        assertTrue(stat.getAdminBytes() > 0);

        assertEquals(stat.getCacheTotalBytes(),
                     stat.getBufferBytes() +
                     stat.getDataBytes() +
                     stat.getLockBytes() +
                     stat.getAdminBytes());

        assertEquals(12, stat.getNCacheMiss());
        assertEquals(12, stat.getNNotResident());

        /* Test deprecated getCacheDataBytes method. */
        final EnvironmentStats finalStat = stat;
        final long expectCacheDataBytes = mb.getCacheMemoryUsage() -
                                          mb.getLogBufferBudget();
        (new Runnable() {
            @Deprecated
            public void run() {
                assertEquals(expectCacheDataBytes,
                             finalStat.getCacheDataBytes());
            }
        }).run();

        txn.abort();
        db.close();
        env.close();
        env = null;
    }
}
