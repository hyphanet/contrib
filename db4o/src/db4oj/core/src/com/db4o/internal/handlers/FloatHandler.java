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



public class FloatHandler extends IntHandler {
    
    private static final Float DEFAULTVALUE = new Float(0);
    
    public Object coerce(Reflector reflector, ReflectClass claxx, Object obj) {
    	return Coercion4.toFloat(obj);
    }

	public Object defaultValue(){
		return DEFAULTVALUE;
	}
	
	public Class primitiveJavaClass() {
		return float.class;
	}

	public Object read(MarshallerFamily mf, StatefulBuffer writer, boolean redirect) throws CorruptionException {
    	return mf._primitive.readFloat(writer);
    }

	Object read1(ByteArrayBuffer a_bytes) {
		return primitiveMarshaller().readFloat(a_bytes);
	}

	public void write(Object a_object, ByteArrayBuffer a_bytes) {
		writeInt(
			Float.floatToIntBits(((Float) a_object).floatValue()),
			a_bytes);
	}

    public Object read(ReadContext context) {
        return new Float(Float.intBitsToFloat(context.readInt()));
    }

    public void write(WriteContext context, Object obj) {
        context.writeInt(Float.floatToIntBits(((Float)obj).floatValue()));
    }
    
    public PreparedComparison internalPrepareComparison(Object source) {
    	final float sourceFloat = ((Float)source).floatValue();
    	return new PreparedComparison() {
			public int compareTo(Object target) {
				if(target == null){
					return 1;
				}
				float targetFloat = ((Float)target).floatValue();
				return sourceFloat == targetFloat ? 0 : (sourceFloat < targetFloat ? - 1 : 1); 
			}
		};
    }

}
