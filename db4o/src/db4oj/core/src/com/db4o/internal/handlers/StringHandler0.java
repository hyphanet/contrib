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

import java.io.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.internal.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;


/**
 * @exclude
 */
public class StringHandler0 extends StringHandler {

    public Object read(ReadContext context) {
        ByteArrayBuffer buffer = (ByteArrayBuffer) ((InternalReadContext)context).readIndirectedBuffer();
        if (buffer == null) {
            return null;
        }
        return readString(context, buffer);
    }
    
    public void delete(DeleteContext context){
    	context.defragmentRecommended();
    }
    
    public void defragment(DefragmentContext context) {
    	int sourceAddress = context.sourceBuffer().readInt();
    	int length = context.sourceBuffer().readInt();
    	if(sourceAddress == 0 && length == 0) {
        	context.targetBuffer().writeInt(0);
        	context.targetBuffer().writeInt(0);
        	return;
    	}

    	int targetAddress = 0;
    	try {
			targetAddress = context.copySlotToNewMapped(sourceAddress, length);
		} 
    	catch (IOException exc) {
    		throw new Db4oIOException(exc);
		}
    	context.targetBuffer().writeInt(targetAddress);
    	context.targetBuffer().writeInt(length);
    }
    
    public Object readIndexEntryFromObjectSlot(MarshallerFamily mf, StatefulBuffer buffer) throws CorruptionException, Db4oIOException {
        return buffer.container().readWriterByAddress(buffer.transaction(), buffer.readInt(), buffer.readInt());
    }
    
    public Object readIndexEntry(ObjectIdContext context) throws CorruptionException, Db4oIOException{
        return context.transaction().container().readWriterByAddress(context.transaction(), context.readInt(), context.readInt());
    }

}
