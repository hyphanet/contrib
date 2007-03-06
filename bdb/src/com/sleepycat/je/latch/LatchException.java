/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: LatchException.java,v 1.17 2006/10/30 21:14:19 bostic Exp $
 */

package com.sleepycat.je.latch;

import com.sleepycat.je.DatabaseException;

/**
 * The root of latch related exceptions.
 */

public class LatchException extends DatabaseException {

    public LatchException() {
	super();
    }

    public LatchException(String message) {
	super(message);
    }
}
