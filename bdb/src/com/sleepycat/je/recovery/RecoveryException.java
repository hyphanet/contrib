/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: RecoveryException.java,v 1.19 2008/05/20 17:52:36 linda Exp $
 */

package com.sleepycat.je.recovery;

import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Recovery related exceptions
 */
public class RecoveryException extends RunRecoveryException {

    public RecoveryException(EnvironmentImpl env,
                             String message,
                             Throwable t) {
	super(env, message, t);
    }

    public RecoveryException(EnvironmentImpl env,
                             String message) {
	super(env, message);
    }
}
