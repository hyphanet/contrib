/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: ExceptionListener.java,v 1.2 2006/09/12 19:16:42 cwl Exp $
 */

package com.sleepycat.je;

public interface ExceptionListener {
    void exceptionThrown(ExceptionEvent event);
}

