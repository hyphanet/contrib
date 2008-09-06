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
package com.db4o.typehandlers;

import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.internal.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.fieldhandlers.*;
import com.db4o.marshall.*;


/**
 * handles reading, writing, deleting, defragmenting and 
 * comparisons for types of objects.<br><br>
 * Custom Typehandlers can be implemented to alter the default 
 * behaviour of storing all non-transient fields of an object.<br><br>
 * @see {@link Configuration#registerTypeHandler(com.db4o.typehandlers.TypeHandlerPredicate, TypeHandler4)} 
 */

// 	TODO: Not all TypeHandlers can implement Comparable4.
// Consider to change the hierarchy, not to extend Comparable4
// and to have callers check, if Comparable4 is implemented by 
// a TypeHandler.
public interface TypeHandler4 extends FieldHandler, Comparable4 {
	
	/**
	 * gets called when an object gets deleted.
	 * @param context 
	 * @throws Db4oIOException
	 */
	void delete(DeleteContext context) throws Db4oIOException;
	
	/**
	 * gets called when an object gets defragmented.
	 * @param context
	 */
	void defragment(DefragmentContext context);

	/**
	 * gets called when an object is read from the database.
	 * @param context
	 * @return the instantiated object
	 */
	Object read(ReadContext context);
	
	/**
	 * gets called when an object is to be written to the database.
	 * @param context
	 * @param obj the object
	 */
    void write(WriteContext context, Object obj);
	
}
