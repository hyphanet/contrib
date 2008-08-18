/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LatchNotHeldException.java,v 1.18 2008/01/07 14:28:50 cwl Exp $
 */

package com.sleepycat.je.latch;

/**
 * An exception that is thrown when a latch is not held but a method is invoked
 * on it that assumes it is held.
 */
public class LatchNotHeldException extends LatchException {

    public LatchNotHeldException(String message) {
	super(message);
    }
}
