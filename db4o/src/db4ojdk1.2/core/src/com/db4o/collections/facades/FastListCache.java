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

/**
 * @exclude 
 * @decaf.ignore.jdk11
 */
public class FastListCache {

	private transient List _list;

	public FastListCache(int size) {
		_list = new ArrayList(size);
		for (int i = 0; i < _list.size(); ++i) {
			_list.set(i, CachedObject.NONE);
		}
	}

	public void add(Object o) {
		_list.add(new CachedObject(o));
	}

	public void add(int index, Object element) {
		_list.add(index, new CachedObject(element));
	}

	public void addAll(Collection c) {
		_list.addAll(toCachedObjectCollection(c));
	}
	
	public void addAll(int index, Collection c) {
		_list.addAll(index, toCachedObjectCollection(c));
	}
	
	public void clear() {
		_list.clear();
	}

	public boolean contains(Object o) {
		return _list.contains(new CachedObject(o));
	}

	public int indexOf(Object o) {
		return _list.indexOf(new CachedObject(o));
	}
	
	public void remove(Object o) {
		_list.remove(new CachedObject(o));	
	}
	
	public void remove(int index) {
		_list.remove(index);
	}

	public void removeAll(Collection c) {
		_list.removeAll(toCachedObjectCollection(c));
	}

	public void retainAll(Collection c) {
		_list.retainAll(toCachedObjectCollection(c));
	}

	public void set(int index, Object element) {
		_list.set(index, new CachedObject(element));
	}
	
	private Collection toCachedObjectCollection(Collection c) {
		ArrayList cachedObjectList = new ArrayList(c.size());
		Iterator iter = c.iterator();
		while(iter.hasNext()) {
			cachedObjectList.add(new CachedObject(iter.next()));
		}
		return cachedObjectList;
	}

	public CachedObject get(int index) {
		return (CachedObject) _list.get(index);
	}

	public boolean containsAll(Collection c) {
		return _list.containsAll(toCachedObjectCollection(c));
	}

}
