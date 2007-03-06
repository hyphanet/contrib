/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: InternalException.java,v 1.14 2006/10/30 21:14:29 bostic Exp $
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
