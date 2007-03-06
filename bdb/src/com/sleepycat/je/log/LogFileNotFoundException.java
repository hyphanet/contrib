/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: LogFileNotFoundException.java,v 1.11 2006/10/30 21:14:20 bostic Exp $
 */

package com.sleepycat.je.log;

/**
 * Log file doesn't exist.
 */
public class LogFileNotFoundException extends LogException {

    public LogFileNotFoundException(String message) {
	super(message);
    }
}

