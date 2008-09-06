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


public final class PrimitiveCodec {
	
	public static final int INT_LENGTH = 4;
	
	public static final int LONG_LENGTH = 8;
	
	
	public static final int readInt(byte[] buffer, int offset){
        offset += 3;
        return (buffer[offset] & 255) | (buffer[--offset] & 255)
            << 8 | (buffer[--offset] & 255)
            << 16 | buffer[--offset]
            << 24;
	}
	
	public static final void writeInt(byte[] buffer, int offset, int val){
        offset += 3;
        buffer[offset] = (byte)val;
        buffer[--offset] = (byte) (val >>= 8);
        buffer[--offset] = (byte) (val >>= 8);
        buffer[--offset] = (byte) (val >> 8);
	}
	
	public static final void writeLong(byte[] buffer, long val){
		writeLong(buffer, 0, val);
	}
	
	public static final void writeLong(byte[] buffer, int offset, long val){
		for (int i = 0; i < LONG_LENGTH; i++){
			buffer[offset++] = (byte) (val >> ((7 - i) * 8));
		}
	}
	
	public static final long readLong(byte[] buffer, int offset){
		long ret = 0;
		for (int i = 0; i < LONG_LENGTH; i++){
			ret = (ret << 8) + (buffer[offset++] & 0xff);
		}
		return ret;
	}

}
