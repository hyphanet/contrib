/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: IOExceptionWrapper.java,v 1.19 2008/05/20 17:52:37 linda Exp $
 */

package com.sleepycat.util;

import java.io.IOException;

/**
 * An IOException that can contain nested exceptions.
 *
 * @author Mark Hayes
 */
public class IOExceptionWrapper
    extends IOException implements ExceptionWrapper {

    private Throwable e;

    public IOExceptionWrapper(Throwable e) {

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
