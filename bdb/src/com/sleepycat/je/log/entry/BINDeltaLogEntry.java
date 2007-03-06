/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: BINDeltaLogEntry.java,v 1.22 2006/10/30 21:14:21 bostic Exp $ 
 */

package com.sleepycat.je.log.entry;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.BINDelta;
import com.sleepycat.je.tree.IN;

/**
 * A BINDeltaLogEntry knows how to create a whole BIN from a delta entry.
 */
public class BINDeltaLogEntry extends SingleItemLogEntry
    implements INContainingEntry {

    /**
     * @param logClass
     */
    public BINDeltaLogEntry(Class logClass) {
        super(logClass);
    }

    /* 
     * @see com.sleepycat.je.log.entry.INContainingEntry#getIN()
     */
    public IN getIN(EnvironmentImpl env) 
    	throws DatabaseException {

        BINDelta delta = (BINDelta) getMainItem();
        return delta.reconstituteBIN(env);
    }

    /*
     * @see com.sleepycat.je.log.entry.INContainingEntry#getDbId()
     */
    public DatabaseId getDbId() {

        BINDelta delta = (BINDelta) getMainItem();
        return delta.getDbId();	
    }

    /**
     * @return the LSN that represents this IN. For this BINDelta, it's
     * the last full version.
     */
    public long getLsnOfIN(long lastReadLsn) {

        BINDelta delta = (BINDelta) getMainItem();
        return delta.getLastFullLsn();
    }
}
