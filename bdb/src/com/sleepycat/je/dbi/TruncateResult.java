/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: TruncateResult.java,v 1.5 2006/10/30 21:14:15 bostic Exp $
 */

package com.sleepycat.je.dbi;

/**
 * Holds the result of a database truncate operation.
 */
public class TruncateResult {

    private DatabaseImpl db;
    private int count;

    TruncateResult(DatabaseImpl db, int count) {
        this.db = db;
        this.count = count;
    }

    public DatabaseImpl getDatabase() {
        return db;
    }

    public int getRecordCount() {
        return count;
    }
}
