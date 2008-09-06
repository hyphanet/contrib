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
package com.db4o.db4ounit.jre5.annotation;

import com.db4o.config.annotations.*;
import com.db4o.ext.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class IndexedAnnotationTestCase extends AbstractDb4oTestCase {

	private static class DataAnnotated {
		@Indexed
		private int _id;

		public DataAnnotated(int id) {
			this._id = id;
		}
		
		public String toString() {
			return "DataAnnotated(" + _id + ")";
		}
	}

	private static class DataNotAnnotated {
		private int _id;

		public DataNotAnnotated(int id) {
			this._id = id;
		}
		
		public String toString() {
			return "DataNotAnnotated(" + _id + ")";
		}
	}

	public void testIndexed() throws Exception {
		storeData();
		assertIndexed();
		reopen();
		assertIndexed();
	}

	private void storeData() {
		db().store(new DataAnnotated(42));
		db().store(new DataNotAnnotated(43));
	}

	private void assertIndexed() {
		assertIndexed(DataNotAnnotated.class,false);
		assertIndexed(DataAnnotated.class,true);
	}
	
	private void assertIndexed(Class clazz,boolean expected) {
		StoredClass storedClass=fileSession().storedClass(clazz);
		StoredField storedField=storedClass.storedField("_id",Integer.TYPE);
		Assert.areEqual(expected,storedField.hasIndex());
	}
	
	public static void main(String[] args) {
		new IndexedAnnotationTestCase().runSoloAndClientServer();
	}
}
