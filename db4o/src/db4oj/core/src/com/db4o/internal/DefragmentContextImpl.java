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

import java.io.*;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.mapping.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public final class DefragmentContextImpl implements ReadWriteBuffer, DefragmentContext {
    
	private ByteArrayBuffer _source;
	
	private ByteArrayBuffer _target;
	
	private DefragmentServices _services;
	
	private final ObjectHeader _objectHeader;
	
	private int _aspectCount;
	
	public DefragmentContextImpl(ByteArrayBuffer source, DefragmentContextImpl context) {
		this(source, context._services, context._objectHeader);
	}

	public DefragmentContextImpl(ByteArrayBuffer source,DefragmentServices services) {
	    this(source, services, null);
	}
	
	public DefragmentContextImpl(ByteArrayBuffer source, DefragmentServices services, ObjectHeader header){
        _source = source;
        _services=services;
        _target = new ByteArrayBuffer(length());
        _source.copyTo(_target, 0, 0, length());
        _objectHeader = header;
	}
	
	public DefragmentContextImpl(DefragmentContextImpl parentContext, ObjectHeader header){
	    _source = parentContext._source;
	    _target = parentContext._target;
	    _services = parentContext._services;
	    _objectHeader = header;
	}
	
	public int offset() {
		return _source.offset();
	}

	public void seek(int offset) {
		_source.seek(offset);
		_target.seek(offset);
	}

	public void incrementOffset(int numBytes) {
		_source.incrementOffset(numBytes);
		_target.incrementOffset(numBytes);
	}

	public void incrementIntSize() {
		incrementOffset(Const4.INT_LENGTH);
	}
	
	public int copySlotlessID() {
	    return copyUnindexedId(false);
	}

	public int copyUnindexedID() {
	    return copyUnindexedId(true);
	}
	
	private int copyUnindexedId(boolean doRegister){
        int orig=_source.readInt();

        // TODO: There is no test case for the zero case
        if(orig == 0){
            _target.writeInt(0);
            return 0;
        }
        
        int mapped=-1;
        try {
            mapped=_services.mappedID(orig);
        } catch (MappingNotFoundException exc) {
            mapped=_services.allocateTargetSlot(Const4.POINTER_LENGTH).address();
            _services.mapIDs(orig,mapped, false);
            if(doRegister){
                _services.registerUnindexed(orig);
            }
        }
        _target.writeInt(mapped);
        return mapped;
	}

	public int copyID() {
		// This code is slightly redundant. 
		// The profiler shows it's a hotspot.
		// The following would be non-redudant. 
		// return copy(false, false);
		
		int id = _source.readInt();
		return writeMappedID(id);
	}

	public int copyID(boolean flipNegative,boolean lenient) {
		int id=_source.readInt();
		return internalCopyID(flipNegative, lenient, id);
	}

	public int copyIDReturnOriginalID() {
		int id=_source.readInt();
		internalCopyID(false, false, id);
		return id;
	}

	private int internalCopyID(boolean flipNegative, boolean lenient, int id) {
		if(flipNegative&&id<0) {
			id=-id;
		}
		int mapped=_services.mappedID(id,lenient);
		if(flipNegative&&id<0) {
			mapped=-mapped;
		}
		_target.writeInt(mapped);
		return mapped;
	}
	
	public void readBegin(byte identifier) {
		_source.readBegin(identifier);
		_target.readBegin(identifier);
	}
	
	public byte readByte() {
		byte value=_source.readByte();
		_target.incrementOffset(1);
		return value;
	}
	
	public void readBytes(byte[] bytes) {
		_source.readBytes(bytes);
		_target.incrementOffset(bytes.length);
	}

	public int readInt() {
		int value=_source.readInt();
		_target.incrementOffset(Const4.INT_LENGTH);
		return value;
	}

	public void writeInt(int value) {
		_source.incrementOffset(Const4.INT_LENGTH);
		_target.writeInt(value);
	}
	
	public void write(LocalObjectContainer file,int address) {
		file.writeBytes(_target,address,0);
	}
	
	public void incrementStringOffset(LatinStringIO sio) {
	    incrementStringOffset(sio, _source);
	    incrementStringOffset(sio, _target);
	}
	
	private void incrementStringOffset(LatinStringIO sio, ByteArrayBuffer buffer) {
	    int length = buffer.readInt();
	    if(length > 0){
	        sio.read(buffer, length);
	    }
	}
	
	public ByteArrayBuffer sourceBuffer() {
		return _source;
	}

	public ByteArrayBuffer targetBuffer() {
		return _target;
	}
	
	public IDMapping mapping() {
		return _services;
	}

	public Transaction systemTrans() {
		return transaction();
	}

	public DefragmentServices services() {
		return _services;
	}

	public static void processCopy(DefragmentServices services, int sourceID,SlotCopyHandler command)  {
		processCopy(services, sourceID, command, false);
	}

	public static void processCopy(DefragmentServices context, int sourceID,SlotCopyHandler command,boolean registerAddressMapping) {
		ByteArrayBuffer sourceReader = context.sourceBufferByID(sourceID);
		processCopy(context, sourceID, command, registerAddressMapping, sourceReader);
	}

	public static void processCopy(DefragmentServices services, int sourceID,SlotCopyHandler command,boolean registerAddressMapping, ByteArrayBuffer sourceReader) {
		int targetID=services.mappedID(sourceID);
	
		Slot targetSlot = services.allocateTargetSlot(sourceReader.length());
		
		if(registerAddressMapping) {
			int sourceAddress=services.sourceAddressByID(sourceID);
			services.mapIDs(sourceAddress, targetSlot.address(), false);
		}
		
		ByteArrayBuffer targetPointerReader=new ByteArrayBuffer(Const4.POINTER_LENGTH);
		if(Deploy.debug) {
			targetPointerReader.writeBegin(Const4.YAPPOINTER);
		}
		targetPointerReader.writeInt(targetSlot.address());
		targetPointerReader.writeInt(targetSlot.length());
		if(Deploy.debug) {
			targetPointerReader.writeEnd();
		}
		services.targetWriteBytes(targetPointerReader,targetID);
		
		DefragmentContextImpl context=new DefragmentContextImpl(sourceReader,services);
		command.processCopy(context);
		services.targetWriteBytes(context,targetSlot.address());
	}

	public void writeByte(byte value) {
		_source.incrementOffset(1);
		_target.writeByte(value);
	}

	public long readLong() {
		long value=_source.readLong();
		_target.incrementOffset(Const4.LONG_LENGTH);
		return value;
	}

	public void writeLong(long value) {
		_source.incrementOffset(Const4.LONG_LENGTH);
		_target.writeLong(value);
	}

	public BitMap4 readBitMap(int bitCount) {
		BitMap4 value=_source.readBitMap(bitCount);
		_target.incrementOffset(value.marshalledLength());
		return value;
	}

	public void readEnd() {
		_source.readEnd();
		_target.readEnd();
	}

    public int writeMappedID(int originalID) {
		int mapped=_services.mappedID(originalID,false);
		_target.writeInt(mapped);
		return mapped;
	}

	public int length() {
		return _source.length();
	}
	
	public Transaction transaction() {
		return services().systemTrans();
	}
	
	private ObjectContainerBase container() {
	    return transaction().container();
	}

	public TypeHandler4 typeHandlerForId(int id) {
		return container().typeHandlerForId(id);
	}
	
	public int handlerVersion(){
		return _objectHeader.handlerVersion();
	}

	public boolean isLegacyHandlerVersion() {
		return handlerVersion() == 0;
	}

	public int mappedID(int origID) {
		return mapping().mappedID(origID);
	}

	public ObjectContainer objectContainer() {
		return container();
	}

	public Slot allocateTargetSlot(int length) {
		return _services.allocateTargetSlot(length);
	}

	public Slot allocateMappedTargetSlot(int sourceAddress, int length) {
		Slot slot = allocateTargetSlot(length);
		_services.mapIDs(sourceAddress, slot.address(), false);
		return slot;
	}

	public int copySlotToNewMapped(int sourceAddress, int length) throws IOException {
    	Slot slot = allocateMappedTargetSlot(sourceAddress, length);
    	ByteArrayBuffer sourceBuffer = sourceBufferByAddress(sourceAddress, length);
    	targetWriteBytes(slot.address(), sourceBuffer);
		return slot.address();
	}

	public void targetWriteBytes(int address, ByteArrayBuffer buffer) {
		_services.targetWriteBytes(buffer, address);
	}

	public ByteArrayBuffer sourceBufferByAddress(int sourceAddress, int length) throws IOException {
		ByteArrayBuffer sourceBuffer = _services.sourceBufferByAddress(sourceAddress, length);
		return sourceBuffer;
	}

	public ByteArrayBuffer sourceBufferById(int sourceId) throws IOException {
		ByteArrayBuffer sourceBuffer = _services.sourceBufferByID(sourceId);
		return sourceBuffer;
	}

	public void writeToTarget(int address) {
		_services.targetWriteBytes(this, address);
	}

    public void writeBytes(byte[] bytes) {
        _target.writeBytes(bytes);
        _source.incrementOffset(bytes.length);
    }

    public void seekCurrentInt() {
        seek(readInt());
    }

    public ReadBuffer buffer() {
        return _source;
    }

    public void defragment(TypeHandler4 handler) {
        final TypeHandler4 typeHandler = Handlers4.correctHandlerVersion(this, handler);
        if(FieldMetadata.useDedicatedSlot(this, typeHandler)){
            if(hasClassIndex(typeHandler)){
                copyID();
            } else {
                copyUnindexedID();
            }
            return;
        }
        typeHandler.defragment(DefragmentContextImpl.this);
    }

    private boolean hasClassIndex(TypeHandler4 typeHandler) {
        if(typeHandler instanceof ClassMetadata){
            return ((ClassMetadata)typeHandler).hasClassIndex();
        }
        return false;
    }

    public void beginSlot() {
        // do nothing
    }

    public ClassMetadata classMetadata() {
        return _objectHeader.classMetadata();
    }

    public boolean isNull(int fieldIndex) {
        return _objectHeader._headerAttributes.isNull(fieldIndex);
    }

	public int aspectCount() {
		return _aspectCount;
	}

	public void aspectCount(int count) {
		_aspectCount = count;
	}

	public SlotFormat slotFormat() {
		return SlotFormat.forHandlerVersion(handlerVersion());
	}
    
}
