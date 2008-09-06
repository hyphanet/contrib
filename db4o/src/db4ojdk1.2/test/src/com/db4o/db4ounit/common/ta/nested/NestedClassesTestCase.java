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
package com.db4o.db4ounit.common.ta.nested;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ta.*;

import db4ounit.*;
import db4ounit.extensions.*;

/**
 * TODO: This test case will fail when run against JDK1.3/JDK1.4 (though it will run green against
 * JDK1.2 and JDK1.5+) because the synthetic "this$0" field is final.
 * See http://developer.db4o.com/Resources/view.aspx/Reference/Implementation_Strategies/Type_Handling/Final_Fields/Final_Fields_Specifics
 */
public class NestedClassesTestCase
	extends AbstractDb4oTestCase
	implements OptOutTA { 

	public static void main(String[] args) {
		new NestedClassesTestCase().runSolo();
	}
	
	protected void store() throws Exception {
		OuterClass outerObject = new OuterClass();
		outerObject._foo = 42;
		
		final Activatable objOne = (Activatable)outerObject.createInnerObject();
		store(objOne);
		
		final Activatable objTwo = (Activatable)outerObject.createInnerObject();
		store(objTwo);
	}

	
	protected void configure(Configuration config) throws Exception {
		config.add(new TransparentActivationSupport());
	}
	
	public void test() throws Exception {
		String property = System.getProperty("java.version");
        if (property != null && property.startsWith("1.3")) {
			System.err.println("IGNORED: " + getClass() + " will fail when run against JDK1.3/JDK1.4");
			return;
		}
		ObjectSet query = db().query(OuterClass.InnerClass.class);
		while(query.hasNext()){
			OuterClass.InnerClass innerObject = (OuterClass.InnerClass) query.next();
			Assert.isNull(innerObject.getOuterObjectWithoutActivation());
			Assert.areEqual(42, innerObject.getOuterObject().foo());
		}
	}
	
}
