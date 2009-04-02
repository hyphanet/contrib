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
package com.db4o.foundation;


/**
 * @exclude
 */
public class Hashtable4 implements DeepClone, Map4 {

	private static final float FILL = 0.5F;
	
	// FIELDS ARE PUBLIC SO THEY CAN BE REFLECTED ON IN JDKs <= 1.1

	public int _tableSize;

	public int _mask;

	public int _maximumSize;

	public int _size;

	public HashtableIntEntry[] _table;

	public Hashtable4(int size) {
		size = newSize(size); // legacy for .NET conversion
		_tableSize = 1;
		while (_tableSize < size) {
			_tableSize = _tableSize << 1;
		}
		_mask = _tableSize - 1;
		_maximumSize = (int) (_tableSize * FILL);
		_table = new HashtableIntEntry[_tableSize];
	}

	public Hashtable4() {
		this(1);
	}
	
    /** @param cloneOnlyCtor */
	protected Hashtable4(DeepClone cloneOnlyCtor) {
	}
	
	public int size() {
		return _size;
	}

	public Object deepClone(Object obj) {
		return deepCloneInternal(new Hashtable4((DeepClone)null), obj);
	}

	public void forEachKeyForIdentity(Visitor4 visitor, Object obj) {
		for (int i = 0; i < _table.length; i++) {
			HashtableIntEntry entry = _table[i];
			while (entry != null) {
				if (entry._object == obj) {
					visitor.visit(entry.key());
				}
				entry = entry._next;
			}
		}
	}

	public Object get(byte[] key) {
		int intKey = HashtableByteArrayEntry.hash(key);
		return getFromObjectEntry(intKey, key);
	}

	public Object get(int key) {
		HashtableIntEntry entry = _table[key & _mask];
		while (entry != null) {
			if (entry._key == key) {
				return entry._object;
			}
			entry = entry._next;
		}
		return null;
	}

	public Object get(Object key) {
		if (key == null) {
			return null;
		}
		return getFromObjectEntry(key.hashCode(), key);
	}
	
	/**
	 * Iterates through all the {@link Entry4 entries}.
	 *   
	 * @return {@link Entry4} iterator
	 */
	public Iterator4 iterator(){
		return new HashtableIterator(_table);
	}
	
	/**
	 * Iterates through all the keys.
	 * 
	 * @return key iterator
	 */
	public Iterator4 keys() {
		return Iterators.map(iterator(), new Function4() {
			public Object apply(Object current) {
				return ((Entry4)current).key();
			}
		});
	}
	
	/**
	 * Iterates through all the values.
	 * 
	 * @return value iterator
	 */
	public Iterator4 values() {
		return Iterators.map(iterator(), new Function4() {
			public Object apply(Object current) {
				return ((Entry4)current).value();
			}
		});
	}
	
	public boolean containsKey(Object key) {
		if (null == key) {
			return false;
		}
		return null != getObjectEntry(key.hashCode(), key); 
	}
	
	public boolean containsAllKeys(Iterable4 collection) {
		return containsAllKeys(collection.iterator());
	}

	public boolean containsAllKeys(Iterator4 iterator) {
		while (iterator.moveNext()) {
			if (!containsKey(iterator.current())) {
				return false;
			}
		}
		return true;
	}

	public void put(byte[] key, Object value) {
		putEntry(new HashtableByteArrayEntry(key, value));
	}

	public void put(int key, Object value) {
		putEntry(new HashtableIntEntry(key, value));
	}

	public void put(Object key, Object value) {
		if (null == key) {
			throw new ArgumentNullException();
		}
		putEntry(new HashtableObjectEntry(key, value));
	}

	public Object remove(byte[] key) {
		int intKey = HashtableByteArrayEntry.hash(key);
		return removeObjectEntry(intKey, key);
	}

	public void remove(int key) {
		HashtableIntEntry entry = _table[key & _mask];
		HashtableIntEntry predecessor = null;
		while (entry != null) {
			if (entry._key == key) {
				removeEntry(predecessor, entry);
				return;
			}
			predecessor = entry;
			entry = entry._next;
		}
	}

	public void remove(Object objectKey) {
		int intKey = objectKey.hashCode();
		removeObjectEntry(intKey, objectKey);
	}
	
	public String toString() {
		return Iterators.join(iterator(), "{", "}", ", ");
	}

	protected Hashtable4 deepCloneInternal(Hashtable4 ret, Object obj) {
		ret._mask = _mask;
		ret._maximumSize = _maximumSize;
		ret._size = _size;
		ret._tableSize = _tableSize;
		ret._table = new HashtableIntEntry[_tableSize];
		for (int i = 0; i < _tableSize; i++) {
			if (_table[i] != null) {
				ret._table[i] = (HashtableIntEntry) _table[i].deepClone(obj);
			}
		}
		return ret;
	}

	private int entryIndex(HashtableIntEntry entry) {
		return entry._key & _mask;
	}

	private HashtableIntEntry findWithSameKey(HashtableIntEntry newEntry) {
		HashtableIntEntry existing = _table[entryIndex(newEntry)];
		while (null != existing) {
			if (existing.sameKeyAs(newEntry)) {
				return existing;
			}
			existing = existing._next;
		}
		return null;
	}

	private Object getFromObjectEntry(int intKey, Object objectKey) {
		final HashtableObjectEntry entry = getObjectEntry(intKey, objectKey);		
		return entry == null ? null : entry._object;
	}

	private HashtableObjectEntry getObjectEntry(int intKey, Object objectKey) {
		HashtableObjectEntry entry = (HashtableObjectEntry) _table[intKey & _mask];
		while (entry != null) {
			if (entry._key == intKey && entry.hasKey(objectKey)) {
				return entry;
			}
			entry = (HashtableObjectEntry) entry._next;
		}
		return null;
	}

	private void increaseSize() {
		_tableSize = _tableSize << 1;
		_maximumSize = _maximumSize << 1;
		_mask = _tableSize - 1;
		HashtableIntEntry[] temp = _table;
		_table = new HashtableIntEntry[_tableSize];
		for (int i = 0; i < temp.length; i++) {
			reposition(temp[i]);
		}
	}

	private void insert(HashtableIntEntry newEntry) {
		_size++;
		if (_size > _maximumSize) {
			increaseSize();
		}
		int index = entryIndex(newEntry);
		newEntry._next = _table[index];
		_table[index] = newEntry;
	}

	private final int newSize(int size) {
		return (int) (size / FILL);
	}

	private void putEntry(HashtableIntEntry newEntry) {
		HashtableIntEntry existing = findWithSameKey(newEntry);
		if (null != existing) {
			replace(existing, newEntry);
		} else {
			insert(newEntry);
		}
	}

	private void removeEntry(HashtableIntEntry predecessor, HashtableIntEntry entry) {
		if (predecessor != null) {
			predecessor._next = entry._next;
		} else {
			_table[entryIndex(entry)] = entry._next;
		}
		_size--;
	}

	private Object removeObjectEntry(int intKey, Object objectKey) {
		HashtableObjectEntry entry = (HashtableObjectEntry) _table[intKey & _mask];
		HashtableObjectEntry predecessor = null;
		while (entry != null) {
			if (entry._key == intKey && entry.hasKey(objectKey)) {
				removeEntry(predecessor, entry);
				return entry._object;
			}
			predecessor = entry;
			entry = (HashtableObjectEntry) entry._next;
		}
		return null;
	}

	private void replace(HashtableIntEntry existing, HashtableIntEntry newEntry) {
		newEntry._next = existing._next;
		HashtableIntEntry entry = _table[entryIndex(existing)];
		if (entry == existing) {
			_table[entryIndex(existing)] = newEntry;
		} else {
			while (entry._next != existing) {
				entry = entry._next;
			}
			entry._next = newEntry;
		}
	}

	private void reposition(HashtableIntEntry entry) {
		if (entry != null) {
			reposition(entry._next);
			entry._next = _table[entryIndex(entry)];
			_table[entryIndex(entry)] = entry;
		}
	}		
}
