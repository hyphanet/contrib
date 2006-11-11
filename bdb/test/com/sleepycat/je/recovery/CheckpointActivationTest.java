/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: CheckpointActivationTest.java,v 1.16 2006/09/12 19:17:22 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.Tracer;

public class CheckpointActivationTest extends TestCase {

    private File envHome;

    public CheckpointActivationTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Write elements to the log, check that the right number of
     * checkpoints ran.
     */
    public void testLogSizeBasedCheckpoints() 
        throws Exception {

        final int BYTE_INTERVAL = 5000;
        final int TRACER_OVERHEAD = 26;
        final int N_TRACES = 100;
        final int N_EXTRA_TRACES = 0;
        final int N_CHECKPOINTS = 10;
        final int MIN_CHECKPOINTS_EXPECTED = 8;
        
        /* Init trace message with zeros. */
        assert BYTE_INTERVAL % N_TRACES == 0;
        int msgBytesPerTrace = (BYTE_INTERVAL / N_TRACES) - TRACER_OVERHEAD;
        StringBuffer traceBuf = new StringBuffer();
        for (int i = 0; i < msgBytesPerTrace; i += 1) {
            traceBuf.append('-');
        }
        String traceMsg = traceBuf.toString();

        Environment env = null;
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            envConfig.setConfigParam(EnvironmentParams.
                                     CHECKPOINTER_BYTES_INTERVAL.getName(),
                                     String.valueOf(BYTE_INTERVAL));

            /*
             * This test needs to control exactly how much goes into the log,
             * so disable daemons. 
             */
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_EVICTOR.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_INCOMPRESSOR.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_CLEANER.getName(), "false");
            env = new Environment(envHome, envConfig);

            /* 
             * Get a first reading on number of checkpoints run. Read once
             * to clear, then read again.
             */
            StatsConfig statsConfig = new StatsConfig();
            statsConfig.setFast(true);
            statsConfig.setClear(true);
            EnvironmentStats stats = env.getStats(statsConfig); // clear stats

            stats = env.getStats(statsConfig);  // read again
            assertEquals(0, stats.getNCheckpoints());
            long lastCkptEnd = stats.getLastCheckpointEnd();
            int actualCheckpoints = 0;

            /* Run several checkpoints to ensure they occur as expected.  */
            for (int i = 0; i < N_CHECKPOINTS; i += 1) {

                /* Write enough to prompt a checkpoint. */
                EnvironmentImpl envImpl =
                    DbInternal.envGetEnvironmentImpl(env);
                /*
                 * Extra traces are used to account for the fact that the
                 * CheckpointMonitor only activates the Checkpointer every
                 * 1/10 of the checkpoint byte interval.
                 */
                for (int j = 0; j < N_TRACES + N_EXTRA_TRACES; j += 1) {

                    /* Write to log to wakeup checkpointer. */
                    Tracer.trace(Level.SEVERE, envImpl, traceMsg);

                    /*
                     * Check to see if a checkpoint has started.  We take
                     * advantage of the fact that the NCheckpoints stat is
                     * set at the start of a checkpoint.
                     */
                    stats = env.getStats(statsConfig);
                    if (stats.getNCheckpoints() > 0) {
                        assertEquals(1, stats.getNCheckpoints());
                        actualCheckpoints += 1;

                        /*
                         * Wait for the checkpointer daemon to do its work.
                         * We do not want to continue writing until the
                         * checkpoint is complete, because the amount of data
                         * we write is calculated to be the correct amount
                         * in between checkpoints.  We know the checkpoint is
                         * finished when the LastCheckpointEnd LSN changes.
                         */
                        while (true) {
                            Thread.yield();
                            Thread.sleep(1);
                            stats = env.getStats(statsConfig);
                            assertEquals(0, stats.getNCheckpoints());
                            if (lastCkptEnd != stats.getLastCheckpointEnd()) {
                                lastCkptEnd = stats.getLastCheckpointEnd();
                                break;
                            }
                        }
                    }
                }
            }

            //System.out.println("actual=" + actualCheckpoints);
            assertTrue("min expected=" + MIN_CHECKPOINTS_EXPECTED +
                       " actual=" + actualCheckpoints,
                       actualCheckpoints >= MIN_CHECKPOINTS_EXPECTED);
        } catch (Exception e) {
            /* 
             * print stack trace now, else it gets subsumed in exceptions
             * caused by difficulty in removing log files.
             */
            e.printStackTrace();
            throw e;
        } finally {
            if (env != null) {
                env.close();
            }
        }
    }

    /* Test programmatic call to checkpoint. */
    public void testApiCalls() 
        throws Exception {

        Environment env = null;
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            envConfig.setConfigParam(EnvironmentParams.
                                     CHECKPOINTER_BYTES_INTERVAL.getName(),
                                     "1000");

            /* Disable all daemons */
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_EVICTOR.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_INCOMPRESSOR.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_CLEANER.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.
                                     ENV_RUN_CHECKPOINTER.getName(), "false");
            env = new Environment(envHome, envConfig);

            /* 
             * Get a first reading on number of checkpoints run. Read once
             * to clear, then read again.
             */
            StatsConfig statsConfig = new StatsConfig();
            statsConfig.setFast(true);
            statsConfig.setClear(true);
            EnvironmentStats stats = env.getStats(statsConfig); // clear stats

            stats = env.getStats(statsConfig);  // read again
            assertEquals(0, stats.getNCheckpoints());

            /* 
	     * From the last checkpoint start LSN, there should be the
	     * checkpoint end log entry and a trace message. These take 196
	     * bytes.
             */
            CheckpointConfig checkpointConfig = new CheckpointConfig();

            /* Should not cause a checkpoint, too little growth. */
            checkpointConfig.setKBytes(1);
            env.checkpoint(checkpointConfig);
            stats = env.getStats(statsConfig);  // read again
            assertEquals(0, stats.getNCheckpoints());

            /* Fill up the log, there should be a checkpoint. */
            String filler = "123456789012345678901245678901234567890123456789";
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            for (int i = 0; i < 20; i++) {
                Tracer.trace(Level.SEVERE, envImpl, filler);
            }
            env.checkpoint(checkpointConfig);
            stats = env.getStats(statsConfig);  // read again
            assertEquals(1, stats.getNCheckpoints());

            /* Try time based, should not checkpoint. */
            checkpointConfig.setKBytes(0);
            checkpointConfig.setMinutes(1);
            env.checkpoint(checkpointConfig);
            stats = env.getStats(statsConfig);  // read again
            assertEquals(0, stats.getNCheckpoints());

            /* 
	     * Sleep, enough time has passed for a checkpoint, but nothing was
	     * written to the log.
             */
            Thread.sleep(1000);
            env.checkpoint(checkpointConfig);
            stats = env.getStats(statsConfig);  // read again
            assertEquals(0, stats.getNCheckpoints());

            /* Log something, now try a checkpoint. */
            Tracer.trace(Level.SEVERE,  envImpl, filler);
            env.checkpoint(checkpointConfig);
            stats = env.getStats(statsConfig);  // read again
            // TODO: make this test more timing independent. Sometimes 
            // the assertion will fail.
            // assertEquals(1, stats.getNCheckpoints());
                        
        } catch (Exception e) {
            /* 
             * print stack trace now, else it gets subsumed in exceptions
             * caused by difficulty in removing log files.
             */
            e.printStackTrace();
            throw e;
        } finally {
            if (env != null) {
                env.close();
            }
        }
    }
}
