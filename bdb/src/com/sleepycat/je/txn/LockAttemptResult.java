/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LockAttemptResult.java,v 1.2 2008/01/07 14:28:56 cwl Exp $
 */

package com.sleepycat.je.txn;

/**
 * This is just a struct to hold a multi-value return.
 */
class LockAttemptResult {
    boolean success;
    Lock useLock;
    LockGrantType lockGrant;

    LockAttemptResult(Lock useLock,
		      LockGrantType lockGrant,
		      boolean success) {

	this.useLock = useLock;
	this.lockGrant = lockGrant;
	this.success = success;
    }
}
