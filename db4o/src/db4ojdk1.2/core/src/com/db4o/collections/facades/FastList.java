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
package com.db4o.collections.facades;

import java.util.*;

import com.db4o.foundation.*;
import com.db4o.internal.collections.*;


/**
 * @exclude
 * @decaf.ignore.jdk11
 */
public class FastList implements java.util.List{
    
    private PersistentList _persistentList;
    
    private transient FastListCache _cache;
    
    public FastList() {
    	
    }
    
    public FastList(PersistentList persistentList) {
    	_persistentList = persistentList;
    	ensureInitFastListCache();
    }
    
    private void ensureInitFastListCache() {
		if(_cache == null) {
			_cache = new FastListCache(size());
		}
	}
    
    public boolean add(Object o) {
    	ensureInitFastListCache();
    	_cache.add(o);
        return _persistentList.add(o);
    }
    
	public void add(int index, Object element) {
    	validateIndex(index);
    	ensureInitFastListCache();
    	_cache.add(index, element);
        _persistentList.add(index, element);
    }

	public boolean addAll(final Collection c) {
		ensureInitFastListCache();
    	_cache.addAll(c);
        return _persistentList.addAll(new JdkCollectionIterable4(c));
    }

    public boolean addAll(int index, Collection c) {
    	validateIndex(index);
    	ensureInitFastListCache();
    	_cache.addAll(index, c);
        return _persistentList.addAll(index, new JdkCollectionIterable4(c));
    }

    public void clear() {
    	ensureInitFastListCache();
    	_cache.clear();
        _persistentList.clear();
    }

    public boolean contains(Object o) {
    	ensureInitFastListCache();
    	if(_cache.contains(o)) {
    		return true;
    	}
        return _persistentList.contains(o);
    }

    public boolean containsAll(Collection c) {
    	ensureInitFastListCache();
    	boolean ret = _cache.containsAll(c);
    	if(ret) {
    		return true;
    	}
        return _persistentList.containsAll(new JdkCollectionIterable4(c));
    }

    public Object get(int index) {
    	ensureInitFastListCache();
    	CachedObject co = _cache.get(index);
    	if(co != CachedObject.NONE) {
    		return co.obj;
    	}
        return _persistentList.get(index);
    }

    public int indexOf(Object o) {
    	int index = _cache.indexOf(o);
    	if(index != -1) {
    		return index;
    	}
        return _persistentList.indexOf(o);
    }

    public boolean isEmpty() {
        return _persistentList.isEmpty();
    }

    public Iterator iterator() {
        return new Iterator4JdkIterator(_persistentList.iterator());
    }

    public int lastIndexOf(Object o) {
        return _persistentList.lastIndexOf(o);
    }

    public ListIterator listIterator() {
        throw new UnsupportedOperationException();
    }

    public ListIterator listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
    	ensureInitFastListCache();
    	_cache.remove(o);
        return _persistentList.remove(o);
    }

    public Object remove(int index) {
    	ensureInitFastListCache();
    	_cache.remove(index);
        return _persistentList.remove(index);
    }

    public boolean removeAll(Collection c) {
    	ensureInitFastListCache();
    	_cache.removeAll(c);
        return _persistentList.removeAll(new JdkCollectionIterable4(c));
    }

    public boolean retainAll(Collection c) {
    	ensureInitFastListCache();
    	_cache.retainAll(c);
        return _persistentList.retainAll(new JdkCollectionIterable4(c));
    }

    public Object set(int index, Object element) {
    	validateIndex(index);
    	ensureInitFastListCache();
    	_cache.set(index, element);
        return _persistentList.set(index, element);
    }

    public int size() {
        return _persistentList.size();
    }

    public List subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        return _persistentList.toArray();
    }

    public Object[] toArray(Object[] a) {
        return _persistentList.toArray(a);
    }
    
    private void validateIndex(int index) {
		if(index < 0 || index > size()) {
    		throw new IndexOutOfBoundsException();
    	}
	}

}
