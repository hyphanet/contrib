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
package com.db4o;

import com.db4o.internal.*;

/**
 * The index record that is written to the database file.
 * Don't obfuscate.
 * 
 * @exclude
 * @persistent
 */
public class MetaIndex implements Internal4{
    
    // The number of entries an the length are redundant, because the handler should
    // return a fixed length, but we absolutely want to make sure, we don't free
    // a slot into nowhere.
 
    public int indexAddress;
    public int indexEntries;
    public int indexLength;
    
    // TODO: make sure this aren't really needed
    // and remove them 
	private final int patchAddress = 0;
	private final int patchEntries = 0;
	private final int patchLength = 0;
    
    public void read(ByteArrayBuffer reader){
        indexAddress = reader.readInt();
        indexEntries = reader.readInt();
        indexLength = reader.readInt();
        
        // no longer used apparently
        /*patchAddress = */reader.readInt();
        /*patchEntries = */reader.readInt();
        /*patchLength = */reader.readInt();
    }
    
    public void write(ByteArrayBuffer writer){
        writer.writeInt(indexAddress);
        writer.writeInt(indexEntries);
        writer.writeInt(indexLength);
        writer.writeInt(patchAddress);
        writer.writeInt(patchEntries);
        writer.writeInt(patchLength);
    }
    
    public void free(LocalObjectContainer file){
        file.free(indexAddress, indexLength);        
        indexAddress = 0;
        indexLength = 0;
//        file.free(patchAddress, patchLength);
//        patchAddress = 0;
//        patchLength = 0;
    }
}
