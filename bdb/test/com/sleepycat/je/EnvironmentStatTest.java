/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStatTest.java,v 1.17.2.1 2007/02/01 14:50:05 cwl Exp $
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
        } catch (DatabaseException e) {
            /* ok, the test closed it */
        }

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Test open and close of an environment.
     */
    public void testCacheStats()
        throws Throwable {

        try {
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
            stat = env.getStats(TestUtils.FAST_STATS);
            MemoryBudget mb =
                DbInternal.envGetEnvironmentImpl(env).getMemoryBudget();
            long cacheSize = mb.getCacheMemoryUsage();
            long bufferSize = mb.getLogBufferBudget();
            
            assertEquals(12, stat.getNCacheMiss());
            assertEquals(12, stat.getNNotResident());

            assertEquals(cacheSize, stat.getCacheDataBytes());

            /* 
             * Buffer size may be slightly different, because the log
             * buffer pool might do some rounding. Just check that
             * it's within an ok margin.
             */
            assertTrue(Math.abs(bufferSize-stat.getBufferBytes()) < 100);
            assertTrue(Math.abs((cacheSize + bufferSize)-
                                stat.getCacheTotalBytes()) < 100);
	    env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
