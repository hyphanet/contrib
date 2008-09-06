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
package com.db4o.db4ounit.jre12.defragment;

import java.io.*;

import com.db4o.db4ounit.common.defragment.*;
import com.db4o.defragment.*;
import com.db4o.foundation.*;
import com.db4o.test.util.*;

import db4ounit.*;

/**
 * This one tests common, non-jdk1.2 specific functionality, but requires an
 * ExcludingClassLoader which doesn't work on JDK < 1.2.
 */
public class DefragmentSkipClassTestCase implements TestLifeCycle {

	public void testSkipsClass() throws Exception {
		DefragmentConfig defragConfig = SlotDefragmentFixture.defragConfig(true);
		Defragment.defrag(defragConfig);
		SlotDefragmentFixture.assertDataClassKnown(true);

		defragConfig = SlotDefragmentFixture.defragConfig(true);
		defragConfig.storedClassFilter(new AvailableClassFilter(SlotDefragmentFixture.Data.class.getClassLoader()));
		Defragment.defrag(defragConfig);
		SlotDefragmentFixture.assertDataClassKnown(true);

		defragConfig = SlotDefragmentFixture.defragConfig(true);
		Collection4 excluded=new Collection4();
		excluded.add(SlotDefragmentFixture.Data.class.getName());
		ExcludingClassLoader loader=new ExcludingClassLoader(SlotDefragmentFixture.Data.class.getClassLoader(),excluded);
		defragConfig.storedClassFilter(new AvailableClassFilter(loader));
		Defragment.defrag(defragConfig);
		SlotDefragmentFixture.assertDataClassKnown(false);
	}

	public void setUp() throws Exception {
		new File(SlotDefragmentTestConstants.FILENAME).delete();
		new File(SlotDefragmentTestConstants.BACKUPFILENAME).delete();
		SlotDefragmentFixture.createFile(SlotDefragmentTestConstants.FILENAME);
	}

	public void tearDown() throws Exception {
	}
}
