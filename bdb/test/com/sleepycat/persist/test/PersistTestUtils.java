/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2006 Oracle.  All rights reserved.
 *
 * $Id: PersistTestUtils.java,v 1.1 2006/11/16 04:18:21 mark Exp $
 */
package com.sleepycat.persist.test;

import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;

class PersistTestUtils {

    /**
     * Asserts than a database expectExists or does not exist. If keyName is
     * null, checks an entity database.  If keyName is non-null, checks a
     * secondary database.
     */
    static void assertDbExists(boolean expectExists,
                               Environment env,
                               String storeName,
                               String entityClassName,
                               String keyName) {
        String dbName = "persist#" + storeName + '#' + entityClassName;
        if (keyName != null) {
            dbName += "#" + keyName;
        }
        List allDbNames;
        try {
            allDbNames = env.getDatabaseNames();
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        if (expectExists != allDbNames.contains(dbName)) {
            TestCase.fail
                ((expectExists ? "Does not exist: " : "Does exist: ") +
                 dbName);
        }
    }
}
