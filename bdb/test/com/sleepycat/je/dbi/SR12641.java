/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SR12641.java,v 1.6.2.1 2007/02/01 14:50:10 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 * This reproduces the bug described SR [#12641], also related to SR [#9543].
 *
 * Note that allthough this is a JUnit test case, it is not run as part of the
 * JUnit test suite.  It takes a long time, and when it fails it hangs.
 * Therefore, it was only used for debugging and is not intended to be a
 * regression test.
 *
 * For some reason the bug was not reproducible with a simple main program,
 * which is why a JUnit test was used.
 */
public class SR12641 extends TestCase {

    /* Use small NODE_MAX to cause lots of splits. */
    private static final int NODE_MAX = 6;

    private File envHome;
    private Environment env;
    private Database db;
    private boolean dups;
    private boolean writerStopped;

    public SR12641()
        throws Exception {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
	throws Exception {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.err.println("TearDown: " + e);
            }
        }
        env = null;
        db = null;
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testSplitsWithScansDups()
        throws Throwable {

        dups = true;
        testSplitsWithScans();
    }

    public void testSplitsWithScans()
        throws Throwable {

        open();

        /* Cause splits in the last BIN. */
        JUnitThread writer = new JUnitThread("writer") {
            public void testBody() {
                try {
                    DatabaseEntry key = new DatabaseEntry(new byte[1]);
                    DatabaseEntry data = new DatabaseEntry(new byte[1]);
                    OperationStatus status;

                    Cursor cursor = db.openCursor(null, null);

                    for (int i = 0; i < 100000; i += 1) {
                        IntegerBinding.intToEntry(i, dups ? data : key);
                        if (dups) {
                            status = cursor.putNoDupData(key, data);
                        } else {
                            status = cursor.putNoOverwrite(key, data);
                        }
                        assertEquals(OperationStatus.SUCCESS, status);

                        if (i % 5000 == 0) {
                            System.out.println("Iteration: " + i);
                        }
                    }

                    cursor.close();
                    writerStopped = true;

                } catch (Exception e) {
                    try {
                        FileOutputStream os =
                            new FileOutputStream(new File("./err.txt"));
                        e.printStackTrace(new PrintStream(os));
                        os.close();
                    } catch (IOException ignored) {}
                    System.exit(1);
                }
            }
        };

        /* Move repeatedly from the last BIN to the prior BIN. */
        JUnitThread reader = new JUnitThread("reader") {
            public void testBody() {
                try {
                    DatabaseEntry key = new DatabaseEntry();
                    DatabaseEntry data = new DatabaseEntry();

                    CursorConfig cursorConfig = new CursorConfig();
                    cursorConfig.setReadUncommitted(true);
                    Cursor cursor = db.openCursor(null, cursorConfig);

                    while (!writerStopped) {
                        cursor.getLast(key, data, null);
                        for (int i = 0; i <= NODE_MAX; i += 1) {
                            cursor.getPrev(key, data, null);
                        }
                    }

                    cursor.close();

                } catch (Exception e) {
                    try {
                        FileOutputStream os =
                            new FileOutputStream(new File("./err.txt"));
                        e.printStackTrace(new PrintStream(os));
                        os.close();
                    } catch (IOException ignored) {}
                    System.exit(1);
                }
            }
        };

        writer.start();
        reader.start();
        writer.finishTest();
        reader.finishTest();

        close();
        System.out.println("SUCCESS");
    }

    private void open()
        throws Exception {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(), String.valueOf(NODE_MAX));
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        dbConfig.setSortedDuplicates(dups);
        db = env.openDatabase(null, "testDb", dbConfig);
    }

    private void close()
        throws Exception {

        db.close();
        db = null;
        env.close();
        env = null;
    } 
}
