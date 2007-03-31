/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2007 Oracle.  All rights reserved.
 *
 * $Id: CheckSplitAuntTest.java,v 1.3.2.1 2007/02/01 14:50:16 cwl Exp $
 */
package com.sleepycat.je.recovery;

import java.util.HashSet;
import java.util.logging.Level;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.Tracer;

public class CheckSplitAuntTest extends CheckBase {

    private static final String DB_NAME = "simpleDB";
    private boolean useDups;

    /**
     */
    public void testSplitAunt()
        throws Throwable {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        turnOffEnvDaemons(envConfig);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                 "4");
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
                                 
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);

        EnvironmentConfig restartConfig = TestUtils.initEnvConfig();
        turnOffEnvDaemons(envConfig);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                 "4");
        envConfig.setTransactional(true);

        testOneCase(DB_NAME,
                    envConfig,
                    dbConfig,
                    new TestGenerator(true){
                        void generateData(Database db)
                            throws DatabaseException {
                            setupSplitData(db);
                        }
                    },
                    restartConfig,
                    new DatabaseConfig());

        /* 
         * Now run the test in a stepwise loop, truncate after each 
         * log entry. We start the steps before the inserts, so the base
         * expected set is empty.
         */
        HashSet currentExpected = new HashSet();
        if (TestUtils.runLongTests()) {
            stepwiseLoop(DB_NAME, envConfig, dbConfig, currentExpected,  0);
        }
    }

    private void setupSplitData(Database db) 
        throws DatabaseException {

        setStepwiseStart();

        int max = 12;

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Populate a tree so it grows to 3 levels, then checkpoint. */
        for (int i = 0; i < max; i ++) {
            IntegerBinding.intToEntry(i*10, key);
            IntegerBinding.intToEntry(i*10, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }

        CheckpointConfig ckptConfig = new CheckpointConfig();
        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "First sync");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "Second sync");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "Third sync");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "Fourth sync");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "Fifth sync");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "Sync6");
        env.sync();

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "After sync");

        /* Add a key to dirty the left hand branch. */
        IntegerBinding.intToEntry(5, key);
        IntegerBinding.intToEntry(5, data);
        assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "After single key insert");

        ckptConfig.setForce(true);
        ckptConfig.setMinimizeRecoveryTime(true);
        env.checkpoint(ckptConfig);       

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "before split");


        /* Add enough keys to split the right hand branch. */
        for (int i = 51; i < 57; i ++) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }

        Tracer.trace(Level.SEVERE, DbInternal.envGetEnvironmentImpl(env),
                     "after split");
    } 
}
