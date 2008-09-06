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
package com.db4o.types;

/**
 *  db4o List implementation for database-aware lists.
 * <br><br>
 * A <code>Db4oList</code> supplies the methods specified in java.util.List.<br><br>
 * All access to the list is controlled by the {@link com.db4o.ObjectContainer ObjectContainer} to help the
 * programmer produce expected results with as little work as possible:<br>  
 * - newly added objects are automatically persisted.<br>
 * - list elements are automatically activated when they are needed. The activation
 * depth is configurable with {@link Db4oCollection#activationDepth(int)}.<br>
 * - removed objects can be deleted automatically, if the list is configured
 * with {@link Db4oCollection#deleteRemoved(boolean)}<br><br>
 * Usage:<br>
 * - declare a <code>java.util.List</code> variable on your persistent classes.<br>
 * - fill this variable with a method in the ObjectContainer collection factory.<br><br>
 * <b>Example:</b><br><br>
 * <code>class MyClass{<br>
 * &nbsp;&nbsp;List myList;<br>
 * }<br><br>
 * MyClass myObject = new MyClass();<br> 
 * myObject.myList = objectContainer.ext().collections().newLinkedList();
 * 
 * @see com.db4o.ext.ExtObjectContainer#collections
 * 
 * @sharpen.ignore
 */
public interface Db4oList extends Db4oCollection {

}
