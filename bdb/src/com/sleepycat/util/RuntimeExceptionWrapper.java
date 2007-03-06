/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2006 Oracle.  All rights reserved.
 *
 * $Id: RuntimeExceptionWrapper.java,v 1.14 2006/10/30 21:14:35 bostic Exp $
 */

package com.sleepycat.util;

/**
 * A RuntimeException that can contain nested exceptions.
 *
 * @author Mark Hayes
 */
public class RuntimeExceptionWrapper extends RuntimeException
    implements ExceptionWrapper {

    private Throwable e;

    public RuntimeExceptionWrapper(Throwable e) {

        super(e.getMessage());
        this.e = e;
    }

    /**
     * @deprecated replaced by {@link #getCause}.
     */
    public Throwable getDetail() {

        return e;
    }

    public Throwable getCause() {

        return e;
    }
}
