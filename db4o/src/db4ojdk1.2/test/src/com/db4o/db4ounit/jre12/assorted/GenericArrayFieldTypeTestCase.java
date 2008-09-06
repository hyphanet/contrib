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
package com.db4o.db4ounit.jre12.assorted;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.io.*;
import com.db4o.reflect.*;
import com.db4o.reflect.jdk.*;
import com.db4o.test.util.*;

import db4ounit.*;

public class GenericArrayFieldTypeTestCase implements TestLifeCycle {
	
	public static class SubData {
		public int _id;

		public SubData(int id) {
			_id = id;
		}
	}
	
	public static class Data {
		public SubData[] _data;

		public Data(SubData[] data) {
			_data = data;
		}
	}

	private static final String FILENAME = Path4.getTempFileName();
	
	public void testGenericArrayFieldType() {
		Class[] excludedClasses = new Class[]{
				Data.class,
				SubData.class,
		};
		ClassLoader loader = new ExcludingClassLoader(getClass().getClassLoader(), excludedClasses);
		Configuration config = Db4o.newConfiguration();
		config.reflectWith(new JdkReflector(loader));
		ObjectContainer db = Db4o.openFile(config, FILENAME);
		try {
			ReflectClass dataClazz = db.ext().reflector().forName(Data.class.getName());
			ReflectField field = dataClazz.getDeclaredField("_data");
			ReflectClass fieldType = field.getFieldType();
			Assert.isTrue(fieldType.isArray());
			ReflectClass componentType = fieldType.getComponentType();
			Assert.areEqual(SubData.class.getName(), componentType.getName());
		}
		finally {
			db.close();
		}
	}

	private void store() {
		ObjectContainer db = Db4o.openFile(Db4o.newConfiguration(), FILENAME);
		SubData[] subData = {
			new SubData(1),
			new SubData(42),
		};
		Data data = new Data(subData);
		db.store(data);
		db.close();
	}

	private void deleteFile() {
		File4.delete(FILENAME);
	}

	public void setUp() throws Exception {
		deleteFile();
		store();
	}

	public void tearDown() throws Exception {
		deleteFile();
	}
}
