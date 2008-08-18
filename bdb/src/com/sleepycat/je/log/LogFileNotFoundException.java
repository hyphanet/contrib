/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogFileNotFoundException.java,v 1.13 2008/01/07 14:28:51 cwl Exp $
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

