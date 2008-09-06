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
package com.db4o.db4ounit.jre12;

import com.db4o.db4ounit.jre12.foundation.*;
import com.db4o.db4ounit.jre12.reflect.*;

import db4ounit.extensions.*;

public class AllTestsJdk1_2 extends Db4oTestSuite {

	public static void main(String[] args) {
		System.exit(new AllTestsJdk1_2().runAll());
    }

	protected Class[] testCases() {
		return new Class[] {
		    
			// FIXME: solve the workspacePath issue and uncomment migration.AllCommonTests.class below
//			com.db4o.db4ounit.common.migration.AllCommonTests.class,
		    
			com.db4o.db4ounit.common.defragment.jdk2only.DefragUnknownClassTestCase.class,
			com.db4o.db4ounit.common.defragment.LegacyDatabaseDefragTestCase.class,
			com.db4o.db4ounit.common.freespace.FreespaceManagerTypeChangeSlotCountTestCase.class,
			com.db4o.db4ounit.common.ta.AllTests.class,
			com.db4o.db4ounit.jre11.AllTests.class,
			com.db4o.db4ounit.jre12.assorted.AllTests.class,
			com.db4o.db4ounit.jre12.blobs.AllTests.class,
			com.db4o.db4ounit.jre12.defragment.AllTests.class,
			com.db4o.db4ounit.jre12.fieldindex.AllTests.class,
			com.db4o.db4ounit.jre12.handlers.AllTests.class,
			com.db4o.db4ounit.jre12.soda.AllTests.class,
			com.db4o.db4ounit.jre12.collections.AllTests.class,
			com.db4o.db4ounit.jre12.collections.facades.AllTests.class,
			com.db4o.db4ounit.jre12.collections.map.AllTests.class,
			com.db4o.db4ounit.jre12.querying.AllTests.class,
			com.db4o.db4ounit.jre12.regression.AllTests.class,
			com.db4o.db4ounit.jre12.ta.AllTests.class,
			com.db4o.db4ounit.jre12.ta.collections.AllTests.class,
			StandaloneNativeReflectorTestCase.class,
			IterableBaseTestCase.class,
		};
	}
}
