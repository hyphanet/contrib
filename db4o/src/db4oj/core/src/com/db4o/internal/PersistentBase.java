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
package com.db4o.internal;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.slots.*;


/**
 * @exclude
 * 
 */
public abstract class PersistentBase implements Persistent {

    protected int _id; // UID and address of pointer to the object in our file

    protected int _state = 2; // DIRTY and ACTIVE

    public final boolean beginProcessing() {
        if (bitIsTrue(Const4.PROCESSING)) {
            return false;
        }
        bitTrue(Const4.PROCESSING);
        return true;
    }

    final void bitFalse(int bitPos) {
        _state &= ~(1 << bitPos);
    }
    
    final boolean bitIsFalse(int bitPos) {
        return (_state | (1 << bitPos)) != _state;
    }

    final boolean bitIsTrue(int bitPos) {
        return (_state | (1 << bitPos)) == _state;
    }

    final void bitTrue(int bitPos) {
        _state |= (1 << bitPos);
    }

    void cacheDirty(Collection4 col) {
        if (!bitIsTrue(Const4.CACHED_DIRTY)) {
            bitTrue(Const4.CACHED_DIRTY);
            col.add(this);
        }
    }

    public void endProcessing() {
        bitFalse(Const4.PROCESSING);
    }
    
    public void free(Transaction trans){
        trans.systemTransaction().slotFreePointerOnCommit(getID());
    }

    public int getID() {
        return _id;
    }

    public final boolean isActive() {
        return bitIsTrue(Const4.ACTIVE);
    }

    public boolean isDirty() {
        return bitIsTrue(Const4.ACTIVE) && (!bitIsTrue(Const4.CLEAN));
    }
    
    public final boolean isNew(){
        return getID() == 0;
    }

    public final int linkLength() {
        return Const4.ID_LENGTH;
    }

    final void notCachedDirty() {
        bitFalse(Const4.CACHED_DIRTY);
    }

    public void read(Transaction trans) {
		if (!beginProcessing()) {
			return;
		}
		try {
			ByteArrayBuffer reader = trans.container().readReaderByID(trans, getID());
			if (Deploy.debug) {
				reader.readBegin(getIdentifier());
			}
			readThis(trans, reader);
			setStateOnRead(reader);
		} finally {
			endProcessing();
		}
	}
	
    
    public void setID(int a_id) {
    	if(DTrace.enabled){
    		DTrace.PERSISTENTBASE_SET_ID.log(a_id);
    	}
        _id = a_id;
    }

    public final void setStateClean() {
        bitTrue(Const4.ACTIVE);
        bitTrue(Const4.CLEAN);
    }

    public final void setStateDeactivated() {
        bitFalse(Const4.ACTIVE);
    }

    public void setStateDirty() {
        bitTrue(Const4.ACTIVE);
        bitFalse(Const4.CLEAN);
    }

    void setStateOnRead(ByteArrayBuffer reader) {
        if (Deploy.debug) {
            reader.readEnd();
        }
        if (bitIsTrue(Const4.CACHED_DIRTY)) {
            setStateDirty();
        } else {
            setStateClean();
        }
    }

    public final void write(Transaction trans) {
        
        if (! writeObjectBegin()) {
            return;
        }
        try {
	            
	        LocalObjectContainer stream = (LocalObjectContainer)trans.container();
	        
	        if(DTrace.enabled){
	            DTrace.PERSISTENT_OWN_LENGTH.log(getID());
	        }
	        
	        int length = ownLength();
	        length = stream.blockAlignedBytes(length);
	        
	        ByteArrayBuffer writer = new ByteArrayBuffer(length);
	        
	        Slot slot;
	        
	        if(isNew()){
	            Pointer4 pointer = stream.newSlot(length);
	            setID(pointer._id);
	            slot = pointer._slot;
                
                trans.setPointer(pointer);
	            // FIXME: Free everything on rollback here too?
                
	        }else{
	            slot = stream.getSlot(length);
	            trans.slotFreeOnRollbackCommitSetPointer(_id, slot, isFreespaceComponent());
	        }
	        
	        writeToFile(trans, writer, slot);
        }finally{
        	endProcessing();
        }

    }
    
    public boolean isFreespaceComponent(){
        return false;
    }
    
	private final void writeToFile(Transaction trans, ByteArrayBuffer writer, Slot slot) {
		
        if(DTrace.enabled){
        	DTrace.PERSISTENTBASE_WRITE.log(getID());
        }
		
		LocalObjectContainer container = (LocalObjectContainer)trans.container();
		
		if (Deploy.debug) {
		    writer.writeBegin(getIdentifier());
		}

		writeThis(trans, writer);

		if (Deploy.debug) {
		    writer.writeEnd();
		}
		
		container.writeEncrypt(writer, slot.address(), 0);

		if (isActive()) {
		    setStateClean();
		}
	}

    public boolean writeObjectBegin() {
        if (isDirty()) {
            return beginProcessing();
        }
        return false;
    }

    public void writeOwnID(Transaction trans, ByteArrayBuffer writer) {
        write(trans);
        writer.writeInt(getID());
    }
    
    public int hashCode() {
    	if(isNew()){
    		throw new IllegalStateException();
    	}
    	return getID();
    }
    
}
