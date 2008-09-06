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
package com.db4o.db4ounit.common.ta.collections;

import java.util.*;

import com.db4o.activation.*;
import com.db4o.db4ounit.common.ta.*;

/**
 * Platform specific facade.
 * 
 * @param 
 * 
 * @sharpen.ignore
 */
public class PagedList extends /* TA BEGIN */ ActivatableImpl /* TA END */ implements List {
		
	PagedBackingStore _store = new PagedBackingStore();
	
	public PagedList() {

	}

	public boolean add(Object item) {
		// TA BEGIN
		activate(ActivationPurpose.READ);
		// TA END
		return _store.add(item);
	}
	
	public Object get(int index) {
		// TA BEGIN
		activate(ActivationPurpose.READ);
		// TA END
		return _store.get(index);
	}

	
	public int size() {
		// TA BEGIN
		activate(ActivationPurpose.READ);
		// TA END
		return _store.size();
	}

	public void add(int index, Object element) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean addAll(Collection c) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean addAll(int index, Collection c) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public void clear() {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean contains(Object o) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean containsAll(Collection c) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public int indexOf(Object o) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean isEmpty() {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public Iterator iterator() {
		// TA BEGIN
		activate(ActivationPurpose.READ);
		// TA END
		return new SimpleListIterator(this);
	}

	public int lastIndexOf(Object o) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public ListIterator listIterator() {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public ListIterator listIterator(int index) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean remove(Object o) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public Object remove(int index) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean removeAll(Collection c) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public boolean retainAll(Collection c) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public Object set(int index, Object element) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public List subList(int fromIndex, int toIndex) {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public Object[] toArray() {
		throw new com.db4o.foundation.NotImplementedException();
	}

	public Object[] toArray(Object[] a) {
		throw new com.db4o.foundation.NotImplementedException();
	}
}