/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DatabaseNotFoundException.java,v 1.9 2008/01/07 14:28:46 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Thrown when an operation requires a database and that database does not
 * exist.
 */
public class DatabaseNotFoundException extends DatabaseException {

    public DatabaseNotFoundException() {
	super();
    }

    public DatabaseNotFoundException(Throwable t) {
        super(t);
    }

    public DatabaseNotFoundException(String message) {
	super(message);
    }

    public DatabaseNotFoundException(String message, Throwable t) {
        super(message, t);
    }
}
