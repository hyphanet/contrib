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
package com.db4o.defragment;

import com.db4o.ext.*;

/**
 * Filter that accepts only StoredClass instances whose corresponding Java
 * class is currently known.
 * @sharpen.ignore
 */
public class AvailableClassFilter implements StoredClassFilter {
	
	private ClassLoader _loader;

	/**
	 * Will accept only classes that are known to the classloader that loaded
	 * this class.
	 */
	public AvailableClassFilter() {
		this(AvailableClassFilter.class.getClassLoader());
	}

	/**
	 * Will accept only classes that are known to the given classloader.
	 * 
	 * @param loader The classloader to check class names against
	 */
	public AvailableClassFilter(ClassLoader loader) {
		_loader = loader;
	}

	/**
	 * Will accept only classes whose corresponding platform class is known
	 * to the configured classloader.
	 * 
	 * @param storedClass The YapClass instance to be checked
	 * @return true if the corresponding platform class is known to the configured classloader, false otherwise
	 */
	public boolean accept(StoredClass storedClass) {
		try {
			_loader.loadClass(storedClass.getName());
			return true;
		} catch (ClassNotFoundException exc) {
			return false;
		}
	}
}
