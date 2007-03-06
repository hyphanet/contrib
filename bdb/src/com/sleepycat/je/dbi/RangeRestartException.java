/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: RangeRestartException.java,v 1.5 2006/10/30 21:14:15 bostic Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;

/**
 * Thrown by the LockManager when requesting a RANGE_READ or RANGE_WRITE
 * lock, and a RANGE_INSERT lock is held or is waiting.  This exception is
 * caught by read operations and causes a restart of the operation.  It should
 * never be seen by the user.
 */
public class RangeRestartException extends DatabaseException {

    public RangeRestartException() {
        super();
    }
}
