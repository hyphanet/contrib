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
package com.db4o.internal.marshall;

import com.db4o.foundation.*;

public class RawFieldSpec {
    private final AspectType _type;
	private final String _name;
	private final int _handlerID;
	private final boolean _isPrimitive;
	private final boolean _isArray;
	private final boolean _isNArray;
	private final boolean _isVirtual;
	private int _indexID;

	public RawFieldSpec(AspectType aspectType, final String name, final int handlerID, final byte attribs) {
        _type = aspectType;
        _name = name;
		_handlerID = handlerID;
		BitMap4 bitmap = new BitMap4(attribs);
        _isPrimitive = bitmap.isTrue(0);
        _isArray = bitmap.isTrue(1);
        _isNArray = bitmap.isTrue(2);
        _isVirtual=false;
        _indexID=0;
	}

	public RawFieldSpec(AspectType aspectType, final String name) {
	    _type = aspectType;
		_name = name;
		_handlerID = 0;
        _isPrimitive = false;
        _isArray = false;
        _isNArray = false;
        _isVirtual=true;
        _indexID=0;
	}

	public String name() {
		return _name;
	}
	
	public int handlerID() {
		return _handlerID;
	}
	
	public boolean isPrimitive() {
		return _isPrimitive;
	}

	public boolean isArray() {
		return _isArray;
	}

	public boolean isNArray() {
		return _isNArray;
	}
	
	public boolean isVirtual() {
		return _isVirtual;
	}
	
	public int indexID() {
		return _indexID;
	}
	
	void indexID(int indexID) {
		_indexID=indexID;
	}
	
	public String toString() {
		return "RawFieldSpec(" + name() + ")"; 
	}

    public boolean isFieldMetadata() {
        return _type.isFieldMetadata();
    }
}
