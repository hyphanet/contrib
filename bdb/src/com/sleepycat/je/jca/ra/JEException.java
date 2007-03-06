/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: JEException.java,v 1.5 2006/10/30 21:14:18 bostic Exp $
 */

package com.sleepycat.je.jca.ra;

public class JEException extends Exception {

    public JEException(String message) {
	super(message);
    }
}
