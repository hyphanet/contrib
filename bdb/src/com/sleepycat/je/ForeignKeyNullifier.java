/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: ForeignKeyNullifier.java,v 1.5 2006/10/30 21:14:12 bostic Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public method is generated via
 * the doc templates in the doc_src directory.
 */
public interface ForeignKeyNullifier {

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean nullifyForeignKey(SecondaryDatabase secondary,
                                     DatabaseEntry data)
	throws DatabaseException;
}
