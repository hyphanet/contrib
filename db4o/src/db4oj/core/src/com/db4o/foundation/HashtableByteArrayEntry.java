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

class HashtableByteArrayEntry extends HashtableObjectEntry {

	public HashtableByteArrayEntry(byte[] bytes, Object value) {
		super(hash(bytes), bytes, value);
	}

    public HashtableByteArrayEntry(){
        super();
    }
    
    public Object deepClone(Object obj) {
        return deepCloneInternal(new HashtableByteArrayEntry(), obj);
    }

	public boolean hasKey(Object key) {
		if (key instanceof byte[]) {
			return areEqual((byte[]) key(), (byte[]) key);
		}
		return false;
	}

	static int hash(byte[] bytes) {
		int ret = 0;
		for (int i = 0; i < bytes.length; i++) {
			ret = ret * 31 + bytes[i];
		}
		return ret;
	}

	static boolean areEqual(byte[] lhs, byte[] rhs) {
		if (rhs.length != lhs.length) return false;
		for (int i = 0; i < rhs.length; i++) {
			if (rhs[i] != lhs[i]) return false;
		}
		return true;
	}
}