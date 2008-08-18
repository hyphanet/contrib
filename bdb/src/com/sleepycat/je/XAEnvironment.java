/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: XAEnvironment.java,v 1.14 2008/01/07 14:28:46 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.txn.TxnManager;

/**
 * An Environment that implements XAResource.  If JE is used in an XA
 * environment, this class should be used instead of Environment so that
 * appropriate XA functions are available.
 */
public class XAEnvironment extends Environment implements XAResource {

    private static final boolean DEBUG = false;

    /**
     * Create a database environment handle.
     *
     * @param envHome The database environment's home directory.
     *
     * @param configuration The database environment attributes.  If null,
     * default attributes are used.
     */
    public XAEnvironment(File envHome, EnvironmentConfig configuration)
        throws DatabaseException {

	super(envHome, configuration);
    }

    /**
     * Used to get the Transaction object given an XA Xid.
     */
    public Transaction getXATransaction(Xid xid)
	throws DatabaseException {

	Txn ret = envImpl.getTxnManager().getTxnFromXid(xid);
	if (ret == null) {
	    return null;
	}

	/* Do we guarantee object identity for Transaction objects? */
	return new Transaction(this, ret);
    }

    /**
     * Used to set the Transaction object for an XA Xid.  Public for tests.
     */
    public void setXATransaction(Xid xid, Transaction txn)
	throws DatabaseException {

	envImpl.getTxnManager().
	    registerXATxn(xid, txn.getTxn(), false);
    }

    /*
     * XAResource methods.
     */

    public void commit(Xid xid, boolean ignore /*onePhase*/)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** commit called " + xid + "/" + ignore);
	}

	if (xid == null) {
	    return;
	}

	try {
	    checkEnv();
	    Transaction txn = getXATransaction(xid);
	    if (txn == null) {
		throw new XAException
		    ("No transaction found for " + xid + " during commit.");
	    }
	    removeReferringHandle(txn);
	    if (txn.getTxn().getOnlyAbortable()) {
		throw new XAException(XAException.XA_RBROLLBACK);
	    }
	    txn.getTxn().commit(xid);
	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}
	if (DEBUG) {
	    System.out.println("*** commit finished");
	}
    }

    public void end(Xid xid, int flags)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** end called " + xid + "/" + flags);
	}

	/* flags - One of TMSUCCESS, TMFAIL, or TMSUSPEND. */

	boolean tmFail = (flags & XAResource.TMFAIL) != 0;
	boolean tmSuccess = (flags & XAResource.TMSUCCESS) != 0;
	boolean tmSuspend = (flags & XAResource.TMSUSPEND) != 0;
	if ((tmFail && tmSuccess) ||
	    ((tmFail || tmSuccess) && tmSuspend)) {
	    throw new XAException(XAException.XAER_INVAL);
	}

	try {
	    if (DEBUG) {
		System.out.println
		    ("Transaction for " + Thread.currentThread() + " is " +
		     envImpl.getTxnManager().getTxnForThread());
	    }

	    Transaction txn =
		envImpl.getTxnManager().unsetTxnForThread();
	    if (txn == null) {
		txn = getXATransaction(xid);
		boolean isSuspended = (txn != null) &&
		    txn.getTxn().isSuspended();
		if (!isSuspended) {
		    throw new XAException(XAException.XAER_NOTA);
		}
	    }

	    if (tmFail) {
		txn.getTxn().setOnlyAbortable();
	    }

	    if (tmSuspend) {
		txn.getTxn().setSuspended(true);
	    }

	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}
    }

    public void forget(Xid xid)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** forget called");
	}

	throw new XAException(XAException.XAER_NOTA);
    }

    public boolean isSameRM(XAResource rm)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** isSameRM called");
	}

	try {
	    checkEnv();
	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}

	if (rm == null) {
	    return false;
	}

	if (!(rm instanceof XAEnvironment)) {
	    return false;
	}

	return envImpl ==
	    DbInternal.envGetEnvironmentImpl((XAEnvironment) rm);
    }

    public int prepare(Xid xid)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** prepare called");
	}

	try {
	    checkEnv();
	    Transaction txn = getXATransaction(xid);
	    if (txn == null) {
		throw new XAException
		    ("No transaction found for " + xid + " during prepare.");
	    }
	    int ret = txn.getTxn().prepare(xid);

	    if (DEBUG) {
		System.out.println("*** prepare returning " + ret);
	    }

	    /*
	     * If this transaction was R/O, then there were no writes.  We'll
	     * commit it here because the user doesn't need to (and isn't
	     * allowed to either).
	     */
	    if (ret == XAResource.XA_RDONLY) {
		commit(xid, true);
	    }

	    return ret;
	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}
	return XAResource.XA_OK;        // for compiler
    }

    public Xid[] recover(int flags)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** recover called");
	}

	/* flags - One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. */

	boolean tmStartRScan = (flags & XAResource.TMSTARTRSCAN) != 0;
	boolean tmEndRScan = (flags & XAResource.TMENDRSCAN) != 0;
	if ((tmStartRScan && tmEndRScan) ||
	    (!tmStartRScan && !tmEndRScan && flags != TMNOFLAGS)) {
	    throw new XAException(XAException.XAER_INVAL);
	}

	/*
	 * We don't have to actually do anything with STARTRSCAN or ENDRSCAN
	 * since we return the whole set of Xid's to be recovered on each call.
	 */
	try {
	    checkHandleIsValid();
	    checkEnv();

	    if (DEBUG) {
		System.out.println("*** recover returning1");
	    }

	    return envImpl.getTxnManager().XARecover();
	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}
	return null;                // for compiler
    }

    public void rollback(Xid xid)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** rollback called");
	}

	try {
	    checkEnv();
	    Transaction txn = getXATransaction(xid);
	    if (txn == null) {
		throw new XAException
		    ("No transaction found for " + xid + " during abort.");
	    }
	    removeReferringHandle(txn);
	    txn.getTxn().abort(xid);
	} catch (DatabaseException DE) {
	    throwNewXAException(DE);
	}

	if (DEBUG) {
	    System.out.println("*** rollback returning");
	}
    }

    public int getTransactionTimeout()
	throws XAException {

	try {
	    return (int) ((getConfig().getTxnTimeout() + 999999L) / 1000000L);
	} catch (Exception DE) {
	    throwNewXAException(DE);
	}
	return 0;                // for compiler
    }

    public boolean setTransactionTimeout(int timeout)
	throws XAException {

	return false;
    }

    public void start(Xid xid, int flags)
	throws XAException {

	if (DEBUG) {
	    System.out.println("*** start called " + xid + "/" + flags);
	}

	boolean tmJoin = (flags & XAResource.TMJOIN) != 0;
	boolean tmResume = (flags & XAResource.TMRESUME) != 0;

	/* Check flags - only one of TMNOFLAGS, TMJOIN, or TMRESUME. */
	if (xid == null ||
	    (tmJoin && tmResume) ||
	    (!tmJoin &&
	     !tmResume &&
	     flags != XAResource.TMNOFLAGS)) {
	    throw new XAException(XAException.XAER_INVAL);
	}

	try {
	    Transaction txn = getXATransaction(xid);
	    TxnManager txnMgr = envImpl.getTxnManager();

	    if (flags == XAResource.TMNOFLAGS) {

		/*
		 * If neither RESUME nor JOIN was set, make sure xid doesn't
		 * exist in allXATxns.  Throw XAER_DUPID if it does.
		 */
		if (txn == null) {
		    if (DEBUG) {
			System.out.println
			    ("Transaction for XID " + xid + " being created");
		    }

		    txn = beginTransaction(null, null);
		    setXATransaction(xid, txn);

		} else {
		    throw new XAException(XAException.XAER_DUPID);
		}
	    } else if (tmJoin) {
		if (txn == null) {
		    throw new XAException(XAException.XAER_NOTA);
		}

		if (txnMgr.getTxnForThread() != null ||
		    txn.getPrepared()) {
		    throw new XAException(XAException.XAER_PROTO);
		}
	    } else if (tmResume) {
		if (txn == null) {
		    throw new XAException(XAException.XAER_NOTA);
		}

		if (!txn.getTxn().isSuspended()) {
		    throw new XAException(XAException.XAER_PROTO);
		}
		txn.getTxn().setSuspended(false);
	    }

	    if (DEBUG) {
		System.out.println
		    ("Setting Transaction for " + Thread.currentThread());
	    }
	    txnMgr.setTxnForThread(txn);
	} catch (DatabaseException DE) {
	    if (DEBUG) {
		System.out.println("*** start exception");
	    }
	    throwNewXAException(DE);
	}

	if (DEBUG) {
	    System.out.println("*** start finished");
	}
    }

    private void throwNewXAException(Exception E)
	throws XAException {

	XAException ret = new XAException(E.toString());
	ret.initCause(E);
	throw ret;
    }
}
