/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: InternalException.java,v 1.13 2006/09/12 19:17:00 cwl Exp $
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
