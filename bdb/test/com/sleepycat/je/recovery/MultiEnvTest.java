/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2007 Oracle.  All rights reserved.
 *
 * $Id: MultiEnvTest.java,v 1.9.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.tree.Node;
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
        try {
            /* 
             * Env1 will be closed w/a certain notion of what the max node id
             * is.
             */
            env1 = openEnv(envHome1);
            long maxNodeId1 = Node.getLastId();
            env1.close();

            /*
             * Env2 increments the node id further.
             */
            env2 = openEnv(envHome2);
            long maxNodeId2 = Node.getLastId();

            /* See what the highest node id is. */
            assertTrue(maxNodeId2 > maxNodeId1);

            /* 
             * Open env1 now. Even though this recovery finds a lower node id,
             * must be sure not to overwrite the higher node id. 
             */
            env1 = openEnv(envHome1);
            long maxNodeId3 = Node.getLastId();
            assertTrue(maxNodeId3 >= maxNodeId2);
            env2.close();
        } catch (Throwable t) {
            /* Dump stack trace before trying to tear down. */
            t.printStackTrace();
            throw t;
        }
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
