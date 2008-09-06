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
public class IntHandler extends PrimitiveHandler {
    

	private static final Integer DEFAULTVALUE = new Integer(0);
    
    public Object coerce(Reflector reflector, ReflectClass claxx, Object obj) {
    	return Coercion4.toInt(obj);
    }

    public Object defaultValue(){
		return DEFAULTVALUE;
	}
	
    public Class primitiveJavaClass() {
        return int.class;
    }

    public int linkLength() {
        return Const4.INT_LENGTH;
    }

    public Object read(MarshallerFamily mf, StatefulBuffer writer, boolean redirect) throws CorruptionException {
        return mf._primitive.readInteger(writer);
    }


    Object read1(ByteArrayBuffer a_bytes) {
        return new Integer(a_bytes.readInt());
    }    

    public void write(Object obj, ByteArrayBuffer writer) {
        write(((Integer)obj).intValue(), writer);
    }

    public void write(int intValue, ByteArrayBuffer writer) {
        writeInt(intValue, writer);
    }

    public static final void writeInt(int a_int, ByteArrayBuffer a_bytes) {
        if (Deploy.debug) {
            a_bytes.writeBegin(Const4.YAPINTEGER);
            if (Deploy.debugLong) {
                String l_s = "                " + new Integer(a_int).toString();
                new LatinStringIO().write(
                    a_bytes,
                    l_s.substring(l_s.length() - Const4.INTEGER_BYTES));
            } else {
                for (int i = Const4.WRITE_LOOP; i >= 0; i -= 8) {
                    a_bytes._buffer[a_bytes._offset++] = (byte) (a_int >> i);
                }
            }
            a_bytes.writeEnd();
        } else {
            a_bytes.writeInt(a_int);
        }
    }

    public void defragIndexEntry(DefragmentContextImpl context) {
    	context.incrementIntSize();
    }
    
    public Object read(ReadContext context) {
        return new Integer(context.readInt());
    }

    public void write(WriteContext context, Object obj) {
        context.writeInt(((Integer)obj).intValue());
    }

    public PreparedComparison internalPrepareComparison(Object source) {
    	return newPrepareCompare(((Integer)source).intValue());
    }
    
	public PreparedComparison newPrepareCompare(int i) {
		return new PreparedIntComparison(i);
	}
	
    public final class PreparedIntComparison implements PreparedComparison {
    	
		private final int _sourceInt;

		public PreparedIntComparison(int sourceInt) {
			_sourceInt = sourceInt;
		}

		public int compareTo(Object target) {
			if(target == null){
				return 1;
			}
			int targetInt = ((Integer)target).intValue();
			return _sourceInt == targetInt ? 0 : (_sourceInt < targetInt ? - 1 : 1); 
		}
	}
	
}