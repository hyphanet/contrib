/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JEException.java,v 1.5.2.1 2007/02/01 14:49:45 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

public class JEException extends Exception {

    public JEException(String message) {
	super(message);
    }
}
