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
package com.db4o.db4ounit.jre12.ta;

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.io.*;
import com.db4o.reflect.generic.*;
import com.db4o.reflect.jdk.*;
import com.db4o.ta.*;
import com.db4o.test.util.*;

import db4ounit.*;

public class TAVirtualFieldTestCase implements TestLifeCycle {
	
	private static String FILEPATH = Path4.getTempFileName();
	
private Db4oUUID _uuid;
	
	public static class Item {
		public Item _next;
	}
	
	public void test() {
		ObjectContainer db = Db4o.openFile(config(true), FILEPATH);
		ObjectSet result = db.query(Item.class);
		Assert.areEqual(1, result.size());
		Object obj = result.next();
		Assert.isInstanceOf(GenericObject.class, obj);
		Assert.areEqual(_uuid, db.ext().getObjectInfo(obj).getUUID());
		db.close();
	}

	public void setUp() throws Exception {
		deleteFile();
		ObjectContainer db = Db4o.openFile(config(false), FILEPATH);
		Item obj = new Item();
		db.store(obj);
		_uuid = db.ext().getObjectInfo(obj).getUUID();
		db.close();
	}

	public void tearDown() throws Exception {
		deleteFile();
	}

	private void deleteFile() {
		new File(FILEPATH).delete();
	}
	
	private Configuration config(boolean withCL) {
		Configuration config = Db4o.newConfiguration();
		config.generateUUIDs(ConfigScope.GLOBALLY);
		config.add(new TransparentActivationSupport());
		if(withCL) {
			ClassLoader cl = new ExcludingClassLoader(Item.class.getClassLoader(), new Class[] { Item.class });
			config.reflectWith(new JdkReflector(cl));
		}
		return config;
	}
}