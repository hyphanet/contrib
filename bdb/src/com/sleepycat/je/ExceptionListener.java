/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: ExceptionListener.java,v 1.3 2006/10/30 21:14:12 bostic Exp $
 */

package com.sleepycat.je;

public interface ExceptionListener {
    void exceptionThrown(ExceptionEvent event);
}

