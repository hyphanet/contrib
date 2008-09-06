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

import com.db4o.db4ounit.jre12.soda.collections.*;
import com.db4o.db4ounit.jre12.soda.deepOR.*;
import com.db4o.db4ounit.jre12.soda.experiments.*;
import com.db4o.internal.*;

import db4ounit.extensions.*;

public class AllTests  extends Db4oTestSuite {
	protected Class[] testCases() {
		 
		return merge(new Class[]{
				HashtableModifiedUpdateDepthTestCase.class,
				ObjectSetListAPITestCase.class,
				STArrayListTTestCase.class,
				STArrayListUTestCase.class,
				STHashSetTTestCase.class,
				STHashSetUTestCase.class,
				STHashtableDTestCase.class,
				STHashtableEDTestCase.class,
				STHashtableETTestCase.class,
				STHashtableEUTestCase.class,
				STHashtableTTestCase.class,
				STHashtableUTestCase.class,
				STLinkedListTTestCase.class,
				STLinkedListUTestCase.class,
				STOwnCollectionTTestCase.class,
				STTreeSetTTestCase.class,
				STTreeSetUTestCase.class,
				STVectorTTestCase.class,
				STVectorUTestCase.class,
				STOrContainsTestCase.class,
				STCurrentTestCase.class,
				STIdentityEvaluationTestCase.class,
				STNullOnPathTestCase.class,
		}, vectorQbeTestCases());
	}
	
	private Class[] merge(Class[] arr1, Class[] arr2){
		Class[] res = new Class[arr1.length + arr2.length];
		System.arraycopy(arr1, 0, res, 0, arr1.length);
		System.arraycopy(arr2, 0, res, arr1.length, arr2.length);
		return res;
	}

	public static void main(String[] args) {
		new AllTests().runAll();
	}
	
	private Class[] vectorQbeTestCases () {
		return TypeHandlerConfiguration.enabled() ? 
				new Class[] {
			//  QBE with vector is not expressible as SODA and it
			//  will no longer work with new collection Typehandlers
				} 
		: 
				new Class[] {
						STVectorDTestCase.class,
						STVectorEDTestCase.class,
		};
	}
}
