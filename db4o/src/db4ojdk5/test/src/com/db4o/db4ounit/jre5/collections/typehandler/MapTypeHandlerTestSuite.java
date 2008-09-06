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

import com.db4o.db4ounit.jre5.collections.typehandler.ListTypeHandlerTestVariables.*;
import com.db4o.internal.*;
import com.db4o.query.*;
import com.db4o.typehandlers.*;

import db4ounit.extensions.*;
import db4ounit.extensions.fixtures.*;
import db4ounit.fixtures.*;

public class MapTypeHandlerTestSuite extends FixtureBasedTestSuite implements Db4oTestCase  {

	@Override
	public FixtureProvider[] fixtureProviders() {
		return new FixtureProvider[]{
				new Db4oFixtureProvider(),
				MapTypeHandlerTestVariables.MAP_FIXTURE_PROVIDER,
				MapTypeHandlerTestVariables.MAP_KEYS_PROVIDER,
				MapTypeHandlerTestVariables.MAP_VALUES_PROVIDER,
				MapTypeHandlerTestVariables.TYPEHANDLER_FIXTURE_PROVIDER,
		};
	}

	@Override
	public Class[] testUnits() {
		return new Class[]{
			MapTypeHandlerUnitTestCase.class,
		};
	}
	
	public static class MapTypeHandlerUnitTestCase extends TypeHandlerUnitTest {
		
		protected void fillItem(Object item) {
			fillMapItem(item);
		}

		protected void assertContent(Object item) {
			assertMapContent(item);
		}

		protected AbstractItemFactory itemFactory() {
			return (AbstractItemFactory) MapTypeHandlerTestVariables.MAP_IMPLEMENTATION.value();
		}
		
		protected TypeHandler4 typeHandler() {
		    return (TypeHandler4) MapTypeHandlerTestVariables.MAP_TYPEHANDER.value();
		}
		
		protected ListTypeHandlerTestElementsSpec elementsSpec() {
			return (ListTypeHandlerTestElementsSpec) MapTypeHandlerTestVariables.MAP_KEYS_SPEC.value();
		}

		protected void assertCompareItems(Object element, boolean successful) {
			Query q = newQuery();
	    	Object item = itemFactory().newItem();
	    	Map map = mapFromItem(item);
			map.put(element, values()[0]);
	    	q.constrain(item);
			assertQueryResult(q, successful);
		}    
		
		//TODO: remove when COR-1311 solved 
		public void testSuccessfulQuery() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testSuccessfulQuery();
		}
		
		//TODO: remove when COR-1311 solved 
		public void testFailingQuery() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testFailingQuery();
		}
		
		//TODO: remove when COR-1311 solved 
		public void testFailingContainsQuery() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testFailingContainsQuery();
		}
		
		//TODO: remove when COR-1311 solved 
		public void testFailingCompareItems() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testFailingCompareItems();
		}
		
		//TODO: remove when COR-1311 solved 
		public void testCompareItems() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testCompareItems();
		}
		
		//TODO: remove when COR-1311 solved 
		public void testSuccessfulContainsQuery() throws Exception {
			if(elements()[0] instanceof FirstClassElement){
				return;
			}
			super.testSuccessfulContainsQuery();
		}
		
	}

}
