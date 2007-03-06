/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: DbConfigException.java,v 1.14 2006/10/30 21:14:15 bostic Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;

/**
 * Configuration related exceptions.
 */
public class DbConfigException extends DatabaseException {

    public DbConfigException(Throwable t) {
        super(t);
    }

    public DbConfigException(String message) {
	super(message);
    }

    public DbConfigException(String message, Throwable t) {
        super(message, t);
    }
}
