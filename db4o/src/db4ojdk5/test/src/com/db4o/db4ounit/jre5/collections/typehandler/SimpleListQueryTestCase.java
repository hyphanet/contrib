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
package com.db4o.db4ounit.jre5.collections.typehandler;

import java.util.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.internal.*;
import com.db4o.query.*;
import com.db4o.typehandlers.*;

import db4ounit.*;
import db4ounit.extensions.*;

/**
 * @exclude
 */
public class SimpleListQueryTestCase extends AbstractDb4oTestCase {
	
	public static class Item {
		public List list;
	}
	
	public static class FirstClassElement {
		
		public String name;
		
		public FirstClassElement(String name_){
			name = name_;
		}
		
		public boolean equals(Object obj) {
			if(! (obj instanceof FirstClassElement)){
				return false;
			}
			FirstClassElement other = (FirstClassElement) obj;
			if(name == null){
				return other.name == null;
			}
			return name.equals(other.name);
		}
		
	}
	
	static final Object[] DATA = new Object[]{
		"one",
		"two",
		new Integer(1),
		new Integer(2),
		new Integer(42),
		new FirstClassElement("one"),
		new FirstClassElement("fortytwo"),
		
	};
	
	
	
	protected void configure(Configuration config) throws Exception {
		config.registerTypeHandler(
			new SingleClassTypeHandlerPredicate(ArrayList.class), 
			new ListTypeHandler());
		config.objectClass(Item.class).cascadeOnDelete(true);
	}
	
	protected void store() throws Exception {
		for (int i = 0; i < DATA.length; i++) {
			storeItem(DATA[i]);
		}
	}
	
	private void storeItem(Object listElement){
		Item item = new Item();
		item.list = new ArrayList();
		item.list.add(listElement);
		store(item);
	}
	
	public void testListConstrainQuery() {
		for (int i = 0; i < DATA.length; i++) {
			assertSingleElementQuery(DATA[i]);
		}
	}

	private void assertSingleElementQuery(Object element) {
		Query q = db().query();
		q.constrain(Item.class);
		q.descend("list").constrain(element);
		assertSingleElementQueryResult(q, element);
	}

	private void assertSingleElementQueryResult(Query query, Object element) {
		ObjectSet objectSet = query.execute();
		Assert.areEqual(1, objectSet.size());
		Item item = (Item) objectSet.next();
		Assert.areEqual(element, item.list.get(0));
	}

}
