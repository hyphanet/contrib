/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: DBINReference.java,v 1.14 2006/10/30 21:14:25 bostic Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.dbi.DatabaseId;

/**
 * A class that embodies a reference to a DBIN that does not rely on a
 * java reference to the actual DBIN.
 */
public class DBINReference extends BINReference {
    private byte[] dupKey;

    DBINReference(long nodeId, DatabaseId databaseId,
                  byte[] idKey, byte[] dupKey) {
	super(nodeId, databaseId, idKey);
	this.dupKey = dupKey;
    }

    public byte[] getKey() {
	return dupKey;
    }

    public byte[] getData() {
	return idKey;
    }

    public String toString() {
	return super.toString() + " dupKey=" + Key.dumpString(dupKey, 0);
    }
}

