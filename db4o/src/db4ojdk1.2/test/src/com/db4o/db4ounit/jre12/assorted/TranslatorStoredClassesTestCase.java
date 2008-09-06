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

import java.io.*;
import java.math.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.io.*;

import db4ounit.*;

public class TranslatorStoredClassesTestCase implements TestCase {

	private final static String FILENAME=Path4.getTempFileName();
	
	public static class DataRawChild implements Serializable {
		public int _id;

		public DataRawChild(int id) {
			_id=id;
		}
	}

	public static class DataRawParent {
		public DataRawChild _child;

		public DataRawParent(int id) {
			_child=new DataRawChild(id);
		}
	}

	public static class DataBigDecimal {
		public BigDecimal _bd;

		public DataBigDecimal(int id) {
			_bd=new BigDecimal(String.valueOf(id));
		}
	}
	
	public void testBigDecimal() {
		assertStoredClassesAfterTranslator(BigDecimal.class,new DataBigDecimal(42));
	}

	public void testRaw() {
		assertStoredClassesAfterTranslator(DataRawChild.class,new DataRawParent(42));
	}

	public void assertStoredClassesAfterTranslator(Class translated,Object data) {
		createFile(translated,data);
		check(translated);
	}

	private static void createFile(Class translated,Object data) {
		new File(FILENAME).delete();
        ObjectContainer server = db(translated,new TSerializable());
        server.store(data);
        server.close();
	}

	private static void check(Class translated) {
		ObjectContainer db=db(translated,null);
		db.ext().storedClasses();
		db.close();
	}

	private static ObjectContainer db(Class translated,ObjectTranslator translator) {
		Configuration config=Db4o.newConfiguration();
		config.objectClass(translated).translate(translator);
		return Db4o.openFile(config,FILENAME);
	}

}
