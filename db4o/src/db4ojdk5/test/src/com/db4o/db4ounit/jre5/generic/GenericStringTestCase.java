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
package com.db4o.db4ounit.jre5.generic;

import db4ounit.*;
import db4ounit.extensions.*;

public class GenericStringTestCase extends AbstractDb4oTestCase {
	public static void main(String[] args) {
		new GenericStringTestCase().runAll();
	}

	public void test1() throws Exception {
		store(new StringWrapper1<String>("hello"));
		StringWrapper1 sw = (StringWrapper1) retrieveOnlyInstance(StringWrapper1.class);
		Assert.areEqual("hello", sw.str);
	}
	
	public void test2() throws Exception {
		store(new StringWrapper1<String>("hello"));
		reopen();
		StringWrapper1 sw = (StringWrapper1) retrieveOnlyInstance(StringWrapper1.class);
		Assert.areEqual("hello", sw.str);
	}
	
	public void test3() throws Exception {
		store(new StringWrapper2<String>("hello"));
		StringWrapper2 sw = (StringWrapper2) retrieveOnlyInstance(StringWrapper2.class);
		Assert.areEqual("hello", sw.str);
	}
	
	public void test4() throws Exception {
		store(new StringWrapper2<String>("hello"));
		reopen();
		StringWrapper2 sw = (StringWrapper2) retrieveOnlyInstance(StringWrapper2.class);
		Assert.areEqual("hello", sw.str);
	}

	static class StringWrapper1<T> {
		public T str;

		public StringWrapper1(T s) {
			str = s;
		}
	}

	class StringWrapper2<T extends Comparable> {
		public T str;
		
		public StringWrapper2() {

		}

		public StringWrapper2(T s) {
			str = s;
		}
	}
}
