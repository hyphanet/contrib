/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DBINReference.java,v 1.18 2008/05/13 01:44:53 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.dbi.DatabaseId;

/**
 * A class that embodies a reference to a DBIN that does not rely on a Java
 * reference to the actual DBIN.
 */
public class DBINReference extends BINReference {
    private byte[] dupKey;

    DBINReference(long nodeId,
                  DatabaseId databaseId,
                  byte[] idKey,
                  byte[] dupKey) {
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
