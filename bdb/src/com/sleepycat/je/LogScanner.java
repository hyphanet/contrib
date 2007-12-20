/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogScanner.java,v 1.1.2.1 2007/09/24 22:54:34 cwl Exp $
 */

package com.sleepycat.je;

public interface LogScanner {
    public boolean scanRecord(DatabaseEntry key,
                              DatabaseEntry data,
                              boolean deleted,
                              String databaseName);
}

