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

import com.db4o.typehandlers.*;

import db4ounit.fixtures.*;

public class MapTypeHandlerTestVariables {

	public final static FixtureVariable MAP_IMPLEMENTATION = new FixtureVariable("map");
	
	public final static FixtureVariable MAP_TYPEHANDER = new FixtureVariable("typehandler");

	
	public final static FixtureProvider MAP_FIXTURE_PROVIDER = 
		new SimpleFixtureProvider(
			MAP_IMPLEMENTATION,
			new Object[] {
				new UntypedHashMapItemFactory(),
				new TypedHashMapItemFactory(),
				new HashtableItemFactory(),
				new NamedHashMapItemFactory(),
			}
		);
	
	public final static FixtureProvider TYPEHANDLER_FIXTURE_PROVIDER =
	        new SimpleFixtureProvider(MAP_TYPEHANDER,
	            new Object[]{
	                null,
	                new MapTypeHandler(),
	            }
	        );

	public static final FixtureVariable MAP_KEYS_SPEC = new FixtureVariable("keys");
	
	public static final FixtureProvider MAP_KEYS_PROVIDER = 
		new SimpleFixtureProvider(
				MAP_KEYS_SPEC,
				new Object[]{
					ListTypeHandlerTestVariables.STRING_ELEMENTS_SPEC,
					ListTypeHandlerTestVariables.INT_ELEMENTS_SPEC,
					ListTypeHandlerTestVariables.OBJECT_ELEMENTS_SPEC,
				}
		);

	public static final FixtureVariable MAP_VALUES_SPEC = new FixtureVariable("values");

	public static final FixtureProvider MAP_VALUES_PROVIDER = 
		new SimpleFixtureProvider(
				MAP_VALUES_SPEC,
				new Object[]{
					ListTypeHandlerTestVariables.STRING_ELEMENTS_SPEC,
					ListTypeHandlerTestVariables.INT_ELEMENTS_SPEC,
					ListTypeHandlerTestVariables.OBJECT_ELEMENTS_SPEC,
				}
		);

	private static class HashtableItemFactory extends AbstractMapItemFactory implements Labeled {
		
		private static class Item {
			public Map _map = new Hashtable();
		}

		public Class containerClass() {
			return Hashtable.class;
		}

		public Class itemClass() {
			return Item.class;
		}

		public Object newItem() {
			return new Item();
		}

		public String label() {
			return "Hashtable";
		}
		
	}
	
	private static class TypedHashMapItemFactory extends AbstractMapItemFactory implements Labeled {
		
		private static class Item {
			public HashMap _map = new HashMap();
		}

		public Class containerClass() {
			return HashMap.class;
		}

		public Class itemClass() {
			return Item.class;
		}

		public Object newItem() {
			return new Item();
		}

		public String label() {
			return "HashMap Typed";
		}
		
	}
	
	private static class NamedHashMapItemFactory extends AbstractMapItemFactory implements Labeled {
		
		private static class Item {
			public Map _map = new NamedHashMap();
		}

		public Class containerClass() {
			return NamedHashMap.class;
		}

		public Class itemClass() {
			return Item.class;
		}

		public Object newItem() {
			return new Item();
		}

		public String label() {
			return "NamedHashMap";
		}
		
	}
			
	private static class UntypedHashMapItemFactory extends AbstractMapItemFactory implements Labeled{
		
		private static class Item {
			public Map _map = new HashMap();
		}

		public Class containerClass() {
			return HashMap.class;
		}

		public Class itemClass() {
			return Item.class;
		}

		public Object newItem() {
			return new Item();
		}

		public String label() {
			return "HashMap Untyped";
		}
		
	}
}
