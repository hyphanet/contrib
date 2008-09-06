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
package com.db4o.internal.slots;

import com.db4o.internal.*;

/**
 * @exclude
 */
public class Slot {
    
    private final int _address;
    
    private final int _length;
    
    public static final Slot ZERO = new Slot(0, 0);

    public Slot(int address, int length){
        _address = address;
        _length = length;
    }
    
    public int address() {
        return _address;
    }

	public int length() {
		return _length;
	}

    public boolean equals(Object obj) {
        if(obj == this){
            return true;
        }
        if(! (obj instanceof Slot)){
            return false;
        }
        Slot other = (Slot) obj;
        return (_address == other._address) && (length() == other.length());
    }
    
    public int hashCode() {
        return _address ^ length();
    }
    
	public Slot subSlot(int offset) {
		return new Slot(_address + offset, length() - offset);
	}

    public String toString() {
    	return "[A:"+_address+",L:"+length()+"]";
    }
    
	public Slot truncate(int requiredLength) {
		return new Slot(_address, requiredLength);
	}
    
    public static int MARSHALLED_LENGTH = Const4.INT_LENGTH * 2;

	public int compareByAddress(Slot slot) {
		
		// FIXME: This is the wrong way around !!!
		// Fix here and in all referers.
		
        int res = slot._address - _address;
        if(res != 0){
            return res;
        }
        return slot.length() - length();
	}
	
	public int compareByLength(Slot slot) {
		
		// FIXME: This is the wrong way around !!!
		// Fix here and in all referers.
		
		int res = slot.length() - length();
		if(res != 0){
			return res;
		}
		return slot._address - _address;
	}

	public boolean isDirectlyPreceding(Slot other) {
		return _address + length() == other._address;
	}

	public Slot append(Slot slot) {
		return new Slot(address(), _length + slot.length());
	}
	
}
