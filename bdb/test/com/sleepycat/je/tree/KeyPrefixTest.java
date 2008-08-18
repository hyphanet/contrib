/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: KeyPrefixTest.java,v 1.2 2008/03/20 18:13:54 linda Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.tree.Key.DumpType;
import com.sleepycat.je.util.TestUtils;

public class KeyPrefixTest extends TestCase {

    private File envHome;
    private Environment env;
    private Database db;

    public KeyPrefixTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        try {
            db.close();
            env.close();
        } catch (DatabaseException E) {
        }

        TestUtils.removeLogFiles("TearDown", envHome, true);
    }

    private void initEnv(int nodeMax)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        if (nodeMax > 0) {
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     Integer.toString(nodeMax));
        }
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        String databaseName = "testDb";
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setKeyPrefixing(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, databaseName, dbConfig);
    }

    private static final String[] keys = {
        "aaa", "aab", "aac", "aae",                // BIN1
        "aaf", "aag", "aah", "aaj",                // BIN2
        "aak", "aala", "aalb", "aam",              // BIN3
        "aan", "aao", "aap", "aas",                // BIN4
        "aat", "aau", "aav", "aaz",                // BIN5
        "baa", "bab", "bac", "bam",                // BIN6
        "ban", "bax", "bay", "baz",                // BIN7
        "caa", "cab", "cay", "caz",                // BIN8
        "daa", "eaa", "faa", "fzz",                // BIN10
        "Aaza", "Aazb", "aal", "aama"
    };

    public void testPrefixBasic()
        throws Exception {

        initEnv(5);
        Key.DUMP_TYPE = DumpType.TEXT;
        try {

            /* Build up a tree. */
            for (int i = 0; i < keys.length; i++) {
                assertEquals(OperationStatus.SUCCESS,
                             db.put(null,
                                    new DatabaseEntry(keys[i].getBytes()),
                                    new DatabaseEntry(new byte[] { 1 })));
            }

            String[] sortedKeys = new String[keys.length];
            System.arraycopy(keys, 0, sortedKeys, 0, keys.length);
            Arrays.sort(sortedKeys);

            Cursor cursor = null;
            int i = 0;
            try {
                cursor = db.openCursor(null, null);
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();

                boolean somePrefixSeen = false;
                while (cursor.getNext(key, data, LockMode.DEFAULT) ==
                       OperationStatus.SUCCESS) {
                    assertEquals(new String(key.getData()), sortedKeys[i++]);
                    byte[] prefix =
                        DbInternal.getCursorImpl(cursor).getBIN().
                        getKeyPrefix();
                    if (prefix != null) {
                        somePrefixSeen = true;
                    }
                }
                assertTrue(somePrefixSeen);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            if (false) {
                System.out.println("<dump>");
                DbInternal.dbGetDatabaseImpl(db).getTree().dump();
            }

        } catch (Throwable t) {
            t.printStackTrace();
            throw new Exception(t);
        }
    }

    public void testPrefixManyRandom()
        throws Exception {

        doTestPrefixMany(true);
    }

    public void testPrefixManySequential()
        throws Exception {

        doTestPrefixMany(false);
    }

    private void doTestPrefixMany(boolean random)
        throws Exception {

        initEnv(0);
        final int N_EXTRA_ENTRIES = 1000;
        Key.DUMP_TYPE = DumpType.BINARY;
        try {

            /* 2008-02-28 11:06:50.009 */
            long start = 1204214810009L;

            /* 3 years after start. Prefixes will be 3 and 4 bytes long. */
            long end = start + (long) (3L * 365L * 24L * 60L * 60L * 1000L);

            /* This will yield 94,608 entries. */
            long inc = 1000000L;
            int nEntries = insertTimestamps(start, end, inc, random);

            /*
             * This will force some splits on the left side of the tree which
             * will force recalculating the suffix on the leg after the initial
             * prefix/suffix calculation.
             */
            insertExtraTimestamps(0, N_EXTRA_ENTRIES);

            /* Do the same on the right side of the tree. */
            insertExtraTimestamps(end, N_EXTRA_ENTRIES);
            assertEquals((nEntries + 2 * N_EXTRA_ENTRIES), db.count());

            Cursor cursor = null;
            try {
                cursor = db.openCursor(null, null);

                verifyEntries(0, N_EXTRA_ENTRIES, cursor, 1);
                verifyEntries(start, nEntries, cursor, inc);
                verifyEntries(end, N_EXTRA_ENTRIES, cursor, 1);

                deleteEntries(start, nEntries);
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                cursor.close();
                cursor = db.openCursor(null, null);
                verifyEntries(0, N_EXTRA_ENTRIES, cursor, 1);
                assertEquals(OperationStatus.SUCCESS,
                             cursor.getNext(key, data, LockMode.DEFAULT));
                assertEquals(end, LongBinding.entryToLong(key));
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            if (false) {
                System.out.println("<dump>");
                DbInternal.dbGetDatabaseImpl(db).getTree().dump();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new Exception(t);
        }
    }

    private int insertTimestamps(long start,
                                 long end,
                                 long inc,
                                 boolean random)
        throws DatabaseException {

        int nEntries = (int) ((end - start) / inc);
        List<Long> keyList = new ArrayList<Long>(nEntries);
        long[] keys = null;
        if (random) {
            for (long i = start; i < end; i += inc) {
                keyList.add(i);
            }
            keys = new long[keyList.size()];
            Random rnd = new Random(10); // fixed seed
            int nextKeyIdx = 0;
            while (keyList.size() > 0) {
                int idx = rnd.nextInt(keyList.size());
                keys[nextKeyIdx++] = keyList.get(idx);
                keyList.remove(idx);
            }
        }

        /* Build up a tree. */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        data.setData(new byte[1]);
        int j = 0;
        for (long i = start; i < end; i += inc) {
            if (random) {
                LongBinding.longToEntry(keys[j], key);
            } else {
                LongBinding.longToEntry(i, key);
            }
            j++;
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, key, data));
        }
        return j;
    }

    private void insertExtraTimestamps(long start, int nExtraEntries)
        throws DatabaseException {

        /* Add (more than one node's worth) to the left side of the tree.*/
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[] { 0 });
        long next = start;
        for (int i = 0; i < nExtraEntries; i++) {
            LongBinding.longToEntry((long) next, key);
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, key, data));
            next++;
        }
    }

    private void deleteEntries(long start, int nEntries)
        throws DatabaseException {

        Cursor cursor = db.openCursor(null, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            LongBinding.longToEntry(start, key);
            assertEquals(OperationStatus.SUCCESS,
                         cursor.getSearchKey(key, data, LockMode.DEFAULT));
            for (int i = 0; i < nEntries; i++) {
                assertEquals(OperationStatus.SUCCESS, cursor.delete());
                assertEquals(OperationStatus.SUCCESS,
                             cursor.getNext(key, data, LockMode.DEFAULT));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void verifyEntries(long start,
                               int nEntries,
                               Cursor cursor,
                               long inc)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        long check = start;
        for (int i = 0; i < nEntries; i++) {
            assertEquals(OperationStatus.SUCCESS,
                         cursor.getNext(key, data, LockMode.DEFAULT));
            long keyInfo = LongBinding.entryToLong(key);
            assertTrue(keyInfo == check);
            check += inc;
        }
    }
}
