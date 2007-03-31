/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LatchTest.java,v 1.33.2.1 2007/02/01 14:50:13 cwl Exp $
 */

package com.sleepycat.je.latch;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.junit.JUnitThread;

public class LatchTest extends TestCase {
    private Latch latch1 = null;
    private Latch latch2 = null;
    private JUnitThread tester1 = null;
    private JUnitThread tester2 = null;
    
    static private final boolean DEBUG = false;

    private void debugMsg(String message) {
	if (DEBUG) {
	    System.out.println(Thread.currentThread().toString()
			       + " " +  message);
	}
    }

    public void setUp() {
    }

    private void initExclusiveLatches() {
	latch1 = LatchSupport.makeLatch("LatchTest-latch1", null);
	latch2 = LatchSupport.makeLatch("LatchTest-latch2", null);
    }

    public void tearDown() {
	latch1 = null;
	latch2 = null;
    }

    public void testAcquireAndReacquire()
	throws Throwable {

	initExclusiveLatches();
	JUnitThread tester =
	    new JUnitThread("testAcquireAndReacquire") {
		public void testBody() {
		    /* Acquire a latch. */
		    try {
			latch1.acquire();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    /* Try to acquire it again -- should fail. */
		    try {
			latch1.acquire();
			fail("didn't catch LatchException");
		    } catch (LatchException LE) {
			assertTrue
			    (latch1.getLatchStats().nAcquiresSelfOwned == 1);
		    } catch (DatabaseException DE) {
			fail("didn't catch LatchException-caught DE instead");
		    }

		    /* Release it. */
		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }

		    /* Release it again -- should fail. */
		    try {
			latch1.release();
			fail("didn't catch LatchNotHeldException");
		    } catch (LatchNotHeldException LNHE) {
		    }
		}
	    };

	tester.doTest();
    }

    public void testAcquireAndReacquireShared()
	throws Throwable {

	final SharedLatch latch =
	    LatchSupport.makeSharedLatch("LatchTest-latch2", null);

	JUnitThread tester =
	    new JUnitThread("testAcquireAndReacquireShared") {
		public void testBody() {
		    /* Acquire a shared latch. */
		    try {
			latch.acquireShared();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    assert latch.isOwner();

		    /* Try to acquire it again -- should succeed. */
		    try {
			latch.acquireShared();
		    } catch (LatchException LE) {
			fail("didn't catch LatchException");
		    } catch (DatabaseException DE) {
			fail("didn't catch LatchException-caught DE instead");
		    }

		    assert latch.isOwner();

		    /* Release it. */
		    try {
			latch.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }

		    /* Release it again -- should succeed. */
		    try {
			latch.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("didn't catch LatchNotHeldException");
		    }

		    /* Release it again -- should fail. */
		    try {
			latch.release();
			fail("didn't catch LatchNotHeldException");
		    } catch (LatchNotHeldException LNHE) {
		    }
		}
	    };

	tester.doTest();
    }

    /* 
     * Do a million acquire/release pairs.  The junit output will tell us how
     * long it took.
     */
    public void testAcquireReleasePerformance()
	throws Throwable {

	initExclusiveLatches();
	JUnitThread tester =
	    new JUnitThread("testAcquireReleasePerformance") {
		public void testBody() {
		    final int N_PERF_TESTS = 1000000;
		    for (int i = 0; i < N_PERF_TESTS; i++) {
			/* Acquire a latch */
			try {
			    latch1.acquire();
			} catch (DatabaseException LE) {
			    fail("caught DatabaseException");
			}

			/* Release it. */
			try {
			    latch1.release();
			} catch (LatchNotHeldException LNHE) {
			    fail("unexpected LatchNotHeldException");
			}
		    }
		    LatchStats stats = latch1.getLatchStats();
		    assertTrue(stats.nAcquiresNoWaiters == N_PERF_TESTS);
		    assertTrue(stats.nReleases == N_PERF_TESTS);
		}
	    };

	tester.doTest();
    }

    /* Test latch waiting. */

    public void testWait()
	throws Throwable {

	initExclusiveLatches();
	for (int i = 0; i < 10; i++) {
	    doTestWait();
	}
    }

    private int nAcquiresWithContention = 0;

    public void doTestWait()
	throws Throwable {

	tester1 =
	    new JUnitThread("testWait-Thread1") {
		public void testBody() {
		    /* Acquire a latch. */
		    try {
			latch1.acquire();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    /* Wait for tester2 to try to acquire the latch. */
		    while (latch1.nWaiters() == 0) {
			Thread.yield();
		    }

		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }
		}
	    };

	tester2 =
	    new JUnitThread("testWait-Thread2") {
		public void testBody() {
		    /* Wait for tester1 to start. */

		    while (latch1.owner() != tester1) {
			Thread.yield();
		    }

		    /* Acquire a latch. */
		    try {
			latch1.acquire();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    assertTrue(latch1.getLatchStats().nAcquiresWithContention
			       == ++nAcquiresWithContention);

		    /* Release it. */
		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }

    /* Test acquireNoWait(). */

    private volatile boolean attemptedAcquireNoWait;

    public void testAcquireNoWait()
	throws Throwable {

	initExclusiveLatches();
	tester1 =
	    new JUnitThread("testWait-Thread1") {
		public void testBody() {
		    debugMsg("Acquiring Latch");
		    /* Acquire a latch. */
		    try {
			latch1.acquire();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    /* Wait for tester2 to try to acquire the latch. */

		    debugMsg("Waiting for other thread");
		    while (!attemptedAcquireNoWait) {
			Thread.yield();
		    }

		    debugMsg("Releasing the latch");
		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }
		}
	    };

	tester2 =
	    new JUnitThread("testWait-Thread2") {
		public void testBody() {
		    /* Wait for tester1 to start. */

		    debugMsg("Waiting for T1 to acquire latch");
		    while (latch1.owner() != tester1) {
			Thread.yield();
		    }

		    /*
		     * Attempt Acquire with no wait -- should fail since
		     * tester1 has it.
		     */
		    debugMsg("Acquiring no wait");
		    try {
			assertFalse(latch1.acquireNoWait());
			assertTrue(latch1.getLatchStats().
				   nAcquireNoWaitUnsuccessful == 1);
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    attemptedAcquireNoWait = true;

		    debugMsg("Waiting for T1 to release latch");
		    while (latch1.owner() != null) {
			Thread.yield();
		    }

		    /* 
		     * Attempt Acquire with no wait -- should succeed now that
		     * tester1 is done.
		     */
		    debugMsg("Acquiring no wait - 2");
		    try {
			assertTrue(latch1.acquireNoWait());
			assertTrue(latch1.getLatchStats().
				   nAcquireNoWaitSuccessful == 1);
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    /* 
		     * Attempt Acquire with no wait again -- should throw
		     * exception since we already have it.
		     */
		    debugMsg("Acquiring no wait - 3");
		    try {
			latch1.acquireNoWait();
			fail("didn't throw LatchException");
		    } catch (LatchException LE) {
		    	// expected
		    } catch (Exception e) {
			fail("caught Exception");
		    }

		    /* Release it. */
		    debugMsg("releasing the latch");
		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }
		}
	    };

	tester1.start();
	tester2.start();
	tester1.finishTest();
	tester2.finishTest();
    }

    /* State for testMultipleWaiters. */
    private final int N_WAITERS = 5;

    /* A JUnitThread that holds the waiter number. */
    private class MultiWaiterTestThread extends JUnitThread {
	private int waiterNumber;
	public MultiWaiterTestThread(String name, int waiterNumber) {
	    super(name);
	    this.waiterNumber = waiterNumber;
	}
    }

    public void testMultipleWaiters()
	throws Throwable {

	initExclusiveLatches();
	JUnitThread[] waiterThreads =
	    new JUnitThread[N_WAITERS];

	tester1 =
	    new JUnitThread("testWait-Thread1") {
		public void testBody() {

		    debugMsg("About to acquire latch");

		    /* Acquire a latch. */
		    try {
			latch1.acquire();
		    } catch (DatabaseException LE) {
			fail("caught DatabaseException");
		    }

		    debugMsg("acquired latch");

		    /* 
		     * Wait for all other testers to be waiting on the latch.
		     */
		    while (latch1.nWaiters() < N_WAITERS) {
			Thread.yield();
		    }

		    debugMsg("About to release latch");

		    try {
			latch1.release();
		    } catch (LatchNotHeldException LNHE) {
			fail("unexpected LatchNotHeldException");
		    }
		}
	    };

	for (int i = 0; i < N_WAITERS; i++) {
	    waiterThreads[i] =
		new MultiWaiterTestThread("testWait-Waiter" + i, i) {
		    public void testBody() {

			int waiterNumber =
			    ((MultiWaiterTestThread)
			     Thread.currentThread()).waiterNumber;

			/* Wait for tester1 to start. */
			debugMsg("Waiting for main to acquire latch");

			while (latch1.owner() != tester1) {
			    Thread.yield();
			}

			/* 
			 * Wait until it's our turn to try to acquire the
			 * latch.
			 */
			debugMsg("Waiting for our turn to acquire latch");
			while (latch1.nWaiters() < waiterNumber) {
			    Thread.yield();
			}

			debugMsg("About to acquire latch");
			/* Try to acquire the latch */
			try {
			    latch1.acquire();
			} catch (DatabaseException LE) {
			    fail("caught DatabaseException");
			}

			debugMsg("nWaiters: " + latch1.nWaiters());
			assertTrue(latch1.nWaiters() ==
				   (N_WAITERS - waiterNumber - 1));

			debugMsg("About to release latch");
			/* Release it. */
			try {
			    latch1.release();
			} catch (LatchNotHeldException LNHE) {
			    fail("unexpected LatchNotHeldException");
			}
		    }
		};
	}

	tester1.start();

	for (int i = 0; i < N_WAITERS; i++) {
	    waiterThreads[i].start();
	}

	tester1.finishTest();
	for (int i = 0; i < N_WAITERS; i++) {
	    waiterThreads[i].finishTest();
	}
    }
}
