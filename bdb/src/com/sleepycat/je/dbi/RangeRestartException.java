/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: RangeRestartException.java,v 1.8 2008/05/06 18:01:32 linda Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;

/**
 * Thrown by the LockManager when requesting a RANGE_READ or RANGE_WRITE
 * lock, and a RANGE_INSERT lock is held or is waiting.  This exception is
 * caught by read operations and causes a restart of the operation.  It should
 * never be seen by the user.
 */
@SuppressWarnings("serial")
public class RangeRestartException extends DatabaseException {

    public RangeRestartException() {
        super();
    }
}
