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
package com.db4o.internal.marshall;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public class MarshallingContext implements FieldListInfo, MarshallingInfo, WriteContext {
    
    private static final int HEADER_LENGTH = Const4.LEADING_LENGTH 
            + Const4.ID_LENGTH  // YapClass ID
            + 1 // Marshaller Version
            + Const4.INT_LENGTH; // number of fields
    
    private static final int NO_INDIRECTION = 3; // and number above 2 
    
    private final Transaction _transaction;
    
    private final ObjectReference _reference;
    
    private int _updateDepth;
    
    private final boolean _isNew;
    
    private final BitMap4 _nullBitMap;
    
    private final MarshallingBuffer _writeBuffer;
    
    private MarshallingBuffer _currentBuffer;
    
    private int _fieldWriteCount;
    
    private ByteArrayBuffer _debugPrepend;
    
    private Object _currentMarshalledObject;
    
    private Object _currentIndexEntry;
    
	private int _aspectCount;
    

    public MarshallingContext(Transaction trans, ObjectReference ref, int updateDepth, boolean isNew) {
        _transaction = trans;
        _reference = ref;
        _nullBitMap = new BitMap4(fieldCount());
        _updateDepth = classMetadata().adjustUpdateDepth(trans, updateDepth);
        _isNew = isNew;
        _writeBuffer = new MarshallingBuffer();
        _currentBuffer = _writeBuffer;
    }

    private int fieldCount() {
        return classMetadata().aspectCount();
    }

    public ClassMetadata classMetadata() {
        return _reference.classMetadata();
    }

    public boolean isNew() {
        return _isNew;
    }

    public boolean isNull(int fieldIndex) {
        // TODO Auto-generated method stub
        
        return false;
    }

    public void isNull(int fieldIndex, boolean flag) {
        _nullBitMap.set(fieldIndex, flag);
    }

    public Transaction transaction() {
        return _transaction;
    }
    
    private Slot createNewSlot(int length){
        Slot slot = new Slot(-1, length);
        if(_transaction instanceof LocalTransaction){
            slot = ((LocalTransaction)_transaction).file().getSlot(length);
            _transaction.slotFreeOnRollback(objectID(), slot);
        }
        _transaction.setPointer(objectID(), slot);
        return slot;
    }
    
    private Slot createUpdateSlot(int length){
        if(transaction() instanceof LocalTransaction){
            return ((LocalTransaction)transaction()).file().getSlotForUpdate(transaction(), objectID(), length);
        }
        return new Slot(0, length);
    }
    
    
    public Pointer4 allocateSlot(){
        int length = container().blockAlignedBytes(marshalledLength());
        Slot slot = isNew() ? createNewSlot(length) : createUpdateSlot(length);
        return new Pointer4(objectID(), slot);
    }
    

    public ByteArrayBuffer ToWriteBuffer(Pointer4 pointer) {
        
        ByteArrayBuffer buffer = new ByteArrayBuffer(pointer.length());
        _writeBuffer.mergeChildren(this, pointer.address(), writeBufferOffset());
        
        if (Deploy.debug) {
            buffer.writeBegin(Const4.YAPOBJECT);
        }
        
        writeObjectClassID(buffer, classMetadata().getID());
        buffer.writeByte(HandlerRegistry.HANDLER_VERSION);
        buffer.writeInt(fieldCount());
        buffer.writeBitMap(_nullBitMap);
        
        _writeBuffer.transferContentTo(buffer);
        
        if (Deploy.debug) {
            buffer.writeEnd();
        }
        
        return buffer;
    }
    
    private int writeBufferOffset(){
        return HEADER_LENGTH + _nullBitMap.marshalledLength();
    }

    private int marshalledLength() {
        int length = writeBufferOffset();
        _writeBuffer.checkBlockAlignment(this, null, new IntByRef(length));
        return length + _writeBuffer.marshalledLength() + Const4.BRACKETS_BYTES;
    }
    
    public int requiredLength(MarshallingBuffer buffer, boolean align) {
        if(! align){
            return buffer.length();
        }
        return container().blockAlignedBytes(buffer.length());
    }
    
    private void writeObjectClassID(ByteArrayBuffer reader, int id) {
        reader.writeInt(-id);
    }

    public Object getObject() {
        return _reference.getObject();
    }

    public Config4Class classConfiguration() {
        return classMetadata().config();
    }

    public int updateDepth() {
        return _updateDepth;
    }

    public void updateDepth(int depth) {
        _updateDepth = depth;
    }

    public int objectID() {
        return _reference.getID();
    }

    public Object currentIndexEntry() {
        // TODO Auto-generated method stub
        return null;
    }

    public ObjectContainerBase container() {
        return transaction().container();
    }

    public ObjectContainer objectContainer() {
        return transaction().objectContainer();
    }

	public void writeByte(byte b) {
	    preWrite();
	    _currentBuffer.writeByte(b);
	    postWrite();
	}
	
	public void writeBytes(byte[] bytes) {
	    preWrite();
	    _currentBuffer.writeBytes(bytes);
	    postWrite();
	}

    public void writeInt(int i) {
        preWrite();
        _currentBuffer.writeInt(i);
        postWrite();
    }
    
    public void writeLong(long l) {
        preWrite();
        _currentBuffer.writeLong(l);
        postWrite();
    }
    
	private void preWrite() {
        _fieldWriteCount++;
        if(Deploy.debug){
            if(_debugPrepend != null){
                for (int i = 0; i < _debugPrepend.offset(); i++) {
                    _currentBuffer.writeByte(_debugPrepend._buffer[i]);
                }
            }
        }
    }
	
	private void postWrite(){
	    if(Deploy.debug){
	        if(_debugPrepend != null){
	            _currentBuffer.debugDecrementLastOffset(_debugPrepend.offset());
	            _debugPrepend = null;
	        }
	    }
	}

    public void createChildBuffer(boolean transferLastWrite, boolean storeLengthInLink) {
        MarshallingBuffer childBuffer = _currentBuffer.addChild(false, storeLengthInLink);
        if(transferLastWrite){
            _currentBuffer.transferLastWriteTo(childBuffer, storeLengthInLink);
        }
        _currentBuffer.reserveChildLinkSpace(storeLengthInLink);
        _currentBuffer = childBuffer;
    }

    public void beginSlot(){
        _fieldWriteCount = 0;
        _currentBuffer = _writeBuffer;
    }
    
    public void fieldCount(int fieldCount) {
        _writeBuffer.writeInt(fieldCount);
    }

    public void debugPrependNextWrite(ByteArrayBuffer prepend) {
        if(Deploy.debug){
            _debugPrepend = prepend;
        }
    }

    public void debugWriteEnd(byte b) {
        _currentBuffer.writeByte(b);
    }

    public void writeObject(Object obj) {
        int id = container().storeInternal(transaction(), obj, _updateDepth, true);
        writeInt(id);
        _currentMarshalledObject = obj;
        _currentIndexEntry = new Integer(id);
    }
    
    public void writeObject(TypeHandler4 handler, Object obj){
        MarshallingContextState state = currentState();
        if(FieldMetadata.useDedicatedSlot(this, handler)){
            writeObject(obj);
        }else{
            if(obj == null){
                
                // TODO: This should never happen. All handlers should take care
                //       of nulls on a higher level, otherwise primitive wrappers
                //       default to their primitive values.
                
                //       Consider to throw an IllegalArgumentException here to
                //       prevent users from calling with null arguments.
                
                writeNullObject(handler);
                
            } else{
                createIndirectionWithinSlot(handler);
                handler.write(this, obj);
            }
        }
        restoreState(state);
    }
    
    private void writeNullObject(TypeHandler4 handler){
        if( isIndirectedWithinSlot(handler)){
            doNotIndirectWrites();
            writeNullLink();
            return;
        }
        
        if (handler instanceof PrimitiveHandler){
            PrimitiveHandler primitiveHandler = (PrimitiveHandler) handler;
            handler.write(this, primitiveHandler.nullRepresentationInUntypedArrays());
            return;
        }
            
        handler.write(this, null);
    }
    
    private void writeNullLink(){
        writeInt(0);
        writeInt(0);
    }
    
    public void addIndexEntry(FieldMetadata fieldMetadata, Object obj) {
        if(! _currentBuffer.hasParent()){
            Object indexEntry = (obj == _currentMarshalledObject) ? _currentIndexEntry : obj; 
            fieldMetadata.addIndexEntry(transaction(), objectID(), indexEntry);
            return;
        }
        _currentBuffer.requestIndexEntry(fieldMetadata);
    }
    
    public ObjectReference reference(){
        return _reference;
    }
    
    public void doNotIndirectWrites(){
        _fieldWriteCount = NO_INDIRECTION;
    }
    
    public void createIndirectionWithinSlot(TypeHandler4 handler) {
        if(isIndirectedWithinSlot(handler)){
        	createIndirectionWithinSlot();
        }
    }
    
    public void createIndirectionWithinSlot() {
    	createChildBuffer(false, true);
    	doNotIndirectWrites();
    }

    private boolean isIndirectedWithinSlot(TypeHandler4 handler) {
        return SlotFormat.current().isIndirectedWithinSlot(handler);
    }

    // FIXME: This method was just temporarily added to fulfill contract of MarshallingInfo
    //        It will go, the buffer is never needed in new marshalling. 
    public ReadBuffer buffer() {
        return null;
    }
    
    public MarshallingContextState currentState(){
        return new MarshallingContextState(_currentBuffer, _fieldWriteCount);
    }
    
    public void restoreState(MarshallingContextState state){
        _currentBuffer = state._buffer;
        _fieldWriteCount = state._fieldWriteCount;
    }

    public ReservedBuffer reserve(final int length) {
        preWrite();
        ReservedBuffer reservedBuffer = _currentBuffer.reserve(length);
        postWrite();
        return reservedBuffer;
    }

	public int aspectCount() {
		return _aspectCount;
	}

	public void aspectCount(int count) {
		_aspectCount = count;
	}
    


}
