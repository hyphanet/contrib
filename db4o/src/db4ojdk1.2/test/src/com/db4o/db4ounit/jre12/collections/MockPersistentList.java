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
package com.db4o.db4ounit.jre12.collections;

import java.util.*;

import com.db4o.foundation.*;
import com.db4o.internal.collections.*;


public class MockPersistentList implements PersistentList{
    
    private Vector _vector = new Vector();

    public boolean add(Object o) {
        return _vector.add(o);
    }

    public void add(int index, Object element) {
        _vector.add(index, element);
    }

    public boolean addAll(Iterable4 i) {
        Iterator4 iterator = i.iterator();
        while(iterator.moveNext()){
            add(iterator.current());
        }
        return true;
    }

    public boolean addAll(int index, Iterable4 i) {
        Iterator4 iterator = i.iterator();
        while(iterator.moveNext()){
            add(index++, iterator.current());
        }
        return true;
    }

    public void clear() {
        _vector.clear();
    }

    public boolean contains(Object o) {
        return _vector.contains(o);
    }

    public boolean containsAll(Iterable4 i) {
        Iterator4 iterator = i.iterator();
        while(iterator.moveNext()){
            if(! contains(iterator.current())){
                return false;
            }
        }
        return true;
    }

    public Object get(int index) {
        return _vector.get(index);
    }

    public int indexOf(Object o) {
        return _vector.indexOf(o);
    }

    public boolean isEmpty() {
        return _vector.isEmpty();
    }

    public Iterator4 iterator() {
        return new Collection4(_vector.toArray()).iterator();
    }

    public int lastIndexOf(Object o) {
        return _vector.lastIndexOf(o);
    }

    public boolean remove(Object o) {
        return _vector.remove(o);
    }

    public Object remove(int index) {
        return _vector.remove(index);
    }

    public boolean removeAll(Iterable4 i) {
        boolean result = false;
        Iterator4 iterator = i.iterator();
        while(iterator.moveNext()){
            if(remove(iterator.current())){
                result = true;
            }
        }
        return result;
    }

    public boolean retainAll(Iterable4 retained) {
    	boolean result = false;
		Iterator iter = _vector.iterator();
		while (iter.hasNext()) {
			if (!contains(retained, iter.next())) {
				iter.remove();
				result = true;
			}
		}
		return result;
    }
    
    private boolean contains(Iterable4 iter, Object element) {
		Iterator4 i = iter.iterator();
		while (i.moveNext()) {
			Object current = i.current();
			if ((current == null && element == null) || current.equals(element)) {
				return true;
			}
		}
		return false;
	}

    public Object set(int index, Object element) {
        return _vector.set(index, element);
    }

    public int size() {
        return _vector.size();
    }

    public PersistentList subList(int fromIndex, int toIndex) {
        throw new NotImplementedException();
    }

    public Object[] toArray() {
        return _vector.toArray();
    }

    public Object[] toArray(Object[] a) {
        return _vector.toArray(a);
    }

}
