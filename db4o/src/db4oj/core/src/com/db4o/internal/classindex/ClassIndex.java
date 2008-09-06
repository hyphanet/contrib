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
package com.db4o.internal.classindex;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.slots.*;

/**
 * representation to collect and hold all IDs of one class
 */
 public class ClassIndex extends PersistentBase implements ReadWriteable {
     
     
    private final ClassMetadata _clazz;
     
	/**
	 * contains TreeInt with object IDs 
	 */
	private TreeInt i_root;
    
    ClassIndex(ClassMetadata yapClass){
        _clazz = yapClass;
    }
	
	public void add(int a_id){
		i_root = TreeInt.add(i_root, a_id);
	}

    public final int marshalledLength() {
    	return Const4.INT_LENGTH * (Tree.size(i_root) + 1);
    }

    public final void clear() {
        i_root = null;
    }
    
    void ensureActive(Transaction trans){
        if (!isActive()) {
            setStateDirty();
            read(trans);
        }
    }

    int entryCount(Transaction ta){
        if(isActive() || isNew()){
            return Tree.size(i_root);
        }
        Slot slot = ((LocalTransaction)ta).getCurrentSlotOfID(getID());
        int length = Const4.INT_LENGTH;
        if(Deploy.debug){
            length += Const4.LEADING_LENGTH;
        }
        ByteArrayBuffer reader = new ByteArrayBuffer(length);
        reader.readEncrypt(ta.container(), slot.address());
        if (Deploy.debug) {
            reader.readBegin(getIdentifier());
        }
        return reader.readInt();
    }
    
    public final byte getIdentifier() {
        return Const4.YAPINDEX;
    }
    
    TreeInt getRoot(){
        return i_root;
    }
    
    public final int ownLength() {
        return Const4.OBJECT_LENGTH + marshalledLength();
    }

    public final Object read(ByteArrayBuffer a_reader) {
    	throw Exceptions4.virtualException();
    }

    public final void readThis(Transaction a_trans, ByteArrayBuffer a_reader) {
    	i_root = (TreeInt)new TreeReader(a_reader, new TreeInt(0)).read();
    }

	public void remove(int a_id){
		i_root = TreeInt.removeLike(i_root, a_id);
	}

    void setDirty(ObjectContainerBase a_stream) {
    	// TODO: get rid of the setDirty call
        a_stream.setDirtyInSystemTransaction(this);
    }

    public void write(ByteArrayBuffer a_writer) {
        writeThis(null, a_writer);
    }

    public final void writeThis(Transaction trans, final ByteArrayBuffer a_writer) {
    	TreeInt.write(a_writer, i_root);
    }
    
    public String toString(){
        return _clazz + " index";  
    }
}