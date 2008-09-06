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

import com.db4o.internal.*;
import com.db4o.typehandlers.*;

import db4ounit.fixtures.*;

@SuppressWarnings("unchecked")
public final class ListTypeHandlerTestVariables {
	
	public final static FixtureVariable LIST_IMPLEMENTATION = new FixtureVariable("list");
	
	public final static FixtureVariable ELEMENTS_SPEC = new FixtureVariable("elements");
	
	public final static FixtureVariable LIST_TYPEHANDER = new FixtureVariable("typehandler");
	
	
	public final static FixtureProvider LIST_FIXTURE_PROVIDER = 
			new SimpleFixtureProvider(
				LIST_IMPLEMENTATION,
				new Object[] {
						new ArrayListItemFactory(),
						new LinkedListItemFactory(),
						new ListItemFactory(),
						new NamedArrayListItemFactory(),
				}
			);
	
	public final static FixtureProvider TYPEHANDLER_FIXTURE_PROVIDER = 
	    	new SimpleFixtureProvider(LIST_TYPEHANDER,
	        new Object[]{
	    		new ListTypeHandler(), 
	            new EmbeddedListTypeHandler(),
	        }
	    );

	public final static ListTypeHandlerTestElementsSpec STRING_ELEMENTS_SPEC = 
		new ListTypeHandlerTestElementsSpec(new Object[]{ "zero", "one" }, "two", "zzz");
	public final static ListTypeHandlerTestElementsSpec INT_ELEMENTS_SPEC =
		new ListTypeHandlerTestElementsSpec(new Object[]{ new Integer(0), new Integer(1) }, new Integer(2), new Integer(Integer.MAX_VALUE));
	public final static ListTypeHandlerTestElementsSpec OBJECT_ELEMENTS_SPEC =
		new ListTypeHandlerTestElementsSpec(new Object[]{ new FirstClassElement(0), new FirstClassElement(1) }, new FirstClassElement(2), null);
	
	private ListTypeHandlerTestVariables() {
	}

	public static class FirstClassElement {

		public int _id;
		
		public FirstClassElement(int id) {
			_id = id;
		}
		
		public boolean equals(Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null || getClass() != obj.getClass()) {
				return false;
			}
			FirstClassElement other = (FirstClassElement) obj;
			return _id == other._id;
		}
		
		public int hashCode() {
			return _id;
		}
		
		public String toString() {
			return "FCE#" + _id;
		}

	}
	
	private static class ArrayListItemFactory extends AbstractListItemFactory implements Labeled {
		private static class Item {
			public ArrayList _list = new ArrayList();
		}
		
		public Object newItem() {
			return new Item();
		}

		public Class itemClass() {
			return Item.class;
		}

		public Class containerClass() {
			return ArrayList.class;
		}

		public String label() {
			return "ArrayList";
		}
	}

	private static class LinkedListItemFactory extends AbstractListItemFactory implements Labeled {
		private static class Item {
			public LinkedList _list = new LinkedList();
		}
		
		public Object newItem() {
			return new Item();
		}

		public Class itemClass() {
			return Item.class;
		}

		public Class containerClass() {
			return LinkedList.class;
		}

		public String label() {
			return "LinkedList";
		}
	}

	private static class ListItemFactory extends AbstractListItemFactory implements Labeled {
		private static class Item {
			public List _list = new LinkedList();
		}
		
		public Object newItem() {
			return new Item();
		}

		public Class itemClass() {
			return Item.class;
		}

		public Class containerClass() {
			return LinkedList.class;
		}

		public String label() {
			return "[Linked]List";
		}
	}
	
	private static class NamedArrayListItemFactory extends AbstractListItemFactory implements Labeled {
	    
	    private static class Item {
	        public List _list = new NamedArrayList();
	    }
	    
	    public Object newItem() {
	        return new Item();
	    }

	    public Class itemClass() {
	        return NamedArrayListItemFactory.Item.class;
	    }

	    public Class containerClass() {
	        return NamedArrayList.class;
	    }

	    public String label() {
	        return "NamedArrayList";
	    }
	}

}
