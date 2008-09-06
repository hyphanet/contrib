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
package com.db4o.db4ounit.jre5;

import db4ounit.extensions.*;

public class AllTestsDb4oUnitJdk5 extends Db4oTestSuite {

	public static void main(String[] args) {
		System.exit(new AllTestsDb4oUnitJdk5().runAll());
//		System.exit(new AllTestsDb4oUnitJdk5().runSolo());
	}

	@Override
	protected Class[] testCases() {
		return new Class[] {
			com.db4o.db4ounit.common.assorted.AllTestsJdk5.class,
			com.db4o.db4ounit.jre5.annotation.AllTests.class,
			com.db4o.db4ounit.jre5.collections.AllTests.class,
			com.db4o.db4ounit.jre5.enums.AllTests.class,
			com.db4o.db4ounit.jre5.generic.AllTests.class,
			com.db4o.db4ounit.jre5.query.AllTests.class,
			com.db4o.db4ounit.jre12.AllTestsJdk1_2.class,
		};
	}

}
