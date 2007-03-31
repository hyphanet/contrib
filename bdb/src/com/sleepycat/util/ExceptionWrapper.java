/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: ExceptionWrapper.java,v 1.16.2.1 2007/02/01 14:49:58 cwl Exp $
 */

package com.sleepycat.util;

/**
 * Interface implemented by exceptions that can contain nested exceptions.
 *
 * @author Mark Hayes
 */
public interface ExceptionWrapper {

    /**
     * Returns the nested exception or null if none is present.
     *
     * @return the nested exception or null if none is present.
     *
     * @deprecated replaced by {@link #getCause}.
     */
    Throwable getDetail();

    /**
     * Returns the nested exception or null if none is present.
     *
     * <p>This method is intentionally defined to be the same signature as the
     * <code>java.lang.Throwable.getCause</code> method in Java 1.4 and
     * greater.  By defining this method to return a nested exception, the Java
     * 1.4 runtime will print the nested stack trace.</p>
     *
     * @return the nested exception or null if none is present.
     */
    Throwable getCause();
}
