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
package com.db4o.internal.cs;

import com.db4o.foundation.*;
import com.db4o.internal.*;


/**
 * @exclude
 */
public class LazyClientIdIterator implements IntIterator4{
	
	private final LazyClientQueryResult _queryResult;
	
	private int _current;
	
	private int[] _ids;
	
	private final int _batchSize;
	
	private int _available;
	
	public LazyClientIdIterator(LazyClientQueryResult queryResult){
		_queryResult = queryResult;
		_batchSize = queryResult.config().prefetchObjectCount();
		_ids = new int[_batchSize];
		_current = -1;
	}

	public int currentInt() {
		if(_current < 0){
			throw new IllegalStateException();
		}
		return _ids[_current];
	}

	public Object current() {
		return new Integer(currentInt());
	}

	public boolean moveNext() {
		if(_available < 0){
			return false;
		}
		if(_available == 0){
			_queryResult.fetchIDs(_batchSize);
			_available --;
			_current = 0;
			return (_available > 0);
		}
		_current++;
		_available --;
		return true;
	}

	public void reset() {
		_queryResult.reset();
		_available = 0;
		_current = -1;
	}

	public void loadFromIdReader(ByteArrayBuffer reader, int count) {
		for (int i = 0; i < count; i++) {
			_ids[i] = reader.readInt();
		}
		if(count > 0){
			_available = count;
			return;
		}
		_available = -1;
	}

}
