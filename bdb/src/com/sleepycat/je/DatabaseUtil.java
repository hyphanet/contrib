/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DatabaseUtil.java,v 1.36 2008/01/07 14:28:46 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Utils for use in the db package.
 */
class DatabaseUtil {

    /**
     * Throws an exception if the parameter is null.
     */
    static void checkForNullParam(Object param, String name) {
        if (param == null) {
            throw new NullPointerException(name + " cannot be null");
        }
    }

    /**
     * Throws an exception if the dbt is null or the data field is not set.
     */
    static void checkForNullDbt(DatabaseEntry dbt,
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
     * Throws an exception if the key dbt has the partial flag set.  This
     * method should be called for all put() operations.
     */
    static void checkForPartialKey(DatabaseEntry dbt) {
        if (dbt.getPartial()) {
            throw new IllegalArgumentException
		("A partial key DatabaseEntry is not allowed");
        }
    }
}
