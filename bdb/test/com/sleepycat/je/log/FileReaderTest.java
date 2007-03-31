/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2007 Oracle.  All rights reserved.
 *
 * $Id: FileReaderTest.java,v 1.11.2.1 2007/02/01 14:50:14 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;


/**
 * Test edge cases for file reading. 
 */
public class FileReaderTest extends TestCase {

    static private final boolean DEBUG = false;

    private File envHome;


    public FileReaderTest()
        throws Exception {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }
    
    public void tearDown()
        throws IOException, DatabaseException {

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /*
     * Check that we can handle the case when we are reading forward
     * with other than the LastFileReader, and the last file exists but is
     * 0 length. This case came up when a run of MemoryStress was killed off,
     * and we then attempted to read it with DbPrintLog.
     */
    public void testEmptyExtraFile()
        throws Throwable {
	
	EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        Environment env = new Environment(envHome, envConfig);

        try {
            /* Make an environment. */
            env.sync();

            /* Add an extra, 0 length file */
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);

            File newFile = new File(envHome, "00000001.jdb");
            newFile.createNewFile();

            INFileReader reader = new INFileReader(envImpl,
                                                   1000,
                                                   DbLsn.NULL_LSN,
						   DbLsn.NULL_LSN,
                                                   false,
                                                   false,
                                                   DbLsn.NULL_LSN,
                                                   null);
            while (reader.readNextEntry()) {
            }

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }
}
