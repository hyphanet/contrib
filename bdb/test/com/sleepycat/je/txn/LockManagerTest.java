/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LockManagerTest.java,v 1.55 2008/01/17 17:22:30 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.util.TestUtils;

public class LockManagerTest extends TestCase {

    private LockManager lockManager = null;
    private Locker txn1;
    private Locker txn2;
    private Locker txn3;
    private Locker txn4;
    private Long nid;
    private volatile int sequence;

    private EnvironmentImpl env;
    private File envHome;

    public LockManagerTest() {
        envHome =  new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam(EnvironmentParams.N_LOCK_TABLES.getName(),
                                 "11");
        envConfig.setAllowCreate(true);
	envConfig.setTransactional(true);
        env = new EnvironmentImpl(envHome,
                                  envConfig,
                                  null /*sharedCacheEnv*/,
                                  false /*replicationIntended*/);

        TxnManager txnManager = env.getTxnManager();
	lockManager = txnManager.getLockManager();
	txn1 = BasicLocker.createBasicLocker(env);
	txn2 = BasicLocker.createBasicLocker(env);
	txn3 = BasicLocker.createBasicLocker(env);
	txn4 = BasicLocker.createBasicLocker(env);
	nid = new Long(1);
	sequence = 0;
    }

    public void tearDown()
	throws DatabaseException {

        txn1.operationEnd();
        txn2.operationEnd();
        txn3.operationEnd();
        txn4.operationEnd();
        env.close();
    }

    /*
     * SR15926 showed a bug where nodeIds that are > 0x80000000 produce
     * negative lock table indexes becuase of the modulo arithmetic in
     * LockManager.getLockTableIndex().
     */
    public void testSR15926LargeNodeIds()
        throws Exception {

        try {
            lockManager.lock(0x80000000L, txn1, LockType.WRITE,
                             0, false, null);
        } catch (Exception e) {
            fail("shouldn't get exception " + e);
        }
    }

    public void testNegatives()
        throws Exception {

	try {
	    assertFalse(lockManager.isOwner(nid, txn1, LockType.READ));
	    assertFalse(lockManager.isOwner(nid, txn1, LockType.WRITE));
	    assertFalse(lockManager.isLocked(nid));
	    assertFalse(lockManager.isWaiter(nid, txn1));
	    lockManager.lock(1, txn1, LockType.READ, 0, false, null);

	    /* already holds this lock */
	    assertEquals(LockGrantType.EXISTING,
                         lockManager.lock(1, txn1, LockType.READ, 0,
					  false, null));
	    assertFalse(lockManager.isOwner(nid, txn2, LockType.READ));
	    assertFalse(lockManager.isOwner(nid, txn2, LockType.WRITE));
	    assertTrue(lockManager.isLocked(nid));
	    assertTrue(lockManager.nOwners(new Long(2)) == -1);
	    assertTrue(lockManager.nWaiters(new Long(2)) == -1);

            /* lock 2 doesn't exist, shouldn't affect any the existing lock */
	    lockManager.release(2L, txn1);
	    txn1.removeLock(2L);
	    assertTrue(lockManager.isLocked(nid));

            /* txn2 is not the owner, shouldn't release lock 1. */
	    lockManager.release(1L, txn2);
	    txn2.removeLock(1L);
	    assertTrue(lockManager.isLocked(nid));
            assertTrue(lockManager.isOwner(nid, txn1, LockType.READ));
	    assertTrue(lockManager.nOwners(nid) == 1);

            /* Now really release. */
	    lockManager.release(1L, txn1);
	    txn1.removeLock(1L);
	    assertFalse(lockManager.isLocked(nid));
            assertFalse(lockManager.isOwner(nid, txn1, LockType.READ));
	    assertFalse(lockManager.nOwners(nid) == 1);

	    lockManager.lock(1, txn1, LockType.WRITE, 0, false, null);
	    /* holds write and subsequent request for READ is ok */
	    lockManager.lock(1, txn1, LockType.READ, 0, false, null);
	    /* already holds this lock */
	    assertTrue(lockManager.lock(1, txn1, LockType.WRITE,
					0, false, null) ==
		       LockGrantType.EXISTING);
	    assertFalse(lockManager.isWaiter(nid, txn1));
	} catch (Exception e) {
            e.printStackTrace();
            throw e;
	}
    }

    /**
     * Acquire three read locks and make sure that they share nicely.
     */
    public void testMultipleReaders()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testMultipleReaders1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			sequence++;
			while (sequence < 3) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testMultipleReaders2") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn2, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			sequence++;
			while (sequence < 3) {
			    Thread.yield();
			}
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester3 =
	    new JUnitThread("testMultipleReaders3") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn3, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn3, LockType.READ));
			sequence++;
			while (sequence < 3) {
			    Thread.yield();
			}
			lockManager.release(1L, txn3);
			txn3.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester3.start();
	tester1.finishTest();
	tester2.finishTest();
	tester3.finishTest();
    }

    /**
     * Grab two read locks, hold them, and make sure that a write lock
     * waits for them to be released.
     */
    public void testMultipleReadersSingleWrite1()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testMultipleReaders1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			assertTrue(lockManager.isWaiter(nid, txn3));
			assertFalse(lockManager.isWaiter(nid, txn1));
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
			assertFalse
			    (lockManager.isOwner(nid, txn1, LockType.READ));
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testMultipleReaders2") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn2, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			assertTrue(lockManager.isWaiter(nid, txn3));
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.READ));
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester3 =
	    new JUnitThread("testMultipleReaders3") {
		public void testBody() {
		    try {
			while (lockManager.nOwners(nid) < 2) {
			    Thread.yield();
			}
			lockManager.lock(1, txn3, LockType.WRITE, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn3, LockType.WRITE));
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester3.start();
	tester1.finishTest();
	tester2.finishTest();
	tester3.finishTest();
    }

    /**
     * Acquire two read locks, put a write locker behind the two
     * read lockers, and then queue a read locker behind the writer.
     * Ensure that the third reader is not granted until the writer
     * releases the lock.
     */
    public void testMultipleReadersSingleWrite2()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testMultipleReaders1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			while (lockManager.nWaiters(nid) < 2) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testMultipleReaders2") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn2, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			while (lockManager.nWaiters(nid) < 2) {
			    Thread.yield();
			}
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester3 =
	    new JUnitThread("testMultipleReaders3") {
		public void testBody() {
		    try {
			while (lockManager.nOwners(nid) < 2) {
			    Thread.yield();
			}
			lockManager.lock(1, txn3, LockType.WRITE, 0,
					 false, null);
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.isOwner(nid, txn3, LockType.WRITE));
			lockManager.release(1L, txn3);
			txn3.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester4 =
	    new JUnitThread("testMultipleReaders4") {
		public void testBody() {
		    try {
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			lockManager.lock(1, txn4, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn4, LockType.READ));
			lockManager.release(1L, txn4);
			txn4.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester3.start();
	tester4.start();
	tester1.finishTest();
	tester2.finishTest();
	tester3.finishTest();
	tester4.finishTest();
    }

    /**
     * Acquire two read locks for two transactions, then request a write
     * lock for a third transaction.  Then request a write lock for one
     * of the first transactions that already has a read lock (i.e.
     * request an upgrade lock).  Make sure it butts in front of the
     * existing wait lock.
     */
    public void testUpgradeLock()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testUpgradeLock1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			while (lockManager.nWaiters(nid) < 2) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testUpgradeLock2") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn2, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			lockManager.lock(1, txn2, LockType.WRITE, 0,
					 false, null);
			assertTrue(lockManager.nWaiters(nid) == 1);
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester3 =
	    new JUnitThread("testUpgradeLock3") {
		public void testBody() {
		    try {
			while (lockManager.nOwners(nid) < 2) {
			    Thread.yield();
			}
			lockManager.lock(1, txn3, LockType.WRITE, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn3, LockType.WRITE));
			lockManager.release(1L, txn3);
			txn3.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester3.start();
	tester1.finishTest();
	tester2.finishTest();
	tester3.finishTest();
    }

    /**
     * Acquire a read lock, then request a write lock for a second
     * transaction in non-blocking mode.  Make sure it fails.
     */
    public void testNonBlockingLock1()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testNonBlocking1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			while (sequence < 1) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testNonBlocking2") {
		public void testBody() {
		    try {
			/* wait for tester1 */
			while (lockManager.nOwners(nid) < 1) {
			    Thread.yield();
			}
                        LockGrantType grant = lockManager.lock
                            (1, txn2, LockType.WRITE, 0, true, null);
                        assertSame(LockGrantType.DENIED, grant);
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			sequence++;
			/* wait for tester1 to release the lock */
			while (lockManager.nOwners(nid) > 0) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.lock(1, txn2, LockType.WRITE, 0,
                                              false, null) ==
			     LockGrantType.NEW);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }

    /**
     * Acquire a write lock, then request a read lock for a second
     * transaction in non-blocking mode.  Make sure it fails.
     */
    public void testNonBlockingLock2()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testNonBlocking1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.WRITE, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.WRITE));
			sequence++;
			while (sequence < 2) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testNonBlocking2") {
		public void testBody() {
		    try {
			/* wait for tester1 */
			while (sequence < 1) {
			    Thread.yield();
			}
                        LockGrantType grant = lockManager.lock
                            (1, txn2, LockType.READ, 0, true, null);
                        assertSame(LockGrantType.DENIED, grant);
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			sequence++;
			/* wait for tester1 to release the lock */
			while (lockManager.nOwners(nid) > 0) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.lock(1, txn2, LockType.READ, 0,
                                              false, null) ==
			     LockGrantType.NEW);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }

    /**
     * Acquire a write lock, then request a read lock for a second
     * transaction in blocking mode.  Make sure it waits.
     */
    public void testWaitingLock()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testBlocking1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.WRITE, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.WRITE));
			sequence++;
			while (sequence < 2) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testBlocking2") {
		public void testBody() {
		    try {
			/* wait for tester1 */
			while (sequence < 1) {
			    Thread.yield();
			}
			try {
			    lockManager.lock(1, txn2, LockType.READ, 500,
                                             false, null);
			    fail("didn't time out");
			} catch (DeadlockException e) {
                            assertTrue(TestUtils.skipVersion(e).startsWith("Lock "));
			}
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			sequence++;
			/* wait for tester1 to release the lock */
			while (lockManager.nOwners(nid) > 0) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.lock(1, txn2, LockType.READ, 0,
                                              false, null) ==
			     LockGrantType.NEW);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			assertFalse
			    (lockManager.isOwner(nid, txn2, LockType.WRITE));
			assertTrue(lockManager.nWaiters(nid) == 0);
			assertTrue(lockManager.nOwners(nid) == 1);
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }

    /**
     * Test that DeadlockException has the correct owners and waiters when
     * it is thrown due to a timeout.
     *
     * Create five threads, the first two of which take a readlock and the
     * second two of which try for a write lock backed up behind the two
     * read locks.  Then have a fifth thread try for a read lock which backs
     * up behind all of them.  The first two threads (read lockers) are owners
     * and the second two threads are waiters.  When the fifth thread catches
     * the DeadlockException make sure that it contains the txn ids for the
     * two readers in the owners array and the txn ids for the two writers
     * in the waiters array.
     */
    public void testDeadlock()
	throws Throwable {

	/* Get rid of these inferior BasicLockers -- we want real Txns. */
        txn1.operationEnd();
        txn2.operationEnd();
        txn3.operationEnd();
        txn4.operationEnd();

	TransactionConfig config = new TransactionConfig();
	txn1 = Txn.createTxn(env, config, ReplicationContext.NO_REPLICATE);
	txn2 = Txn.createTxn(env, config, ReplicationContext.NO_REPLICATE);
	txn3 = Txn.createTxn(env, config, ReplicationContext.NO_REPLICATE);
	txn4 = Txn.createTxn(env, config, ReplicationContext.NO_REPLICATE);
	final Txn txn5 =
	    Txn.createTxn(env, config, ReplicationContext.NO_REPLICATE);

	sequence = 0;
	JUnitThread tester1 =
	    new JUnitThread("testMultipleReaders1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.READ));
			while (sequence < 1) {
			    Thread.yield();
			}
			lockManager.release(1L, txn1);
			txn1.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testMultipleReaders2") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn2, LockType.READ, 0,
					 false, null);
			assertTrue
			    (lockManager.isOwner(nid, txn2, LockType.READ));
			while (sequence < 1) {
			    Thread.yield();
			}
			lockManager.release(1L, txn2);
			txn2.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester3 =
	    new JUnitThread("testMultipleReaders3") {
		public void testBody() {
		    try {
			while (lockManager.nOwners(nid) < 2) {
			    Thread.yield();
			}
			lockManager.lock(1, txn3, LockType.WRITE, 0,
					 false, null);
			while (sequence < 1) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.isOwner(nid, txn3, LockType.WRITE));
			lockManager.release(1L, txn3);
			txn3.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester4 =
	    new JUnitThread("testMultipleReaders4") {
		public void testBody() {
		    try {
			while (lockManager.nOwners(nid) < 2) {
			    Thread.yield();
			}
			lockManager.lock(1, txn4, LockType.WRITE, 0,
					 false, null);
			while (sequence < 1) {
			    Thread.yield();
			}
			assertTrue
			    (lockManager.isOwner(nid, txn4, LockType.WRITE));
			lockManager.release(1L, txn4);
			txn4.removeLock(1L);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("caught DatabaseException " + DBE);
		    }
		}
	    };

	JUnitThread tester5 =
	    new JUnitThread("testMultipleReaders5") {
		public void testBody() {
		    try {
			while (lockManager.nWaiters(nid) < 1) {
			    Thread.yield();
			}
			lockManager.lock(1, txn5, LockType.READ, 900,
					 false, null);
			fail("expected DeadlockException");
		    } catch (DeadlockException DLE) {

			long[] owners = DLE.getOwnerTxnIds();
			long[] waiters = DLE.getWaiterTxnIds();

			assertTrue((owners[0] == txn1.getId() &&
				    owners[1] == txn2.getId()) ||
				   (owners[1] == txn1.getId() &&
				    owners[0] == txn2.getId()));

			assertTrue((waiters[0] == txn3.getId() &&
				    waiters[1] == txn4.getId()) ||
				   (waiters[1] == txn3.getId() &&
				    waiters[0] == txn4.getId()));

		    } catch (DatabaseException DBE) {
			fail("expected DeadlockException");
			DBE.printStackTrace(System.out);
		    }
		    sequence = 1;
		}
	    };

	tester1.start();
	tester2.start();
	tester3.start();
	tester4.start();
	tester5.start();
	tester1.finishTest();
	tester2.finishTest();
	tester3.finishTest();
	tester4.finishTest();
	tester5.finishTest();
	((Txn) txn1).abort(false);
	((Txn) txn2).abort(false);
	((Txn) txn3).abort(false);
	((Txn) txn4).abort(false);
	txn5.abort(false);
    }
}
