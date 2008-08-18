/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: APILockedException.java,v 1.5 2008/05/20 17:52:34 linda Exp $
 */

package com.sleepycat.je;

/**
 * @hidden
 * An APILockedException is thrown when a replicated environment
 * does not permit application level operations.
 */
public class APILockedException extends DatabaseException {

    public APILockedException() {
	super();
    }

    public APILockedException(Throwable t) {
        super(t);
    }

    public APILockedException(String message) {
	super(message);
    }

    public APILockedException(String message, Throwable t) {
        super(message, t);
    }
}
