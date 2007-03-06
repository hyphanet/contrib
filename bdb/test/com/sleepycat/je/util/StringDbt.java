/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: StringDbt.java,v 1.12 2006/10/30 21:14:54 bostic Exp $
 */

package com.sleepycat.je.util;

import com.sleepycat.je.DatabaseEntry;

public class StringDbt extends DatabaseEntry {
    public StringDbt() {
    }

    public StringDbt(String value) {
	setString(value);
    }

    public StringDbt(byte[] value) {
	setData(value);
    }

    public void setString(String value) {
	byte[] data = value.getBytes();
	setData(data);
    }

    public String getString() {
	return new String(getData(), 0, getSize());
    }
    
    public String toString() {
        return getString();
    }
}

