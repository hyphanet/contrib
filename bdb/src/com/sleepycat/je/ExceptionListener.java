/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ExceptionListener.java,v 1.3.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

public interface ExceptionListener {
    void exceptionThrown(ExceptionEvent event);
}

