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


/**
 * a reserved buffer within a write buffer.
 * The usecase this class was written for: A null bitmap should be at the 
 * beginning of a slot to allow lazy processing. During writing the content 
 * of the null bitmap is not yet fully known until all members are processed.
 * With the Reservedbuffer the space in the slot can be occupied and writing
 * can happen after all members are processed. 
 */
public interface ReservedBuffer {

    /**
     * writes a byte array to the reserved buffer.
     * @param bytes the byte array.
     */
    public void writeBytes(byte[] bytes);
    
}
