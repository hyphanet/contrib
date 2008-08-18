/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: InternalException.java,v 1.19 2008/05/20 17:52:37 linda Exp $
 */

package com.sleepycat.je.utilint;

import com.sleepycat.je.DatabaseException;

/**
 * Some internal inconsistency exception.
 */
public class InternalException extends DatabaseException {

    public InternalException() {
	super();
    }

    public InternalException(String message) {
	super(message);
    }
}
