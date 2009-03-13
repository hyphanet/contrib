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
package com.db4o.db4ounit.jre12.assorted;

import java.util.*;

import com.db4o.*;
import com.db4o.db4ounit.common.assorted.*;

import db4ounit.*;

public class UnavailableClassAsTreeSetElementTestCase extends UnavailableClassTestCaseBase {
	
	public static class Item implements Comparable {
		private int _value;

		public Item(int value) {
			_value = value;
        }

		public int compareTo(Object o) {
			return _value - ((Item)o)._value;
        }
	}
	
	public static class Parent {
		Set _items = new TreeSet();
		
		public Parent(Item[] items) {
			for (int i = 0; i < items.length; i++) {
				_items.add(items[i]);
			}
		}
	}
	
	protected void store() throws Exception {
	    store(new Parent(new Item[] { new Item(-1), new Item(42) }));
	}
	
	public void testDefragment() throws Exception {
		reopenHidingItemClass();
		defragment();
	}

	private void reopenHidingItemClass() throws Exception {
		reopenHidingClasses(new Class[] { Item.class });
	}
	
	public void testUnavailableItem() throws Exception {
		reopenHidingItemClass();
		
		final ObjectSet result = newQuery().execute();
		Assert.areEqual(4, result.size());
		while (result.hasNext()) {
	        Assert.isNotNull(result.next());
        }
	}

}
