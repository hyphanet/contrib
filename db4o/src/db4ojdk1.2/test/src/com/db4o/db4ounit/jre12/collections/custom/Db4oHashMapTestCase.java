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
package com.db4o.db4ounit.jre12.collections.custom;

import java.util.*;

import com.db4o.*;
import com.db4o.db4ounit.common.sampledata.*;
import com.db4o.ext.*;
import com.db4o.query.*;
import com.db4o.types.*;

import db4ounit.*;
import db4ounit.extensions.*;

/**
 * 
 */
public class Db4oHashMapTestCase extends AbstractDb4oTestCase {

	public static class Data {
		Map i_map;
		Db4oHashMapHelper i_helper;
	}
	
	public static class Db4oHashMapHelper {
		public Db4oHashMapHelper i_child;

		public List i_childList;

	}

	static final int COUNT = 10;

	static final String[] DEFAULT = { "wow", "cool", "great" };

	static final String MORE = "more and more ";


	/**
	 * @deprecated using deprecated api
	 */
	protected void store() {
		Data data=new Data();
		data.i_map = db().collections().newHashMap(10);
		setDefaultValues(data.i_map);
		data.i_helper = helper(10);
		store(data);
	}

	/**
	 * @deprecated using deprecated api
	 */
	private Db4oHashMapHelper helper(int a_depth) {
		if (a_depth > 0) {
			Db4oHashMapHelper helper = new Db4oHashMapHelper();
			helper.i_childList = db().collections().newLinkedList();
			helper.i_childList.add("hi");
			helper.i_child = helper(a_depth - 1);
			return helper;
		}
		return null;
	}

	private void setDefaultValues(Map a_map) {
		for (int i = 0; i < DEFAULT.length; i++) {
			a_map.put(DEFAULT[i], new AtomData(DEFAULT[i]));
		}
	}

	public void test() throws Exception {
		Data data=(Data)retrieveOnlyInstance(Data.class);
		
		checkHelper(data.i_helper);
		runElementTest(data,true);

		store(data);
		store(data.i_helper);
		db().commit();

		checkHelper(data.i_helper);
		runElementTest(data,false);
	}

	/**
	 * @deprecated using deprecated api
	 */
	private void runElementTest(Data data,boolean onOriginal) throws Exception {

		Map otherMap = new HashMap();

		AtomData atom = null;

		tDefaultValues(data);

		int itCount = 0;
		Iterator i = data.i_map.keySet().iterator();
		while (i.hasNext()) {
			String str = (String) i.next();
			itCount++;
			atom = (AtomData) data.i_map.get(str);
			Assert.areEqual(str,atom.name);
			otherMap.put(str, atom);
		}
		Assert.areEqual(DEFAULT.length,itCount);

		Assert.areEqual(DEFAULT.length,data.i_map.size());
		Assert.isFalse(data.i_map.isEmpty());
		db().deactivate(data.i_map, Integer.MAX_VALUE);
		data.i_map.get("great");
		Assert.areEqual("great",((AtomData) data.i_map.get("great")).name);
		db().deactivate(data.i_map, Integer.MAX_VALUE);

		if (onOriginal) {
			Query q = newQuery();
			Data template = new Data();
			template.i_map = db().collections().newHashMap(1);
			template.i_map.put("cool", new AtomData("cool"));
			q.constrain(template);
			ObjectSet qResult = q.execute();
			Assert.areEqual(1,qResult.size());
			Assert.areEqual(data,qResult.next());
		}

		Assert.isTrue(data.i_map.keySet().containsAll(otherMap.keySet()));

		Object[] arr = data.i_map.keySet().toArray();
		tDefaultArray(arr);

		String[] cmp = new String[DEFAULT.length];
		System.arraycopy(DEFAULT, 0, cmp, 0, DEFAULT.length);

		i = data.i_map.keySet().iterator();
		while (i.hasNext()) {
			String str = (String) i.next();
			boolean found = false;
			for (int j = 0; j < cmp.length; j++) {
				if (str.equals(cmp[j])) {
					cmp[j] = null;
					found = true;
				}
			}
			Assert.isTrue(found);
		}

		for (int j = 0; j < cmp.length; j++) {
			Assert.isNull(cmp[j]);
		}

		db().deactivate(data.i_map, Integer.MAX_VALUE);
		Assert.isFalse(data.i_map.isEmpty());
		db().deactivate(data.i_map, Integer.MAX_VALUE);
		data.i_map.put("yup", new AtomData("yup"));

		db().store(data.i_map);
		db().store(data.i_map);
		db().store(data.i_map);
		db().store(data.i_helper);
		db().store(data.i_helper);
		db().store(data.i_helper);
		db().commit();

		Assert.areEqual(4,data.i_map.size());

		atom = (AtomData) data.i_map.get("yup");
		Assert.areEqual("yup",atom.name);

		AtomData removed = (AtomData) data.i_map.remove("great");

		Assert.areEqual("great",removed.name);
		Assert.isNull(data.i_map.remove("great"));
		db().deactivate(data.i_map, Integer.MAX_VALUE);
		Assert.areEqual(3,data.i_map.size());

		Assert.isTrue(data.i_map.keySet().removeAll(otherMap.keySet()));
		db().deactivate(data.i_map, Integer.MAX_VALUE);
		Assert.isFalse(data.i_map.keySet().removeAll(otherMap.keySet()));
		Assert.areEqual(1,data.i_map.size());
		i = data.i_map.keySet().iterator();
		String str = (String) i.next();
		Assert.areEqual("yup",str);
		Assert.isFalse(i.hasNext());

		data.i_map.clear();
		Assert.isTrue(data.i_map.isEmpty());
		Assert.areEqual(0,data.i_map.size());

		setDefaultValues(data.i_map);

		String[] strArr = new String[1];
		strArr = (String[]) data.i_map.keySet().toArray(strArr);
		tDefaultArray(strArr);

		data.i_map.clear();
		data.i_map.put("zero", "zero");

		for (int j = 0; j < COUNT; j++) {
			data.i_map.put(MORE + j, new AtomData(MORE + j));
		}
		Assert.areEqual(COUNT + 1,data.i_map.size());
		lookupLast(data);

		db().deactivate(data.i_map, Integer.MAX_VALUE);
		lookupLast(data);
		lookupLast(data);

		reopen();
		restoreMembers(data);
		lookupLast(data);

		atom = new AtomData("double");

		data.i_map.put("double", atom);

		int previousSize = data.i_map.size();

		db().deactivate(data.i_map, Integer.MAX_VALUE);

		AtomData doubleAtom = (AtomData) data.i_map.put("double", new AtomData("double"));
		Assert.areSame(atom,doubleAtom);

		Assert.areEqual(previousSize,data.i_map.size());
		data.i_map.put("double", doubleAtom);

		db().commit();

		data.i_map.put("rollBack", "rollBack");
		data.i_map.put("double", new AtomData("nono"));

		db().rollback();
		Assert.isNull(data.i_map.get("rollBack"));
		Assert.areEqual(previousSize,data.i_map.size());
		atom = (AtomData) data.i_map.get("double");
		Assert.areSame(atom,doubleAtom);
		Assert.isTrue(data.i_map.containsKey("double"));
		Assert.isFalse(data.i_map.containsKey("rollBack"));

		otherMap.clear();
		otherMap.put("other1", doubleAtom);
		otherMap.put("other2", doubleAtom);

		data.i_map.putAll(otherMap);
		db().deactivate(data.i_map, Integer.MAX_VALUE);

		Assert.areSame(doubleAtom,data.i_map.get("other1"));
		Assert.areSame(doubleAtom,data.i_map.get("other2"));

		data.i_map.clear();
		Assert.areEqual(0,data.i_map.size());
		setDefaultValues(data.i_map);

		int j = 0;
		i = data.i_map.keySet().iterator();
		while (i.hasNext()) {
			String key = (String) i.next();
			if (key.equals("cool")) {
				i.remove();
			}
			j++;
		}
		Assert.areEqual(2,data.i_map.size());
		Assert.isFalse(data.i_map.containsKey("cool"));
		Assert.areEqual(3,j);

		data.i_map.put("double", doubleAtom);
		((Db4oMap) data.i_map).deleteRemoved(true);
		data.i_map.keySet().remove("double");
		Assert.isFalse(db().isStored(doubleAtom));
		((Db4oMap) data.i_map).deleteRemoved(false);

		data.i_map.clear();
		Assert.areEqual(0,data.i_map.size());
		setDefaultValues(data.i_map);
	}

	private void tDefaultValues(Data data) {
		for (int i = 0; i < DEFAULT.length; i++) {
			AtomData atom = (AtomData) data.i_map.get(DEFAULT[i]);
			Assert.areEqual(DEFAULT[i],atom.name);
		}
	}

	private void tDefaultArray(Object[] arr) {
		Assert.areEqual(DEFAULT.length,arr.length);
		String str[] = new String[DEFAULT.length];
		System.arraycopy(DEFAULT, 0, str, 0, DEFAULT.length);
		for (int i = 0; i < arr.length; i++) {
			boolean found = false;
			for (int j = 0; j < str.length; j++) {
				if (arr[i].equals(str[j])) {
					str[j] = null;
					found = true;
				}
			}
			Assert.isTrue(found);
		}
		for (int j = 0; j < str.length; j++) {
			Assert.isNull(str[j]);
		}
	}

	private void restoreMembers(Data data) {
		Query q = newQuery(Data.class);
		ObjectSet objectSet = q.execute();
		Data rdata=(Data) objectSet.next();
		data.i_map = rdata.i_map;
		data.i_helper = rdata.i_helper;
	}

	private void lookupLast(Data data) {
		AtomData atom = (AtomData) data.i_map.get(MORE + (COUNT - 1));
		Assert.areEqual(MORE + (COUNT - 1),atom.name);
	}

	void checkHelper(Db4oHashMapHelper helper) {
		ExtObjectContainer con = db();
		if (con.isActive(helper)) {
			Assert.areEqual("hi",helper.i_childList.get(0));
			checkHelper(helper.i_child);
		}
	}

	public static void main(String[] args) {
		new Db4oHashMapTestCase().runAll();
	}
}
