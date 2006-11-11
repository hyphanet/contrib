/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: LogFileNotFoundException.java,v 1.10 2006/09/12 19:16:52 cwl Exp $
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

