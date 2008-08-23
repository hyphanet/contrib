/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ToManyTest.java,v 1.10 2008/07/01 03:21:34 tao Exp $
 */

package com.sleepycat.je.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.Test;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryMultiKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.util.test.TxnTestCase;

/**
 * Tests multi-key secondary operations.  Exhaustive API testing of multi-key
 * secondaries is part of SecondaryTest and ForeignKeyTest, which test the use
 * of a single key with SecondaryMultiKeyCreator.  This class adds tests for
 * multiple keys per record.
 */
public class ToManyTest extends TxnTestCase {

    /*
     * The primary database has a single byte key and byte[] array data.  Each
     * byte of the data array is a secondary key in the to-many index.
     *
     * The primary map mirrors the primary database and contains Byte keys and
     * a set of Byte objects for each map entry value.  The secondary map
     * mirrors the secondary database, and for every secondary key (Byte)
     * contains a set of primary keys (set of Byte).
     */
    private Map<Byte, Set<Byte>> priMap0 = new HashMap<Byte, Set<Byte>>();
    private Map<Byte, Set<Byte>> secMap0 = new HashMap<Byte, Set<Byte>>();
    private Database priDb;
    private SecondaryDatabase secDb;

    public static Test suite() {

        /*
         * This test does not work with TXN_NULL because with transactions we
         * cannot abort the update in a one-to-many test when secondary key
         * already exists in another primary record.
         */
        return txnTestSuite(ToManyTest.class, null,
                            new String[] {TxnTestCase.TXN_USER,
                                          TxnTestCase.TXN_AUTO});
    }

    public void tearDown()
        throws Exception {

        super.tearDown();
        priMap0 = null;
        secMap0 = null;
        priDb = null;
        secDb = null;
    }

    public void testManyToMany()
        throws DatabaseException {

        priDb = openPrimary("pri");
        secDb = openSecondary(priDb, "sec", true /*dups*/);

        writeAndVerify((byte) 0, new byte[] {});
        writeAndVerify((byte) 0, null);
        writeAndVerify((byte) 0, new byte[] {0, 1, 2});
        writeAndVerify((byte) 0, null);
        writeAndVerify((byte) 0, new byte[] {});
        writeAndVerify((byte) 0, new byte[] {0});
        writeAndVerify((byte) 0, new byte[] {0, 1});
        writeAndVerify((byte) 0, new byte[] {0, 1, 2});
        writeAndVerify((byte) 0, new byte[] {1, 2});
        writeAndVerify((byte) 0, new byte[] {2});
        writeAndVerify((byte) 0, new byte[] {});
        writeAndVerify((byte) 0, null);

        writeAndVerify((byte) 0, new byte[] {0, 1, 2});
        writeAndVerify((byte) 1, new byte[] {1, 2, 3});
        writeAndVerify((byte) 0, null);
        writeAndVerify((byte) 1, null);
        writeAndVerify((byte) 0, new byte[] {0, 1, 2});
        writeAndVerify((byte) 1, new byte[] {1, 2, 3});
        writeAndVerify((byte) 0, new byte[] {0});
        writeAndVerify((byte) 1, new byte[] {3});
        writeAndVerify((byte) 0, null);
        writeAndVerify((byte) 1, null);

        secDb.close();
        priDb.close();
    }

    public void testOneToMany()
        throws DatabaseException {

        priDb = openPrimary("pri");
        secDb = openSecondary(priDb, "sec", false /*dups*/);

        writeAndVerify((byte) 0, new byte[] {1, 5});
        writeAndVerify((byte) 1, new byte[] {2, 4});
        writeAndVerify((byte) 0, new byte[] {0, 1, 5, 6});
        writeAndVerify((byte) 1, new byte[] {2, 3, 4});
        write((byte) 0, new byte[] {3}, true /*expectException*/);
        writeAndVerify((byte) 1, new byte[] {});
        writeAndVerify((byte) 0, new byte[] {0, 1, 2, 3, 4, 5, 6});
        writeAndVerify((byte) 0, null);
        writeAndVerify((byte) 1, new byte[] {0, 1, 2, 3, 4, 5, 6});
        writeAndVerify((byte) 1, null);

        secDb.close();
        priDb.close();
    }

    /**
     * Puts or deletes a single primary record, updates the maps, and verifies
     * that the maps match the databases.
     */
    private void writeAndVerify(byte priKey, byte[] priData)
        throws DatabaseException {

        write(priKey, priData, false /*expectException*/);
        updateMaps(new Byte(priKey), bytesToSet(priData));
        verify();
    }

    /**
     * Puts or deletes a single primary record.
     */
    private void write(byte priKey, byte[] priData, boolean expectException)
        throws DatabaseException {

        DatabaseEntry keyEntry = new DatabaseEntry(new byte[] { priKey });
        DatabaseEntry dataEntry = new DatabaseEntry(priData);

        Transaction txn = txnBegin();
        try {
            OperationStatus status;
            if (priData != null) {
                status = priDb.put(txn, keyEntry, dataEntry);
            } else {
                status = priDb.delete(txn, keyEntry);
            }
            assertSame(OperationStatus.SUCCESS, status);
            txnCommit(txn);
            assertTrue(!expectException);
        } catch (Exception e) {
            txnAbort(txn);
            assertTrue(e.toString(), expectException);
        }
    }

    /**
     * Updates map 0 to reflect a record added to the primary database.
     */
    private void updateMaps(Byte priKey, Set<Byte> newPriData) {

        /* Remove old secondary keys. */
        Set<Byte> oldPriData = priMap0.get(priKey);
        if (oldPriData != null) {
            for (Iterator<Byte> i = oldPriData.iterator(); i.hasNext();) {
                Byte secKey = (Byte) i.next();
                Set<Byte> priKeySet = secMap0.get(secKey);
                assertNotNull(priKeySet);
                assertTrue(priKeySet.remove(priKey));
                if (priKeySet.isEmpty()) {
                    secMap0.remove(secKey);
                }
            }
        }

        if (newPriData != null) {
            /* Put primary entry. */
            priMap0.put(priKey, newPriData);
            /* Add new secondary keys. */
            for (Iterator<Byte> i = newPriData.iterator(); i.hasNext();) {
                Byte secKey = i.next();
                Set<Byte> priKeySet = secMap0.get(secKey);
                if (priKeySet == null) {
                    priKeySet = new HashSet<Byte>();
                    secMap0.put(secKey, priKeySet);
                }
                assertTrue(priKeySet.add(priKey));
            }
        } else {
            /* Remove primary entry. */
            priMap0.remove(priKey);
        }
    }

    /**
     * Verifies that the maps match the databases.
     */
    private void verify()
        throws DatabaseException {

        Transaction txn = txnBeginCursor();
        DatabaseEntry priKeyEntry = new DatabaseEntry();
        DatabaseEntry secKeyEntry = new DatabaseEntry();
        DatabaseEntry dataEntry = new DatabaseEntry();
        Map<Byte, Set<Byte>> priMap1 = new HashMap<Byte, Set<Byte>>();
        Map<Byte, Set<Byte>> priMap2 = new HashMap<Byte, Set<Byte>>();
        Map<Byte, Set<Byte>> secMap1 = new HashMap<Byte, Set<Byte>>();
        Map<Byte, Set<Byte>> secMap2 = new HashMap<Byte, Set<Byte>>();

        /* Build map 1 from the primary database. */
        priMap2 = new HashMap<Byte, Set<Byte>>();
        Cursor priCursor = priDb.openCursor(txn, null);
        while (priCursor.getNext(priKeyEntry, dataEntry, null) ==
               OperationStatus.SUCCESS) {
            Byte priKey = new Byte(priKeyEntry.getData()[0]);
            Set<Byte> priData = bytesToSet(dataEntry.getData());

            /* Update primary map. */
            priMap1.put(priKey, priData);

            /* Update secondary map. */
            for (Iterator<Byte> i = priData.iterator(); i.hasNext();) {
                Byte secKey = i.next();
                Set<Byte> priKeySet = secMap1.get(secKey);
                if (priKeySet == null) {
                    priKeySet = new HashSet<Byte>();
                    secMap1.put(secKey, priKeySet);
                }
                assertTrue(priKeySet.add(priKey));
            }

            /*
             * Add empty primary records to priMap2 while we're here, since
             * they cannot be built from the secondary database.
             */
            if (priData.isEmpty()) {
                priMap2.put(priKey, priData);
            }
        }
        priCursor.close();

        /* Build map 2 from the secondary database. */
        SecondaryCursor secCursor = secDb.openSecondaryCursor(txn, null);
        while (secCursor.getNext(secKeyEntry, priKeyEntry, dataEntry, null) ==
               OperationStatus.SUCCESS) {
            Byte priKey = new Byte(priKeyEntry.getData()[0]);
            Byte secKey = new Byte(secKeyEntry.getData()[0]);

            /* Update primary map. */
            Set<Byte> priData = priMap2.get(priKey);
            if (priData == null) {
                priData = new HashSet<Byte>();
                priMap2.put(priKey, priData);
            }
            priData.add(secKey);

            /* Update secondary map. */
            Set<Byte> secData = secMap2.get(secKey);
            if (secData == null) {
                secData = new HashSet<Byte>();
                secMap2.put(secKey, secData);
            }
            secData.add(priKey);
        }
        secCursor.close();

        /* Compare. */
        assertEquals(priMap0, priMap1);
        assertEquals(priMap1, priMap2);
        assertEquals(secMap0, secMap1);
        assertEquals(secMap1, secMap2);

        txnCommit(txn);
    }

    private Set<Byte> bytesToSet(byte[] bytes) {
        Set<Byte> set = null;
        if (bytes != null) {
            set = new HashSet<Byte>();
            for (int i = 0; i < bytes.length; i += 1) {
                set.add(new Byte(bytes[i]));
            }
        }
        return set;
    }

    private Database openPrimary(String name)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);

        Transaction txn = txnBegin();
        try {
            return env.openDatabase(txn, name, dbConfig);
        } finally {
            txnCommit(txn);
        }
    }

    private SecondaryDatabase openSecondary(Database priDb,
                                            String dbName,
                                            boolean dups)
        throws DatabaseException {

        SecondaryConfig dbConfig = new SecondaryConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        dbConfig.setMultiKeyCreator(new MyKeyCreator());

        Transaction txn = txnBegin();
        try {
            return env.openSecondaryDatabase(txn, dbName, priDb, dbConfig);
        } finally {
            txnCommit(txn);
        }
    }

    private static class MyKeyCreator implements SecondaryMultiKeyCreator {

        public void createSecondaryKeys(SecondaryDatabase secondary,
                                        DatabaseEntry key,
                                        DatabaseEntry data,
                                        Set<DatabaseEntry> results)
            throws DatabaseException {

            for (int i = 0; i < data.getSize(); i+= 1) {
                results.add(new DatabaseEntry(data.getData(), i, 1));
            }
        }
    }
}
