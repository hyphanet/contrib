/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: NotImplementedYetException.java,v 1.16 2006/10/30 21:14:29 bostic Exp $
 */

package com.sleepycat.je.utilint;

/**
 * Something is not yet implemented.
 */
public class NotImplementedYetException extends RuntimeException {

    public NotImplementedYetException() {
	super();
    }

    public NotImplementedYetException(String message) {
	super(message);
    }
}
