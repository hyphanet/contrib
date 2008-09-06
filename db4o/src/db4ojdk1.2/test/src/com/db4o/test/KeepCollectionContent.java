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

import java.util.*;

public class KeepCollectionContent {
	
	public void store(){
		Test.deleteAllInstances(new ComparableAtom());
		Test.deleteAllInstances(new HashMap());
		Test.deleteAllInstances(new Hashtable());
		Test.deleteAllInstances(new ArrayList());
		Test.deleteAllInstances(new Vector());
		Test.deleteAllInstances(new TreeMap());
		HashMap hm = new HashMap();
		hm.put(new ComparableAtom(), new ComparableAtom());
		Test.store(hm);
		Hashtable ht = new Hashtable();
		ht.put(new ComparableAtom(), new ComparableAtom());
		Test.store(ht);
		ArrayList al = new ArrayList();
		al.add(new ComparableAtom());
		Test.store(al);
		Vector vec = new Vector();
		vec.add(new ComparableAtom());
		Test.store(vec);
		TreeMap tm = new TreeMap();
		tm.put(new ComparableAtom(), new ComparableAtom());
		Test.store(tm);
	}
	
	public void test(){
		Test.deleteAllInstances(new HashMap());
		Test.deleteAllInstances(new Hashtable());
		Test.deleteAllInstances(new ArrayList());
		Test.deleteAllInstances(new Vector());
		Test.deleteAllInstances(new TreeMap());
		Test.ensureOccurrences(new ComparableAtom(), 8);
		// System.out.println(Test.occurrences(new ComparableAtom()));
		
	}

}
