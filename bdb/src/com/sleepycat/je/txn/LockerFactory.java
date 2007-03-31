/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LockerFactory.java,v 1.8.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Factory of static methods for creating Locker objects.
 */
public class LockerFactory {

    /**
     * Get a locker for a writable operation, checking whether the db and
     * environment is transactional or not. Must return a non null locker.
     */
    public static Locker getWritableLocker(Environment env,
                                           Transaction userTxn,
                                           boolean dbIsTransactional)
        throws DatabaseException {

        return getWritableLocker(env, userTxn, dbIsTransactional, false, null);
    }

    /**
     * Get a locker for a writable operation, also specifying whether to retain
     * non-transactional locks when a new locker must be created.
     *
     * @param retainNonTxnLocks is true for DbTree operations, so that the
     * handle lock may be transferred out of the locker when the operation is
     * complete.
     */
    public static Locker getWritableLocker(Environment env,
                                           Transaction userTxn,
                                           boolean dbIsTransactional,
                                           boolean retainNonTxnLocks,
                                           TransactionConfig autoCommitConfig)
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        boolean envIsTransactional = envImpl.isTransactional();

	if (userTxn == null) {
	    Transaction xaLocker = env.getThreadTransaction();
	    if (xaLocker != null) {
		return DbInternal.getLocker(xaLocker);
	    }
	}

        if (dbIsTransactional && userTxn == null) {

            if (autoCommitConfig == null) {
                autoCommitConfig = DbInternal.getDefaultTxnConfig(env);
            }
            return new AutoTxn(envImpl, autoCommitConfig);

        } else if (userTxn == null) {

            if (retainNonTxnLocks) {
                return new BasicLocker(envImpl);
            } else {
                return new ThreadLocker(envImpl);
            }

        } else {

            /* 
             * The user provided a transaction, the environment and the
             * database had better be opened transactionally.
             */
            if (!envIsTransactional) {
                throw new DatabaseException
		    ("A Transaction cannot be used because the"+
		     " environment was opened" +
		     " non-transactionally");
            }
            if (!dbIsTransactional) {
                throw new DatabaseException
		    ("A Transaction cannot be used because the" +
		     " database was opened" +
		     " non-transactionally");
            }

            /*
             * Use the locker for the given transaction.  For read-comitted,
             * wrap the given transactional locker in a special locker for that
             * isolation level.  But if retainNonTxnLocks we cannot use
             * read-committed, since retainNonTxnLocks is used for handle locks
             * that must be retained across operations.
             */
            Locker locker = DbInternal.getLocker(userTxn);
            if (locker.isReadCommittedIsolation() && !retainNonTxnLocks) {
                return new ReadCommittedLocker(envImpl, locker);
            } else {
                return locker;
            }
        }
    }
   
    /**
     * Get a locker for a read or cursor operation.
     * See getWritableLocker for an explanation of retainNonTxnLocks.
     */
    public static Locker getReadableLocker(Environment env,
                                           Transaction userTxn,
                                           boolean dbIsTransactional,
                                           boolean retainNonTxnLocks,
                                           boolean readCommittedIsolation) 
        throws DatabaseException {

        if (userTxn != null && !dbIsTransactional) {
            throw new DatabaseException
                ("A Transaction cannot be used because the" +
                 " database was opened" +
                 " non-transactionally");
        }

        Locker locker = null;
	if (userTxn != null) {

            /*
             * Use the locker for the given transaction.  Request read-comitted
             * if that isolation level is configured for the transaction, or if
             * true is passed for the parameter (this is the case when
             * read-committed is configured for the cursor).
             */
            locker = DbInternal.getLocker(userTxn);
            if (locker.isReadCommittedIsolation()) {
                readCommittedIsolation = true;
            }
        }

        return getReadableLocker
	    (env, locker, retainNonTxnLocks, readCommittedIsolation);
    }

    /**
     * Get a locker for this database handle for a read or cursor operation.
     * See getWritableLocker for an explanation of retainNonTxnLocks.
     */
    public static Locker getReadableLocker(Environment env,
                                           Database dbHandle,
                                           Locker locker,
                                           boolean retainNonTxnLocks,
                                           boolean readCommittedIsolation) 
        throws DatabaseException {
        
        DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(dbHandle);
        if (!dbImpl.isTransactional() && 
            locker != null &&
            locker.isTransactional()) {
            throw new DatabaseException
                ("A Transaction cannot be used because the" +
                 " database was opened" +
                 " non-transactionally");
        }

        /* 
         * Don't reuse a non-transactional locker unless retaining
         * non-transactional locks was requested.
         */
        if (locker != null &&
            !locker.isTransactional() &&
            !retainNonTxnLocks) {
            locker = null;
        }

        /*
         * Request read-comitted if that isolation level is configured for the
         * locker being reused, or if true is passed for the parameter (this is
         * the case when read-committed is configured for the cursor).
         */
        if (locker != null && locker.isReadCommittedIsolation()) {
            readCommittedIsolation = true;
        }

        return getReadableLocker
            (env, locker, retainNonTxnLocks, readCommittedIsolation);
    }

    /**
     * Get a locker for a read or cursor operation.
     * See getWritableLocker for an explanation of retainNonTxnLocks.
     */
    private static Locker getReadableLocker(Environment env,
                                            Locker locker,
                                            boolean retainNonTxnLocks,
                                            boolean readCommittedIsolation) 
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);

	if (locker == null) {
	    Transaction xaTxn = env.getThreadTransaction();
	    if (xaTxn != null) {
		return DbInternal.getLocker(xaTxn);
	    }
	}

        if (locker == null) {

            /*
             * A non-transactional locker is requested.  If we're retaining
             * non-transactional locks across operations, use a BasicLocker
             * since the locker may be used across threads; this is used when
             * acquiring handle locks internally (open, close, remove, etc).
             * Otherwise, use a ThreadLocker to avoid self-deadlocks within the
             * same thread; this used for ordinary user operations.
             */
            if (retainNonTxnLocks) {
                locker = new BasicLocker(envImpl);
            } else {
                locker = new ThreadLocker(envImpl);
            }
        } else {

            /*
             * Use the given locker.  For read-committed, wrap the given
             * transactional locker in a special locker for that isolation
             * level.  But if retainNonTxnLocks we cannot use read-committed,
             * since retainNonTxnLocks is used for handle locks that must be
             * retained across operations.
             */
            if (readCommittedIsolation && !retainNonTxnLocks) {
                locker = new ReadCommittedLocker(envImpl, locker);
            }
        }
        return locker;
    }
}
