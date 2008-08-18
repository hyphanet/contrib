/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogException.java,v 1.16 2008/01/07 14:28:51 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.DatabaseException;

/**
 * Configuration related exceptions.
 */
public class LogException extends DatabaseException {
    public LogException(String message) {
	super(message);
    }

    public LogException(String message, Exception e) {
	super(message, e);
    }
}

