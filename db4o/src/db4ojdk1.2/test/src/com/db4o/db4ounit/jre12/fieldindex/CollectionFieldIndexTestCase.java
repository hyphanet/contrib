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
package com.db4o.db4ounit.jre12.fieldindex;

import java.util.*;

import com.db4o.ObjectSet;
import com.db4o.config.*;
import com.db4o.query.Query;

import db4ounit.Assert;
import db4ounit.extensions.AbstractDb4oTestCase;

/**
 * @exclude
 */
public class CollectionFieldIndexTestCase extends AbstractDb4oTestCase {
	
	public static void main(String[] args) {
		new CollectionFieldIndexTestCase().runSolo();
	}
	
	private static class Item {
		private String _name;
		
		public Item(String name) {
			_name = name;
		}
		
		public String getName() {
			return _name;
		}
	}
	
	private static class UntypedContainer {
		private Object _set = new HashSet();
		
		public UntypedContainer(Object item) {
			((Set)_set).add(item);
		}
		
		public Iterator iterator() {
			return ((Set)_set).iterator();
		}
	}
	
	protected void configure(Configuration config) {
		indexField(config,Item.class, "_name");
		indexField(config,UntypedContainer.class, "_set");
	}
	
	protected void store() throws Exception {
		db().store(new UntypedContainer(new Item("foo")));
		db().store(new UntypedContainer(new Item("bar")));
	}
	
	public void testUntypedContainer() {
		final Query q = db().query();
		q.constrain(UntypedContainer.class);
		q.descend("_set").descend("_name").constrain("foo");
		
		final ObjectSet result = q.execute();
		Assert.areEqual(1, result.size());
		
		final UntypedContainer container = (UntypedContainer)result.next();
		final Item item = (Item)container.iterator().next();
		Assert.areEqual("foo", item.getName());
	}

}
