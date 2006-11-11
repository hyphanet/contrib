/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DatabaseNotFoundException.java,v 1.5 2006/09/12 19:16:42 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
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
