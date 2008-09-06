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
package com.db4o.collections;

import java.util.*;


/**
 * @exclude
 * 
 * @sharpen.ignore
 * @decaf.ignore
 */
public class MapEntry4<K, V> implements Map.Entry<K, V> {

    private K _key;

    private V _value;

    public MapEntry4(K key, V value) {
        _key = key;
        _value = value;
    }

    public K getKey() {
        return _key;
    }

    public V getValue() {
        return _value;
    }

    public V setValue(V value) {
        V oldValue = value;
        this._value = value;
        return oldValue;
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Map.Entry)) {
            return false;
        }

        MapEntry4<K, V> other = (MapEntry4<K, V>) o;

        return (_key == null ? other.getKey() == null : _key.equals(other
                .getKey())
                && _value == null ? other.getValue() == null : _value
                .equals(other.getValue()));

    }

    public int hashCode() {
        return (_key == null ? 0 : _key.hashCode())
                ^ (_value == null ? 0 : _value.hashCode());
    }
}
