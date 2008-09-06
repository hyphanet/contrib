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
package com.db4o.internal;

import com.db4o.marshall.*;


/**
 * @exclude
 */
public final class UnicodeStringIO extends LatinStringIO{
	
    public int bytesPerChar(){
        return 2;
    }
    
    public byte encodingByte(){
		return Const4.UNICODE;
	}
	
	public int length(String str){
		return (str.length() * 2) + Const4.OBJECT_LENGTH + Const4.INT_LENGTH;
	}
	
	public String read(ReadBuffer buffer, int length){
	    char[] chars = new char[length];
		for(int ii = 0; ii < length; ii++){
			chars[ii] = (char)((buffer.readByte() & 0xff) | ((buffer.readByte() & 0xff) << 8));
		}
		return new String(chars, 0, length);
	}
	
	public String read(byte[] bytes){
	    int length = bytes.length / 2;
	    char[] chars = new char[length];
	    int j = 0;
	    for(int ii = 0; ii < length; ii++){
	        chars[ii] = (char)((bytes[j++]& 0xff) | ((bytes[j++]& 0xff) << 8));
	    }
	    return new String(chars,0,length);
	}
	
	public int shortLength(String str){
		return (str.length() * 2)  + Const4.INT_LENGTH;
	}
	
	public void write(WriteBuffer buffer, String str){
	    final int length = str.length();
	    char[] chars = new char[length];
	    str.getChars(0, length, chars, 0);
	    for (int i = 0; i < length; i ++){
	        buffer.writeByte((byte) (chars[i] & 0xff));
	        buffer.writeByte((byte) (chars[i] >> 8));
		}
	}
	
	public byte[] write(String str){
	    final int length = str.length();
	    char[] chars = new char[length];
	    str.getChars(0, length, chars, 0);
	    byte[] bytes = new byte[length * 2];
	    int j = 0;
	    for (int i = 0; i < length; i ++){
	        bytes[j++] = (byte) (chars[i] & 0xff);
	        bytes[j++] = (byte) (chars[i] >> 8);
	    }
	    return bytes;
	}
	
}
