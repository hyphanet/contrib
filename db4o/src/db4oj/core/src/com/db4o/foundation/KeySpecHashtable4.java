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

/**
 * @exclude
 */
public class KeySpecHashtable4 {
    
    private SynchronizedHashtable4 _delegate;
    
	private KeySpecHashtable4(SynchronizedHashtable4 delegate_) {
		_delegate = delegate_;
	}
	
	public KeySpecHashtable4(int size) {
	    this(new SynchronizedHashtable4(size));
	}
	
    public void put(KeySpec spec,byte value) {
    	_delegate.put(spec,new Byte(value));
    }

    public void put(KeySpec spec,boolean value) {
    	_delegate.put(spec,new Boolean(value));
    }

    public void put(KeySpec spec,int value) {
    	_delegate.put(spec,new Integer(value));
    }

    public void put(KeySpec spec, Object value) {
    	_delegate.put(spec,value);
    }

    public byte getAsByte(KeySpec spec) {
    	return ((Byte)get(spec)).byteValue();
    }

    public boolean getAsBoolean(KeySpec spec) {
    	return ((Boolean)get(spec)).booleanValue();
    }

    public int getAsInt(KeySpec spec) {
    	return ((Integer)get(spec)).intValue();
    }

    public TernaryBool getAsTernaryBool(KeySpec spec) {
    	return (TernaryBool)get(spec);
    }

    public String getAsString(KeySpec spec) {
    	return (String)get(spec);
    }

    public synchronized Object get(KeySpec spec) {
        Object value=_delegate.get(spec);
        if(value == null){
            value = spec.defaultValue();
            if(value != null){
                _delegate.put(spec, value);
            }
        }
        return value;
    }
    
    public Object deepClone(Object obj) {
    	return new KeySpecHashtable4((SynchronizedHashtable4) _delegate.deepClone(obj));
    }
}
