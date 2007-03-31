/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbTreeTest.java,v 1.26.2.1 2007/02/01 14:50:10 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

public class DbTreeTest extends TestCase {
    private File envHome;
    
    public DbTreeTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() throws IOException, DatabaseException {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown() throws IOException, DatabaseException {
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testDbLookup() throws Throwable {
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setAllowCreate(true);
            Environment env = new Environment(envHome, envConfig);

            // Make two databases
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database dbHandleAbcd = env.openDatabase(null, "abcd", dbConfig);
            Database dbHandleXyz = env.openDatabase(null, "xyz", dbConfig);

            // Can we get them back?
            dbConfig.setAllowCreate(false);
            Database newAbcdHandle = env.openDatabase(null, "abcd", dbConfig);
            Database newXyzHandle = env.openDatabase(null, "xyz", dbConfig);

            dbHandleAbcd.close();
            dbHandleXyz.close();
            newAbcdHandle.close();
            newXyzHandle.close();
            env.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    } 
}
