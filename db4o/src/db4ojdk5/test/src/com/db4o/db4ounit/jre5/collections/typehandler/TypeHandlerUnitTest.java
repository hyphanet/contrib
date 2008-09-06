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

import com.db4o.db4ounit.jre5.collections.typehandler.ListTypeHandlerTestVariables.*;
import com.db4o.query.*;

import db4ounit.extensions.*;

public abstract class TypeHandlerUnitTest extends TypeHandlerTestUnitBase {

	protected abstract void assertCompareItems(Object element, boolean successful);

	protected void assertQuery(boolean successful, Object element, boolean withContains) {
		Query q = newQuery(itemFactory().itemClass());
		Constraint constraint = q.descend(itemFactory().fieldName()).constrain(element);
		if(withContains) {
			constraint.contains();
		}
		assertQueryResult(q, successful);
	}
	
	public void testRetrieveInstance() {
	    Object item = retrieveItemInstance();
	    assertContent(item);
	}

    protected Object retrieveItemInstance() {
        Class itemClass = itemFactory().itemClass();
	    Object item = retrieveOnlyInstance(itemClass);
        return item;
    }
	
	public void testSuccessfulQuery() throws Exception {
		assertQuery(true, elements()[0], false);
	}

	public void testFailingQuery() throws Exception {
		assertQuery(false, notContained(), false);
	}

	public void testSuccessfulContainsQuery() throws Exception {
		assertQuery(true, elements()[0], true);
	}

	public void testFailingContainsQuery() throws Exception {
		assertQuery(false, notContained(), true);
	}

	public void testCompareItems() throws Exception {
		assertCompareItems(elements()[0], true);
	}

	public void testFailingCompareItems() throws Exception {
		assertCompareItems(notContained(), false);
	}

	public void testDeletion() throws Exception {
	    assertFirstClassElementCount(elements().length);
	    Object item = retrieveOnlyInstance(itemFactory().itemClass());
	    db().delete(item);
	    db().purge();
	    Db4oAssert.persistedCount(0, itemFactory().itemClass());
	    assertFirstClassElementCount(0);
	}

	protected void assertFirstClassElementCount(int expected) {
		if(!isFirstClass(elementClass())) {
			return;
		}
		Db4oAssert.persistedCount(expected, elementClass());
	}

	private boolean isFirstClass(Class elementClass) {
		return FirstClassElement.class == elementClass;
	}

}
