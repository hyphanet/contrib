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

/** 
 * representation for java.lang.reflect.Field.
 * <br><br>See the respective documentation in the JDK API.
 * @see Reflector
 */
public interface ReflectField {
	
	public Object get(Object onObject);
	
	public String getName();
	
	/**
	 * The ReflectClass returned by this method should have been
	 * provided by the parent reflector.
	 * 
	 * @return the ReflectClass representing the field type as provided by the parent reflector
	 */
	public ReflectClass getFieldType();
	
	public boolean isPublic();
	
	public boolean isStatic();
	
	public boolean isTransient();
	
	public void set(Object onObject, Object value);
	
	/**
	 * The ReflectClass returned by this method should have been
	 * provided by the parent reflector.
	 * 
	 * @return the ReflectClass representing the index type as provided by the parent reflector
	 */
	public ReflectClass indexType();
	
	public Object indexEntry(Object orig);
}
