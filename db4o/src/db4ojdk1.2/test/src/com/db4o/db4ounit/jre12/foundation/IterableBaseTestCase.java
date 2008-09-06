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
package com.db4o.db4ounit.jre12.foundation;

import java.util.*;

import com.db4o.db4ounit.jre12.collections.*;
import com.db4o.foundation.*;

import db4ounit.*;

public class IterableBaseTestCase implements TestCase {

	private static final String[] ITEMS = {"a", "b", "c"};
	
	private static class CustomIterable {
		public Iterator iterator() {
			return createList().iterator();
		}
	}
	
	public void testReflectionIterableBase() throws Exception {
		Assert.expect(NoSuchMethodException.class, new CodeBlock() {
			public void run() throws Throwable {
				new ReflectionIterableBase(new Object());
			}
		});
		CustomIterable delegate = new CustomIterable();
		ReflectionIterableBase iterable = new ReflectionIterableBase(delegate);
		assertIterableWrapper(iterable, delegate);
	}

	public void testCollectionIterableBase() throws Exception {
		List list = createList();
		CollectionIterableBase iterable = new CollectionIterableBase(list);
		assertIterableWrapper(iterable, list);
	}

	public void testIterableBaseFactory() {
		CustomIterable customIterable = new CustomIterable();
		IterableBaseWrapper customCoerced = (IterableBaseWrapper) IterableBaseFactory.coerce(customIterable);
		Assert.isInstanceOf(ReflectionIterableBase.class, customCoerced);
		Assert.areSame(customIterable, IterableBaseFactory.unwrap(customCoerced));
		List list = createList();
		IterableBase listCoerced = IterableBaseFactory.coerce(list);
		Assert.isInstanceOf(CollectionIterableBase.class, listCoerced);
		Assert.areSame(list, IterableBaseFactory.unwrap(listCoerced));
	}
	
	private void assertIterableWrapper(IterableBaseWrapper iterable, Object delegate) throws Exception {
		IteratorAssert.areEqual(ITEMS, iterable.iterator());
		Assert.areSame(delegate, iterable.delegate());
	}

	private static List createList() {
		return Arrays.asList(ITEMS);
	}
}
