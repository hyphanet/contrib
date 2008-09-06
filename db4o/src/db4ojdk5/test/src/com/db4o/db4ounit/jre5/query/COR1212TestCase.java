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
package com.db4o.db4ounit.jre5.query;

import java.util.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class COR1212TestCase extends AbstractDb4oTestCase {
	
	public static void main(String[] args) {
		new COR1212TestCase().runSolo();
	}

	private final class TestEvaluation implements Evaluation {
		public void evaluate(Candidate candidate) {
			candidate.include(true);				
		}
	}

	public static class Item {
		String name = null;
		Date modified;
		Hashtable hashtable;
		public Item(String name_) {
			name = name_;
			modified = new Date();
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
	}
	
	protected void store() throws Exception {
		for (int i = 0; i < 3; i++) {
			store(new Item("item " + Integer.valueOf(i)));
		}
		
	}
	
	protected void configure(Configuration config) throws Exception {
		config.objectClass(Item.class).cascadeOnDelete(true);
	}
	
	public void _test() throws Exception {
		Query query = newQuery();
		query.constrain(new TestEvaluation());
		query.constrain(Item.class);
		query.descend("name").orderDescending();
		ObjectSet set = query.execute();
		Assert.areEqual(3, set.size());
	}
}
