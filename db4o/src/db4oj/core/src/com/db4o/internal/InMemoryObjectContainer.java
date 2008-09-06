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

import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;


/**
 * @exclude
 */
public class InMemoryObjectContainer extends LocalObjectContainer {

	private boolean _closed = false;
	private final MemoryFile _memoryFile;
	private int _length = 0;

	protected InMemoryObjectContainer(Configuration config,
			ObjectContainerBase parent, MemoryFile memoryFile)
			throws OldFormatException {
		super(config, parent);
		_memoryFile = memoryFile;
		open();
	}

    public InMemoryObjectContainer(Configuration config, MemoryFile memoryFile) {
        this(config, null, memoryFile);
    }
    
    protected final void openImpl() throws OldFormatException {
		byte[] bytes = _memoryFile.getBytes();
		if (bytes == null || bytes.length == 0) {
			_memoryFile.setBytes(new byte[_memoryFile.getInitialSize()]);
			configureNewFile();
			commitTransaction();
			writeHeader(false, false);
		} else {
			_length = bytes.length;
			readThis();
		}
	}
    
    public void backup(String path) throws NotSupportedException {
        throw new NotSupportedException();
    }
    
    public void blockSize(int size){
        // do nothing, blocksize is always 1
    }

    protected void freeInternalResources() {
    	// nothing to do here
    }

    protected void shutdownDataStorage() {
		if (!_closed) {
			byte[] temp = new byte[_length];
			System.arraycopy(_memoryFile.getBytes(), 0, temp, 0, _length);
			_memoryFile.setBytes(temp);
		}
		_closed = true;
		dropReferences();
	}
    
    protected void dropReferences() {
    	// do nothing
    }

	public void copy(int oldAddress, int oldAddressOffset, int newAddress, int newAddressOffset, int length) {
		int fullNewAddress = newAddress + newAddressOffset;
		ensureMemoryFileSize(fullNewAddress + length);
		byte[] bytes = _memoryFile.getBytes();
		System.arraycopy(bytes, oldAddress + oldAddressOffset, bytes, fullNewAddress, length);
	}

    public long fileLength() {
        return _length;
    }

    public String fileName() {
        return "Memory File";
    }

    protected boolean hasShutDownHook() {
        return false;
    }

    public final boolean needsLockFileThread() {
        return false;
    }

	public void readBytes(byte[] bytes, int address, int length) {
		try {
			System.arraycopy(_memoryFile.getBytes(), address, bytes, 0, length);
		} catch (Exception e) {
			Exceptions4.throwRuntimeException(13, e);
		}
	}

	public void readBytes(byte[] bytes, int address, int addressOffset, int length){
		readBytes(bytes, address + addressOffset, length);
	}

    public void syncFiles() {
    }

	public void writeBytes(ByteArrayBuffer buffer, int address, int addressOffset) {
		int fullAddress = address + addressOffset;
		int length = buffer.length();
		ensureMemoryFileSize(fullAddress + length);   
		System.arraycopy(buffer._buffer, 0, _memoryFile.getBytes(), fullAddress , length);
	}

    private void ensureMemoryFileSize(int last) {
		if (last < _length) return;
		
		byte[] bytes = _memoryFile.getBytes();
		if (last < bytes.length) {
			_length = last;
			return;
		}
		
		int increment = _memoryFile.getIncrementSizeBy();
        while (last > (increment + bytes.length)) {
        	increment <<= 1;
        }
		
		byte[] newBytes = new byte[bytes.length + increment];
		System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
		_memoryFile.setBytes(newBytes);
		_length = newBytes.length;
	}

    public void overwriteDeletedBytes(int a_address, int a_length) {
    }

	public void reserve(int byteCount) {
		throw new NotSupportedException();
	}

	public byte blockSize() {
		return 1;
	}
}
