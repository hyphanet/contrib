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
package com.db4o;

import com.db4o.internal.*;
import com.db4o.types.*;

/**
 * @persistent
 * @exclude
 * @deprecated since 7.0
 * @decaf.ignore.jdk11
 */
public class P2Collections implements Db4oCollections{
    
    private final Transaction _transaction;
    
    public P2Collections(Transaction transaction){
        _transaction = transaction;
    }

    public Db4oList newLinkedList() {
        synchronized(lock()) {
        	if(canCreateCollection(container())){
	            Db4oList l = new P2LinkedList();
	            container().store(_transaction, l);
	            return l;
	        }
	        return null;
        }
    }

    public Db4oMap newHashMap(int a_size) {
        synchronized(lock()) {
        	if(canCreateCollection(container())){
	            return new P2HashMap(a_size);
	        }
	        return null;
        }
    }
    
    public Db4oMap newIdentityHashMap(int a_size) {
        synchronized(lock()) {
	        if(canCreateCollection(container())){
	            P2HashMap m = new P2HashMap(a_size);
	            m.i_type = 1;
	            container().store(_transaction, m);
	            return m;
	        }
	        return null;
        }
    }
    
    private Object lock(){
        return container().lock();
    }
    
    private ObjectContainerBase container(){
        return _transaction.container();
    }
    
	private boolean canCreateCollection(ObjectContainerBase container){
	    container.checkClosed();
	    return ! container.isInstantiating();
	}

}
