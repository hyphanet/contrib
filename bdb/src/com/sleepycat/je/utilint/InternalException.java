/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: InternalException.java,v 1.14.2.2 2007/11/20 13:32:37 cwl Exp $
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
