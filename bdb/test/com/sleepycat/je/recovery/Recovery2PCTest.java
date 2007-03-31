/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Recovery2PCTest.java,v 1.10.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.IOException;
import java.util.Hashtable;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.log.LogUtils.XidImpl;

public class Recovery2PCTest extends RecoveryTestBase {
    private static final boolean DEBUG = false;

    private boolean explicitTxn;
    private boolean commit;
    private boolean recover;

    public static Test suite() {
        TestSuite allTests = new TestSuite();
	for (int i = 0; i < 2; i++) {
	    for (int j = 0; j < 2; j++) {
		for (int k = 0; k < 2; k++) {
		    allTests.addTest
			(((Recovery2PCTest)
			  (TestSuite.createTest(Recovery2PCTest.class,
						"testBasic"))).
			 init(i, j, k));
		}
	    }
	}

	/* We only need to test XARecoveryAPI for implicit and explicit. */
	allTests.addTest
	    (((Recovery2PCTest)
	      (TestSuite.createTest(Recovery2PCTest.class,
				    "testXARecoverAPI"))).
	     init(0, 0, 0));
	allTests.addTest
	    (((Recovery2PCTest)
	      (TestSuite.createTest(Recovery2PCTest.class,
				    "testXARecoverAPI"))).
	     init(1, 0, 0));
	allTests.addTest
	    (((Recovery2PCTest)
	      (TestSuite.createTest(Recovery2PCTest.class,
				    "testXARecoverArgCheck"))).
	     init(0, 0, 0));
        return allTests;
    }

    public Recovery2PCTest() {
	super(true);
    }

    private Recovery2PCTest init(int explicitTxn,
				 int commit,
				 int recover) {
	this.explicitTxn = (explicitTxn == 0);
	this.commit = (commit == 0);
	this.recover = (recover == 0);
	return this;
    }

    private String opName() {
	StringBuffer sb = new StringBuffer();

	if (explicitTxn) {
	    sb.append("Exp");
	} else {
	    sb.append("Imp");
	}

	sb.append("/");

	if (commit) {
	    sb.append("C");
	} else {
	    sb.append("A");
	}

	sb.append("/");

	if (recover) {
	    sb.append("Rec");
	} else {
	    sb.append("No Rec");
	}

	return sb.toString();
    }

    public void tearDown() 
	throws IOException, DatabaseException {
        
        /* Set test name for reporting; cannot be done in the ctor or setUp. */
        setName(getName() + ": " + opName());
	super.tearDown();
    }

    public void testBasic()
	throws Throwable {

	createXAEnvAndDbs(1 << 20, false, NUM_DBS);
	XAEnvironment xaEnv = (XAEnvironment) env;
        int numRecs = NUM_RECS * 3;

        try {
            /* Set up an repository of expected data. */
            Hashtable expectedData = new Hashtable();
            
            /* Insert all the data. */
	    XidImpl xid = new XidImpl(1, "TwoPCTest1".getBytes(), null);
            Transaction txn = null;
	    if (explicitTxn) {
		txn = env.beginTransaction(null, null);
		xaEnv.setXATransaction(xid, txn);
	    } else {
		xaEnv.start(xid, 0);
	    }
            insertData(txn, 0, numRecs - 1, expectedData, 1, commit, NUM_DBS);
	    if (!explicitTxn) {
		xaEnv.end(xid, 0);
	    }

	    xaEnv.prepare(xid);

	    if (recover) {
		closeEnv();
		xaRecoverOnly(NUM_DBS);
		xaEnv = (XAEnvironment) env;
	    }

	    if (commit) {
		xaEnv.commit(xid, false);
	    } else {
		xaEnv.rollback(xid);
	    }

	    if (recover) {
		verifyData(expectedData, commit, NUM_DBS);
		forceCloseEnvOnly();
	    } else {
		closeEnv();
	    }
            xaRecoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            /* Print stacktrace before trying to clean up files. */
            t.printStackTrace();
            throw t;
        }
    }

    public void testXARecoverAPI()
 	throws Throwable {

	createXAEnvAndDbs(1 << 20, false, NUM_DBS << 1);
	final XAEnvironment xaEnv = (XAEnvironment) env;
        final int numRecs = NUM_RECS * 3;

        try {
            /* Set up an repository of expected data. */
            final Hashtable expectedData1 = new Hashtable();
            final Hashtable expectedData2 = new Hashtable();
            
            /* Insert all the data. */
            final Transaction txn1 =
		(explicitTxn ?
		 env.beginTransaction(null, null) :
		 null);
            final Transaction txn2 =
		(explicitTxn ?
		 env.beginTransaction(null, null) :
		 null);
	    final XidImpl xid1 = new XidImpl(1, "TwoPCTest1".getBytes(), null);
	    final XidImpl xid2 = new XidImpl(1, "TwoPCTest2".getBytes(), null);

	    Thread thread1 = new Thread() {
		    public void run() {
			try {
			    if (explicitTxn) {
				xaEnv.setXATransaction(xid1, txn1);
			    } else {
				xaEnv.start(xid1, 0);
			    }
			    Thread.yield();
			    insertData(txn1, 0, numRecs - 1, expectedData1, 1,
				       true, 0, NUM_DBS);
			    Thread.yield();
			    if (!explicitTxn) {
				xaEnv.end(xid1, 0);
			    }
			    Thread.yield();
			} catch (Exception E) {
			    fail("unexpected: " + E);
			}
		    }
		};

	    Thread thread2 = new Thread() {
		    public void run() {
			try {
			    if (explicitTxn) {
				xaEnv.setXATransaction(xid2, txn2);
			    } else {
				xaEnv.start(xid2, 0);
			    }
			    Thread.yield();
			    insertData(txn2, numRecs, numRecs << 1,
				       expectedData2, 1, false, NUM_DBS,
				       NUM_DBS << 1);
			    Thread.yield();
			    if (!explicitTxn) {
				xaEnv.end(xid2, 0);
			    }
			    Thread.yield();
			} catch (Exception E) {
			    fail("unexpected: " + E);
			}
		    }
		};

	    thread1.start();
	    thread2.start();
	    thread1.join();
	    thread2.join();

	    xaEnv.prepare(xid1);
	    try {
		xaEnv.prepare(xid1);
		fail("should have thrown XID has already been registered");
	    } catch (XAException XAE) {
		// xid1 has already been registered.
	    }
	    xaEnv.prepare(xid2);

	    XAEnvironment xaEnv2 = xaEnv;
	    Xid[] unfinishedXAXids = xaEnv2.recover(0);
	    assertTrue(unfinishedXAXids.length == 2);
	    boolean sawXid1 = false;
	    boolean sawXid2 = false;
	    for (int i = 0; i < 2; i++) {
		if (unfinishedXAXids[i].equals(xid1)) {
		    if (sawXid1) {
			fail("saw Xid1 twice");
		    }
		    sawXid1 = true;
		}
		if (unfinishedXAXids[i].equals(xid2)) {
		    if (sawXid2) {
			fail("saw Xid2 twice");
		    }
		    sawXid2 = true;
		}
	    }
	    assertTrue(sawXid1 && sawXid2);

            closeEnv();
	    xaEnv2 = (XAEnvironment) env;
	    xaRecoverOnly(NUM_DBS);
	    xaEnv2 = (XAEnvironment) env;

	    unfinishedXAXids = xaEnv2.recover(0);
	    assertTrue(unfinishedXAXids.length == 2);
	    sawXid1 = false;
	    sawXid2 = false;
	    for (int i = 0; i < 2; i++) {
		if (unfinishedXAXids[i].equals(xid1)) {
		    if (sawXid1) {
			fail("saw Xid1 twice");
		    }
		    sawXid1 = true;
		}
		if (unfinishedXAXids[i].equals(xid2)) {
		    if (sawXid2) {
			fail("saw Xid2 twice");
		    }
		    sawXid2 = true;
		}
	    }
	    assertTrue(sawXid1 && sawXid2);

	    xaEnv2 = (XAEnvironment) env;
	    xaEnv2.getXATransaction(xid1);
	    xaEnv2.getXATransaction(xid2);
	    xaEnv2.commit(xid1, false);
	    xaEnv2.rollback(xid2);
	    verifyData(expectedData1, false, 0, NUM_DBS);
	    verifyData(expectedData2, false, NUM_DBS, NUM_DBS << 1);
	    forceCloseEnvOnly();
	    xaRecoverOnly(NUM_DBS);
	    verifyData(expectedData1, false, 0, NUM_DBS);
	    verifyData(expectedData2, false, NUM_DBS, NUM_DBS << 1);
        } catch (Throwable t) {
            /* Print stacktrace before trying to clean up files. */
            t.printStackTrace();
            throw t;
        }
    }

    public void testXARecoverArgCheck()
 	throws Throwable {

	createXAEnvAndDbs(1 << 20, false, NUM_DBS);
	XAEnvironment xaEnv = (XAEnvironment) env;

        try {
	    XidImpl xid = new XidImpl(1, "TwoPCTest1".getBytes(), null);

	    /* Check that only one of TMJOIN and TMRESUME can be set. */
	    try {
		xaEnv.start(xid, XAResource.TMJOIN | XAResource.TMRESUME);
		fail("Expected XAException(XAException.XAER_INVAL)");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_INVAL);
	    }

	    /* 
	     * Check that only one of TMJOIN and TMRESUME can be set by passing
	     * a bogus flag value (TMSUSPEND).
	     */
	    try {
		xaEnv.start(xid, XAResource.TMSUSPEND);
		fail("Expected XAException(XAException.XAER_INVAL)");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_INVAL);
	    }

	    xaEnv.start(xid, XAResource.TMNOFLAGS);
	    try {
		xaEnv.start(xid, XAResource.TMNOFLAGS);
		fail("Expected XAER_DUPID");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_DUPID);
	    }
	    xaEnv.end(xid, XAResource.TMNOFLAGS);

	    /* 
	     * Check that JOIN with a non-existant association throws NOTA.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
		xaEnv.start(xid, XAResource.TMJOIN);
		fail("Expected XAER_NOTA");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_NOTA);
	    }

	    /* 
	     * Check that RESUME with a non-existant association throws NOTA.
	     */
	    try {
		xaEnv.start(xid, XAResource.TMRESUME);
		fail("Expected XAER_NOTA");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_NOTA);
	    }

	    /*
	     * Check that start(JOIN) from a thread that is already associated
	     * throws XAER_PROTO.
	     */
	    Xid xid2 = new XidImpl(1, "TwoPCTest3".getBytes(), null);
	    xaEnv.start(xid2, XAResource.TMNOFLAGS);
	    xaEnv.end(xid2, XAResource.TMNOFLAGS);
	    xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
	    xaEnv.start(xid, XAResource.TMNOFLAGS);
	    try {
		xaEnv.start(xid2, XAResource.TMJOIN);
		fail("Expected XAER_PROTO");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_PROTO);
	    }

	    /*
	     * Check that start(RESUME) for an xid that is not suspended throws
	     * XAER_PROTO.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
		xaEnv.start(xid, XAResource.TMRESUME);
		fail("Expected XAER_PROTO");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_PROTO);
	    }

	    /*
	     * Check that end(TMFAIL | TMSUCCESS) throws XAER_INVAL.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
		xaEnv.end(xid, XAResource.TMFAIL | XAResource.TMSUCCESS);
		fail("Expected XAER_INVAL");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_INVAL);
	    }

	    /*
	     * Check that end(TMFAIL | TMSUSPEND) throws XAER_INVAL.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
		xaEnv.end(xid, XAResource.TMFAIL | XAResource.TMSUSPEND);
		fail("Expected XAER_INVAL");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_INVAL);
	    }

	    /*
	     * Check that end(TMSUCCESS | TMSUSPEND) throws XAER_INVAL.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest2".getBytes(), null);
		xaEnv.end(xid, XAResource.TMSUCCESS | XAResource.TMSUSPEND);
		fail("Expected XAER_INVAL");
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_INVAL);
	    }

	    /*
	     * Check that end(TMSUSPEND) actually works.
	     */
	    Xid xid4 = new XidImpl(1, "TwoPCTest4".getBytes(), null);
	    xaEnv.start(xid4, XAResource.TMNOFLAGS);
	    Transaction txn4 = xaEnv.getThreadTransaction();
	    assertTrue(txn4 != null);
	    xaEnv.end(xid4, XAResource.TMSUSPEND);
	    assertTrue(xaEnv.getThreadTransaction() == null);
	    Xid xid5 = new XidImpl(1, "TwoPCTest5".getBytes(), null);
	    xaEnv.start(xid5, XAResource.TMNOFLAGS);
	    Transaction txn5 = xaEnv.getThreadTransaction();
	    xaEnv.end(xid5, XAResource.TMSUSPEND);
	    assertTrue(xaEnv.getThreadTransaction() == null);
	    xaEnv.start(xid4, XAResource.TMRESUME);
	    assertTrue(xaEnv.getThreadTransaction().equals(txn4));
	    xaEnv.end(xid4, XAResource.TMNOFLAGS);
	    xaEnv.start(xid5, XAResource.TMRESUME);
	    assertTrue(xaEnv.getThreadTransaction().equals(txn5));
	    xaEnv.end(xid5, XAResource.TMNOFLAGS);

	    /*
	     * Check TMFAIL.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest6".getBytes(), null);
		xaEnv.start(xid, XAResource.TMNOFLAGS);
		xaEnv.end(xid, XAResource.TMFAIL);
		xaEnv.commit(xid, false);
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XA_RBROLLBACK);
	    }
	    xaEnv.rollback(xid);

	    /*
	     * Check TMSUCCESS.
	     */
	    xid = new XidImpl(1, "TwoPCTest6".getBytes(), null);
	    xaEnv.start(xid, XAResource.TMNOFLAGS);
	    xaEnv.end(xid, XAResource.TMSUCCESS);
	    xaEnv.commit(xid, false);

	    /*
	     * Check start(); end(SUSPEND); end(SUCCESS).  This is a case that
	     * JBoss causes to happen.  It should succeed.
	     */
	    xid = new XidImpl(1, "TwoPCTest7".getBytes(), null);
	    xaEnv.start(xid, XAResource.TMNOFLAGS);
	    xaEnv.end(xid, XAResource.TMSUSPEND);
	    xaEnv.end(xid, XAResource.TMSUCCESS);
	    xaEnv.commit(xid, false);

	    /*
	     * Check end(SUSPEND); end(SUCCESS) [with no start() call.].
	     * This should fail.
	     */
	    try {
		xid = new XidImpl(1, "TwoPCTest8".getBytes(), null);
		xaEnv.end(xid, XAResource.TMFAIL);
		xaEnv.commit(xid, false);
	    } catch (XAException XAE) {
		/* Expect this. */
		assertTrue(XAE.errorCode == XAException.XAER_NOTA);
	    }
	} catch (Throwable t) {
	    t.printStackTrace();
	    throw t;
	}
    }
}
