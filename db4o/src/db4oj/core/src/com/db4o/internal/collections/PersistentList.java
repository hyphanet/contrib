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
package com.db4o.internal.collections;

import com.db4o.foundation.*;


/**
 * @exclude
 */
public interface PersistentList {
    
    public boolean add(Object o);

    public void add(int index, Object element);

    public boolean addAll(Iterable4 i);

    public boolean addAll(int index, Iterable4 i);

    public void clear();
    
    public boolean contains(Object o);

    public boolean containsAll(Iterable4 i);

    public Object get(int index);

    public int indexOf(Object o);

    public boolean isEmpty();

    public Iterator4 iterator();

    public int lastIndexOf(Object o);

    public boolean remove(Object o);

    public Object remove(int index);

    public boolean removeAll(Iterable4 i);

    public boolean retainAll(Iterable4 i);

    public Object set(int index, Object element);

    public int size();

    public PersistentList subList(int fromIndex, int toIndex);

    public Object[] toArray();

    public Object[] toArray(Object[] a);

}
