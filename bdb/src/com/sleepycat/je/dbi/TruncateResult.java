/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TruncateResult.java,v 1.7 2008/01/07 14:28:48 cwl Exp $
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
