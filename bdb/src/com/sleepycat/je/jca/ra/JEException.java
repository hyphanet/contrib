/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: JEException.java,v 1.7 2008/01/07 14:28:49 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

public class JEException extends Exception {

    public JEException(String message) {
	super(message);
    }
}
