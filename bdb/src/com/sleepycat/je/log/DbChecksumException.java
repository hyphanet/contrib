/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: DbChecksumException.java,v 1.18 2006/10/30 21:14:20 bostic Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Configuration related exceptions.
 */
public class DbChecksumException extends RunRecoveryException {

    public DbChecksumException(EnvironmentImpl env, String message) {
	super(env, message);
    }

    public DbChecksumException(EnvironmentImpl env,
                               String message,
                               Throwable t) {
	super(env, message, t);
    }
}

