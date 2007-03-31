/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MultiEnvOpenCloseTest.java,v 1.10.2.1 2007/02/01 14:50:19 cwl Exp $
 */

package com.sleepycat.je.test;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

/**
 * Test out-of-memory fix to DaemonThread [#10504].
 */
public class MultiEnvOpenCloseTest extends TestCase {

    private File envHome;

    public void setUp()
        throws IOException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }
    
    public void testMultiOpenClose()
        throws Exception {

        /*
         * Before fixing the bug in DaemonThread [#10504] this test would run
         * out of memory after 7 iterations.  The bug was, if we open an
         * environment read-only we won't start certain daemon threads, they
         * will not be GC'ed because they are part of a thread group, and they
         * will retain a reference to the Environment.  The fix was to not
         * create the threads until we need to start them.
         */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        final int DATA_SIZE = 1024 * 10;
        final int N_RECORDS = 1000;
        final int N_ITERS = 30;

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[DATA_SIZE]);

        Environment env = new Environment(envHome, envConfig);
        Database db = env.openDatabase(null, "MultiEnvOpenCloseTest",
                                       dbConfig);
        for (int i = 0; i < N_RECORDS; i += 1) {
            IntegerBinding.intToEntry(i, key);
            db.put(null, key, data);
        }

        db.close();
        env.close();

        envConfig.setAllowCreate(false);
        envConfig.setReadOnly(true);
        dbConfig.setAllowCreate(false);
        dbConfig.setReadOnly(true);

        for (int i = 1; i <= N_ITERS; i += 1) {
            //System.out.println("MultiEnvOpenCloseTest iteration # " + i);
            env = new Environment(envHome, envConfig);
            db = env.openDatabase(null, "MultiEnvOpenCloseTest", dbConfig);
            for (int j = 0; j < N_RECORDS; j += 1) {
                IntegerBinding.intToEntry(j, key);
                db.get(null, key, data, null);
            }
            db.close();
            env.close();
        }
    }
}
