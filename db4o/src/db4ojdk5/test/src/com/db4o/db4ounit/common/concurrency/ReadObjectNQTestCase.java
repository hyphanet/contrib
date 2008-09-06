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
package com.db4o.db4ounit.common.concurrency;

import com.db4o.*;
import com.db4o.db4ounit.common.persistent.*;
import com.db4o.ext.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class ReadObjectNQTestCase extends Db4oClientServerTestCase {

	public static void main(String[] args) {
		new ReadObjectNQTestCase().runConcurrency();
	}

	private static String testString = "simple test string";

	protected void store() throws Exception {
		for (int i = 0; i < threadCount(); i++) {
			store(new SimpleObject(testString + i, i));
		}
	}

	public void concReadSameObject(ExtObjectContainer oc) throws Exception {
		int mid = threadCount() / 2;
		final SimpleObject expected = new SimpleObject(testString + mid, mid);
		ObjectSet result = oc.query(new MyPredicate(expected));
		Assert.areEqual(1, result.size());
		Assert.areEqual(expected, result.next());
	}

	public void concReadDifferentObject(ExtObjectContainer oc, int seq)
			throws Exception {
		final SimpleObject expected = new SimpleObject(testString + seq, seq);
		ObjectSet result = oc.query(new MyPredicate(expected));
		Assert.areEqual(1, result.size());
		Assert.areEqual(expected, result.next());
	}

	public static class MyPredicate extends Predicate<SimpleObject> {
		SimpleObject expected;

		MyPredicate(SimpleObject o) {
			this.expected = o;
		}

		public boolean match(SimpleObject candidate) {
			return expected.equals(candidate);
		}
	}

}

