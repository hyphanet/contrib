/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2008 Oracle.  All rights reserved.
 *
 * $Id: MultiEnvTest.java,v 1.15 2008/05/20 03:27:37 linda Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

public class MultiEnvTest extends TestCase {

    private File envHome1;
    private File envHome2;
    private Environment env1;
    private Environment env2;

    public MultiEnvTest() {
        envHome1 = new File(System.getProperty(TestUtils.DEST_DIR));
        envHome2 = new File(System.getProperty(TestUtils.DEST_DIR),
                            "propTest");
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome1, false);
        TestUtils.removeLogFiles("Setup", envHome2, false);
    }

    public void tearDown()
        throws Exception {

	TestUtils.removeLogFiles("TearDown", envHome1, false);
	TestUtils.removeLogFiles("TearDown", envHome2, false);
    }

    public void testNodeIdsAfterRecovery()
        throws Throwable {
            /* 
             * TODO: replace this test which previously checked that the node
             * id sequence shared among environments was correct with a test
             * that checks all sequences, including replicated ones. This
             * change is appropriate because the node id sequence is no longer
             * a static field.
             */
    }

    private Environment openEnv(File envHome)
        throws DatabaseException {

        /* Create an environment. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        Environment e = new Environment(envHome, envConfig);
        return e;
    }
}
