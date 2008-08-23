/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentLockedException.java,v 1.1.2.1 2008/07/24 07:26:33 tao Exp $
 */

package com.sleepycat.je;

/**
 * Thrown by the Environment constructor when an environment cannot be
 * opened for write access because another process has the same environment
 * open for write access.
 */
public class EnvironmentLockedException extends DatabaseException {

    private static final long serialVersionUID = 629594964L;

    public EnvironmentLockedException() {
        super();
    }

    public EnvironmentLockedException(Throwable t) {
        super(t);
    }

    public EnvironmentLockedException(String message) {
        super(message);
    }

    public EnvironmentLockedException(String message, Throwable t) {
        super(message, t);
    }
}
