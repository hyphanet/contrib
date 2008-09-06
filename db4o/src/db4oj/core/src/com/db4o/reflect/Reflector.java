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
package com.db4o.reflect;

import com.db4o.foundation.*;

/**
 * root of the reflection implementation API.
 * <br><br>The open reflection interface is supplied to allow to implement
 * reflection functionality on JDKs that do not come with the
 * java.lang.reflect.* package.<br><br>
 * Use {@link com.db4o.config.Configuration#reflectWith Db4o.configure().reflectWith(IReflect reflector)}
 * to register the use of your implementation before opening database
 * files.
 */
public interface Reflector extends DeepClone{
	
	void configuration(ReflectorConfiguration config);
	
	/**
	 * returns an ReflectArray object, the equivalent to java.lang.reflect.Array.
	 */
	public ReflectArray array();
	
	/**
	 * returns an ReflectClass for a Class
	 */
	public ReflectClass forClass(Class clazz);
	
	/**
	 * returns an ReflectClass class reflector for a class name or null
	 * if no such class is found
	 */
	public ReflectClass forName(String className);
	
	/**
	 * returns an ReflectClass for an object or null if the passed object is null.
	 */
	public ReflectClass forObject(Object obj);
	
	public boolean isCollection(ReflectClass clazz);
    
    public void setParent(Reflector reflector);
	
}
