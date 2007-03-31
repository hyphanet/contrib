/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryCreateDupTest.java,v 1.8.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Hashtable;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.SearchFileReader;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.DbLsn;

/*
 * Test the log entries that are made when a duplicate tree is
 * created. Inspired by SR 10203.
 */
public class RecoveryCreateDupTest extends RecoveryTestBase {

    /* 
     * These tests insert 2 records in order to create a duplicate tree.  Then
     * they truncate the log and recover, checking that (a) the recovery was
     * able to succeed, and (b), that the results are correct.
     *
     * They vary where the truncation happens and if the two records are
     * inserted in a single or in two txns.
     */

    public void testCreateDup1() 
        throws Throwable {
        errorHaltCase(false, true);
    }

    public void testCreateDup2() 
        throws Throwable {
        errorHaltCase(true, true);
    }

    public void testCreateDup3() 
        throws Throwable {
        errorHaltCase(false, false);
    }

    public void testCreateDup4() 
        throws Throwable {
        errorHaltCase(true, false);
    }

    /**
     * Insert 2 duplicate records, cut the log off at different places,
     * recover.
     *
     * @param allValuesCreatedWithinTxn true if both records are inserted
     * the same txn.
     * @param truncateBeforeDIN if true, truncate just before the DIN entry.
     * If false, truncate before the first LN
     */
    private void errorHaltCase(boolean allValuesCreatedWithinTxn,
                               boolean truncateBeforeDIN) 
        throws Throwable {

        /* test data setup. */
        byte [] key = new byte [1];
        key[0] = 5;
        DatabaseEntry keyEntry1 = new DatabaseEntry(key);
        DatabaseEntry keyEntry2 = new DatabaseEntry(key);
        byte [] data1 = new byte [1];
        byte [] data2 = new byte [1];
        data1[0] = 7;
        data2[0] = 8;
        DatabaseEntry dataEntry1 = new DatabaseEntry(data1);
        DatabaseEntry dataEntry2 = new DatabaseEntry(data2);

        /* Create 1 database. */
        createEnvAndDbs(1 << 20, true, 1);
        try {
            /* 
             * Set up an repository of expected data. We'll be inserting
             * 2 records, varying whether they are in the same txn or not.
             */
            Hashtable expectedData = new Hashtable();

            Transaction txn = env.beginTransaction(null, null);
            dbs[0].put(txn, keyEntry1, dataEntry1);
            addExpectedData(expectedData, 0, keyEntry1, dataEntry1, 
                            !allValuesCreatedWithinTxn);

            if (!allValuesCreatedWithinTxn) {
                txn.commit();
                txn = env.beginTransaction(null, null);
            }

            dbs[0].put(txn, keyEntry2, dataEntry2);
            addExpectedData(expectedData, 0, keyEntry2, dataEntry2, false);

            txn.commit();
            closeEnv();

            /* 
             * Find the location of the DIN and the location of the followon
             * LN.
             */

            env = new Environment(envHome, null);
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            SearchFileReader searcher = 
                new SearchFileReader(envImpl, 1000, true, DbLsn.NULL_LSN,
				     DbLsn.NULL_LSN, LogEntryType.LOG_DIN);
            searcher.readNextEntry();
            long dinLsn = searcher.getLastLsn();

            searcher = 
                new SearchFileReader(envImpl, 1000, true, dinLsn,
				     DbLsn.NULL_LSN,
                                     LogEntryType.LOG_LN_TRANSACTIONAL);
            searcher.readNextEntry();
            long lnLsn = searcher.getLastLsn();
            
            env.close();

            /*
             *  Truncate the log, sometimes before the DIN, sometimes after. 
             */
            EnvironmentImpl cmdEnvImpl =
                CmdUtil.makeUtilityEnvironment(envHome, false);

            /* Go through the file manager to get the JE file. Truncate. */
            long truncateLsn = truncateBeforeDIN ? dinLsn : lnLsn;
            cmdEnvImpl.getFileManager().
                truncateLog(DbLsn.getFileNumber(truncateLsn),
                            DbLsn.getFileOffset(truncateLsn));

            cmdEnvImpl.close();

            /* 
             * Recover and verify that we have the expected data.
             */
            recoverAndVerify(expectedData, 1);
            

	} catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test when a duplicate tree reuses an entry previously populated by
     * a deleted LN. [#SR12847]
     * The sequence is this:
     *   create database
     *   insert k1/d1 (make BIN with a slot for k1)
     *   abort the insert, so the BIN is marked known deleted
     *   flush the BIN to the log
     *
     *   insert k1/d100
     *   insert k1/d200 (creates a new duplicate tree)
     *
     * Now recover from here. The root of the duplicate tree must be put
     * into the old known deleted slot used for k1/d1. There is some
     * finagling to make this happen; namely the BIN must not be compressed
     * during checkpoint.
     */
    public void testReuseSlot() 
        throws DatabaseException {

        /* Create 1 database. */
        createEnvAndDbs(1 << 20, false, 1);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Insert a record, then abort it so it's marked knownDeleted. */
        Transaction txn = env.beginTransaction(null, null);
        IntegerBinding.intToEntry(100, key);
        IntegerBinding.intToEntry(1, data);
        dbs[0].put(txn, key, data);
        txn.abort();

        /* 
         * Put a cursor on this bin to prevent lazy compression and preserve
         * the slot created above.
         */
        IntegerBinding.intToEntry(200, key);
        IntegerBinding.intToEntry(1, data);
        txn = env.beginTransaction(null, null);
        Cursor c = dbs[0].openCursor(txn, null);
        c.put(key, data);

        /* Flush this bin to the log. */
        CheckpointConfig ckptConfig = new CheckpointConfig();
        ckptConfig.setForce(true);
        env.checkpoint(ckptConfig);
        c.close();
        txn.abort();

        /* 
         * Now create a duplicate tree, reusing the known deleted slot
         * in the bin.
         */
        Hashtable expectedData = new Hashtable();
        IntegerBinding.intToEntry(100, key);
        IntegerBinding.intToEntry(1, data);
        dbs[0].put(null, key, data);
        addExpectedData(expectedData, 0, key, data, true);

        IntegerBinding.intToEntry(2, data);
        dbs[0].put(null, key, data);
        addExpectedData(expectedData, 0, key, data, true);

        /* close the environment. */
        closeEnv();

        recoverAndVerify(expectedData, 1);
    }
}
