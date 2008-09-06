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

import com.db4o.query.*;

import db4ounit.extensions.*;
import db4ounit.extensions.fixtures.*;
import db4ounit.fixtures.*;

@SuppressWarnings("unchecked")
public class ListTypeHandlerStringElementTestSuite extends FixtureBasedTestSuite implements Db4oTestCase {
	
	
	public FixtureProvider[] fixtureProviders() {
		ListTypeHandlerTestElementsSpec[] elementSpecs = {
				ListTypeHandlerTestVariables.STRING_ELEMENTS_SPEC,
		};
		return new FixtureProvider[] {
			new Db4oFixtureProvider(),
			ListTypeHandlerTestVariables.LIST_FIXTURE_PROVIDER,
			new SimpleFixtureProvider(
				ListTypeHandlerTestVariables.ELEMENTS_SPEC,
				elementSpecs
			),
			ListTypeHandlerTestVariables.TYPEHANDLER_FIXTURE_PROVIDER,
		};
	}

	public Class[] testUnits() { 
		return new Class[] {
			ListTypeHandlerStringElementTestUnit.class,
		};
	}

	public static class ListTypeHandlerStringElementTestUnit extends ListTypeHandlerTestUnitBase {
		
		public void testSuccessfulEndsWithQuery() throws Exception {
	    	Query q = newQuery(itemFactory().itemClass());
	    	q.descend(AbstractItemFactory.LIST_FIELD_NAME).constrain(successfulEndChar()).endsWith(false);
	    	assertQueryResult(q, true);
		}
		
		public void testFailingEndsWithQuery() throws Exception {
	    	Query q = newQuery(itemFactory().itemClass());
	    	q.descend(AbstractItemFactory.LIST_FIELD_NAME).constrain(failingEndChar()).endsWith(false);
	    	assertQueryResult(q, false);
		}

		private String successfulEndChar() {
			return String.valueOf(endChar());
		}

		private String failingEndChar() {
			return String.valueOf(endChar() + 1);
		}

		private char endChar() {
			String str = (String)elements()[0];
			return str.charAt(str.length()-1);
		}
	}

}
