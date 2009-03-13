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
package com.db4o.db4ounit.common.migration;

import java.util.*;

import com.db4o.config.*;
import com.db4o.db4ounit.common.handlers.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.reflect.*;

import db4ounit.*;

/**
 * @sharpen.ignore
 * @decaf.ignore.jdk11
 */
public class KnownClassesMigrationTestCase extends FormatMigrationTestCaseBase {

	protected void assertObjectsAreReadable(ExtObjectContainer objectContainer) {
		if (isVersionWithoutTCollection())
			return;
		
		final ReflectClass[] knownClasses = objectContainer.knownClasses();
		
		Assert.isNotNull(knownClasses);
		Assert.isGreater(2, knownClasses.length);
		
		ReflectClass type = objectContainer.reflector().forClass(TCollection.class);
		Assert.isGreaterOrEqual(0, Arrays4.indexOf(knownClasses, type));
	}

	private boolean isVersionWithoutTCollection() {
		return db4oMajorVersion() < 5;
	}

	protected String fileNamePrefix() {
		return "KnownClasses";
	}

	protected void store(ExtObjectContainer objectContainer) {
		objectContainer.set(new Item(new LinkedList()));
	}
	
	private static class Item {
		public Item(LinkedList list) {
			_list = list;
		}
		
		public LinkedList _list;
	}

}
