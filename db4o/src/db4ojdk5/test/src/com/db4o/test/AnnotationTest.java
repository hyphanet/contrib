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
package com.db4o.test;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.ext.ExtDb4o;
import com.db4o.ext.MemoryFile;

public class AnnotationTest {

	public static void main(String[] args) {
		MemoryFile file = new MemoryFile();
		ObjectContainer db = ExtDb4o.openMemoryFile(file);
		try {
			fillDBwithSheeps(db);
			db = ExtDb4o.openMemoryFile(file);
			retriveSheep(db);

			db = ExtDb4o.openMemoryFile(file);
			System.out.println("\n");
			updateSheep(db);

			db = ExtDb4o.openMemoryFile(file);
			System.out.println("\n");
			deleteSheep(db);

		} finally {
			db.close();
		}
	}

	private static void updateSheep(ObjectContainer db) {
		System.out.println("updating annotated sheeps");
		Sheep sheep = (Sheep) db.queryByExample(new Sheep("test7", null)).next();
		sheep.setName(sheep.getName() + "_new");
		db.store(sheep);
		ObjectSet<Sheep> result = db.queryByExample(Sheep.class);
		while (result.hasNext()) {
			System.out.println(result.next());
		}

		System.out.println("updating not annotated sheeps");
		SheepNotAnnotated notAnnot = (SheepNotAnnotated) db.queryByExample(
				new SheepNotAnnotated("notAnnotTest7", null)).next();
		notAnnot.setName(notAnnot.getName() + "_new");
		db.store(notAnnot);
		ObjectSet<SheepNotAnnotated> res = db.queryByExample(SheepNotAnnotated.class);
		while (res.hasNext()) {
			System.out.println(res.next());

		}

		db.close();
	}

	private static void deleteSheep(ObjectContainer db) {
		System.out.println("deleting annotated sheeps");
		Sheep sheep = (Sheep) db.queryByExample(new Sheep("test15", null)).next();
		db.delete(sheep);
		ObjectSet<Sheep> result = db.queryByExample(Sheep.class);
		while (result.hasNext()) {
			System.out.println(">>" + (Sheep) result.next());
		}
		System.out.println("deleting not annotated sheeps");
		SheepNotAnnotated notAnnot = (SheepNotAnnotated) db.queryByExample(
				new SheepNotAnnotated("notAnnotTest15", null)).next();
		db.delete(notAnnot);
		ObjectSet<SheepNotAnnotated> res = db.queryByExample(SheepNotAnnotated.class);
		while (res.hasNext()) {
			System.out.println(">>>" + (SheepNotAnnotated) res.next());
		}
		db.commit();
		db.close();
	}

	private static void retriveSheep(ObjectContainer db) {
		System.out.println("retriving annotated sheeps");
		ObjectSet<Sheep> result = db.queryByExample(new Sheep("test23", null));
		System.out.println((Sheep) result.next());

		System.out.println("retriving not annotated sheeps");
		ObjectSet<SheepNotAnnotated> res = db.queryByExample(new SheepNotAnnotated(
				"notAnnotTest23", null));
		System.out.println((SheepNotAnnotated) res.next());
		db.close();
	}

	private static void fillDBwithSheeps(ObjectContainer db) {
		Sheep parent = null;
		for (int i = 0; i < 36; i++) {
			Sheep sheep = new Sheep("test" + i, parent);
			db.store(sheep);
			parent = sheep;
		}

		SheepNotAnnotated notAnnotParent = null;
		for (int j = 0; j < 36; j++) {
			SheepNotAnnotated notAnnotChild = new SheepNotAnnotated(
					"notAnnotTest" + j, notAnnotParent);
			db.store(notAnnotChild);
			notAnnotParent = notAnnotChild;
		}

		db.commit();
		db.close();
	}

}
