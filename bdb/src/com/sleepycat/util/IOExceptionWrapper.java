/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: IOExceptionWrapper.java,v 1.20 2008/06/10 02:52:17 cwl Exp $
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

    @Override
    public Throwable getCause() {

        return e;
    }
}
