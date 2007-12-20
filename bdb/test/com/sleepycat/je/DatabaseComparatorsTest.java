/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DatabaseComparatorsTest.java,v 1.6.2.3 2007/11/20 13:32:42 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
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

        if (env != null) {
            try {
                env.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
        }
        TestUtils.removeLogFiles("TearDown", envHome, false);
        env = null;
        envHome = null;
    }

    private void openEnv()
        throws DatabaseException {

        openEnv(false);
    }

    private void openEnv(boolean transactional)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(transactional);
        envConfig.setConfigParam(EnvironmentParams.ENV_CHECK_LEAKS.getName(),
                                 "true");
        /* Prevent compression. */
        envConfig.setConfigParam("je.env.runINCompressor", "false");
        envConfig.setConfigParam("je.env.runCheckpointer", "false");
        envConfig.setConfigParam("je.env.runEvictor", "false");
        envConfig.setConfigParam("je.env.runCleaner", "false");
        env = new Environment(envHome, envConfig);
    }

    private Database openDb(boolean transactional,
                            boolean dups,
                            Class btreeComparator,
                            Class dupComparator)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        dbConfig.setTransactional(transactional);
        dbConfig.setBtreeComparator(btreeComparator);
        dbConfig.setDuplicateComparator(dupComparator);
        return env.openDatabase(null, "testDB", dbConfig);
    }

    public void testSR12517()
        throws Exception {

        openEnv();
        Database db = openDb(false /*transactional*/, false /*dups*/,
                             ReverseComparator.class, ReverseComparator.class);

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
        read(db);

        db.close();
        env.close();

        openEnv();
        db = openDb(false /*transactional*/, false /*dups*/,
                    ReverseComparator.class, ReverseComparator.class);

        read(db);
        db.close();
        env.close();
        env = null;
    }

    private void read(Database db)
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

    /**
     * Checks that when reusing a slot and then aborting the transaction, the
     * original data is restored, when using a btree comparator. [#15704]
     *
     * When using partial keys to reuse a slot with a different--but equal
     * according to a custom comparator--key, a bug caused corruption of an
     * existing record after an abort.  The sequence for a non-duplicate
     * database and a btree comparator that compares only the first integer in
     * a two integer key is:
     *
     * 100 Insert LN key={0,0} txn 1
     * 110 Commit txn 1
     * 120 Delete LN key={0,0} txn 2
     * 130 Insert LN key={0,1} txn 2
     * 140 Abort txn 2
     *
     * When key {0,1} is inserted at LSN 130, it reuses the slot for {0,0}
     * because these two keys are considered equal by the comparator.  When txn
     * 2 is aborted, it restores LSN 100 in the slot, but the key in the BIN
     * stays {0,1}.  Fetching the record after the abort gives key {0,1}.
     */
    public void testReuseSlotAbortPartialKey()
        throws DatabaseException {

        doTestReuseSlotPartialKey(false /*runRecovery*/);
    }

    /**
     * Same as testReuseSlotAbortPartialKey but runs recovery after the abort.
     */
    public void testReuseSlotRecoverPartialKey()
        throws DatabaseException {

        doTestReuseSlotPartialKey(true /*runRecovery*/);
    }

    private void doTestReuseSlotPartialKey(boolean runRecovery)
        throws DatabaseException {

        openEnv(true /*transactional*/);
        Database db = openDb
            (true /*transactional*/, false /*dups*/,
             Partial2PartComparator.class /*btreeComparator*/,
             null /*dupComparator*/);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;

        /* Insert key={0,0}/data={0} using auto-commit. */
        status = db.put(null, entry(0, 0), entry(0));
        assertSame(OperationStatus.SUCCESS, status);
        key = entry(0, 1);
        data = entry(0);
        status = db.getSearchBoth(null, key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0, 0);
        check(data, 0);

        /* Delete, insert key={0,1}/data={1}, abort. */
        Transaction txn = env.beginTransaction(null, null);
        status = db.delete(txn, entry(0, 1));
        assertSame(OperationStatus.SUCCESS, status);
        status = db.get(txn, entry(0, 0), data, null);
        assertSame(OperationStatus.NOTFOUND, status);
        status = db.put(txn, entry(0, 1), entry(1));
        assertSame(OperationStatus.SUCCESS, status);
        key = entry(0, 0);
        data = entry(1);
        status = db.getSearchBoth(txn, key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0, 1);
        check(data, 1);
        txn.abort();

        if (runRecovery) {
            db.close();
            env.close();
            env = null;
            openEnv(true /*transactional*/);
            db = openDb
                (true /*transactional*/, false /*dups*/,
                 Partial2PartComparator.class /*btreeComparator*/,
                 null /*dupComparator*/);
        }

        /* Check that we rolled back to key={0,0}/data={0}. */
        key = entry(0, 1);
        data = entry(0);
        status = db.getSearchBoth(null, key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0, 0);
        check(data, 0);

        db.close();
        env.close();
        env = null;
    }

    /**
     * Same as testReuseSlotAbortPartialKey but for reuse of duplicate data
     * slots.  [#15704]
     *
     * The sequence for a duplicate database and a duplicate comparator that
     * compares only the first integer in a two integer data value is:
     *
     * 100 Insert LN key={0}/data={0,0} txn 1
     * 110 Insert LN key={0}/data={1,1} txn 1
     * 120 Commit txn 1
     * 130 Delete LN key={0}/data={0,0} txn 2
     * 140 Insert LN key={0}/data={0,1} txn 2
     * 150 Abort txn 2
     *
     * When data {0,1} is inserted at LSN 140, it reuses the slot for {0,0}
     * because these two data values are considered equal by the comparator.
     * When txn 2 is aborted, it restores LSN 100 in the slot, but the data in
     * the DBIN stays {0,1}.  Fetching the record after the abort gives data
     * {0,1}.
     */
    public void testReuseSlotAbortPartialDup()
        throws DatabaseException {

        doTestReuseSlotPartialDup(false /*runRecovery*/);
    }

    /**
     * Same as testReuseSlotAbortPartialDup but runs recovery after the abort.
     */
    public void testReuseSlotRecoverPartialDup()
        throws DatabaseException {

        doTestReuseSlotPartialDup(true /*runRecovery*/);
    }

    private void doTestReuseSlotPartialDup(boolean runRecovery)
        throws DatabaseException {

        openEnv(true /*transactional*/);
        Database db = openDb
            (true /*transactional*/, true /*dups*/,
             null /*btreeComparator*/,
             Partial2PartComparator.class /*dupComparator*/);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;

        /* Insert key={0}/data={0,0} using auto-commit. */
        Transaction txn = env.beginTransaction(null, null);
        status = db.put(txn, entry(0), entry(0, 0));
        assertSame(OperationStatus.SUCCESS, status);
        status = db.put(txn, entry(0), entry(1, 1));
        assertSame(OperationStatus.SUCCESS, status);
        txn.commit();
        key = entry(0);
        data = entry(0, 1);
        status = db.getSearchBoth(null, key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0);
        check(data, 0, 0);

        /* Delete, insert key={0}/data={0,1}, abort. */
        txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        key = entry(0);
        data = entry(0, 1);
        status = cursor.getSearchBoth(key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0);
        check(data, 0, 0);
        status = cursor.delete();
        assertSame(OperationStatus.SUCCESS, status);
        status = cursor.put(entry(0), entry(0, 1));
        assertSame(OperationStatus.SUCCESS, status);
        key = entry(0);
        data = entry(0, 1);
        status = cursor.getSearchBoth(key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0);
        check(data, 0, 1);
        cursor.close();
        txn.abort();

        if (runRecovery) {
            db.close();
            env.close();
            env = null;
            openEnv(true /*transactional*/);
            db = openDb
                (true /*transactional*/, true /*dups*/,
                 null /*btreeComparator*/,
                 Partial2PartComparator.class /*dupComparator*/);
        }

        /* Check that we rolled back to key={0,0}/data={0}. */
        key = entry(0);
        data = entry(0, 1);
        status = db.getSearchBoth(null, key, data, null);
        assertSame(OperationStatus.SUCCESS, status);
        check(key, 0);
        check(data, 0, 0);

        db.close();
        env.close();
        env = null;
    }

    /**
     * Check that we prohibit the case where dups are configured and the btree
     * comparator does not compare all bytes of the key.  To support this would
     * require maintaining the BIN slot and DIN/DBIN.dupKey fields to be
     * transactionally correct.  This is impractical since INs by design are
     * non-transctional.  [#15704]
     */
    public void testDupsWithPartialComparatorNotAllowed()
        throws DatabaseException {

        openEnv(false /*transactional*/);
        Database db = openDb
            (false /*transactional*/, true /*dups*/,
             Partial2PartComparator.class /*btreeComparator*/,
             null /*dupComparator*/);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;

        /* Insert key={0,0}/data={0} and data={1}. */
        status = db.put(null, entry(0, 0), entry(0));
        assertSame(OperationStatus.SUCCESS, status);
        try {
            status = db.put(null, entry(0, 1), entry(1));
            fail(status.toString());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf
                ("Custom Btree comparator matches two non-identical keys " +
                 "in a Database with duplicates configured") >= 0);
        }

        db.close();
        env.close();
        env = null;
    }

    private void check(DatabaseEntry entry, int p1) {
        assertEquals(4, entry.getSize());
        TupleInput input = TupleBase.entryToInput(entry);
        assertEquals(p1, input.readInt());
    }

    private void check(DatabaseEntry entry, int p1, int p2) {
        assertEquals(8, entry.getSize());
        TupleInput input = TupleBase.entryToInput(entry);
        assertEquals(p1, input.readInt());
        assertEquals(p2, input.readInt());
    }

    /*
    private void dump(Database db, Transaction txn)
        throws DatabaseException {

        System.out.println("-- dump --");
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status;
        Cursor c = db.openCursor(txn, null);
        while (c.getNext(key, data, null) == OperationStatus.SUCCESS) {
            TupleInput keyInput = TupleBase.entryToInput(key);
            int keyP1 = keyInput.readInt();
            int keyP2 = keyInput.readInt();
            int dataVal = IntegerBinding.entryToInt(data);
            System.out.println("keyP1=" + keyP1 +
                               " keyP2=" + keyP2 +
                               " dataVal=" + dataVal);
        }
        c.close();
    }
    */

    private DatabaseEntry entry(int p1) {
        DatabaseEntry entry = new DatabaseEntry();
        TupleOutput output = new TupleOutput();
        output.writeInt(p1);
        TupleBase.outputToEntry(output, entry);
        return entry;
    }

    private DatabaseEntry entry(int p1, int p2) {
        DatabaseEntry entry = new DatabaseEntry();
        TupleOutput output = new TupleOutput();
        output.writeInt(p1);
        output.writeInt(p2);
        TupleBase.outputToEntry(output, entry);
        return entry;
    }

    /**
     * Writes two integers to the byte array.
     */
    private void make2PartEntry(int p1, int p2, DatabaseEntry entry) {
        TupleOutput output = new TupleOutput();
        output.writeInt(p1);
        output.writeInt(p2);
        TupleBase.outputToEntry(output, entry);
    }

    /**
     * Compares only the first integer in the byte arrays.
     */
    public static class Partial2PartComparator implements Comparator {

	public int compare(Object o1, Object o2) {
            int val1 = new TupleInput((byte[]) o1).readInt();
            int val2 = new TupleInput((byte[]) o2).readInt();
            return val1 - val2;
	}
    }
}
