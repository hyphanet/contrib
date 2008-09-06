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

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.query.processor.*;
import com.db4o.query.*;


/**
 * @exclude
 */
public abstract class AbstractQueryResult implements QueryResult {
	
	protected final Transaction _transaction;

	public AbstractQueryResult(Transaction transaction) {
		_transaction = transaction;
	}
	
	public final Object activate(Object obj) {
		stream().activate(_transaction, obj);
		return obj;
	}

	public final Object activatedObject(int id) {
	    ObjectContainerBase stream = stream();
	    Object ret = stream.getActivatedObjectFromCache(_transaction, id);
	    if(ret != null){
	        return ret;
	    }
	    return stream.readActivatedObjectNotInCache(_transaction, id);
	}

	public Object lock() {
		final ObjectContainerBase stream = stream();
		stream.checkClosed();
		return stream.lock();
	}

	public ObjectContainerBase stream() {
		return _transaction.container();
	}
	
	public Transaction transaction(){
		return _transaction;
	}

	public ExtObjectContainer objectContainer() {
	    return transaction().objectContainer().ext();
	}
	
    public Iterator4 iterator() {
    	return new MappingIterator(iterateIDs()){
    		protected Object map(Object current) {
    			if(current == null){
    				return Iterators.SKIP;
    			}
    			synchronized (lock()) {
    				Object obj = activatedObject(((Integer)current).intValue());
    				if(obj == null){
    					return Iterators.SKIP;
    				}
    				return obj; 
    			}
    		}
    	};
    }
    
    public AbstractQueryResult supportSize(){
    	return this;
    }
    
    public AbstractQueryResult supportSort(){
    	return this;
    }
    
    public AbstractQueryResult supportElementAccess(){
    	return this;
    }
    
    protected int knownSize(){
    	return size();
    }
    
    public AbstractQueryResult toIdList(){
    	IdListQueryResult res = new IdListQueryResult(transaction(), knownSize());
    	IntIterator4 i = iterateIDs();
    	while(i.moveNext()){
    		res.add(i.currentInt());
    	}
    	return res;
    }
    
    protected AbstractQueryResult toIdTree(){
    	return new IdTreeQueryResult(transaction(), iterateIDs());
    }
    
	public Config4Impl config(){
		return stream().config();
	}

	public int size() {
		throw new NotImplementedException();
	}

	public void sort(QueryComparator cmp) {
		throw new NotImplementedException();
	}

	public Object get(int index) {
		throw new NotImplementedException();
	}
	
    /** @param i */
	public int getId(int i) {
		throw new NotImplementedException();
	}

	public int indexOf(int id) {
		throw new NotImplementedException();
	}

    /** @param c */
	public void loadFromClassIndex(ClassMetadata c) {
		throw new NotImplementedException();
	}

    /** @param i */
	public void loadFromClassIndexes(ClassMetadataIterator i) {
		throw new NotImplementedException();
	}

    /** @param r */
	public void loadFromIdReader(ByteArrayBuffer r) {
		throw new NotImplementedException();
	}

    /** @param q */
	public void loadFromQuery(QQuery q) {
		throw new NotImplementedException();
	}

}
