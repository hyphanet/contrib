/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: ExceptionEvent.java,v 1.2 2006/09/12 19:16:42 cwl Exp $
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

