/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BinDeltaTest.java,v 1.44.2.1 2007/02/01 14:50:21 cwl Exp $
 */
package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

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
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Exercise the delta based BIN logging.
 */
public class BinDeltaTest extends TestCase {
    private static final String DB_NAME = "test";
    private static final boolean DEBUG = false;
    private Environment env;
    private File envHome;
    private Database db;
    private LogManager logManager;

    public BinDeltaTest() throws DatabaseException {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));

       	/* Print keys as numbers */
       	Key.DUMP_BINARY = true;
    }

    public void setUp() throws IOException, DatabaseException {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        /* 
         * Properties for creating an environment. 
         * Disable the evictor for this test, use larger BINS
         */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_EVICTOR.getName(), "true");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "50");
        envConfig.setConfigParam(EnvironmentParams.BIN_DELTA_PERCENT.getName(), "50");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
        logManager = DbInternal.envGetEnvironmentImpl(env).getLogManager();
    }

    public void tearDown() throws IOException, DatabaseException {
        if (env != null) {
            try {
                env.close();
            } catch (DatabaseException E) {
            }
        }
        TestUtils.removeFiles("TearDown", envHome,
                              FileManager.JE_SUFFIX, true);
    }

    /**
     * Create a db, fill with numRecords, return the first BIN.
     * @param numRecords
     */
    private BIN initDb(int start, int end) 
        throws DatabaseException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, DB_NAME, dbConfig);

        addRecords(start, end);

        /* Now reach into the tree and get the first BIN */
        Locker txn = new BasicLocker(DbInternal.envGetEnvironmentImpl(env));
        CursorImpl internalCursor = new CursorImpl(DbInternal.dbGetDatabaseImpl(db),
						   txn);
        assertTrue(internalCursor.positionFirstOrLast(true, null));
        BIN firstBIN = internalCursor.getBIN();
        firstBIN.releaseLatch();
        internalCursor.close();
        txn.operationEnd();
        return firstBIN;
    }

    /**
     * Modify the data, just to dirty the BIN.
     */
    private void modifyRecords(int start, int end, int increment)
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        DatabaseEntry searchKey = new DatabaseEntry();
        DatabaseEntry foundData = new DatabaseEntry();
        DatabaseEntry newData = new DatabaseEntry();
        
        for (int i = start; i <= end; i++) {
            searchKey.setData(TestUtils.getTestArray(i));
            assertEquals(OperationStatus.SUCCESS,
                         cursor.getSearchKey(searchKey, foundData,
                                             LockMode.DEFAULT));
            newData.setData(TestUtils.getTestArray(i+increment));
            cursor.putCurrent(newData);
        }
	cursor.close();
        txn.commit();
    }

    /* 
     * Add the specified records.
     */
    private void addRecords(int start, int end) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = start;  i < end; i++) {
            byte [] keyData = TestUtils.getTestArray(i);
            byte [] dataData = TestUtils.byteArrayCopy(keyData);
            key.setData(keyData);
            data.setData(dataData);
            db.put(null, key, data);
        }
    }
	
    /**
     * Simple test, delta a BIN several times, reconstruct.
     */
    public void testSimple()
    	throws Throwable {

        try {
            /* Create a db, insert records value 10 - 30, get the first BIN */
            BIN bin = initDb(10, 30);

            /* Log a full version. */
	    bin.latch();
            long fullLsn = bin.log
                (logManager, true, false, false, false, null);
	    bin.releaseLatch();
            assertTrue(fullLsn != DbLsn.NULL_LSN);
          
            if (DEBUG) {
                System.out.println("Start");
                System.out.println(bin.dumpString(0, true));
            }
          
            /* Modify some of the data, add data so the BIN is changed. */
            modifyRecords(11,13,10);
            addRecords(1,3);
            logAndCheck(bin);

            /* Modify more of the data, so the BIN is changed. */
            modifyRecords(14,15,10);
            logAndCheck(bin);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            db.close();
	}
    }

    /**
     * Test that a delta is correctly generated when there are entries
     * that have been aborted and rolled back.
     *
     * The case we're trying to test, (that was in error before)
     *  - a record is deleted
     *  - a full version of BIN x is written to the log, reflecting that
     *    deletion.
     *  - the deleting txn is aborted, so the record is restored. Now the
     *    BIN has an entry where the child LSN is less than the last full
     *    BIN version LSN.
     *  - generate a delta, make sure that the restoration of the record is
     *    present.
     */
    public void testUndo()
        throws Throwable {

        try {
            /* Create a db, insert records value 10 - 30, get the first BIN */
            BIN bin = initDb(10, 30);

            /* Delete the first record, then abort the delete. */
            Transaction txn = env.beginTransaction(null, null);
            Cursor cursor = db.openCursor(txn, null);
            DatabaseEntry firstKey = new DatabaseEntry();
            DatabaseEntry foundData = new DatabaseEntry();
            OperationStatus status = cursor.getFirst(firstKey, foundData,
						     LockMode.DEFAULT);
            assertEquals(OperationStatus.SUCCESS, status);
            status = cursor.delete();
            assertEquals(OperationStatus.SUCCESS, status);
	    cursor.close();

            /* Log a full version. This will reflect the delete. */
	    bin.latch();
            long fullLsn = bin.log
                (logManager, true, false, false, false, null);
	    bin.releaseLatch();
            assertTrue(fullLsn != DbLsn.NULL_LSN);
          
            /* 
             * Roll back the deletion. Now the full version of the LSN is out
             * of date.
             */
            txn.abort();

            /* 
             * Make sure a delta reflect the abort, even though the abort
             * returns an older LSN back into the BIN.
             */
            logAndCheck(bin);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            db.close();
        }
    }

    /* Check if full is logged when percent > max */
    /* Check that max deltas works. */
    /* check knownDelete. */

    /**
     * Log the targetBIN, then read it back from the log and make sure
     * the recreated BIN matches the in memory BIN.
     */
    private void logAndCheck(BIN targetBIN) 
        throws DatabaseException {

        /*
         *  Log it as a delta. If the logging was done as a delta, this method
         * returns null, so we expect null
         */
        assertTrue(targetBIN.log
                    (logManager, true, false, false, false, null) ==
		   DbLsn.NULL_LSN);

        /* Read the delta back. */
        LogEntry partial =
            logManager.getLogEntry(targetBIN.getLastDeltaVersion());

        /* Make sure that this is was a delta entry. */
        assertTrue(partial.getMainItem() instanceof BINDelta);
        BINDelta delta = (BINDelta) partial.getMainItem();

        /* Compare to the current version. */
        BIN createdBIN =
            delta.reconstituteBIN(DbInternal.envGetEnvironmentImpl(env));
        if (DEBUG) {
            System.out.println("created");
            System.out.println(createdBIN.dumpString(0, true));
        }
          
        assertEquals(targetBIN.getClass().getName(),
                     createdBIN.getClass().getName());
        assertEquals(targetBIN.getNEntries(), createdBIN.getNEntries());
        
        for (int i = 0; i < createdBIN.getNEntries(); i++) {
            assertEquals("LSN " + i, targetBIN.getLsn(i),
                         createdBIN.getLsn(i));
        }
        assertEquals(true, createdBIN.getDirty());
        assertEquals(true, targetBIN.getDirty());
    }
}
