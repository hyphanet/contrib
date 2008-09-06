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
package com.db4o.db4ounit.jre12.querying;

import java.util.*;

import com.db4o.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class ObjectSetCollectionAPITestCase extends AbstractDb4oTestCase {

	private static final int ID = 42;

	private static class Data {
		private int _id;
		
		public Data(int id) {
			_id = id;
			use(_id);
		}

		private void use(int id) {
		}
	}
	
	protected void store() throws Exception {
		store(new Data(ID));
	}
	
	public void testIteratorForClassQuery() {
		assertIteratorRepeat(classQuery(), new IteratorAssertion());
	}

	public void testIteratorForCustomQuery() {
		assertIteratorRepeat(customQuery(), new IteratorAssertion());
	}

	public void testToArrayForClassQuery() {
		assertIteratorRepeat(classQuery(), new ToArrayAssertion());
	}

	public void testToArrayForCustomQuery() {
		assertIteratorRepeat(customQuery(), new ToArrayAssertion());
	}

	private Query classQuery() {
		return newQuery(Data.class);
	}

	private Query customQuery() {
		Query query = classQuery();
		query.descend("_id").constrain(new Integer(ID));
		return query;
	}

	private void assertIteratorRepeat(Query query, ObjectSetAssertion assertion) {
		ObjectSet result = query.execute();
		for(int round = 0; round < 2; round++) {
			assertion.check(result);
		}
	}

	private static interface ObjectSetAssertion {
		void check(ObjectSet result);
	}
	
	private static class IteratorAssertion implements ObjectSetAssertion {
		public void check(ObjectSet result) {
			Iterator iter = result.iterator();
			int count = 0;
			while(iter.hasNext()) {
				Assert.isNotNull(iter.next());
				count++;
			}
			Assert.areEqual(1, count);
		}
	}

	private static class ToArrayAssertion implements ObjectSetAssertion {
		public void check(ObjectSet result) {
			Object[] arr = result.toArray();
			Assert.areEqual(1, arr.length);
			Assert.isNotNull(arr[0]);
		}
	}

}
