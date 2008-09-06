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

import java.util.*;

import com.db4o.*;
import com.db4o.internal.*;


public class PrimitiveMarshaller0 extends PrimitiveMarshaller {
    
    public boolean useNormalClassRead(){
        return true;
    }
    
    public Date readDate(ByteArrayBuffer bytes) {
		final long value = bytes.readLong();
		if (value == Long.MAX_VALUE) {
			return MarshallingConstants0.NULL_DATE;
		}
		return new Date(value);
	}
    
    public Object readInteger(ByteArrayBuffer bytes) {
		final int value = bytes.readInt();
		if (value == Integer.MAX_VALUE) {
			return null;
		}
		return new Integer(value);
	}

	public Object readFloat(ByteArrayBuffer bytes) {
		Float value = unmarshallFloat(bytes);
		if (value.isNaN()) {
			return null;
		}
		return value;
	}
	
	public Object readDouble(ByteArrayBuffer buffer) {
		Double value = unmarshalDouble(buffer);
		if (value.isNaN()) {
			return null;
		}
		return value;
	}	

	public Object readLong(ByteArrayBuffer buffer) {
		long value = buffer.readLong();
		if (value == Long.MAX_VALUE) {
			return null;
		}
		return new Long(value);
	}
	
	public Object readShort(ByteArrayBuffer buffer) {
		short value = unmarshallShort(buffer);
		if (value == Short.MAX_VALUE) {
			return null;
		}
		return new Short(value);
	}
	
	public static Double unmarshalDouble(ByteArrayBuffer buffer) {
		return new Double(Platform4.longToDouble(buffer.readLong()));
	}

	public static Float unmarshallFloat(ByteArrayBuffer buffer) {
		return new Float(Float.intBitsToFloat(buffer.readInt()));
	}	
	
	public static short unmarshallShort(ByteArrayBuffer buffer){
		int ret = 0;
		if (Deploy.debug){
			buffer.readBegin(Const4.YAPSHORT);
		}
		for (int i = 0; i < Const4.SHORT_BYTES; i++){
			ret = (ret << 8) + (buffer._buffer[buffer._offset++] & 0xff);
		}
		if (Deploy.debug){
			buffer.readEnd();
		}
		return (short)ret;
	}
}
