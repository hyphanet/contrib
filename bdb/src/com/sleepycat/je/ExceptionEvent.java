/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ExceptionEvent.java,v 1.4.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

public class ExceptionEvent {
    private Exception exception;
    private String threadName;

    public ExceptionEvent(Exception exception, String threadName) {
	this.exception = exception;
	this.threadName = threadName;
    }

    public ExceptionEvent(Exception exception) {
	this.exception = exception;
	this.threadName = Thread.currentThread().toString();
    }

    public Exception getException() {
	return exception;
    }

    public String getThreadName() {
	return threadName;
    }
}

