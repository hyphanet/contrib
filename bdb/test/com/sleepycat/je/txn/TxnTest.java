/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TxnTest.java,v 1.58.2.1 2007/02/01 14:50:22 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.util.TestUtils;

/*
 * Simple transaction testing
 */
public class TxnTest extends TestCase {
    private File envHome;
    private Environment env;
    private Database db;

    public TxnTest()
        throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() 
        throws IOException, DatabaseException {

	IN.ACCUMULATED_LIMIT = 0;
	Txn.ACCUMULATED_LIMIT = 0;

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }

    public void tearDown()
        throws IOException, DatabaseException {

        db.close();
        env.close();
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Test transaction locking and releasing
     */
    public void testBasicLocking()
        throws Throwable {

        try {

            LN ln = new LN(new byte[0]);

            /*
             * Make a null txn that will lock. Take a lock and then end the 
             * operation.
             */
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            MemoryBudget mb = envImpl.getMemoryBudget();
            
            long beforeLock = mb.getCacheMemoryUsage();
            Locker nullTxn = new BasicLocker(envImpl);

            LockGrantType lockGrant = nullTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            long afterLock = mb.getCacheMemoryUsage();
            checkHeldLocks(nullTxn, 1, 0);

            nullTxn.operationEnd();
            long afterRelease = mb.getCacheMemoryUsage();
            checkHeldLocks(nullTxn, 0, 0);
            checkCacheUsage(beforeLock, afterLock, afterRelease,
                            LockManager.TOTAL_LOCK_OVERHEAD +
                            MemoryBudget.LOCKINFO_OVERHEAD);

            /* Take a lock, release it. */
            beforeLock = mb.getCacheMemoryUsage();
            lockGrant = nullTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            afterLock = mb.getCacheMemoryUsage();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(nullTxn, 1, 0);

            nullTxn.releaseLock(ln.getNodeId());
            checkHeldLocks(nullTxn, 0, 0);
            afterRelease = mb.getCacheMemoryUsage();
            checkCacheUsage(beforeLock, afterLock, afterRelease,
                            LockManager.TOTAL_LOCK_OVERHEAD +
                            MemoryBudget.LOCKINFO_OVERHEAD);

            /*
             * Make a user transaction, check lock and release.
             */
            beforeLock = mb.getCacheMemoryUsage();
            Txn userTxn = new Txn(envImpl, new TransactionConfig());
            lockGrant = userTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            afterLock = mb.getCacheMemoryUsage();

            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 1, 0);

            /* Try demoting, nothing should happen. */
            try {
                userTxn.demoteLock(ln.getNodeId());
                fail("exception not thrown on phoney demoteLock");
            } catch (AssertionError e){
            }
            checkHeldLocks(userTxn, 1, 0);
            long afterDemotion = mb.getCacheMemoryUsage();
            assertEquals(afterLock, afterDemotion);

            /* Make it a write lock, then demote. */
            lockGrant = userTxn.lock
                (ln.getNodeId(), LockType.WRITE, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.PROMOTION, lockGrant);
            long afterWriteLock = mb.getCacheMemoryUsage();
            assertTrue(afterWriteLock > afterLock);
            assertTrue(afterLock > beforeLock);

            checkHeldLocks(userTxn, 0, 1);
            userTxn.demoteLock(ln.getNodeId());
            checkHeldLocks(userTxn, 1, 0);

            /* Shouldn't release at operation end. */
            userTxn.operationEnd();
            checkHeldLocks(userTxn, 1, 0);

            userTxn.releaseLock(ln.getNodeId());
            checkHeldLocks(userTxn, 0, 0);
            userTxn.commit(Txn.TXN_SYNC);
            afterRelease = mb.getCacheMemoryUsage();
            assertTrue(afterLock > beforeLock);
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    private void checkHeldLocks(Locker txn,
				int numReadLocks,
				int numWriteLocks)
        throws DatabaseException {

        LockStats stat = txn.collectStats(new LockStats());
        assertEquals(numReadLocks, stat.getNReadLocks());
        assertEquals(numWriteLocks, stat.getNWriteLocks());
    }

    /**
     * Test transaction commit, from the locking point of view.
     */
    public void testCommit()
        throws Throwable {

        try {
            LN ln1 = new LN(new byte[0]);
            LN ln2 = new LN(new byte[0]);
                                          
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            Txn userTxn = new Txn(envImpl, new TransactionConfig());

            /* Get read lock 1. */
            LockGrantType lockGrant = userTxn.lock
                (ln1.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 1, 0);

            /* Get read lock 2. */
            lockGrant = userTxn.lock
                (ln2.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 2, 0);

            /* Upgrade read lock 2 to a write. */
            lockGrant = userTxn.lock
                (ln2.getNodeId(), LockType.WRITE, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.PROMOTION, lockGrant);
            checkHeldLocks(userTxn, 1, 1);

            /* Read lock 1 again, shouldn't increase count. */
            lockGrant = userTxn.lock
                (ln1.getNodeId(), LockType.READ, false,
                 DbInternal.dbGetDatabaseImpl(db)).
		getLockGrant();
            assertEquals(LockGrantType.EXISTING, lockGrant);
            checkHeldLocks(userTxn, 1, 1);

            /* Shouldn't release at operation end. */
            long commitLsn = userTxn.commit(Txn.TXN_SYNC);
            checkHeldLocks(userTxn, 0, 0);

            TxnCommit commitRecord =
                (TxnCommit) envImpl.getLogManager().get(commitLsn);

            assertEquals(userTxn.getId(), commitRecord.getId());
            assertEquals(userTxn.getLastLsn(), commitRecord.getLastLsn());
        } catch (Throwable t) {
            /* Print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Make sure an abort never tries to split the tree.
     */
    public void testAbortNoSplit() 
        throws Throwable {

        try {
            Transaction txn = env.beginTransaction(null, null);

            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            dataDbt.setData(new byte[1]);
        
            /* Insert enough data so that the tree is ripe for a split. */
            int numForSplit = 25;
            for (int i = 0; i < numForSplit; i++) {
                keyDbt.setData(TestUtils.getTestArray(i));
                db.put(txn, keyDbt, dataDbt);
            }

            /* Check that we're ready for a split. */
            DatabaseImpl database = DbInternal.dbGetDatabaseImpl(db);
            CheckReadyToSplit splitChecker = new CheckReadyToSplit(database);
            database.getTree().withRootLatchedShared(splitChecker);
            assertTrue(splitChecker.getReadyToSplit());

            /* 
             * Make another txn that will get a read lock on the map
             * LSN. Then abort the first txn. It shouldn't try to do a
             * split, if it does, we'll run into the
             * no-latches-while-locking check.
             */
            Transaction txnSpoiler = env.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            Database dbSpoiler = env.openDatabase(txnSpoiler, "foo", dbConfig);

            txn.abort();

            /*
             * The database should be empty
             */
            Cursor cursor = dbSpoiler.openCursor(txnSpoiler, null);
            
            assertTrue(cursor.getFirst(keyDbt, dataDbt, LockMode.DEFAULT) != 
                       OperationStatus.SUCCESS);
            cursor.close();
            txnSpoiler.abort();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        } 
    }

    public void testTransactionName() 
        throws Throwable {

        try {
            Transaction txn = env.beginTransaction(null, null);
	    txn.setName("blort");
	    assertEquals("blort", txn.getName());
            txn.abort();

            /* 
             * [#14349] Make sure the txn is printable after closing. We
             * once had a NullPointerException. 
             */
            String s = txn.toString(); 
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        } 
    }

    /**
     * Test all combinations of sync, nosync, and writeNoSync for txn
     * commits.
     */

    /* SyncCombo expresses all the combinations of txn sync properties. */
    private static class SyncCombo {
        private boolean envNoSync;
        private boolean envWriteNoSync;
        private boolean txnNoSync;
        private boolean txnWriteNoSync;
        private boolean txnSync;
        boolean expectSync;
        boolean expectWrite;

        SyncCombo(int envWriteNoSync,
                  int envNoSync,
                  int txnSync,
                  int txnWriteNoSync,
                  int txnNoSync,
                  boolean expectSync,
                  boolean expectWrite) {
            this.envNoSync = (envNoSync == 0) ? false : true;
            this.envWriteNoSync = (envWriteNoSync == 0) ? false : true;
            this.txnNoSync = (txnNoSync == 0) ? false : true;
            this.txnWriteNoSync = (txnWriteNoSync == 0) ? false : true;
            this.txnSync = (txnSync == 0) ? false : true;
            this.expectSync = expectSync;
            this.expectWrite = expectWrite;
        }

        TransactionConfig getTxnConfig() {
            TransactionConfig txnConfig = new TransactionConfig();
            txnConfig.setSync(txnSync);
            txnConfig.setWriteNoSync(txnWriteNoSync);
            txnConfig.setNoSync(txnNoSync);
            return txnConfig;
        }

        void setEnvironmentMutableConfig(Environment env)
            throws DatabaseException {
            EnvironmentMutableConfig config = env.getMutableConfig();
            config.setTxnNoSync(envNoSync);
            config.setTxnWriteNoSync(envWriteNoSync);
            env.setMutableConfig(config);
        }
    }

    public void testSyncCombo() 
        throws Throwable {

        RandomAccessFile logFile =
            new RandomAccessFile(new File(envHome, "00000000.jdb"), "r");
        try {
            SyncCombo [] testCombinations = {
            /*            Env    Env    Txn    Txn    Txn    Expect Expect
             *            WrNoSy NoSy   Sync  WrNoSy  NoSyc  Sync   Write */
            new SyncCombo(  0,     0,     0,     0,     0,    true,  true),
            new SyncCombo(  0,     0,     0,     0,     1,   false, false),
            new SyncCombo(  0,     0,     0,     1,     0,   false,  true),
            new SyncCombo(  0,     0,     0,     1,     1,   false,  true),
            new SyncCombo(  0,     0,     1,     0,     0,    true,  true),
            new SyncCombo(  0,     0,     1,     0,     1,    true,  true),
            new SyncCombo(  0,     0,     1,     1,     0,    true,  true),
            new SyncCombo(  0,     0,     1,     1,     1,    true,  true),
            new SyncCombo(  0,     1,     0,     0,     0,   false, false),
            new SyncCombo(  0,     1,     0,     0,     1,   false, false),
            new SyncCombo(  0,     1,     0,     1,     0,   false,  true),
            new SyncCombo(  0,     1,     0,     1,     1,   false,  true),
            new SyncCombo(  0,     1,     1,     0,     0,    true,  true),
            new SyncCombo(  0,     1,     1,     0,     1,    true,  true),
            new SyncCombo(  0,     1,     1,     1,     0,    true,  true),
            new SyncCombo(  0,     1,     1,     1,     1,    true,  true),
            new SyncCombo(  1,     0,     0,     0,     0,   false,  true),
            new SyncCombo(  1,     0,     0,     0,     1,   false, false),
            new SyncCombo(  1,     0,     0,     1,     0,   false,  true),
            new SyncCombo(  1,     0,     0,     1,     1,   false,  true),
            new SyncCombo(  1,     0,     1,     0,     0,    true,  true),
            new SyncCombo(  1,     0,     1,     0,     1,    true,  true),
            new SyncCombo(  1,     0,     1,     1,     0,    true,  true),
            new SyncCombo(  1,     0,     1,     1,     1,    true,  true),
            new SyncCombo(  1,     1,     0,     0,     0,   false,  true),
            new SyncCombo(  1,     1,     0,     0,     1,   false, false),
            new SyncCombo(  1,     1,     0,     1,     0,   false,  true),
            new SyncCombo(  1,     1,     0,     1,     1,   false,  true),
            new SyncCombo(  1,     1,     1,     0,     0,    true,  true),
            new SyncCombo(  1,     1,     1,     0,     1,    true,  true),
            new SyncCombo(  1,     1,     1,     1,     0,    true,  true),
            new SyncCombo(  1,     1,     1,     1,     1,    true,  true)};

            /* envNoSync=false with default env config */
            assertTrue(!env.getMutableConfig().getTxnNoSync());

            /* envWriteNoSync=false with default env config */
            assertTrue(!env.getMutableConfig().getTxnWriteNoSync());

            /* 
             * For each combination of settings, call commit and
             * check that we have the expected sync and log
             * write. Make sure that commitSync(), commitNoSync always
             * override all preferences.
             */
            for (int i = 0; i < testCombinations.length; i++) {
                SyncCombo combo = testCombinations[i];
                TransactionConfig txnConfig = combo.getTxnConfig();
                combo.setEnvironmentMutableConfig(env);
                syncExplicit(logFile, txnConfig,
                             combo.expectSync, combo.expectWrite);
            }

            SyncCombo [] autoCommitCombinations = {
            /*            Env    Env    Txn    Txn    Txn    Expect Expect
             *            WrNoSy NoSy   Sync  WrNoSy  NoSyc  Sync   Write */
            new SyncCombo(  0,     0,     0,     0,     0,    true,  true),
            new SyncCombo(  0,     1,     0,     0,     0,   false, false),
            new SyncCombo(  1,     0,     0,     0,     0,   false,  true),
            new SyncCombo(  1,     1,     0,     0,     0,   false,  true)};

            for (int i = 0; i < autoCommitCombinations.length; i++) {
                SyncCombo combo = autoCommitCombinations[i];
                combo.setEnvironmentMutableConfig(env);
                syncAutoCommit(logFile, combo.expectSync, combo.expectWrite);
            }
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        } finally {
            logFile.close();
        }
    }

    /**
     * Does an explicit commit and returns whether an fsync occured.
     */
    private void syncExplicit(RandomAccessFile lastLogFile,
                              TransactionConfig config,
                              boolean expectSync,
                              boolean expectWrite)
        throws DatabaseException, IOException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);

        long beforeSyncs = getNSyncs();
        Transaction txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        long beforeLength = lastLogFile.length();
        txn.commit();
        long afterSyncs = getNSyncs();
        long afterLength = lastLogFile.length();
        boolean syncOccurred = afterSyncs > beforeSyncs;
        boolean writeOccurred = afterLength > beforeLength;
        assertEquals(expectSync, syncOccurred);
        assertEquals(expectWrite, writeOccurred);

        /* 
         * Make sure explicit sync/noSync/writeNoSync always works.
         */

        /* Expect a sync and write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs > beforeSyncs);
        assert(afterLength > beforeLength);

        /* Expect neither a sync nor write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitNoSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs == beforeSyncs);
        assert(afterLength == beforeLength);

        /* Expect no sync but do expect a write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitWriteNoSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs == beforeSyncs);
        assert(afterLength > beforeLength);
    }

    /**
     * Does an auto-commit and returns whether an fsync occured.
     */
    private void syncAutoCommit(RandomAccessFile lastLogFile,
                                boolean expectSync,
                                boolean expectWrite)
        throws DatabaseException, IOException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);
        long beforeSyncs = getNSyncs();
        long beforeLength = lastLogFile.length();
        db.put(null, key, data);
        long afterLength = lastLogFile.length();
        long afterSyncs = getNSyncs();
        boolean syncOccurred = afterSyncs > beforeSyncs;
        assertEquals(expectSync, syncOccurred);
        assertEquals(expectWrite, (afterLength > beforeLength));
    }

    /**
     * Returns number of fsyncs statistic.
     */
    private long getNSyncs() {
        return DbInternal.envGetEnvironmentImpl(env)
                         .getFileManager()
                         .getNFSyncs();
    }

    public void testNoWaitConfig() 
        throws Throwable {

        try {
            TransactionConfig defaultConfig = new TransactionConfig();
            TransactionConfig noWaitConfig = new TransactionConfig();
            noWaitConfig.setNoWait(true);
            Transaction txn;

            /* noWait=false */

            assertTrue(!isNoWaitTxn(null));

            txn = env.beginTransaction(null, null);
            assertTrue(!isNoWaitTxn(txn));
            txn.abort();

            txn = env.beginTransaction(null, defaultConfig);
            assertTrue(!isNoWaitTxn(txn));
            txn.abort();

            /* noWait=true */

            txn = env.beginTransaction(null, noWaitConfig);
            assertTrue(isNoWaitTxn(txn));
            txn.abort();

        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        } 
    }

    /**
     * Returns whether the given txn is a no-wait txn, or if the txn parameter
     * is null returns whether an auto-commit txn is a no-wait txn.
     */
    private boolean isNoWaitTxn(Transaction txn) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);

        /* Use a wait txn to get a write lock. */
        Transaction txn2 = env.beginTransaction(null, null);
        db.put(txn2, key, data);

        try {
            db.put(txn, key, data);
            throw new IllegalStateException
                ("Lock should not have been granted");
        } catch (LockNotGrantedException e) {
            return true;
        } catch (DeadlockException e) {
            return false;
        } finally {
            txn2.abort();
        }
    }

    /* 
     * Assert that cache utilization is correctly incremented by locks and
     * txns, and decremented after release.
     */
    private void checkCacheUsage(long beforeLock,
                                 long afterLock,
                                 long afterRelease,
                                 long expectedSize) {
        assertEquals(beforeLock, afterRelease);
        assertEquals(afterLock, (beforeLock + expectedSize));
    }

    class CheckReadyToSplit implements WithRootLatched {
        private boolean readyToSplit;
        private DatabaseImpl database;

        CheckReadyToSplit(DatabaseImpl database) {
            readyToSplit = false;
            this.database = database;
        }
        
        public boolean getReadyToSplit() {
            return readyToSplit;
        }

        public IN doWork(ChildReference root) 
            throws DatabaseException {

            IN rootIN = (IN) root.fetchTarget(database, null);
            readyToSplit = rootIN.needsSplitting();
            return null;
        }
    }
}
