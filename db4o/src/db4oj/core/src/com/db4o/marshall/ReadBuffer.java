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

import com.db4o.foundation.*;

/**
 * a buffer interface with methods to read and to position 
 * the read pointer in the buffer.
 */
public interface ReadBuffer {
    
	/**
	 * returns the current offset in the buffer
	 * @return the offset
	 */
    int offset();
    
    public BitMap4 readBitMap(int bitCount);

    /**
     * reads a byte from the buffer.
     * @return the byte
     */
    byte readByte();
    
    /**
     * reads an array of bytes from the buffer.
     * The length of the array that is passed as a parameter specifies the
     * number of bytes that are to be read. The passed bytes buffer parameter
     * is directly filled.  
     * @param bytes the byte array to read the bytes into.
     */
    void readBytes(byte[] bytes);

    /**
     * reads an int from the buffer.
     * @return the int
     */
    int readInt();
    
    /**
     * reads a long from the buffer.
     * @return the long
     */
    long readLong();
    
    /**
     * positions the read pointer at the specified position
     * @param offset the desired position in the buffer
     */
	void seek(int offset);
	
	/**
	 * reads and int from the current offset position and
	 * seeks the  
	 */
	void seekCurrentInt();
	
	


}
