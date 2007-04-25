/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Transaction.java,v 1.48.2.2 2007/03/28 15:53:44 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.PropUtil;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class Transaction {

    private Txn txn;
    private Environment env;
    private long id; 
    private String name;

    /**
     * Creates a transaction.
     */
    Transaction(Environment env, Txn txn) {
        this.env = env;
        this.txn = txn;

        /* 
         * Copy the id to this wrapper object so the id will be available
         * after the transaction is closed and the txn field is nulled.
         */
        this.id = txn.getId();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void abort()
	throws DatabaseException {

	try {
	    checkEnv();
	    env.removeReferringHandle(this);
	    txn.abort(false);      // no sync required

	    /* Remove reference to internal txn, so we can reclaim memory. */
	    txn = null;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(env).invalidate(E);
	    throw E;
	}
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getId() 
        throws DatabaseException {

        return id;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void commit()
	throws DatabaseException {

	try {
	    checkEnv();
	    env.removeReferringHandle(this);
	    txn.commit();
	    /* Remove reference to internal txn, so we can reclaim memory. */
	    txn = null;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(env).invalidate(E);
	    throw E;
	}
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void commitSync()
	throws DatabaseException {

        doCommit(Txn.TXN_SYNC);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void commitNoSync()
	throws DatabaseException {

        doCommit(Txn.TXN_NOSYNC);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void commitWriteNoSync()
	throws DatabaseException {

        doCommit(Txn.TXN_WRITE_NOSYNC);
    }

    public boolean getPrepared() {
	return txn.getPrepared();
    }

    private void doCommit(byte commitType) 
	throws DatabaseException {

	try {
	    checkEnv();
	    env.removeReferringHandle(this);
	    txn.commit(commitType);

	    /* Remove reference to internal txn, so we can reclaim memory. */
	    txn = null;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(env).invalidate(E);
	    throw E;
	}
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTxnTimeout(long timeOut) 
        throws DatabaseException {

        checkEnv();
        txn.setTxnTimeout(PropUtil.microsToMillis(timeOut));
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setLockTimeout(long timeOut) 
        throws DatabaseException {

        checkEnv();
        txn.setLockTimeout(PropUtil.microsToMillis(timeOut));
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public String getName() {
	return name;
    }

    public int hashCode() {
	return (int) id;
    }

    public boolean equals(Object o) {
	if (o == null) {
	    return false;
	}

	if (!(o instanceof Transaction)) {
	    return false;
	}

	if (((Transaction) o).id == id) {
	    return true;
	}

	return false;
    }

    public String toString() {
	StringBuffer sb = new StringBuffer();
	sb.append("<Transaction id=\"");
	sb.append(id).append("\"");
	if (name != null) {
	    sb.append(" name=\"");
	    sb.append(name).append("\"");
	    }
	sb.append(">");
	return sb.toString();
    }

    /**
     * This method should only be called by the LockerFactory.getReadableLocker
     * and getWritableLocker methods.  The locker returned does not enforce the
     * readCommitted isolation setting.
     */
    Locker getLocker() 
        throws DatabaseException {

        if (txn == null) {
            throw new DatabaseException("Transaction " + id +
                                        " has been closed and is no longer"+
                                        " usable.");
        } else {
            return txn;
        }
    }

    /*
     * Helpers
     */

    Txn getTxn() {
	return txn;
    }

    /**
     * @throws RunRecoveryException if the underlying environment is invalid.
     */
    private void checkEnv() 
        throws RunRecoveryException {
        
	env.getEnvironmentImpl().checkIfInvalid();
    }
}
