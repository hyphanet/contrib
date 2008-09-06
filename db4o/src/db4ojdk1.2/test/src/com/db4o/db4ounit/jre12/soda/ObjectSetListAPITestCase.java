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
package com.db4o.db4ounit.jre12.soda;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class ObjectSetListAPITestCase extends AbstractDb4oTestCase {

	private static final int NUMDATA = 1000;
	
	private static class Data {
		private int _id;

		public Data(int id) {
			_id = id;
			use(_id);
		}

		private void use(int id) {
		}
	}

	protected void configure(Configuration config) throws Exception {
		config.queries().evaluationMode(QueryEvaluationMode.LAZY);
	}
	
	protected void store() throws Exception {
		for(int i = 0; i < NUMDATA; i++) {
			store(new Data(i));
		}
	}
	
	public void testOutOfBounds() {
		final ObjectSet result = result();
		Assert.expect(IndexOutOfBoundsException.class, new CodeBlock() {
			public void run() throws Throwable {
				try {
					result.get(NUMDATA);
				}
				catch(Db4oException exc) {
					exc.printStackTrace();
				}
			}
		});
	}

	private ObjectSet result() {
		Query query = newQuery(Data.class);
		query.descend("_id").constrain(new Integer(Integer.MAX_VALUE)).not();
		final ObjectSet result = query.execute();
		return result;
	}
	
	public static void main(String[] args) {
		new ObjectSetListAPITestCase().runAll();
	}
}
