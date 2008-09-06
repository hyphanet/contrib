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
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;
import com.db4o.reflect.*;



/**
 * @exclude
 */
public class LongHandler extends PrimitiveHandler {

    private static final Long DEFAULTVALUE = new Long(0);

    public Object coerce(Reflector reflector, ReflectClass claxx, Object obj) {
    	return Coercion4.toLong(obj);
    }
    
    public Object defaultValue(){
		return DEFAULTVALUE;
	}
	
    public Class primitiveJavaClass(){
		return long.class;
	}
	
	public int linkLength(){
		return Const4.LONG_LENGTH;
	}
	
	public Object read(MarshallerFamily mf, StatefulBuffer buffer,
			boolean redirect) throws CorruptionException {
		return mf._primitive.readLong(buffer);
	}
	
	Object read1(ByteArrayBuffer a_bytes){
		return new Long(a_bytes.readLong());
	}
	
	public void write(Object obj, ByteArrayBuffer buffer){
	    writeLong(buffer, ((Long)obj).longValue());
	}
	
	public static final void writeLong(WriteBuffer buffer, long val){
		if(Deploy.debug){
		    Debug.writeBegin(buffer, Const4.YAPLONG);
		}
		if(Deploy.debug && Deploy.debugLong){
			String l_s = "                                " + val;
			new LatinStringIO().write(buffer, l_s.substring(l_s.length() - Const4.LONG_BYTES));
		}else{
			for (int i = 0; i < Const4.LONG_BYTES; i++){
			    buffer.writeByte((byte) (val >> ((Const4.LONG_BYTES - 1 - i) * 8)));
			}
		}
		if(Deploy.debug){
		    Debug.writeEnd(buffer);
		}
	}
	
	public static final long readLong(ReadBuffer buffer){
        long ret = 0;
        if (Deploy.debug){
            Debug.readBegin(buffer, Const4.YAPLONG);
        }
        if(Deploy.debug && Deploy.debugLong){
            ret = Long.parseLong(new LatinStringIO().read(buffer, Const4.LONG_BYTES).trim()); 
        }else{
            for (int i = 0; i < Const4.LONG_BYTES; i++){
                ret = (ret << 8) + (buffer.readByte() & 0xff);
            }
        }
        if (Deploy.debug){
            Debug.readEnd(buffer);
        }
        
        return ret;
	}
	
    public Object read(ReadContext context) {
        return new Long(context.readLong());
    }

    public void write(WriteContext context, Object obj) {
        context.writeLong(((Long) obj).longValue());
    }
    
    public PreparedComparison internalPrepareComparison(Object source) {
    	final long sourceLong = ((Long)source).longValue();
    	return new PreparedComparison() {
			public int compareTo(Object target) {
				if(target == null){
					return 1;
				}
				long targetLong = ((Long)target).longValue();
				return sourceLong == targetLong ? 0 : (sourceLong < targetLong ? - 1 : 1); 
			}
		};
    }

}
