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

import java.util.*;

import com.db4o.internal.*;
import com.db4o.reflect.*;

/**
 * @persistent
 * @deprecated since 7.0
 * @decaf.ignore.jdk11
 */
class P2HashMapKeySet implements Set {

    private final P2HashMap i_map;

    P2HashMapKeySet(P2HashMap a_map) {
        i_map = a_map;
    }

    public boolean add(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        i_map.clear();
    }

    public boolean contains(Object o) {
        return i_map.containsKey(o);
    }

    public boolean containsAll(Collection c) {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            Iterator i = c.iterator();
            while (i.hasNext()) {
                if (i_map.get4(i.next()) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return i_map.isEmpty();
    }

    public Iterator iterator() {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            return new P2HashMapIterator(i_map);
        }
    }

    public boolean remove(Object o) {
        return i_map.remove(o) != null;
    }

    public boolean removeAll(Collection c) {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            boolean ret = false;
            Iterator i = c.iterator();
            while (i.hasNext()) {
                if (i_map.remove4(i.next()) != null) {
                    ret = true;
                }
            }
            return ret;
        }
    }

    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return i_map.size();
    }

    public Object[] toArray() {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            Object[] arr = new Object[i_map.i_size];
            int j = 0;
            Iterator i = new P2HashMapIterator(i_map);
            while (i.hasNext()) {
                arr[j++] = i.next();
            }
            return arr;
        }
    }

    public Object[] toArray(Object[] a) {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            int size = i_map.i_size;
            if (a.length < size) {
                Transaction trans = i_map.getTrans();
                if(trans == null){
                    Exceptions4.throwRuntimeException(29);
                }
                Reflector reflector = trans.reflector();
                a =
                    (Object[])reflector.array().newInstance(
                        reflector.forObject(a).getComponentType(),
                        size);
            }
            int j = 0;
            Iterator i = new P2HashMapIterator(i_map);
            while (i.hasNext()) {
                a[j++] = i.next();
            }
            if (a.length > size) {
                a[size] = null;
            }
            return a;
        }
    }

}
