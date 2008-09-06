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
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.slots.*;

/**
 * public for .NET conversion reasons
 * 
 * TODO: Split this class for individual usecases. Only use the member
 * variables needed for the respective usecase.
 * 
 * @exclude
 */
public final class StatefulBuffer extends ByteArrayBuffer {
	
    private int i_address;
    private int _addressOffset;

    private int i_cascadeDelete; 

    private int i_id;

    // carries instantiation depth through the reading process
    private ActivationDepth i_instantionDepth;
    private int i_length;

    Transaction i_trans;

    // carries updatedepth depth through the update process
    // and carries instantiation information through the reading process 
    private int i_updateDepth = 1;
    
    public int _payloadOffset;
    

    public StatefulBuffer(Transaction a_trans, int a_initialBufferSize) {
        i_trans = a_trans;
        i_length = a_initialBufferSize;
        _buffer = new byte[i_length];
    }
    
    public StatefulBuffer(Transaction a_trans, int address, int length) {
        this(a_trans, length);
        i_address = address;
    }
    
    public StatefulBuffer(Transaction trans, Slot slot){
        this(trans, slot.address(), slot.length());
    }

    public StatefulBuffer(Transaction trans, Pointer4 pointer){
        this(trans, pointer._slot);
        i_id = pointer._id;
    }


    public void debugCheckBytes() {
        if (Debug.xbytes) {
            if (_offset != i_length) {
                // Db4o.log("!!! YapBytes.debugCheckBytes not all bytes used");
                // This is normal for writing The FreeSlotArray, becauce one
                // slot is possibly reserved by it's own pointer.
            }
        }
    }

    public int getAddress() {
        return i_address;
    }
    
    public int addressOffset(){
        return _addressOffset;
    }

    public int getID() {
        return i_id;
    }

    public ActivationDepth getInstantiationDepth() {
        return i_instantionDepth;
    }

    public int length() {
        return i_length;
    }

    public ObjectContainerBase container(){
        return i_trans.container();
    }
    
    public LocalObjectContainer file(){
        return ((LocalTransaction)i_trans).file();
    }

    public Transaction transaction() {
        return i_trans;
    }

    public int getUpdateDepth() {
        return i_updateDepth;
    }
    
    public byte[] getWrittenBytes(){
        byte[] bytes = new byte[_offset];
        System.arraycopy(_buffer, 0, bytes, 0, _offset);
        return bytes;
    }
    
    public int preparePayloadRead() {
        int newPayLoadOffset = readInt();
        int length = readInt();
        int linkOffSet = _offset;
        _offset = newPayLoadOffset;
        _payloadOffset += length;
        return linkOffSet;
    }

    public void read() throws Db4oIOException {
        container().readBytes(_buffer, i_address,_addressOffset, i_length);
    }

    public final StatefulBuffer readEmbeddedObject() throws Db4oIOException {
        int id = readInt();
        int length = readInt();
        if(id == 0){
            return null;
        }
        StatefulBuffer bytes = null;
            bytes = container().readWriterByAddress(i_trans, id, length);
            if (bytes != null) {
                bytes.setID(id);
            }
        if(bytes != null){
            bytes.setUpdateDepth(getUpdateDepth());
            bytes.setInstantiationDepth(getInstantiationDepth());
        }
        return bytes;
    }

    public final StatefulBuffer readYapBytes() {
        int length = readInt();
        if (length == 0) {
            return null;
        }
        StatefulBuffer yb = new StatefulBuffer(i_trans, length);
        System.arraycopy(_buffer, _offset, yb._buffer, 0, length);
        _offset += length;
        return yb;
    }

    public void removeFirstBytes(int aLength) {
        i_length -= aLength;
        byte[] temp = new byte[i_length];
        System.arraycopy(_buffer, aLength, temp, 0, i_length);
        _buffer = temp;
        _offset -= aLength;
        if (_offset < 0) {
            _offset = 0;
        }
    }

    public void address(int a_address) {
        i_address = a_address;
    }

    public void setID(int a_id) {
        i_id = a_id;
    }

    public void setInstantiationDepth(ActivationDepth a_depth) {
        i_instantionDepth = a_depth;
    }

    public void setTransaction(Transaction aTrans) {
        i_trans = aTrans;
    }

    public void setUpdateDepth(int a_depth) {
        i_updateDepth = a_depth;
    }

    public void slotDelete() {
        i_trans.slotDelete(i_id, slot());
    }
    
    public void trim4(int a_offset, int a_length) {
        byte[] temp = new byte[a_length];
        System.arraycopy(_buffer, a_offset, temp, 0, a_length);
        _buffer = temp;
        i_length = a_length;
    }

    public void useSlot(int a_adress) {
        i_address = a_adress;
        _offset = 0;
    }

    // FIXME: FB remove
    public void useSlot(int address, int length) {
    	useSlot(new Slot(address, length));
    }
    
    public void useSlot(Slot slot) {
        i_address = slot.address();
        _offset = 0;
        if (slot.length() > _buffer.length) {
            _buffer = new byte[slot.length()];
        }
        i_length = slot.length();
    }

    // FIXME: FB remove
    public void useSlot(int a_id, int a_adress, int a_length) {
        i_id = a_id;
        useSlot(a_adress, a_length);
    }
    
    public void write() {
        if (Debug.xbytes) {
            debugCheckBytes();
        }
        file().writeBytes(this, i_address, _addressOffset);
    }

    public void writeEmbeddedNull() {
        writeInt(0);
        writeInt(0);
    }

    public void writeEncrypt() {
        if (Deploy.debug) {
            debugCheckBytes();
        }
        file().writeEncrypt(this, i_address, _addressOffset);
    }
    
    /* Only used for Strings, topLevel therefore means aligning blocksize, so
     * index will be possible.
     */
    public void writePayload(StatefulBuffer payLoad, boolean topLevel){
        checkMinimumPayLoadOffsetAndWritePointerAndLength(payLoad.length(), topLevel);
        System.arraycopy(payLoad._buffer, 0, _buffer, _payloadOffset, payLoad._buffer.length);
        transferPayLoadAddress(payLoad, _payloadOffset);
        _payloadOffset += payLoad._buffer.length;
    }
    
    private void checkMinimumPayLoadOffsetAndWritePointerAndLength(int length, boolean alignToBlockSize){
        if(_payloadOffset <= _offset + (Const4.INT_LENGTH * 2)){
            _payloadOffset = _offset + (Const4.INT_LENGTH * 2);
        }
        if(alignToBlockSize){
            _payloadOffset = container().blockAlignedBytes(_payloadOffset);
        }
        writeInt(_payloadOffset);
        
        // TODO: This length is here for historical reasons. 
        //       It's actually never really needed during reading.
        //       It's only necessary because array and string used
        //       to consist of a double pointer in marshaller family 0
        //       and it was not considered a good idea to change
        //       their linkLength() values for compatibility reasons
        //       with marshaller family 0.
        writeInt(length);
    }
    
    public int reserveAndPointToPayLoadSlot(int length){
        checkMinimumPayLoadOffsetAndWritePointerAndLength(length, false);
        int linkOffset = _offset;
        _offset = _payloadOffset;
        _payloadOffset += length;
        return linkOffset;
    }
    
    public ByteArrayBuffer readPayloadWriter(int offset, int length){
        StatefulBuffer payLoad = new StatefulBuffer(i_trans, 0, length);
        System.arraycopy(_buffer,offset, payLoad._buffer, 0, length);
        transferPayLoadAddress(payLoad, offset);
        return payLoad;
    }

    private void transferPayLoadAddress(StatefulBuffer toWriter, int offset) {
        int blockedOffset = offset / container().blockSize();
        toWriter.i_address = i_address + blockedOffset;
        toWriter.i_id = toWriter.i_address;
        toWriter._addressOffset = _addressOffset;
    }

    void writeShortString(String a_string) {
        writeShortString(i_trans, a_string);
    }

    public void moveForward(int length) {
        _addressOffset += length;
    }
    
    public void writeForward() {
        write();
        _addressOffset += i_length;
        _offset = 0;
    }
    
    public String toString(){
        return "id " + i_id + " adr " + i_address + " len " + i_length;
    }
    
    public void noXByteCheck() {
        if(Debug.xbytes && Deploy.overwrite){
            setID(Const4.IGNORE_ID);
        }
    }
    
	public void writeIDs(IntIterator4 idIterator, int maxCount ) {
		int savedOffset = _offset; 
        writeInt(0);
        int actualCount = 0;
        while(idIterator.moveNext()){
            writeInt(idIterator.currentInt());
            actualCount ++;
            if(actualCount >= maxCount){
            	break;
            }
        }
        int secondSavedOffset = _offset;
        _offset = savedOffset;
        writeInt(actualCount);
        _offset = secondSavedOffset;
	}
	
	public Slot slot(){
		return new Slot(i_address, i_length);
	}
	
	public Pointer4 pointer(){
	    return new Pointer4(i_id, slot());
	}
	
    public int cascadeDeletes() {
        return i_cascadeDelete;
    }
    
    public void setCascadeDeletes(int depth) {
        i_cascadeDelete = depth;
    }

}
