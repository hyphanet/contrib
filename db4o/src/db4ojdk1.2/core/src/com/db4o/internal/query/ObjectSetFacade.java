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
package com.db4o.internal.query;

import java.util.*;

import com.db4o.ext.ExtObjectSet;
import com.db4o.foundation.*;
import com.db4o.internal.query.result.*;
import com.db4o.query.*;

/**
 * @exclude
 * @sharpen.ignore 
 * @decaf.ignore.extends.jdk11
 */
public class ObjectSetFacade extends AbstractList implements ExtObjectSet {
    
    public final StatefulQueryResult _delegate;
    
    public ObjectSetFacade(QueryResult qResult){
        _delegate = new StatefulQueryResult(qResult);
    }
    
	public void sort(QueryComparator cmp) {
		_delegate.sort(cmp);
	}	
    
    /**
     * @decaf.ignore.jdk11
     */
    public Iterator iterator() {
    	class JDKIterator extends Iterable4Adaptor implements Iterator {
			public JDKIterator(Iterable4 delegate) {
				super(delegate);
			}
			
			protected boolean moveNext() {
				synchronized (_delegate.lock()) {
					return super.moveNext();
				}
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
    		
    	}
    	return new JDKIterator(_delegate);
    }
    
    public long[] getIDs() {
        return _delegate.getIDs();
    }

    public ExtObjectSet ext() {
        return this;
    }

    public boolean hasNext() {
        return _delegate.hasNext();
    }

    public Object next() {
        return _delegate.next();
    }

    public void reset() {
        _delegate.reset();
    }

    public int size() {
        return _delegate.size();
    }
    
    /**
     * @decaf.ignore.jdk11
     */
    public boolean contains(Object a_object) {
        return indexOf(a_object) >= 0;
    }

    public Object get(int index) {
        return _delegate.get(index);
    }

    /**
     * @decaf.ignore.jdk11
     */
    public int indexOf(Object a_object) {
    	return _delegate.indexOf(a_object);
    }
    
    /**
     * @decaf.ignore.jdk11
     */
    public int lastIndexOf(Object a_object) {
        return indexOf(a_object);
    }
    
    /**
     * @decaf.ignore.jdk11
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
