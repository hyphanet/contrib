/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: PutMode.java,v 1.5 2006/09/12 19:16:46 cwl Exp $
 */

package com.sleepycat.je.dbi;

/**
 * Internal class used to distinguish which variety of putXXX() that
 * Cursor.putInternal() should use.
 */
public class PutMode {
    public static final PutMode NODUP =       new PutMode();
    public static final PutMode CURRENT =     new PutMode();
    public static final PutMode OVERWRITE =   new PutMode();
    public static final PutMode NOOVERWRITE = new PutMode();
}
