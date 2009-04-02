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

import com.db4o.*;
import com.db4o.foundation.*;

public class CascadeToHashMap {

	HashMap hm;

	public void configure() {
		Db4o.configure().objectClass(this).cascadeOnUpdate(true);
		Db4o.configure().objectClass(this).cascadeOnDelete(true);
	}

	public void store() {
		Test.deleteAllInstances(this);
		Test.deleteAllInstances(new Atom());
		CascadeToHashMap cth = new CascadeToHashMap();
		cth.hm = new HashMap();
		cth.hm.put("key1", new Atom("stored1"));
		cth.hm.put("key2", new Atom(new Atom("storedChild1"), "stored2"));
		Test.store(cth);
	}

	public void test() {

		Test.forEach(this, new Visitor4() {
			public void visit(Object obj) {
				CascadeToHashMap cth = (CascadeToHashMap) obj;
				cth.hm.put("key1", new Atom("updated1"));
				Atom atom = (Atom)cth.hm.get("key2"); 
				atom.name = "updated2";
				Test.store(cth);
			}
		});
		Test.reOpen();
		
		Test.forEach(this, new Visitor4() {
			public void visit(Object obj) {
				CascadeToHashMap cth = (CascadeToHashMap) obj;
				Atom atom = (Atom)cth.hm.get("key1");
				Test.ensure(atom.name.equals("updated1"));
				atom = (Atom)cth.hm.get("key2");
				Test.ensure(atom.name.equals("updated2"));
			}
		});
		
		// Cascade-On-Delete Test: We only want one atom to remain.
		
		Test.reOpen();
		Test.deleteAllInstances(this);
		Test.ensureOccurrences(new Atom(), 1);
	}
}
