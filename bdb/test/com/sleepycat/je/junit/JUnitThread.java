/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JUnitThread.java,v 1.19.2.1 2007/02/01 14:50:12 cwl Exp $
 */

package com.sleepycat.je.junit;

import junit.framework.Assert;

/**
 * JUnitThread is a utility class that allows JUtil assertions to be
 * run in other threads.  A JUtil assertion thrown from a
 * thread other than the invoking one can not be caught by JUnit.
 * This class allows these AssertionFailedErrors to be caught and
 * passed back to the original thread.
 * <p>
 * To use, create a JUnitThread and override the testBody() method with
 * the test code.  Then call doTest() on the thread to run the test
 * and re-throw any assertion failures that were thrown by the
 * subthread.
 * <p>
 * Example:
 * <pre>
    public void testEquality() {
    JUnitThread tester =
    new JUnitThread("testEquality") {
    public void testBody() {
    int one = 1;
    assertTrue(one == 1);
    }
    };
    tester.doTest();
    }
 * </pre>
 */
public class JUnitThread extends Thread {
    private Throwable errorReturn;

    /**
     * Construct a new JUnitThread.
     */
    public JUnitThread(String name) {
	super(name);
    }

    public void run() {
	try {
	    testBody();
	} catch (Throwable T) {
	    errorReturn = T;
	}
    }

    /**
     * Method that is to be overridden by the user.  Code should be
     * the guts of the test.  assertXXXX() methods may be called in
     * this method.
     */
    public void testBody()
	throws Throwable {

    }

    /**
     * This method should be called after the JUnitThread has been
     * constructed to cause the actual test to be run and any failures
     * to be returned.
     */
    public void doTest()
	throws Throwable {

	start();
        finishTest();
    }

    /**
     * This method should be called after the JUnitThread has been
     * started to cause the test to report any failures.
     */
    public void finishTest()
	throws Throwable {

	try {
	    join();
	} catch (InterruptedException IE) {
	    Assert.fail("caught unexpected InterruptedException");
	}
	if (errorReturn != null) {
	    throw errorReturn;
	}
    }

    public String toString() {
	return "<JUnitThread: " + super.toString() + ">";
    }
}
