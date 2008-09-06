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
package com.db4o.marshall;

import com.db4o.typehandlers.*;


/**
 * this interface is passed to internal class com.db4o.internal.TypeHandler4 during marshalling
 * and provides methods to marshal objects. 
 */
public interface WriteContext extends Context, WriteBuffer {

    /**
     * makes sure the object is stored and writes the ID of
     * the object to the context.
     * Use this method for first class objects only (objects that
     * have an identity in the database). If the object can potentially
     * be a primitive type, do not use this method but use 
     * a matching {@link WriteBuffer} method instead.
     * @param obj the object to write.
     */
    void writeObject(Object obj);

    /**
     * writes sub-objects, in cases where the TypeHandler4
     * is known.
     * @param obj the object to write
     */
    void writeObject(TypeHandler4 handler, Object obj);

    /**
     * reserves a buffer with a specific length at the current
     * position, to be written in a later step.
     * @param length the length to be reserved. 
     * @return the ReservedBuffer
     */
    ReservedBuffer reserve(int length);
    
}
