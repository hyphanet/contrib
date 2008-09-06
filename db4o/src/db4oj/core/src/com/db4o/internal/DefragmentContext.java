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

import java.io.*;

import com.db4o.internal.marshall.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;

public interface DefragmentContext extends BufferContext, MarshallingInfo, HandlerVersionContext{
	
	public TypeHandler4 typeHandlerForId(int id);

	public int copyID();

	public int copyIDReturnOriginalID();
	
	public int copySlotlessID();

	public int copyUnindexedID();
	
	public void defragment(TypeHandler4 handler);
	
	public int handlerVersion();

	public void incrementOffset(int length);

	boolean isLegacyHandlerVersion();
	
	public int mappedID(int origID);
	
	public ByteArrayBuffer sourceBuffer();
	
	public ByteArrayBuffer targetBuffer();

	public Slot allocateTargetSlot(int length);

	public Slot allocateMappedTargetSlot(int sourceAddress, int length);

	public int copySlotToNewMapped(int sourceAddress, int length) throws IOException;

	public ByteArrayBuffer sourceBufferByAddress(int sourceAddress, int length) throws IOException;
	
	public ByteArrayBuffer sourceBufferById(int sourceId) throws IOException;
	
	public void targetWriteBytes(int address, ByteArrayBuffer buffer);
}
