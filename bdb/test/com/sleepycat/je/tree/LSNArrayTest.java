/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LSNArrayTest.java,v 1.5.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import junit.framework.TestCase;

import com.sleepycat.je.utilint.DbLsn;

public class LSNArrayTest extends TestCase {
    private static final int N_ELTS = 128;

    private IN theIN;

    public void setUp() {
	theIN = new IN();
    }

    public void tearDown() {
    }

    public void testPutGetElement() {
	doTest(N_ELTS);
    }

    public void testOverflow() {
	doTest(N_ELTS << 2);
    }

    public void testFileOffsetGreaterThan3Bytes() {
	theIN.initEntryLsn(10);
	theIN.setLsnElement(0, 0xfffffe);
	assertTrue(theIN.getLsn(0) == 0xfffffe);
	assertTrue(theIN.getEntryLsnByteArray() != null);
	assertTrue(theIN.getEntryLsnLongArray() == null);
	theIN.setLsnElement(1, 0xffffff);
	assertTrue(theIN.getLsn(1) == 0xffffff);
	assertTrue(theIN.getEntryLsnLongArray() != null);
	assertTrue(theIN.getEntryLsnByteArray() == null);

	theIN.initEntryLsn(10);
	theIN.setLsnElement(0, 0xfffffe);
	assertTrue(theIN.getLsn(0) == 0xfffffe);
	assertTrue(theIN.getEntryLsnByteArray() != null);
	assertTrue(theIN.getEntryLsnLongArray() == null);
	theIN.setLsnElement(1, 0xffffff + 1);
	assertTrue(theIN.getLsn(1) == 0xffffff + 1);
	assertTrue(theIN.getEntryLsnLongArray() != null);
	assertTrue(theIN.getEntryLsnByteArray() == null);
    }

    private void doTest(int nElts) {
	theIN.initEntryLsn(nElts);
	for (int i = nElts - 1; i >= 0; i--) {
	    long thisLsn = DbLsn.makeLsn(i, i);
	    theIN.setLsnElement(i, thisLsn);
	    if (theIN.getLsn(i) != thisLsn) {
		System.out.println(i + " found: " +
				   DbLsn.toString(theIN.getLsn(i)) +
				   " expected: " +
				   DbLsn.toString(thisLsn));
	    }
	    assertTrue(theIN.getLsn(i) == thisLsn);
	}

	for (int i = 0; i < nElts; i++) {
	    long thisLsn = DbLsn.makeLsn(i, i);
	    theIN.setLsnElement(i, thisLsn);
	    assertTrue(theIN.getLsn(i) == thisLsn);
	}
    }
}
