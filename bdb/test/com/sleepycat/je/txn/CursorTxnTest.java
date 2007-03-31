/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CursorTxnTest.java,v 1.40.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

public class CursorTxnTest extends TestCase {
    private File envHome;
    private Environment env;
    private Database myDb;
    private int initialEnvReadLocks;
    private int initialEnvWriteLocks;
    private boolean noLocking;

    public CursorTxnTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        DbEnvPool.getInstance().clear();
    }

    public void setUp()
	throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        DbInternal.setLoadPropertyFile(envConfig, false);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

	EnvironmentConfig envConfigAsSet = env.getConfig();
	noLocking = !(envConfigAsSet.getLocking());

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        myDb = env.openDatabase(null, "test", dbConfig);
    }
    
    public void tearDown()
	throws IOException, DatabaseException {

        try {
            myDb.close();
        } catch (DatabaseException ignored) {}
        try {
            env.close();
        } catch (DatabaseException ignored) {}
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Create a cursor with a null transaction.
     */
    public void testNullTxnLockRelease()
        throws DatabaseException {

        getInitialEnvStats();
        Cursor cursor = myDb.openCursor(null, null);
        
        /* First put() holds a write lock on the non-duplicate entry. */
        insertData(cursor, 10, 1);
        checkReadWriteLockCounts(cursor, 0, 1);

        // Check that count does not add more locks
        int count = cursor.count();
        assertEquals(1, count);
        checkReadWriteLockCounts(cursor, 0, 1);

        /* 
	 * Second put() holds a write lock on first record (since it
	 * was write locked to discover that the duplicate tree is not
	 * present yet) and a write lock on the duplicate entry and a
	 * write lock on the DupCountLN.
	 */
        insertData(cursor, 10, 2);
        checkReadWriteLockCounts(cursor, 0, 3);

        /* Check that count does not add more locks. */
        count = cursor.count();
        assertEquals(2, count);
        checkReadWriteLockCounts(cursor, 0, 3);
        
        /* Third put() holds a write lock on the duplicate entry and a write
         * lock on the DupCountLN. */
        insertData(cursor, 10, 3);
        checkReadWriteLockCounts(cursor, 0, 2);

        DatabaseEntry foundKey = new DatabaseEntry();
        DatabaseEntry foundData = new DatabaseEntry();

        /* Check that read locks are held on forward traversal. */
        OperationStatus status =
	    cursor.getFirst(foundKey, foundData, LockMode.DEFAULT);
        checkReadWriteLockCounts(cursor, 1, 0);
        int numSeen = 0;
        while (status == OperationStatus.SUCCESS) {
            numSeen++;
            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
            checkReadWriteLockCounts(cursor, 1, 0);
	    if (status != OperationStatus.SUCCESS) {
		break;
	    }

            status = cursor.getCurrent(foundKey, foundData,
                                       LockMode.DEFAULT);
            checkReadWriteLockCounts(cursor, 1, 0);
        }
        assertEquals(30, numSeen);

        /* Check that read locks are held on backwards traversal and count. */
        status = cursor.getLast(foundKey, foundData, LockMode.DEFAULT);
        checkReadWriteLockCounts(cursor, 1, 0);

        while (status == OperationStatus.SUCCESS) {
            count = cursor.count();
            assertEquals("For key " +
                         TestUtils.dumpByteArray(foundKey.getData()),
                         3, count);
            status = cursor.getPrev(foundKey, foundData, LockMode.DEFAULT);
            checkReadWriteLockCounts(cursor, 1, 0);
        }

        /* Check that delete holds a write lock. */
        status = cursor.getFirst(foundKey, foundData, LockMode.DEFAULT);
        while (status == OperationStatus.SUCCESS) {
            assertEquals("For key " +
                         TestUtils.dumpByteArray(foundKey.getData()),
                         OperationStatus.SUCCESS, cursor.delete());
            checkReadWriteLockCounts(cursor, 0, 2);
            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
            if (status == OperationStatus.SUCCESS) {
                checkReadWriteLockCounts(cursor, 1, 0);
            } else {
                checkReadWriteLockCounts(cursor, 0, 2);
            }
        }

        /* Check that count does not add more locks. */
        count = cursor.count();
        assertEquals(0, count);
        checkReadWriteLockCounts(cursor, 0, 2);
        
	cursor.close();
    }

    private void checkReadWriteLockCounts(Cursor cursor,
                                          int expectReadLocks,
                                          int expectWriteLocks)
        throws DatabaseException {

	if (noLocking) {
	    expectReadLocks = expectWriteLocks = 0;
	}

        CursorImpl cursorImpl = DbTestProxy.dbcGetCursorImpl(cursor);
        LockStats lockStats = cursorImpl.getLockStats();
        assertEquals(expectReadLocks, lockStats.getNReadLocks());
        assertEquals(expectWriteLocks, lockStats.getNWriteLocks());
        lockStats = env.getLockStats(null);
        assertEquals(initialEnvReadLocks + expectReadLocks,
                     lockStats.getNReadLocks());
        assertEquals(initialEnvWriteLocks + expectWriteLocks,
                     lockStats.getNWriteLocks());
    }

    private void getInitialEnvStats()
        throws DatabaseException {

        LockStats lockStats = env.getLockStats(null);
        initialEnvReadLocks = lockStats.getNReadLocks();
        initialEnvWriteLocks = lockStats.getNWriteLocks();
    }
    
    private void insertData(Cursor cursor, int numRecords, int dataVal)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        for (int i = 0; i < numRecords; i++) {
            byte[] keyData = TestUtils.getTestArray(i);
            byte[] dataData = new byte[1];
            dataData[0] = (byte) dataVal;
            key.setData(keyData);
            data.setData(dataData);
            OperationStatus status = cursor.putNoDupData(key, data);
            assertEquals(OperationStatus.SUCCESS, status);
        }
    }
}
