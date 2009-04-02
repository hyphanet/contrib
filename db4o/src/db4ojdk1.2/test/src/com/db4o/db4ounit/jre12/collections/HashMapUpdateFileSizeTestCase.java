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
package com.db4o.db4ounit.jre12.collections;

import java.io.*;
import java.util.*;

import db4ounit.*;
import db4ounit.extensions.*;
import db4ounit.extensions.fixtures.*;

public class HashMapUpdateFileSizeTestCase extends AbstractDb4oTestCase implements OptOutCS, OptOutDefragSolo, OptOutTA {

	public static void main(String[] args) {
		new HashMapUpdateFileSizeTestCase().runAll();
	}

	protected void store() throws Exception {
		HashMap map = new HashMap();
		fillMap(map);
		store(map);
	}

	private void fillMap(HashMap map) {
		map.put(new Integer(1), "string 1");
		map.put(new Integer(2), "String 2");
	}

	public void testFileSize() throws Exception {
		warmUp();
		assertFileSizeConstant();
	}

	private void assertFileSizeConstant() throws Exception {
		defragment();
		long beforeUpdate = dbSize();
		for (int i = 0; i < 15; ++i) {
			updateMap();
		}
		defragment();
		long afterUpdate = dbSize();
		/*
		 * FIXME: the database file size is uncertain? 
		 * We met similar problem before.
		 */
		Assert.isTrue(afterUpdate - beforeUpdate < 2);
	}

	private void warmUp() throws Exception, IOException {
		for (int i = 0; i < 3; ++i) {
			updateMap();
		}
	}

	private void updateMap() throws Exception, IOException {
		HashMap map = (HashMap) retrieveOnlyInstance(HashMap.class);
		fillMap(map);
		store(map);
		db().commit();
	}
	
	private long dbSize() {
		return db().systemInfo().totalSize();
	}

}
