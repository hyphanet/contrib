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
package com.db4o.db4ounit.jre5.collections;

import java.util.*;

import com.db4o.collections.*;
import com.db4o.db4ounit.common.ta.*;

import db4ounit.*;

public class ArrayList4TransparentUpdateTestCase implements TestLifeCycle {
	
	private ArrayList4<Object> list;
	private MockActivator activator;

	public void testAdd() {
		list.add("foo");
		assertWriteCount(1);
		list.add("bar");
		assertWriteCount(2);
		list.add(1, "baz");
		assertWriteCount(3);
	}

	public void testAddAll() {
		List<Object> collection = newList("foo", "bar");
		list.addAll(collection);
		assertWriteCount(1);
		
		list.addAll(1, collection);
		assertWriteCount(2);
	}

	public void testRemove() {
		list.add("foo");
		assertWriteCount(1);
		
		list.remove("foo");
		assertWriteCount(2);
		
		list.remove("foo"); // foo is not there
		assertWriteCount(2);
	}
	
	public void testRemoveAll() {
		list.add("foo");
		list.add("bar");
		assertWriteCount(2);
		
		list.removeAll(newList("foo", "bar"));
		Assert.isTrue(activator.writeCount() > 2);
	}
	
	public void testClear() {
		
		list.add("foo");
		assertWriteCount(1);
		
		list.clear();
		assertWriteCount(2);
	}
	
	public void testRetainAll() {
		list.add("foo");
		list.add("bar");
		assertWriteCount(2);
		
		list.retainAll(newList("foo"));
		assertWriteCount(3);
	}
	
	public void testSet() {
		
		list.add("foo");
		assertWriteCount(1);
		
		list.set(0, "bar");
		assertWriteCount(2);
	}
	
	public void testTrimToSize() {
		list.add("foo");
		assertWriteCount(1);
		
		list.trimToSize();
		assertWriteCount(2);
	}
	
	public void testIteratorRemove() {
		
		list.add("foo");
		assertWriteCount(1);
		
		Iterator<Object> iterator = list.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
		}
		
		assertWriteCount(2);
	}

	public void setUp() throws Exception {
		list = new ArrayList4<Object>();
		activator = MockActivator.activatorFor(list);
	}

	public void tearDown() throws Exception {
	}
	
	private void assertWriteCount(int expected) {
		Assert.areEqual(expected, activator.writeCount());
	}
	
	private List<Object> newList(Object ... elements) {
		return Arrays.asList(elements);
	}
}
