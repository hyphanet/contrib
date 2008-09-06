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
package com.db4o.util.io.spikes;

import java.io.File;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.io.IoAdapter;
import com.db4o.util.io.NIOFileAdapter;
import com.db4o.util.io.win32.Win32IoAdapter;

/**
 * @exclude
 */
public class SimpleIoBenchmark {
	
	private static final String DBFILENAME = "SimpleIoBenchmark.yap";
	private static final int ITERATIONS = 10000;

	public static void main(String[] args) {
		
		for (int i=0; i<3; ++i) {
			System.out.println("*******************");
			test("Default IO adapter", null);
			test("NIOFileadapter", new NIOFileAdapter(1024*32, 16));
			test("Win32IoAdapter", new Win32IoAdapter());
		}
		
	}
	
	private static void test(String name, IoAdapter adapter) {
		if (null != adapter) {
			Db4o.configure().io(adapter);
		}
		
		long start = System.currentTimeMillis();
		store();
		query();
		long elapsed = System.currentTimeMillis() - start;
		
		// System.gc is necessary to circumvent
		//      http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
		System.gc();

		
		System.out.println(name);
		System.out.println("\t" + elapsed + "ms");
		File file = new File(DBFILENAME);
		System.out.println("\tFile size is " + file.length() + " bytes.");
		System.out.println("\t" + file.length()/elapsed + " bytes/ms");
		if (!file.delete()) {
			System.err.println("Unable to delete '" + DBFILENAME + "'");
		}
	}
	

	private static void query() {
		ObjectContainer db = Db4o.openFile(DBFILENAME);
		try {
			ObjectSet set = db.queryByExample(TestDummy.class);
			if (ITERATIONS != set.size()) {
				System.err.println("Expected: " + ITERATIONS + ", actual: " + set.size());
			}
		} finally {
			db.close();
		}
	}

	private static void store() {
		
		ObjectContainer db = Db4o.openFile(DBFILENAME);
		try {
			for (int i=0; i<ITERATIONS; ++i) {
				db.store(new TestDummy("Dummy " + i));
				if (0 == i % 10) {
					db.commit();
				}
			}
		} finally {
			db.close();
		}
	}
}