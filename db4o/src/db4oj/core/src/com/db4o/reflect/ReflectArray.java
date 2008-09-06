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
 * representation for java.lang.reflect.Array.
 * <br><br>See the respective documentation in the JDK API.
 * @see Reflector
 */
public interface ReflectArray {
    
    public void analyze(Object obj, ArrayInfo info);
    
    public int[] dimensions(Object arr);
    
    public int flatten(
        Object a_shaped,
        int[] a_dimensions,
        int a_currentDimension,
        Object[] a_flat,
        int a_flatElement);
	
	public Object get(Object onArray, int index);
	
    public ReflectClass getComponentType(ReflectClass a_class);
	
	public int getLength(Object array);
	
	public boolean isNDimensional(ReflectClass a_class);
	
	public Object newInstance(ReflectClass componentType, ArrayInfo info);
	
	public Object newInstance(ReflectClass componentType, int length);
	
	public Object newInstance(ReflectClass componentType, int[] dimensions);
	
	public void set(Object onArray, int index, Object element);
    
    public int shape(
        Object[] a_flat,
        int a_flatElement,
        Object a_shaped,
        int[] a_dimensions,
        int a_currentDimension);

	
}

