/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileReaderBufferingTest.java,v 1.18 2008/05/22 19:35:38 linda Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * Check our ability to adjust the file reader buffer size.
 */
public class FileReaderBufferingTest extends TestCase {

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private ArrayList<Long> expectedLsns;
    private ArrayList<String> expectedVals;

    public FileReaderBufferingTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException, DatabaseException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Should overflow once and then grow.
     */
    public void testBasic()
        throws Exception {

        readLog(1050,   // starting size of object in entry
                0,      // object growth increment
                100,    // starting read buffer size
                "3000", // max read buffer size
                0);     // expected number of overflows.
    }

    /**
     * Should overflow once and then grow.
     */
    public void testCantGrow()
        throws Exception {

        readLog(2000,   // starting size of object in entry
                0,      // object growth increment
                100,    // starting read buffer size
                "1000", // max read buffer size
                10);    // expected number of overflows.
    }

    /**
     * Should overflow, grow, and then reach the max.
     */
    public void testReachMax()
        throws Exception {

        readLog(1000,   // size of object in entry
                1000,      // object growth increment
                100,    // starting read buffer size
                "3500", // max read buffer size
                7);     // expected number of overflows.
    }
    /**
     *
     */
    private void readLog(int entrySize,
                         int entrySizeIncrement,
                         int readBufferSize,
                         String bufferMaxSize,
                         int expectedOverflows)
        throws Exception {

        try {

            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            envConfig.setConfigParam
                (EnvironmentParams.LOG_ITERATOR_MAX_SIZE.getName(),
                 bufferMaxSize);
            env = new Environment(envHome, envConfig);

            envImpl = DbInternal.envGetEnvironmentImpl(env);

            /* Make a log file */
            createLogFile(10, entrySize, entrySizeIncrement);
            SearchFileReader reader =
                new SearchFileReader(envImpl,
                                     readBufferSize,
                                     true,
                                     DbLsn.longToLsn
				     (expectedLsns.get(0)),
                                     DbLsn.NULL_LSN,
                                     LogEntryType.LOG_TRACE);

            Iterator<Long> lsnIter = expectedLsns.iterator();
            Iterator<String> valIter = expectedVals.iterator();
            while (reader.readNextEntry()) {
                Tracer rec = (Tracer)reader.getLastObject();
                assertTrue(lsnIter.hasNext());
                assertEquals(reader.getLastLsn(),
			     DbLsn.longToLsn(lsnIter.next()));
                assertEquals(valIter.next(), rec.getMessage());
            }
            assertEquals(10, reader.getNumRead());
            assertEquals(expectedOverflows, reader.getNRepeatIteratorReads());

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            env.close();
        }
    }

    /**
     * Write a logfile of entries, put the entries that we expect to
     * read into a list for later verification.
     * @return end of file LSN.
     */
    private void createLogFile(int numItems, int size, int sizeIncrement)
        throws IOException, DatabaseException {

        LogManager logManager = envImpl.getLogManager();
        expectedLsns = new ArrayList<Long>();
        expectedVals = new ArrayList<String>();

        for (int i = 0; i < numItems; i++) {
            /* Add a debug record just to be filler. */
            int recordSize = size + (i * sizeIncrement);
            byte[] filler = new byte[recordSize];
            Arrays.fill(filler, (byte)i);
            String val = new String(filler);

            Tracer rec = new Tracer(val);
            long lsn = rec.log(logManager);
            expectedLsns.add(new Long(lsn));
            expectedVals.add(val);
        }

        logManager.flush();
        envImpl.getFileManager().clear();
    }
}
