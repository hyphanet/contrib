/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: NoRootException.java,v 1.1 2006/11/27 18:38:26 linda Exp $
 */

package com.sleepycat.je.recovery;

import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Recovery related exceptions
 */
public class NoRootException extends RecoveryException {

    public NoRootException(EnvironmentImpl env,
                           String message) {
	super(env, message);
    }
}
