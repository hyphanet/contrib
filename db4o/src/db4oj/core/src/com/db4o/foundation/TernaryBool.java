/* Copyright (C) 2004 - 2008  db4objects Inc.  http://www.db4o.com

This file is part of the db4o open source object database.

db4o is free software; you can redistribute it and/or modify it under
the terms of version 2 of the GNU General Public License as published
by the Free Software Foundation and as clarified by db4objects' GPL 
interpretation policy, available at
http://www.db4o.com/about/company/legalpolicies/gplinterpretation/
Alternatively you can write to db4objects, Inc., 1900 S Norfolk Street,
Suite 350, San Mateo, CA 94403, USA.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. */
package com.db4o.foundation;

import java.io.*;

/**
 * yes/no/dontknow data type
 * 
 * @exclude
 */
public final class TernaryBool implements Serializable {

	private static final int NO_ID = -1;
	private static final int YES_ID = 1;
	private static final int UNSPECIFIED_ID = 0;

	public static final TernaryBool NO = new TernaryBool(NO_ID);
	public static final TernaryBool YES = new TernaryBool(YES_ID);
	public static final TernaryBool UNSPECIFIED = new TernaryBool(UNSPECIFIED_ID);

	private final int _value;
	
	private TernaryBool(int value) {
		_value=value;
	}

	public boolean booleanValue(boolean defaultValue) {
		switch(_value) {
			case NO_ID:
				return false;
			case YES_ID: 
				return true;
			default:
				return defaultValue;
		}
	}
	
	public boolean isUnspecified() {
		return this==UNSPECIFIED;
	}

	public boolean definiteYes() {
		return this==YES;
	}

	public boolean definiteNo() {
		return this==NO;
	}

	public static TernaryBool forBoolean(boolean value) {
		return (value ? YES : NO);
	}
	
	public boolean equals(Object obj) {
		if(this==obj) {
			return true;
		}
		if(obj==null||getClass()!=obj.getClass()) {
			return false;
		}
		TernaryBool tb=(TernaryBool)obj;
		return _value==tb._value;
	}
	
	public int hashCode() {
		return _value;
	}
	
	private Object readResolve() {
		switch(_value) {
			case NO_ID:
				return NO;
			case YES_ID:
				return YES;
			default:
				return UNSPECIFIED;
		}
	}
	
	public String toString() {
		switch(_value) {
		case NO_ID:
			return "NO";
		case YES_ID:
			return "YES";
		default:
			return "UNSPECIFIED";
	}
	}
}
