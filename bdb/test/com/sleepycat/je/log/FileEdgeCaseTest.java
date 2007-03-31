/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileEdgeCaseTest.java,v 1.2.2.1 2007/02/01 14:50:13 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.recovery.NoRootException;
import com.sleepycat.je.util.TestUtils;

public class FileEdgeCaseTest extends TestCase {

    private File envHome;
    private Environment env;
    private String firstFile;

    public FileEdgeCaseTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        /* 
	 * Close down environments in case the unit test failed so that the log
	 * files can be removed.
         */
        try {
            if (env != null) {
                env.close();
                env = null;
            }
        } catch (DatabaseException e) {
            e.printStackTrace();
            // ok, the test closed it
        }
       TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * SR #15133
     * Create a JE environment with a single log file and a checksum 
     * exception in the second entry in the log file.
     *
     * When an application attempts to open this JE environment, JE truncates
     * the log file at the point before the bad checksum, because it assumes
     * that bad entries at the end of the log are the result of incompletely
     * flushed writes from the last environment use.  However, the truncated
     * log doesn't have a valid environment root, so JE complains and asks the
     * application to move aside the existing log file (via the exception
     * message). The resulting environment has a single log file, with
     * a single valid entry, which is the file header.
     *
     * Any subsequent attempts to open the environment should also fail at the
     * same point. In the error case reported by this SR, we didn't handle this
     * single log file/single file header case right, and subsequent opens
     * first truncated before the file header, leaving a 0 length log, and
     * then proceeded to write error trace messages into the log. This
     * resulted in a log file with no file header, (but with trace messages)
     * and any following opens got unpredictable errors like 
     * ClassCastExceptions and BufferUnderflows.
     *
     * The correct situation is to continue to get the same exception.
     */
    public void testPostChecksumError()
        throws IOException, DatabaseException {

        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        env = new Environment(envHome, config);

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        FileManager fm = envImpl.getFileManager();
        firstFile = fm.getFullFileName(0, FileManager.JE_SUFFIX);

        env.close();
        env = null;
        
        /* Intentionally corrupt the second entry. */
        corruptSecondEntry();

        /* 
         * Next attempt to open the environment should fail with a 
         * NoRootException
         */
        try {
            env = new Environment(envHome, config);
        } catch (NoRootException expected) {
        }

        /* 
         * Next attempt to open the environment should fail with a 
         * NoRootException
         */
        try {
            env = new Environment(envHome, config);
        } catch (NoRootException expected) {
        }
    }

    /**
     * Write junk into the second log entry, after the file header.
     */
    private void corruptSecondEntry() 
        throws IOException {

        RandomAccessFile file = 
            new RandomAccessFile(firstFile,
                                 FileManager.FileMode.
                                 READWRITE_MODE.getModeValue());
        
        try {
            byte [] junk = new byte[20];
            file.seek(FileManager.firstLogEntryOffset());
            file.write(junk);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            file.close();
        }
    }
}
