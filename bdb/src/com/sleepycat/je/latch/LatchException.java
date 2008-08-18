/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LatchException.java,v 1.20 2008/01/07 14:28:50 cwl Exp $
 */

package com.sleepycat.je.latch;

import com.sleepycat.je.DatabaseException;

/**
 * The root of latch related exceptions.
 */

public class LatchException extends DatabaseException {

    public LatchException(String message) {
	super(message);
    }
}
