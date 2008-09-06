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
package com.db4o.internal.query.result;

import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.query.processor.*;
import com.db4o.query.*;


/**
 * @exclude
 */
public class HybridQueryResult extends AbstractQueryResult {
	
	private AbstractQueryResult _delegate;
	
	public HybridQueryResult(Transaction transaction, QueryEvaluationMode mode) {
		super(transaction);
		_delegate = forMode(transaction, mode);
	}
	
	private static AbstractQueryResult forMode(Transaction transaction, QueryEvaluationMode mode){
		if(mode == QueryEvaluationMode.LAZY){
			return new LazyQueryResult(transaction); 
		}
		if(mode == QueryEvaluationMode.SNAPSHOT){
			return new SnapShotQueryResult(transaction); 
		}
		return new IdListQueryResult(transaction);
	}

	public Object get(int index) {
		_delegate = _delegate.supportElementAccess();
		return _delegate.get(index);
	}
	
	public int getId(int index) {
		_delegate = _delegate.supportElementAccess();
		return _delegate.getId(index);
	}

	public int indexOf(int id) {
		_delegate = _delegate.supportElementAccess();
		return _delegate.indexOf(id);
	}

	public IntIterator4 iterateIDs() {
		return _delegate.iterateIDs();
	}
	
	public Iterator4 iterator() {
		return _delegate.iterator();
	}

	public void loadFromClassIndex(ClassMetadata clazz) {
		_delegate.loadFromClassIndex(clazz);
	}

	public void loadFromClassIndexes(ClassMetadataIterator iterator) {
		_delegate.loadFromClassIndexes(iterator);
	}

	public void loadFromIdReader(ByteArrayBuffer reader) {
		_delegate.loadFromIdReader(reader);
	}

	public void loadFromQuery(QQuery query) {
		if(query.requiresSort()){
			_delegate = new IdListQueryResult(transaction());
		}
		_delegate.loadFromQuery(query);
	}

	public int size() {
		_delegate = _delegate.supportSize();
		return _delegate.size();
	}

	public void sort(QueryComparator cmp) {
		_delegate = _delegate.supportSort();
		_delegate.sort(cmp);
	}

}
