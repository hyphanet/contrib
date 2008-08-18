/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DatabaseException.java,v 1.26 2008/05/29 03:38:23 linda Exp $
 */

package com.sleepycat.je;

/**
 * The root of all database exceptions.
 *
 * Note that in some cases, certain methods return status values without
 * issuing an exception. This occurs in situations that are not normally
 * considered an error, but when some informational status is returned.  For
 * example, {@link com.sleepycat.je.Database#get Database.get} returns {@link
 * com.sleepycat.je.OperationStatus#NOTFOUND OperationStatus.NOTFOUND} when a
 * requested key does not appear in the database.
 */
public class DatabaseException extends Exception {

    public DatabaseException() {
        super();
    }

    public DatabaseException(Throwable t) {
        super(t);
    }

    public DatabaseException(String message) {
        super(getVersionHeader() + message);
    }

    public DatabaseException(String message, Throwable t) {
        super((getVersionHeader() + message), t);
    }

    /* 
     * @hidden 
     * Utility for generating the version at the start of the exception 
     * message. Public for unit tests. 
     */
    public static String getVersionHeader() {
        return "(JE " + JEVersion.CURRENT_VERSION + ") ";
    }
}
