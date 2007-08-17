/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LockManagerTest.java,v 1.45.2.2 2007/07/13 02:32:06 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitThread;
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
        envConfig.setAllowCreate(true);
	envConfig.setTransactional(true);
        env = new EnvironmentImpl(envHome, envConfig);

        TxnManager txnManager = env.getTxnManager();
	lockManager = txnManager.getLockManager();
	txn1 = new BasicLocker(env);
	txn2 = new BasicLocker(env);
	txn3 = new BasicLocker(env);
	txn4 = new BasicLocker(env);
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

    public void xtestDeadlock()
	throws Throwable {

	JUnitThread tester1 =
	    new JUnitThread("testDeadlock1") {
		public void testBody() {
		    try {
			lockManager.lock(1, txn1, LockType.WRITE, 0,
					 false, null);
			System.out.println("t1 has locked 1");
			assertTrue
			    (lockManager.isOwner(nid, txn1, LockType.WRITE));
			sequence++;     // bump to 1

			/* wait for tester2 */
			while (sequence < 2) {
			    Thread.yield();
			}

			lockManager.lock(2, txn1, LockType.READ, 1000,
					 false, null);
			System.out.println("t1 about to sleep");
			Thread.sleep(5000);

			lockManager.release(1, txn1);
			txn1.removeLock(1);
			lockManager.release(2, txn1);
			txn1.removeLock(2);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("tester1 caught DatabaseException " + DBE);
		    } catch (InterruptedException IE) {
			fail("tester1 caught InterruptedException " + IE);
		    }
		}
	    };

	JUnitThread tester2 =
	    new JUnitThread("testDeadlock2") {
		public void testBody() {
		    try {
			/* wait for tester1 */
			while (sequence < 1) {
			    Thread.yield();
			}

			lockManager.lock(2, txn2, LockType.WRITE, 0,
					 false, null);
			System.out.println("t2 has locked 2");

			sequence++;   // bump to 2

			System.out.println("t2 about to lock 1");
			lockManager.lock(1, txn2, LockType.READ, 1000,
					 false, null);
			System.out.println("t2 about to sleep");
			Thread.sleep(5000);

			lockManager.release(1, txn2);
			txn2.removeLock(1);
			lockManager.release(2, txn1);
			txn1.removeLock(2);
		    } catch (DatabaseException DBE) {
                        DBE.printStackTrace();
			fail("tester2 caught DatabaseException " + DBE);
		    } catch (InterruptedException IE) {
			fail("tester2 caught InterruptedException " + IE);
		    }
		}
	    };

	tester1.start();
	tester2.start();
	//tester3.start();
	tester1.finishTest();
	tester2.finishTest();
	//tester3.finishTest();
    }
}
