/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ForeignKeyTest.java,v 1.13.2.1 2007/02/01 14:50:19 cwl Exp $
 */

package com.sleepycat.je.test;

import junit.framework.Test;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.ForeignKeyDeleteAction;
import com.sleepycat.je.ForeignKeyNullifier;
import com.sleepycat.je.ForeignMultiKeyNullifier;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.util.TestUtils;

public class ForeignKeyTest extends MultiKeyTxnTestCase {

    public static Test suite() {
        return multiKeyTxnTestSuite(ForeignKeyTest.class, null, null);
    }

    public void testDupsNotAllowed()
        throws DatabaseException {

        Database priDb1 = openPrimary("pri1");
        Database priDb2 = openPrimary("pri2", true /*duplicates*/);

        try {
            openSecondary(priDb1, "sec2", priDb2, ForeignKeyDeleteAction.ABORT);
            fail();
        } catch (IllegalArgumentException expected) {
            String msg = expected.getMessage();
            assertTrue
                (msg, msg.indexOf("Duplicates must not be allowed") >= 0);
        }

        priDb1.close();
        priDb2.close();
    }

    public void testIllegalNullifier()
        throws DatabaseException {

        Database priDb1 = openPrimary("pri1");
        Transaction txn = txnBegin();
        MyKeyCreator myCreator = new MyKeyCreator();
        SecondaryConfig config;

        /* A nullifier is required with NULLIFY. */
        config = new SecondaryConfig();
        config.setForeignKeyDeleteAction(ForeignKeyDeleteAction.NULLIFY);
        config.setKeyCreator(myCreator);
        try {
            env.openSecondaryDatabase(txn, "sec1", priDb1, config);
            fail();
        } catch (NullPointerException expected) { }

        /* Both nullifiers are not allowed. */
        config = new SecondaryConfig();
        config.setForeignKeyDeleteAction(ForeignKeyDeleteAction.NULLIFY);
        config.setKeyCreator(myCreator);
        config.setForeignKeyNullifier(myCreator);
        config.setForeignMultiKeyNullifier(myCreator);
        try {
            env.openSecondaryDatabase(txn, "sec1", priDb1, config);
            fail();
        } catch (IllegalArgumentException expected) { }

        /* ForeignKeyNullifier is not allowed with MultiKeyCreator. */
        config = new SecondaryConfig();
        config.setForeignKeyDeleteAction(ForeignKeyDeleteAction.NULLIFY);
        config.setMultiKeyCreator(new SimpleMultiKeyCreator(myCreator));
        config.setForeignKeyNullifier(myCreator);
        try {
            env.openSecondaryDatabase(txn, "sec1", priDb1, config);
            fail();
        } catch (IllegalArgumentException expected) { }

        txnCommit(txn);
        priDb1.close();
    }

    public void testAbort()
        throws DatabaseException {

        doTest(ForeignKeyDeleteAction.ABORT);
    }

    public void testCascade()
        throws DatabaseException {

        doTest(ForeignKeyDeleteAction.CASCADE);
    }

    public void testNullify()
        throws DatabaseException {

        doTest(ForeignKeyDeleteAction.NULLIFY);
    }

    private void doTest(ForeignKeyDeleteAction onDelete)
        throws DatabaseException {

        Database priDb1 = openPrimary("pri1");
        Database priDb2 = openPrimary("pri2");

        SecondaryDatabase secDb1 = openSecondary(priDb1, "sec1", null, null);
        SecondaryDatabase secDb2 = openSecondary(priDb2, "sec2", priDb1,
                                                 onDelete);

        OperationStatus status;
        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry pkey = new DatabaseEntry();
        Transaction txn = txnBegin();

        /*
         * pri1 has a record with primary key 1 and index key 3.
         * pri2 has a record with primary key 2 and foreign key 1,
         * which is the primary key of pri1.
         * pri2 has another record with primary key 3 and foreign key 1,
         * to enable testing cascade and nullify for secondary duplicates.
         */

        /* Add three records. */

        status = priDb1.put(txn, entry(1), entry(3));
        assertEquals(OperationStatus.SUCCESS, status);

        status = priDb2.put(txn, entry(2), entry(1));
        assertEquals(OperationStatus.SUCCESS, status);

        status = priDb2.put(txn, entry(3), entry(1));
        assertEquals(OperationStatus.SUCCESS, status);

        /* Verify record data. */

        status = priDb1.get(txn, entry(1), data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(3, val(data));

        status = secDb1.get(txn, entry(3), data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(3, val(data));

        status = priDb2.get(txn, entry(2), data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(data));

        status = priDb2.get(txn, entry(3), data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(data));

        SecondaryCursor cursor = secDb2.openSecondaryCursor(txn, null);
        status = cursor.getFirst(key, pkey, data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(key));
        assertEquals(2, val(pkey));
        assertEquals(1, val(data));
        status = cursor.getNext(key, pkey, data, LockMode.DEFAULT);
        assertEquals(OperationStatus.SUCCESS, status);
        assertEquals(1, val(key));
        assertEquals(3, val(pkey));
        assertEquals(1, val(data));
        status = cursor.getNext(key, pkey, data, LockMode.DEFAULT);
        assertEquals(OperationStatus.NOTFOUND, status);
        cursor.close();

        txnCommit(txn);
        txn = txnBegin();

        /* Test delete action. */

        if (onDelete == ForeignKeyDeleteAction.ABORT) {

            /* Test that we abort trying to delete a referenced key. */

            try {
                status = priDb1.delete(txn, entry(1));
                fail();
            } catch (DatabaseException expected) {
                txnAbort(txn);
                txn = txnBegin();
            }

            /* Test that we can put a record into pri2 with a null foreign key
             * value. */

            status = priDb2.put(txn, entry(2), entry(0));
            assertEquals(OperationStatus.SUCCESS, status);

            status = priDb2.put(txn, entry(3), entry(0));
            assertEquals(OperationStatus.SUCCESS, status);

            /* The sec2 records should not be present since the key was set
             * to null above. */

            status = secDb2.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            /* Test that now we can delete the record in pri1, since it is no
             * longer referenced. */

            status = priDb1.delete(txn, entry(1));
            assertEquals(OperationStatus.SUCCESS, status);

            status = priDb1.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            status = secDb1.get(txn, entry(3), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

        } else if (onDelete == ForeignKeyDeleteAction.NULLIFY) {

            /* Delete the referenced key. */

            status = priDb1.delete(txn, entry(1));
            assertEquals(OperationStatus.SUCCESS, status);

            status = priDb1.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            status = secDb1.get(txn, entry(3), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            /* The pri2 records should still exist, but should have a zero/null
             * secondary key since it was nullified. */

            status = priDb2.get(txn, entry(2), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.SUCCESS, status);
            assertEquals(0, val(data));

            status = priDb2.get(txn, entry(3), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.SUCCESS, status);
            assertEquals(0, val(data));
            
            status = secDb2.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

        } else if (onDelete == ForeignKeyDeleteAction.CASCADE) {

            /* Delete the referenced key. */

            status = priDb1.delete(txn, entry(1));
            assertEquals(OperationStatus.SUCCESS, status);

            status = priDb1.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            status = secDb1.get(txn, entry(3), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            /* The pri2 records should have deleted also. */

            status = priDb2.get(txn, entry(2), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

            status = priDb2.get(txn, entry(3), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);
            
            status = secDb2.get(txn, entry(1), data, LockMode.DEFAULT);
            assertEquals(OperationStatus.NOTFOUND, status);

        } else {
            throw new IllegalStateException();
        }

        /*
         * Test that a foreign key value may not be used that is not present
         * in the foreign db. Key 2 is not in pri1 in this case.
         */
        try {
            status = priDb2.put(txn, entry(3), entry(2));
            fail();
        } catch (DatabaseException expected) { }

        txnCommit(txn);
        secDb1.close();
        secDb2.close();
        priDb1.close();
        priDb2.close();
    }

    private Database openPrimary(String name)
        throws DatabaseException {

        return openPrimary(name, false);
    }

    private Database openPrimary(String name, boolean duplicates)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(duplicates);

        Transaction txn = txnBegin();
        try {
            return env.openDatabase(txn, name, dbConfig);
        } finally {
            txnCommit(txn);
        }
    }

    private SecondaryDatabase openSecondary(Database priDb, String dbName,
                                            Database foreignDb,
                                            ForeignKeyDeleteAction onDelete)
        throws DatabaseException {

        SecondaryConfig dbConfig = new SecondaryConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);

        MyKeyCreator keyCreator = new MyKeyCreator();
        if (useMultiKey) {
            dbConfig.setMultiKeyCreator(new SimpleMultiKeyCreator(keyCreator));
        } else {
            dbConfig.setKeyCreator(keyCreator);
        }

        if (foreignDb != null) {

            if (useMultiKey) {
                dbConfig.setForeignMultiKeyNullifier(keyCreator);
            } else {
                dbConfig.setForeignKeyNullifier(keyCreator);
            }
            dbConfig.setForeignKeyDatabase(foreignDb);
            dbConfig.setForeignKeyDeleteAction(onDelete);
        }

        Transaction txn = txnBegin();
        try {
            return env.openSecondaryDatabase(txn, dbName, priDb, dbConfig);
        } finally {
            txnCommit(txn);
        }
    }

    static private DatabaseEntry entry(int val) {

        return new DatabaseEntry(TestUtils.getTestArray(val));
    }

    static private int val(DatabaseEntry entry) {

        return TestUtils.getTestVal(entry.getData());
    }

    private class MyKeyCreator implements SecondaryKeyCreator,
                                          ForeignMultiKeyNullifier,
                                          ForeignKeyNullifier {

        /* SecondaryKeyCreator */
        public boolean createSecondaryKey(SecondaryDatabase secondary,
                                          DatabaseEntry key,
                                          DatabaseEntry data,
                                          DatabaseEntry result)
            throws DatabaseException {

            int val = val(data);
            if (val != 0) {
                result.setData(TestUtils.getTestArray(val));
                return true;
            } else {
                return false;
            }
        }

        /* ForeignMultiKeyNullifier */
        public boolean nullifyForeignKey(SecondaryDatabase secondary,
                                         DatabaseEntry key,
                                         DatabaseEntry data,
                                         DatabaseEntry secKey)
            throws DatabaseException {

            DatabaseEntry entry = new DatabaseEntry();
            assertTrue(createSecondaryKey(secondary, null, data, entry));
            assertEquals(entry, secKey);

            return nullifyForeignKey(secondary, data);
        }

        /* ForeignKeyNullifier */
        public boolean nullifyForeignKey(SecondaryDatabase secondary,
                                         DatabaseEntry data)
            throws DatabaseException {

            int val = val(data);
            if (val != 0) {
                data.setData(TestUtils.getTestArray(0));
                return true;
            } else {
                return false;
            }
        }
    }
}
