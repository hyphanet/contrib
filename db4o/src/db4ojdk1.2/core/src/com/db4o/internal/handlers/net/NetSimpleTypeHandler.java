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
package com.db4o.internal.handlers.net;

import com.db4o.internal.handlers.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;

/**
 * @exclude
 * @sharpen.ignore
 * @decaf.ignore.jdk11
 */
public abstract class NetSimpleTypeHandler extends NetTypeHandler implements GenericConverter{
	
	private final String _name;
	private final int _typeID;
	private final int _byteCount;
	
	public NetSimpleTypeHandler(Reflector reflector, int typeID, int byteCount) {
        super();
        _name = dotNetClassName();
        _typeID = typeID;
        _byteCount = byteCount;
        _classReflector = reflector.forName(_name);
    }
	
    public ReflectClass classReflector(){
    	return _classReflector;  
    }
	
	public Object defaultValue() {
		return new byte[_byteCount];
	}

	public Object primitiveNull() {
		return defaultValue();
	}

	public String getName() {
		return _name;
	}
	
	public int typeID() {
		return _typeID;
	}
	
	public void write(Object obj, byte[] bytes, int offset) {
		byte[] objBytes = bytesFor(obj);
		System.arraycopy(objBytes, 0, bytes, offset, objBytes.length);
	}

	public Object read(byte[] bytes, int offset) {
		byte[] ret = new byte[_byteCount];
		System.arraycopy(bytes, offset, ret, 0, ret.length);
		GenericObject go = new GenericObject((GenericClass)classReflector());
		go.set(0, ret);
		return go;
	}
	
	GenericObject genericObject(Object obj) {
		if(obj != null) {
			return (GenericObject)obj;	
		}
		GenericObject go = new GenericObject((GenericClass)classReflector()); 
		go.set(0, defaultValue());
		return go;
	}
	
	byte[] genericObjectBytes(Object obj) {
		GenericObject go = genericObject(obj);
		return (byte[])go.get(0);
	}
	
	byte[] bytesFor(Object obj) {
		if(obj instanceof byte[]) {
			return (byte[])obj;
		}
		return genericObjectBytes(obj);
	}
	
	public String toString(GenericObject obj) {
		return toString((byte[])obj.get(0));
	}
	
    /** @param bytes */
	public String toString(byte[] bytes) {
		return ""; //$NON-NLS-1$
	}
	
	
}
