/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DatabaseComparatorsTest.java,v 1.6.2.1 2007/02/01 14:50:04 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class DatabaseComparatorsTest extends TestCase {
    
    private File envHome;
    private Environment env;
    private Database db;
    private boolean DEBUG = false;

    public DatabaseComparatorsTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
	throws IOException, DatabaseException {

        closeDb();
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testSR12517()
        throws Exception {

        openEnv();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Comparator reverse = new ReverseComparator();
        dbConfig.setBtreeComparator(reverse.getClass());
        dbConfig.setDuplicateComparator(reverse.getClass());
        db = env.openDatabase(null, "testDB", dbConfig);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Insert 5 items. */
        for (int i = 0; i < 5; i++) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
	    /* Add a dup. */
            IntegerBinding.intToEntry(i * 2, data);
            assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        }
        read();

        db.close();
        env.close();

        openEnv();
        db = env.openDatabase(null, "testDB", dbConfig);

        read();
    }

    private void read() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Iterate */
        Cursor c = db.openCursor(null, null);
        int expected = 4;
        while (c.getNext(key, data, LockMode.DEFAULT) ==
               OperationStatus.SUCCESS) {
            assertEquals(expected, IntegerBinding.entryToInt(key));
            expected--;
	    if (DEBUG) {
		System.out.println("cursor: k=" +
				   IntegerBinding.entryToInt(key) +
				   " d=" +
				   IntegerBinding.entryToInt(data));
	    }
        }
	assertEquals(expected, -1);

        c.close();

        /* Retrieve 5 items */
        for (int i = 0; i < 5; i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals(OperationStatus.SUCCESS,
                         db.get(null, key, data, LockMode.DEFAULT));
            assertEquals(i, IntegerBinding.entryToInt(key));
            assertEquals(i * 2, IntegerBinding.entryToInt(data));
	    if (DEBUG) {
		System.out.println("k=" +
				   IntegerBinding.entryToInt(key) +
				   " d=" +
				   IntegerBinding.entryToInt(data));
	    }
        }
    }

    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                                 "true");
        env = new Environment(envHome, envConfig);
    }

    private void closeDb()
        throws DatabaseException {

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    public static class ReverseComparator implements Comparator {

	public ReverseComparator() {
	}

	public int compare(Object o1, Object o2) {

            DatabaseEntry arg1 = new DatabaseEntry((byte[]) o1);
            DatabaseEntry arg2 = new DatabaseEntry((byte[]) o2);
            int val1 = IntegerBinding.entryToInt(arg1);
            int val2 = IntegerBinding.entryToInt(arg2);

            if (val1 < val2) {
                return 1;
            } else if (val1 > val2) {
                return -1;
            } else {
                return 0;
            }
	}
    }
}
