/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: GetSearchBothRangeTest.java,v 1.11.2.1 2007/02/01 14:50:05 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import junit.framework.TestCase;
import java.util.Comparator;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests getSearchBothRange when searching for a key that doesn't exist.
 * [#11119]
 */
public class GetSearchBothRangeTest extends TestCase {

    private File envHome;
    private Environment env;
    private Database db;
    private boolean dups;

    public GetSearchBothRangeTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
	throws Exception {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.out.println("Ignored during close: " + e);
            }
        }
        TestUtils.removeLogFiles("TearDown", envHome, false);
        envHome = null;
        env = null;
        db = null;
    }

    /**
     * Open environment and database.
     */
    private void openEnv()
        throws DatabaseException {

	openEnvWithComparator(null);
    }

    private void openEnvWithComparator(Class comparatorClass)
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        //*
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        //*/
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setSortedDuplicates(dups);
        dbConfig.setAllowCreate(true);

        dbConfig.setBtreeComparator(comparatorClass);

        db = env.openDatabase(null, "GetSearchBothRangeTest", dbConfig);
    }

    /**
     * Close environment and database.
     */
    private void closeEnv()
        throws DatabaseException {

        db.close();
        db = null;
        env.close();
        env = null;
    }

    public void testSearchKeyRangeWithDupTree()
        throws Exception {

        dups = true;
        openEnv();

        insert(1, 1);
        insert(1, 2);
        insert(3, 1);

        DatabaseEntry key = entry(2);
        DatabaseEntry data = new DatabaseEntry();

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchKeyRange(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(3, val(key));
        assertEquals(1, val(data));
        cursor.close();

        closeEnv();
    }

    public void testSearchBothWithNoDupTree()
        throws Exception {

        dups = true;
        openEnv();

        insert(1, 1);

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBoth(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        key = entry(1);
        data = entry(1);

        cursor = db.openCursor(null, null);
        status = cursor.getSearchBoth(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(key));
        assertEquals(1, val(data));
        cursor.close();

        key = entry(1);
        data = entry(0);

        cursor = db.openCursor(null, null);
        status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(key));
        assertEquals(1, val(data));
        cursor.close();

        closeEnv();
    }

    public void testSuccess()
        throws DatabaseException {

        openEnv();
        insert(1, 1);
        insert(3, 1);
        if (dups) {
            insert(1, 2);
            insert(3, 2);
        }

        DatabaseEntry key = entry(3);
        DatabaseEntry data = entry(0);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(3, val(key));
        assertEquals(1, val(data));
        cursor.close();

        closeEnv();
    }

    public void testSuccessDup()
        throws DatabaseException {

        dups = true;
        testSuccess();
    }

    public void testNotFound()
        throws DatabaseException {

        openEnv();
        insert(1, 0);
        if (dups) {
            insert(1, 1);
        }

        DatabaseEntry key = entry(2);
        DatabaseEntry data = entry(0);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    public void testNotFoundDup()
        throws DatabaseException {

        dups = true;
        testNotFound();
    }

    public void testSearchBefore()
        throws DatabaseException {

        dups = true;
        openEnv();
        insert(1, 0);

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    public void testSearchBeforeDups()
        throws DatabaseException {

        dups = true;
        openEnv();
        insert(1, 1);
        insert(1, 2);

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(0);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(key));
        assertEquals(1, val(data));
        cursor.close();

        closeEnv();
    }

    public static class NormalComparator implements Comparator {

	public NormalComparator() {
	}

	public int compare(Object o1, Object o2) {

            DatabaseEntry arg1 = new DatabaseEntry((byte[]) o1);
            DatabaseEntry arg2 = new DatabaseEntry((byte[]) o2);
            int val1 = IntegerBinding.entryToInt(arg1);
            int val2 = IntegerBinding.entryToInt(arg2);

            if (val1 < val2) {
                return -1;
            } else if (val1 > val2) {
                return 1;
            } else {
                return 0;
            }
	}
    }

    public void testSearchAfterDups()
        throws DatabaseException {

        dups = true;
        openEnv();
        insert(1, 0);
        insert(1, 1);
        insert(2, 0);
        insert(2, 1);

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    public void testSearchAfterDupsWithComparator()
        throws DatabaseException {

        dups = true;
        openEnvWithComparator(NormalComparator.class);
        insert(1, 0);
        insert(1, 1);
        insert(2, 0);
        insert(2, 1);

        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);

        Cursor cursor = db.openCursor(null, null);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    public void testSearchAfterDeletedDup()
        throws DatabaseException {

        dups = true;
        openEnv();
        insert(1, 1);
        insert(1, 2);
        insert(1, 3);

        /* Delete {1,3} leaving {1,1} in dup tree. */
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(3);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.SUCCESS, status);
        cursor.delete();
        cursor.close();
        env.compress();

        /* Search for {1,3} and expect NOTFOUND. */
        cursor = db.openCursor(null, null);
        key = entry(1);
        data = entry(3);
        status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    public void testSingleDatumBug()
        throws DatabaseException {

        dups = true;
        openEnv();
        insert(1, 1);
        insert(2, 2);

        /* Search for {1,2} and expect NOTFOUND. */
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry key = entry(1);
        DatabaseEntry data = entry(2);
        OperationStatus status = cursor.getSearchBothRange(key, data, null);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        closeEnv();
    }

    private int val(DatabaseEntry entry) {
        return IntegerBinding.entryToInt(entry);
    }

    private DatabaseEntry entry(int val) {
        DatabaseEntry entry = new DatabaseEntry();
        IntegerBinding.intToEntry(val, entry);
        return entry;
    }

    private void insert(int keyVal, int dataVal)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(keyVal, key);
        IntegerBinding.intToEntry(dataVal, data);
        OperationStatus status;
        if (dups) {
            status = db.putNoDupData(null, key, data);
        } else {
            status= db.putNoOverwrite(null, key, data);
        }
        assertEquals(OperationStatus.SUCCESS, status);
    }
}
