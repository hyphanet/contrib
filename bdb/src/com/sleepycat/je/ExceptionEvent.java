/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: ExceptionEvent.java,v 1.3 2006/10/30 21:14:12 bostic Exp $
 */

package com.sleepycat.je;

public class ExceptionEvent {
    private Exception exception;
    private String threadName;

    ExceptionEvent(Exception exception, String threadName) {
	this.exception = exception;
	this.threadName = threadName;
    }

    public Exception getException() {
	return exception;
    }

    public String getThreadName() {
	return threadName;
    }
}

