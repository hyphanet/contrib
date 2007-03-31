/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DatabaseUtil.java,v 1.1.2.1 2007/02/01 14:49:54 cwl Exp $
 */

package com.sleepycat.je.utilint;

import com.sleepycat.je.DatabaseEntry;

/**
 * Utils for use in the db package.
 */
public class DatabaseUtil {

    /**
     * Throw an exception if the parameter is null.
     */
    static public void checkForNullParam(Object param, String name) {
        if (param == null) {
            throw new NullPointerException(name + " cannot be null");
        }
    }

    /**
     * Throw an exception if the dbt is null or the data field is not set.
     */
    static public void checkForNullDbt(DatabaseEntry dbt,
				String name,
				boolean checkData) {
        if (dbt == null) {
            throw new NullPointerException
		("DatabaseEntry " + name + " cannot be null");
        }

        if (checkData) {
            if (dbt.getData() == null) {
                throw new NullPointerException
		    ("Data field for DatabaseEntry " +
		     name + " cannot be null");
            }
        }
    }

    /**
     * Throw an exception if the key dbt has the partial flag set.  This method
     * should be called for all put() operations.
     */
    static public void checkForPartialKey(DatabaseEntry dbt) {
        if (dbt.getPartial()) {
            throw new IllegalArgumentException
		("A partial key DatabaseEntry is not allowed");
        }
    }
}
