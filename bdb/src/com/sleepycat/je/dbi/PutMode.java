/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: PutMode.java,v 1.9 2008/01/07 14:28:48 cwl Exp $
 */

package com.sleepycat.je.dbi;

/**
 * Internal class used to distinguish which variety of putXXX() that
 * Cursor.putInternal() should use.
 */
public class PutMode {
    private String name;

    private PutMode(String name) {
	this.name = name;
    }

    public static final PutMode NODUP =       new PutMode("NODUP");
    public static final PutMode CURRENT =     new PutMode("CURRENT");
    public static final PutMode OVERWRITE =   new PutMode("OVERWRITE");
    public static final PutMode NOOVERWRITE = new PutMode("NOOVERWRITE");

    @Override
    public String toString() {
	return name;
    }
}
