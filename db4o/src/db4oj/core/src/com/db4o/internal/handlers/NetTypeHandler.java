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
package com.db4o.internal.handlers;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.reflect.*;


/**
 * @exclude
 */
public abstract class NetTypeHandler extends PrimitiveHandler implements NetType{
    
	private int i_linkLength;
	
    public String dotNetClassName(){
        String className = this.getClass().getName();
        int pos = className.indexOf(".Net") ;
        if(pos >=0){
            return "System." + className.substring(pos + 4) + ", mscorlib";    
        }
        return defaultValue().getClass().getName();
    }
	
    public void registerReflector(Reflector reflector) {
    	super.registerReflector(reflector);

		byte[] bytes = new byte[65];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = 55;  // TODO: Why 55? This is a '7'. Remove.
        }
        write(primitiveNull(), bytes, 0);
		for (int i = 0; i < bytes.length; i++) {
			if(bytes[i] == 55){
				i_linkLength = i;
				break;
			}
		}
	}
	
	public int getID() {
		return typeID();
	}
	
	// This method is needed for NetSimpleTypeHandler only during
    // initalisation and overloaded there. No abstract declaration 
    // here, so we don't have to implement the methods on .NET.
	public String getName() {
		return dotNetClassName();
	}

	public int linkLength() {
		return i_linkLength;
	}

    public Class primitiveJavaClass(){
    	return null;
    }
    
    protected Class javaClass() {
    	return super.javaClass();
    }
    
    public abstract Object read(byte[] bytes, int offset);
    
    Object read1(ByteArrayBuffer a_bytes) throws CorruptionException {
		int offset = a_bytes._offset;
		Object ret = read(a_bytes._buffer, a_bytes._offset);
		a_bytes._offset = offset + linkLength();
		return ret;
	}
    
    public abstract int typeID();
    
    public abstract void write(Object obj, byte[] bytes, int offset);

    public void write(Object a_object, ByteArrayBuffer a_bytes) {
        int offset = a_bytes._offset;
        if(a_object != null){
            write(a_object, a_bytes._buffer, a_bytes._offset);
        }
        a_bytes._offset = offset + linkLength();
    }

	public PreparedComparison internalPrepareComparison(Object obj) {
		throw new NotImplementedException();
	}
}
