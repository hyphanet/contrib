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
package com.db4o.db4ounit.jre5.enums;

import java.lang.reflect.Field;

import com.db4o.ObjectSet;
import com.db4o.activation.ActivationPurpose;
import com.db4o.config.Configuration;
import com.db4o.db4ounit.common.ta.ActivatableImpl;
import com.db4o.ta.TransparentActivationSupport;

import db4ounit.Assert;
import db4ounit.extensions.AbstractDb4oTestCase;

public class TAEnumsTestCase extends AbstractDb4oTestCase {

	public static void main(String[] args) {
		new TAEnumsTestCase().runSolo();
	}
	
	public static class Item extends ActivatableImpl {
		private TypeCountEnum _enum;
		private String _name;
		public Item(TypeCountEnum enum_, String name) {
			this._enum = enum_;
			this._name = name;
		}
		public TypeCountEnum get_enum() {
			activate(ActivationPurpose.READ);
			return _enum;
		}
		public String get_name() {
			activate(ActivationPurpose.READ);
			return _name;
		}
	}
	
	protected void configure(Configuration config) throws Exception {
		config.add(new TransparentActivationSupport());
	}
	
	protected void store() throws Exception {
		store(new Item(TypeCountEnum.A ,"A"));
		store(new Item(TypeCountEnum.B, "B"));
	}
	
	@SuppressWarnings("unchecked")
	public void test() throws Exception {
		ObjectSet<Item> set = newQuery(Item.class).execute();
		while(set.hasNext()){
			Item item = (Item)set.next();
			assertItemIsNotActivated(item);
			assertItemEnum(item);
		}
	}

	private void assertItemIsNotActivated(Item item) throws Exception {
		Field[] declaredFields = Item.class.getDeclaredFields();
		for (int i = 0; i < declaredFields.length; i++) {
			declaredFields[i].setAccessible(true);
			Assert.isNull(declaredFields[i].get(item));
		}
	}

	private void assertItemEnum(Item item) {
		if(item.get_name().equals("A")){
			Assert.areSame(TypeCountEnum.A, item.get_enum());
		}
		if(item.get_name().equals("B")){
			Assert.areSame(TypeCountEnum.B, item.get_enum());
		}
	}
	
	
	
}
