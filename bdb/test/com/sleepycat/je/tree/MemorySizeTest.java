/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MemorySizeTest.java,v 1.21.2.3 2007/07/02 19:54:55 mark Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import junit.framework.TestCase;

import com.sleepycat.je.CheckpointConfig;
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
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.util.TestUtils;

/**
 * Check maintenance of the memory size count within nodes.
 */
public class MemorySizeTest extends TestCase {
    private static final boolean DEBUG = false;

    private Environment env;
    private File envHome;
    private Database db;

    public MemorySizeTest()
	throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));

       	/* Print keys as numbers */
       	Key.DUMP_BINARY = true;
    }

    public void setUp()
	throws IOException, DatabaseException {

	IN.ACCUMULATED_LIMIT = 0;
	Txn.ACCUMULATED_LIMIT = 0;

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        /* 
         * Properties for creating an environment. 
         * Disable the evictor for this test, use larger BINS
         */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                                 "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CLEANER.getName(),
                       "false");

        /* Don't checkpoint utilization info for this test. */
        DbInternal.setCheckpointUP(envConfig, false);

        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "4");
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setTransactional(true);
        env = new Environment(envHome, envConfig);
    }

    public void tearDown()
	throws IOException, DatabaseException {

        if (env != null) {
            try {
                env.close();
            } catch (DatabaseException E) {
            }
        }
        TestUtils.removeFiles("TearDown", envHome,
                              FileManager.JE_SUFFIX, true);
    }

    /* 
     * Do a series of these actions and make sure that the stored memory
     * sizes match the calculated memory size.
     * - create db
     * - insert records, no split
     * - cause IN split
     * - modify
     * - delete, compress
     * - checkpoint
     * - evict
     * - insert duplicates
     * - cause duplicate IN split
     * - do an abort
     */
    public void testMemSizeMaintenance()
        throws Throwable {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        try {
            initDb();
            
            /* Insert one record. Adds two INs and an LN to our cost.*/
            insert((byte) 1, 10, (byte) 1, 100, true);
            long newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > 0);

            /* Fill out the node. */
            insert((byte) 2, 10, (byte) 2, 100, true);
            insert((byte) 3, 10, (byte) 3, 100, true);
            insert((byte) 4, 10, (byte) 4, 100, true);
            long oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* Cause a split */
            insert((byte) 5, 10, (byte) 5, 100, true);
            insert((byte) 6, 10, (byte) 6, 100, true);
            insert((byte) 7, 10, (byte) 7, 100, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* Modify data */
            modify((byte) 1, 10, (byte) 1, 1010, true);
            modify((byte) 7, 10, (byte) 7, 1010, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* Delete data */
            delete((byte) 2, 10, true);
            delete((byte) 6, 10, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize < oldSize);

            /* Compress. */
            compress();
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize < oldSize);

            /* Checkpoint */
            CheckpointConfig ckptConfig = new CheckpointConfig();
            ckptConfig.setForce(true);
            env.checkpoint(ckptConfig);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertEquals(oldSize, newSize);

            /* Evict by doing LN stripping. */
            evict();
            TestUtils.validateNodeMemUsage(envImpl, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize < oldSize);

            /* insert duplicates */
            insert((byte) 3, 10, (byte) 30, 200, true);
            insert((byte) 3, 10, (byte) 31, 200, true);
            insert((byte) 3, 10, (byte) 32, 200, true);
            insert((byte) 3, 10, (byte) 33, 200, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* create duplicate split. */
            insert((byte) 3, 10, (byte) 34, 200, true);
            insert((byte) 3, 10, (byte) 35, 200, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* There should be 11 records. */
            checkCount(11);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);

            /* modify and abort */
            modify((byte) 5, 10, (byte) 30, 1000, false);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize == oldSize);

            /* delete and abort */
            delete((byte) 1, 10, false);
            delete((byte) 7, 10, false);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);

            /* Delete dup */
            delete((byte) 3, 10, (byte)34, 200, false);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);

            /* insert and abort */
            insert((byte) 2, 10, (byte) 5, 100, false);
            insert((byte) 6, 10, (byte) 6, 100, false);
            insert((byte) 8, 10, (byte) 7, 100, false);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            if (db != null) {
                db.close();
            }

            if (env != null) {
                env.close();
            }


        }
    }

    /* 
     * Do a series of these actions and make sure that the stored memory
     * sizes match the calculated memory size.
     * - create db
     * - insert records, cause split
     * - delete
     * - insert and re-use slots.
     */
    public void testSlotReuseMaintenance()
        throws Exception {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        try {

            initDb();

            /* Insert enough records to create one node. */
            insert((byte) 1, 10, (byte) 1, 100, true);
            insert((byte) 2, 10, (byte) 2, 100, true);
            insert((byte) 3, 10, (byte) 3, 100, true);
            long newSize = TestUtils.validateNodeMemUsage(envImpl, true);

            /* Delete  */
            delete((byte) 3, 10, true);
            long oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize < oldSize);

            /* Insert again, reuse those slots */
            insert((byte) 3, 10, (byte) 2, 400, true);
            oldSize = newSize;
            newSize = TestUtils.validateNodeMemUsage(envImpl, true);
            assertTrue(newSize > oldSize);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (db != null) {
                db.close();
            }
            
            if (env != null) {
                env.close();
            }
        }
    }


    private void initDb() 
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setTransactional(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }
    
    private void insert(byte keyVal, int keySize,
                        byte dataVal, int dataSize,
                        boolean commit)
        throws DatabaseException {

        Transaction txn = null;
        if (!commit) {
            txn = env.beginTransaction(null, null);
        }
        assertEquals(OperationStatus.SUCCESS,
                     db.put(null, getEntry(keyVal, keySize),
                            getEntry(dataVal, dataSize)));
        if (!commit) {
            txn.abort();
        }
    }

    private void modify(byte keyVal, int keySize,
                        byte dataVal, int dataSize,
                        boolean commit)
        throws DatabaseException {

        Transaction txn = null;

        txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchKey(getEntry(keyVal, keySize),
                                     new DatabaseEntry(),
                                     LockMode.DEFAULT));
        assertEquals(OperationStatus.SUCCESS,
                     cursor.delete());
        assertEquals(OperationStatus.SUCCESS,
                     cursor.put(getEntry(keyVal, keySize),
				getEntry(dataVal, dataSize)));
        cursor.close();

        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    private void delete(byte keyVal, int keySize, boolean commit)
        throws DatabaseException {

        Transaction txn = null;
        if (!commit) {
            txn = env.beginTransaction(null, null);
        }
        assertEquals(OperationStatus.SUCCESS,
                     db.delete(txn, getEntry(keyVal, keySize)));
        if (!commit) {
            txn.abort();
        }
    }
    private void delete(byte keyVal, int keySize,
                        byte dataVal, int dataSize, boolean commit)
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchBoth(getEntry(keyVal, keySize),
                                          getEntry(dataVal, dataSize),
                                          LockMode.DEFAULT));
        assertEquals(OperationStatus.SUCCESS,  cursor.delete());
        cursor.close();

        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /* 
     * Fake compressing daemon by call BIN.compress explicitly on all
     * BINS on the IN list.
     */
    private void compress() 
        throws DatabaseException {

        INList inList = DbInternal.envGetEnvironmentImpl(env).getInMemoryINs();
        inList.latchMajor();
        try {
            Iterator iter = inList.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
		in.latch();
                if (in instanceof BIN) {
                    in.compress(null, true, null);
                }
		in.releaseLatch();
            }
        } finally {
            inList.releaseMajorLatch();
        }
    }

    /* 
     * Fake eviction daemon by call BIN.evictLNs explicitly on all
     * BINS on the IN list.
     */
    private void evict() 
        throws DatabaseException {

        INList inList = DbInternal.envGetEnvironmentImpl(env).getInMemoryINs();
        inList.latchMajor();
        try {
            Iterator iter = inList.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                if (in instanceof BIN &&
                    !in.getDatabase().getId().equals(DbTree.ID_DB_ID)) {
                    BIN bin = (BIN) in;
                    bin.latch();
                    assertTrue(bin.evictLNs() > 0);
                    bin.releaseLatch();
                }
            }
        } finally {
            inList.releaseMajorLatch();
        }
    }


    private DatabaseEntry getEntry(byte val, int size) {
        byte [] bArray = new byte[size];
        bArray[0] = val;
        return new DatabaseEntry(bArray);
    }

    private void checkCount(int expectedCount) 
        throws DatabaseException {

        Cursor cursor = db.openCursor(null, null);
        int count = 0;
        while (cursor.getNext(new DatabaseEntry(), new DatabaseEntry(),
                              LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            count++;
        }
        cursor.close();
        assertEquals(expectedCount, count);
    }

    private void dumpINList()
        throws DatabaseException {
       
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env); 
        INList inList = envImpl.getInMemoryINs();
        inList.latchMajor();
        try {
            Iterator iter = inList.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                System.out.println("in nodeId=" + in.getNodeId());
            }
        } finally {
            inList.releaseMajorLatch();
        }
    }
}
