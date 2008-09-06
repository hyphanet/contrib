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

import com.db4o.internal.collections.*;


/**
 * @exclude
 * @decaf.ignore.jdk11
 */
public class ArrayListFacade extends ArrayList{
    
    public PersistentList _delegate;

    public void add(int index, Object element) {
        // TODO Auto-generated method stub
        super.add(index, element);
    }

    public boolean add(Object o) {
        // TODO Auto-generated method stub
        return super.add(o);
    }

    public boolean addAll(Collection c) {
        // TODO Auto-generated method stub
        return super.addAll(c);
    }

    public boolean addAll(int index, Collection c) {
        // TODO Auto-generated method stub
        return super.addAll(index, c);
    }

    public void clear() {
        // TODO Auto-generated method stub
        super.clear();
    }

    public Object clone() {
        // TODO Auto-generated method stub
        return super.clone();
    }

    public boolean contains(Object elem) {
        // TODO Auto-generated method stub
        return super.contains(elem);
    }

    public void ensureCapacity(int minCapacity) {
        // TODO Auto-generated method stub
        super.ensureCapacity(minCapacity);
    }

    public Object get(int index) {
        // TODO Auto-generated method stub
        return super.get(index);
    }

    public int indexOf(Object elem) {
        // TODO Auto-generated method stub
        return super.indexOf(elem);
    }

    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return super.isEmpty();
    }

    public int lastIndexOf(Object elem) {
        // TODO Auto-generated method stub
        return super.lastIndexOf(elem);
    }

    public Object remove(int index) {
        // TODO Auto-generated method stub
        return super.remove(index);
    }

    protected void removeRange(int fromIndex, int toIndex) {
        // TODO Auto-generated method stub
        super.removeRange(fromIndex, toIndex);
    }

    public Object set(int index, Object element) {
        // TODO Auto-generated method stub
        return super.set(index, element);
    }

    public int size() {
        // TODO Auto-generated method stub
        return super.size();
    }

    public Object[] toArray() {
        // TODO Auto-generated method stub
        return super.toArray();
    }

    public Object[] toArray(Object[] a) {
        // TODO Auto-generated method stub
        return super.toArray(a);
    }

    public void trimToSize() {
        // TODO Auto-generated method stub
        super.trimToSize();
    }

    public boolean equals(Object o) {
        // TODO Auto-generated method stub
        return super.equals(o);
    }

    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }

    public Iterator iterator() {
        // TODO Auto-generated method stub
        return super.iterator();
    }

    public ListIterator listIterator() {
        // TODO Auto-generated method stub
        return super.listIterator();
    }

    public ListIterator listIterator(int index) {
        // TODO Auto-generated method stub
        return super.listIterator(index);
    }

    public List subList(int fromIndex, int toIndex) {
        // TODO Auto-generated method stub
        return super.subList(fromIndex, toIndex);
    }

    public boolean containsAll(Collection c) {
        // TODO Auto-generated method stub
        return super.containsAll(c);
    }

    public boolean remove(Object o) {
        // TODO Auto-generated method stub
        return super.remove(o);
    }

    public boolean removeAll(Collection c) {
        // TODO Auto-generated method stub
        return super.removeAll(c);
    }

    public boolean retainAll(Collection c) {
        // TODO Auto-generated method stub
        return super.retainAll(c);
    }

    public String toString() {
        // TODO Auto-generated method stub
        return super.toString();
    }
    

}
