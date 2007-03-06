/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: INContainingEntry.java,v 1.16 2006/10/30 21:14:21 bostic Exp $ 
 */

package com.sleepycat.je.log.entry;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.IN;

/**
 * An INContainingEntry is a log entry that contains internal nodes.
 */
public interface INContainingEntry {
	
    /**
     * @return the IN held within this log entry.
     */
    public IN getIN(EnvironmentImpl env) 
        throws DatabaseException;
	
    /**
     * @return the database id held within this log entry.
     */
    public DatabaseId getDbId();

    /**
     * @return the LSN that represents this IN.
     */
    public long getLsnOfIN(long lastReadLsn);
}
