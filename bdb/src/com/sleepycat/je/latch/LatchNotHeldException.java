/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: LatchNotHeldException.java,v 1.15 2006/10/30 21:14:19 bostic Exp $
 */

package com.sleepycat.je.latch;

/**
 * An exception that is thrown when a latch is not held but a method is invoked
 * on it that assumes it is held.
 */
public class LatchNotHeldException extends LatchException {
    public LatchNotHeldException() {
	super();
    }

    public LatchNotHeldException(String message) {
	super(message);
    }
}
