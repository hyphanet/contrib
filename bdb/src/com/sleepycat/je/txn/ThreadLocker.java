/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ThreadLocker.java,v 1.20 2008/03/18 15:53:05 mark Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Extends BasicLocker to share locks among all lockers for the same thread.
 * This locker is used when a JE entry point is called with a null transaction
 * parameter.
 */
public class ThreadLocker extends BasicLocker {

    /**
     * Creates a ThreadLocker.
     */
    private ThreadLocker(EnvironmentImpl env)
        throws DatabaseException {

        super(env);
    }

    public static ThreadLocker createThreadLocker(EnvironmentImpl env)
        throws DatabaseException {

	ThreadLocker ret = null;
	try {
	    ret = new ThreadLocker(env);
	    ret.initApiReadLock();
	} catch (DatabaseException DE) {
	    ret.operationEnd(false);
	    throw DE;
	}
	return ret;
    }

    /**
     * Check that this txn is not used in the wrong thread.
     */
    @Override
    protected void checkState(boolean ignoreCalledByAbort)
        throws DatabaseException {

        if (thread != Thread.currentThread()) {
            throw new DatabaseException
		("Non-transactional Cursors may not be used in multiple " +
                 "threads; Cursor was created in " + thread +
		 " but used in " + Thread.currentThread());
        }
    }

    /**
     * Returns a new non-transactional locker that shares locks with this
     * locker by virtue of being a ThreadLocker for the same thread.
     */
    @Override
    public Locker newNonTxnLocker()
        throws DatabaseException {

        checkState(false);
        return ThreadLocker.createThreadLocker(envImpl);
    }

    /**
     * Returns whether this locker can share locks with the given locker.
     * Locks are shared when both are txns are ThreadLocker instances for the
     * same thread.
     */
    @Override
    public boolean sharesLocksWith(Locker other) {

        if (super.sharesLocksWith(other)) {
            return true;
        } else if (other instanceof ThreadLocker) {
            return thread == ((ThreadLocker) other).thread;
        } else {
            return false;
        }
    }
}
