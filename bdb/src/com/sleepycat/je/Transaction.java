/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Transaction.java,v 1.60 2008/06/09 16:17:59 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.PropUtil;

/**
 * The Transaction object is the handle for a transaction.  Methods off the
 * transaction handle are used to configure, abort and commit the transaction.
 * Transaction handles are provided to other Berkeley DB methods in order to
 * transactionally protect those operations.
 *
 * <p>Transaction handles are free-threaded; transactions handles may be used
 * concurrently by multiple threads. Once the {@link
 * com.sleepycat.je.Transaction#abort Transaction.abort} or {@link
 * com.sleepycat.je.Transaction#commit Transaction.commit} methods are called,
 * the handle may not be accessed again, regardless of the success or failure
 * of the method.</p>
 *
 * <p>To obtain a transaction with default attributes:</p>
 *
 * <blockquote><pre>
 *     Transaction txn = myEnvironment.beginTransaction(null, null);
 * </pre></blockquote>
 *
 * <p>To customize the attributes of a transaction:</p>
 *
 * <blockquote><pre>
 *     TransactionConfig config = new TransactionConfig();
 *     config.setReadUncommitted(true);
 *     Transaction txn = myEnvironment.beginTransaction(null, config);
 * </pre></blockquote>
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
     * Cause an abnormal termination of the transaction.
     *
     * <p>The log is played backward, and any necessary undo operations are
     * done. Before Transaction.abort returns, any locks held by the
     * transaction will have been released.</p>
     *
     * <p>In the case of nested transactions, aborting a parent transaction
     * causes all children (unresolved or not) of the parent transaction to be
     * aborted.</p>
     *
     * <p>All cursors opened within the transaction must be closed before the
     * transaction is aborted.</p>
     *
     * <p>After Transaction.abort has been called, regardless of its return,
     * the {@link com.sleepycat.je.Transaction Transaction} handle may not be
     * accessed again.</p>
     *
     * @throws DatabaseException if a failure occurs.
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
     * Return the transaction's unique ID.
     *
     * @return The transaction's unique ID.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public long getId()
        throws DatabaseException {

        return id;
    }

    /**
     * End the transaction.  If the environment is configured for synchronous
     * commit, the transaction will be committed synchronously to stable
     * storage before the call returns.  This means the transaction will
     * exhibit all of the ACID (atomicity, consistency, isolation, and
     * durability) properties.
     *
     * <p>If the environment is not configured for synchronous commit, the
     * commit will not necessarily have been committed to stable storage before
     * the call returns.  This means the transaction will exhibit the ACI
     * (atomicity, consistency, and isolation) properties, but not D
     * (durability); that is, database integrity will be maintained, but it is
     * possible this transaction may be undone during recovery.</p>
     *
     * <p>All cursors opened within the transaction must be closed before the
     * transaction is committed.</p>
     *
     * <p>After this method returns the {@link com.sleepycat.je.Transaction
     * Transaction} handle may not be accessed again, regardless of the
     * method's success or failure. If the method encounters an error, the
     * transaction and all child transactions of the transaction will have been
     * aborted when the call returns.</p>
     *
     * @throws DatabaseException if a failure occurs.
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
     *
     * @hidden
     * Feature not yet available.
     *
     * End the transaction using the specified durability requirements. This
     * requirement overrides any default durability requirements associated
     * with the environment. If the durability requirements cannot be satisfied,
     * an exception is thrown to describe the problem. Please see
     * {@link Durability} for specific exceptions that could result when the
     * durability requirements cannot be satisfied.
     *
     * @param durability the durability requirements for this transaction
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void commit(Durability durability)
        throws DatabaseException {

        doCommit(durability, false /* explicitSync */);
    }

    /**
     * End the transaction, committing synchronously.  This means the
     * transaction will exhibit all of the ACID (atomicity, consistency,
     * isolation, and durability) properties.
     *
     * <p>This behavior is the default for database environments unless
     * otherwise configured using the {@link
     * com.sleepycat.je.EnvironmentConfig#setTxnNoSync
     * EnvironmentConfig.setTxnNoSync} method.  This behavior may also be set
     * for a single transaction using the {@link
     * com.sleepycat.je.Environment#beginTransaction
     * Environment.beginTransaction} method.  Any value specified to this
     * method overrides both of those settings.</p>
     *
     * <p>All cursors opened within the transaction must be closed before the
     * transaction is committed.</p>
     *
     * <p>After this method returns the {@link com.sleepycat.je.Transaction
     * Transaction} handle may not be accessed again, regardless of the
     * method's success or failure. If the method encounters an error, the
     * transaction and all child transactions of the transaction will have been
     * aborted when the call returns.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void commitSync()
	throws DatabaseException {

        doCommit(TransactionConfig.SYNC, true /* explicitSync */);
    }

    /**
     * End the transaction, not committing synchronously. This means the
     * transaction will exhibit the ACI (atomicity, consistency, and isolation)
     * properties, but not D (durability); that is, database integrity will be
     * maintained, but it is possible this transaction may be undone during
     * recovery.
     *
     * <p>This behavior may be set for a database environment using the {@link
     * com.sleepycat.je.EnvironmentConfig#setTxnNoSync
     * EnvironmentConfig.setTxnNoSync} method or for a single transaction using
     * the {@link com.sleepycat.je.Environment#beginTransaction
     * Environment.beginTransaction} method.  Any value specified to this
     * method overrides both of those settings.</p>
     *
     * <p>All cursors opened within the transaction must be closed before the
     * transaction is committed.</p>
     *
     * <p>After this method returns the {@link com.sleepycat.je.Transaction
     * Transaction} handle may not be accessed again, regardless of the
     * method's success or failure. If the method encounters an error, the
     * transaction and all child transactions of the transaction will have been
     * aborted when the call returns.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void commitNoSync()
	throws DatabaseException {

        doCommit(TransactionConfig.NO_SYNC, true /* explicitSync */);
    }

    /**
     * End the transaction, committing synchronously.  This means the
     * transaction will exhibit all of the ACID (atomicity, consistency,
     * isolation, and durability) properties.
     *
     * <p>This behavior is the default for database environments unless
     * otherwise configured using the {@link
     * com.sleepycat.je.EnvironmentConfig#setTxnNoSync
     * EnvironmentConfig.setTxnNoSync} method.  This behavior may also be set
     * for a single transaction using the {@link
     * com.sleepycat.je.Environment#beginTransaction
     * Environment.beginTransaction} method.  Any value specified to this
     * method overrides both of those settings.</p>
     *
     * <p>All cursors opened within the transaction must be closed before the
     * transaction is committed.</p>
     *
     * <p>After this method returns the {@link com.sleepycat.je.Transaction
     * Transaction} handle may not be accessed again, regardless of the
     * method's success or failure. If the method encounters an error, the
     * transaction and all child transactions of the transaction will have been
     * aborted when the call returns.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void commitWriteNoSync()
	throws DatabaseException {

        doCommit(TransactionConfig.WRITE_NO_SYNC, true /* explicitSync */);
    }


    /**
     * @hidden
     * For internal use.
     */
    public boolean getPrepared() {
	return txn.getPrepared();
    }

    /**
     * Perform error checking and invoke the commit on Txn.
     *
     * @param durability the durability to use for the commit
     * @param explicitSync true if the method was invoked from one of the
     * sync-specific APIs, false if durability was used explicitly. This
     * parameter exists solely to support mixed mode api usage checks.
     *
     * @throws DatabaseException if a failure occurs.
     */
    private void doCommit(Durability durability, boolean explicitSync)
	throws DatabaseException {

	try {
	    checkEnv();
	    env.removeReferringHandle(this);
	    if (explicitSync) {
	        /* A sync-specific api was invoked. */
	        if (txn.getExplicitDurabilityConfigured()) {
	            throw new IllegalArgumentException
	                ("Mixed use of deprecated durability API for the " +
	                 "transaction commit with the new durability API for " +
	                "TransactionConfig or MutableEnvironmentConfig");
	        }
	    } else if (txn.getExplicitSyncConfigured()) {
	        /* Durability was explicitly configured for commit */
                throw new IllegalArgumentException
                    ("Mixed use of new durability API for the " +
                      "transaction commit with deprecated durability API for " +
                      "TransactionConfig or MutableEnvironmentConfig");
	    }
	    txn.commit(durability);
	    /* Remove reference to internal txn, so we can reclaim memory. */
	    txn = null;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(env).invalidate(E);
	    throw E;
	}
    }

    /**
     * Configure the timeout value for the transaction lifetime.
     *
     * <p>If the transaction runs longer than this time, the transaction may
     * throw {@link com.sleepycat.je.DatabaseException DatabaseException}.</p>
     *
     * <p>Timeouts are checked whenever a thread of control blocks on a lock or
     * when deadlock detection is performed.  For this reason, the accuracy of
     * the timeout depends on how often deadlock detection is performed.</p>
     *
     * @param timeOut The timeout value for the transaction lifetime, in
     * microseconds. A value of 0 disables timeouts for the transaction.
     *
     * @throws IllegalArgumentException If the value of timeout is negative
     * @throws DatabaseException if a failure occurs.
     */
    public void setTxnTimeout(long timeOut)
        throws IllegalArgumentException, DatabaseException {

        checkEnv();
        txn.setTxnTimeout(PropUtil.microsToMillis(timeOut));
    }

    /**
     * Configure the lock request timeout value for the transaction.
     *
     * <p>If a lock request cannot be granted in this time, the transaction may
     * throw {@link com.sleepycat.je.DatabaseException DatabaseException}.</p>
     *
     * <p>Timeouts are checked whenever a thread of control blocks on a lock or
     * when deadlock detection is performed.  For this reason, the accuracy of
     * the timeout depends on how often deadlock detection is performed.</p>
     *
     * @param timeOut The lock request timeout value for the transaction, in
     * microseconds.  A value of 0 disables timeouts for the transaction.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @throws IllegalArgumentException If the value of timeout is negative.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setLockTimeout(long timeOut)
        throws IllegalArgumentException, DatabaseException {

        checkEnv();
        txn.setLockTimeout(PropUtil.microsToMillis(timeOut));
    }

    /**
     * Set the user visible name for the transaction.
     *
     * @param name The user visible name for the transaction.
     *
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Get the user visible name for the transaction.
     *
     * @return The user visible name for the transaction.
     *
     */
    public String getName() {
	return name;
    }

    /**
     * @hidden
     * For internal use.
     */
    @Override
    public int hashCode() {
	return (int) id;
    }

    /**
     * @hidden
     * For internal use.
     */
    @Override
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

    @Override
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
        throws DatabaseException {

        EnvironmentImpl envImpl =  env.getEnvironmentImpl();
        if (envImpl == null) {
            throw new DatabaseException("The environment has been closed. " +
                                        " This transaction is no longer" +
                                        " usable.");
        }

        envImpl.checkIfInvalid();
    }
}
