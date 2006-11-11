/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: JEException.java,v 1.4 2006/09/12 19:16:48 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

public class JEException extends Exception {

    public JEException(String message) {
	super(message);
    }
}
