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

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;

/**
 * @exclude
 */
public abstract class AbstractBufferContext implements BufferContext, HandlerVersionContext{
	
	private ReadBuffer _buffer;
	
	private final Transaction _transaction;
	
	public AbstractBufferContext(Transaction transaction, ReadBuffer buffer) {
		_transaction = transaction;
        _buffer = buffer;
	}

	public ReadBuffer buffer(ReadBuffer buffer) {
	    ReadBuffer temp = _buffer;
	    _buffer = buffer;
	    return temp;
	}

	public ReadBuffer buffer() {
	    return _buffer;
	}

	public byte readByte() {
	    return _buffer.readByte();
	}

	public void readBytes(byte[] bytes) {
	    _buffer.readBytes(bytes);
	}

	public int readInt() {
	    return _buffer.readInt();
	}

	public long readLong() {
	    return _buffer.readLong();
	}

	public int offset() {
	    return _buffer.offset();
	}

	public void seek(int offset) {
	    _buffer.seek(offset);
	}

	public ObjectContainerBase container() {
	    return _transaction.container();
	}

	public ObjectContainer objectContainer() {
	    return container();
	}

	public Transaction transaction() {
	    return _transaction;
	}

	public abstract int handlerVersion();
	
	public boolean isLegacyHandlerVersion() {
		return handlerVersion() == 0;
	}
	
    public BitMap4 readBitMap(int bitCount){
        return _buffer.readBitMap(bitCount);
    }
    
    public void seekCurrentInt() {
        seek(readInt());
    }
    
	public SlotFormat slotFormat() {
		return SlotFormat.forHandlerVersion(handlerVersion());
	}

}
