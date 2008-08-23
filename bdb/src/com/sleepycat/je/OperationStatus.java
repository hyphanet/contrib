/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: OperationStatus.java,v 1.15 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Status values from database operations.
 */
public class OperationStatus {

    /**
     * The operation was successful.
     */
    public static final OperationStatus SUCCESS =
	new OperationStatus("SUCCESS");

    /**
     * The operation to insert data was configured to not allow overwrite and
     * the key already exists in the database.
     */
    public static final OperationStatus KEYEXIST =
	new OperationStatus("KEYEXIST");

    /**
     * The cursor operation was unsuccessful because the current record was
     * deleted.
     */
    public static final OperationStatus KEYEMPTY =
	new OperationStatus("KEYEMPTY");

    /**
     * The requested key/data pair was not found.
     */
    public static final OperationStatus NOTFOUND =
	new OperationStatus("NOTFOUND");

    /* For toString. */
    private String statusName;

    private OperationStatus(String statusName) {
	this.statusName = statusName;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	return "OperationStatus." + statusName;
    }
}
