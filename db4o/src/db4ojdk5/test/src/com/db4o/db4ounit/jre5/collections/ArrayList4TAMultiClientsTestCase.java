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
package com.db4o.db4ounit.jre5.collections;

import java.util.*;

import com.db4o.collections.*;
import com.db4o.ext.*;

import db4ounit.*;
import db4ounit.extensions.fixtures.*;

/**
 * @exclude
 */
public class ArrayList4TAMultiClientsTestCase extends ArrayList4TATestCaseBase implements OptOutSolo {
	public static void main(String[] args) {
		new ArrayList4TAMultiClientsTestCase().runEmbeddedClientServer();
	}
	
	
	private static final ArrayList4Operation <Integer> _addOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			list.add(new Integer(ArrayList4Asserter.CAPACITY));
		}
	};
	
	private static final ArrayList4Operation<Integer> _removeOp = new ArrayList4Operation<Integer>() {
		
		public void operate(ArrayList4<Integer> list) {
			list.remove(0);
		}
	};	
	
	private static final ArrayList4Operation<Integer> _setOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			list.set(0, new Integer(1));
		}
	};	
	
	private static final ArrayList4Operation<Integer> _clearOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			list.clear();
		}
	};	

	private static final ArrayList4Operation<Integer> _containsOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			Assert.isFalse(list.contains(new Integer(ArrayList4Asserter.CAPACITY)));
		}
	};
	
	private static final ArrayList4Operation<Integer> _addAllOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			final Vector<Integer> v = new Vector<Integer>();
			for (int i = 0; i < ArrayList4Asserter.CAPACITY; ++i) {
				v.add(new Integer(ArrayList4Asserter.CAPACITY + i));
			}
			list.addAll(v);
		}
	};
	
	private static final ArrayList4Operation<Integer> _removeRangeOp = new ArrayList4Operation<Integer>() {
		public void operate(ArrayList4<Integer> list) {
			list.subList(ArrayList4Asserter.CAPACITY-10, ArrayList4Asserter.CAPACITY).clear();
		}
	};
	
	public void testAddAdd() throws Exception {
		ArrayList4Operation<Integer> anotherAddOp = new ArrayList4Operation<Integer>() {
			public void operate(ArrayList4<Integer> list) {
				list.add(new Integer(ArrayList4Asserter.CAPACITY + 42));
			}	
		};	
		operate(anotherAddOp, _addOp);
		checkAdd();
	}

	public void testSetAdd() throws Exception {
		operate(_setOp, _addOp);
		checkAdd();
	}
	
	public void testRemoveAdd() throws Exception {
		operate(_removeOp, _addOp);
		checkAdd();
	}
	
	private void checkAdd() throws Exception {
		checkListSizeAndContents(ArrayList4Asserter.CAPACITY+1);
	}
	
	private void checkNotModified() throws Exception {
		checkListSizeAndContents(ArrayList4Asserter.CAPACITY);
	}
	
	private void checkListSizeAndContents(int expectedSize) throws Exception {
		ArrayList4<Integer> list = retrieveAndAssertNullArrayList4();
		Assert.areEqual(expectedSize, list.size());
		for (int i = 0; i < expectedSize; ++i) {
			Assert.areEqual(new Integer(i), list.get(i));
		}
	}

	public void testAddRemove() throws Exception {
		operate(_addOp, _removeOp);
		checkRemove();
	}
	
	public void testsetRemove() throws Exception {
		operate(_setOp, _removeOp);
		checkRemove();
	}
	
	public void testRemoveRemove() throws Exception {
		ArrayList4Operation<Integer> anotherRemoveOp = new ArrayList4Operation<Integer>() {
			public void operate(ArrayList4<Integer> list) {
				list.remove(1);
			}	
		};	
		operate(anotherRemoveOp, _removeOp);
		checkRemove();
	}
	
	private void checkRemove() throws Exception {
		ArrayList4<Integer> list = retrieveAndAssertNullArrayList4();
		Assert.areEqual(ArrayList4Asserter.CAPACITY - 1, list.size());
		for (int i = 0; i < ArrayList4Asserter.CAPACITY - 1; ++i) {
			Assert.areEqual(new Integer(i + 1), list.get(i));
		}
	}

	public void testAddSet() throws Exception {
		operate(_addOp, _setOp);
		checkSet();
	}
	
	public void testRemoveSet() throws Exception {
		operate(_removeOp, _setOp);
		checkSet();
	}
	
	public void testSetSet() throws Exception {
		ArrayList4Operation<Integer>  anotherSetOp = new ArrayList4Operation<Integer>() {
			public void operate(ArrayList4<Integer> list) {
				list.set(0, new Integer(2));
			}	
		};
		operate(anotherSetOp, _setOp);
		checkSet();
	}
	
	public void testClearSet() throws Exception {
		operate(_clearOp, _setOp);
		checkSet();
	}
	
	public void testSetClear() throws Exception {
		operate(_setOp, _clearOp);
		checkClear();
	}
	
	public void testClearRemove() throws Exception {
		operate(_clearOp, _removeOp);
		checkRemove();
	}
	
	public void testRemoveClear() throws Exception {
		operate(_removeOp, _clearOp);
		checkClear();
	}
	
	public void testContainsClear() throws Exception {
		operate(_containsOp, _clearOp);
		checkClear();
	}
	
	public void testContainsSet() throws Exception {
		operate(_containsOp, _setOp);
		checkSet();
	}
	
	public void testContainsRemove() throws Exception {
		operate(_containsOp, _removeOp);
		checkRemove();
	}
	
	public void testContainsAdd() throws Exception {
		operate(_containsOp, _addOp);
		checkAdd();
	}
	
	public void testContainsRemoveRange() throws Exception {
		operate(_containsOp, _removeRangeOp);
		checkRemoveRange();
	}
	
	public void testAddContains() throws Exception {
		operate(_addOp, _containsOp);
		checkNotModified();
	}
	
	public void testSetContains() throws Exception {
		operate(_setOp, _containsOp);
		checkNotModified();
	}
	
	public void testRemoveContains() throws Exception {
		operate(_removeOp, _containsOp);
		checkNotModified();
	}
	
	public void testClearContains() throws Exception {
		operate(_clearOp, _containsOp);
		checkNotModified();
	}
	
	public void testRemoveRangeContains() throws Exception {
		operate(_removeRangeOp, _containsOp);
		checkNotModified();
	}
	
	public void testAddAllSet() throws Exception {
		operate(_addAllOp, _setOp);
		checkSet();		
	}
	
	public void testAddAllClear() throws Exception {
		operate(_addAllOp, _clearOp);
		checkClear();		
	}
	
	public void testAddAllRemove() throws Exception {
		operate(_addAllOp, _removeOp);
		checkRemove();		
	}
	
	public void testAddAllAdd() throws Exception {
		operate(_addAllOp, _addOp);
		checkAdd();		
	}

	public void testSetAddAll() throws Exception {
		operate(_setOp, _addAllOp);
		checkAddAll();		
	}
	
	public void testClearAddAll() throws Exception {
		operate(_clearOp, _addAllOp);
		checkAddAll();		
	}
	
	public void testRemoveAddAll() throws Exception {
		operate(_removeOp, _addAllOp);
		checkAddAll();		
	}
	
	public void testAddAddAll() throws Exception {
		operate(_addOp, _addAllOp);
		checkAddAll();		
	}
	
	public void testRemoveRangeSet() throws Exception {
		operate(_removeRangeOp, _setOp);
		checkSet();		
	}

	public void testRemoveRangeAdd() throws Exception {
		operate(_removeRangeOp, _addOp);
		checkAdd();		
	}

	public void testRemoveRangeClear() throws Exception {
		operate(_removeRangeOp, _clearOp);
		checkClear();		
	}

	public void testRemoveRangeAddAll() throws Exception {
		operate(_removeRangeOp, _addAllOp);
		checkAddAll();		
	}

	public void testRemoveRangeRemove() throws Exception {
		operate(_removeRangeOp, _removeOp);
		checkRemove();		
	}
	
	public void testSetRemoveRange() throws Exception {
		operate(_setOp, _removeRangeOp);
		checkRemoveRange();		
	}
	
	public void testAddRemoveRange() throws Exception {
		operate(_addOp, _removeRangeOp);
		checkRemoveRange();		
	}

	public void testClearRemoveRange() throws Exception {
		operate(_clearOp, _removeRangeOp);
		checkRemoveRange();		
	}
	
	public void testAddAllRemoveRange() throws Exception {
		operate(_addAllOp, _removeRangeOp);
		checkRemoveRange();		
	}
	
	public void testRemoveRemoveRange() throws Exception {
		operate(_removeOp, _removeRangeOp);
		checkRemoveRange();		
	}
	
	private void checkRemoveRange() throws Exception {
		checkListSizeAndContents(ArrayList4Asserter.CAPACITY-10);
	}
	
	private void checkAddAll() throws Exception {
		ArrayList4<Integer> list = retrieveAndAssertNullArrayList4();
		for (int i = 0; i < ArrayList4Asserter.CAPACITY * 2; ++i) {
			Assert.areEqual(new Integer(i), list.get(i));
		}
	}

	private void checkClear() throws Exception {
		ArrayList4<Integer> list = retrieveAndAssertNullArrayList4();
		Assert.areEqual(0, list.size());
	}

	private void checkSet() throws Exception {
		ArrayList4<Integer> list = retrieveAndAssertNullArrayList4();
		Assert.areEqual(ArrayList4Asserter.CAPACITY, list.size());
		Assert.areEqual(new Integer(1), list.get(0));
		for (int i = 1; i < ArrayList4Asserter.CAPACITY; ++i) {
			Assert.areEqual(new Integer(i), list.get(i));
		}
	}
	
	private void operate(ArrayList4Operation <Integer> op1, ArrayList4Operation<Integer> op2) throws Exception {
		ExtObjectContainer client1 = openNewClient();
		ExtObjectContainer client2 = openNewClient();
		ArrayList4<Integer> list1 = retrieveAndAssertNullArrayList4(client1);
		ArrayList4<Integer> list2 = retrieveAndAssertNullArrayList4(client2);
		op1.operate(list1);
		op2.operate(list2);
		client1.store(list1);
		client2.store(list2);
		client1.commit();
		client2.commit();
		client1.close();
		client2.close();
	}

}
