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
package com.db4o.internal.fileheader;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.internal.*;

/**
 * @exclude
 */
public class FileHeader0 extends FileHeader {
    
    static final int HEADER_LENGTH = 2 + (Const4.INT_LENGTH * 4);

    // The header format is:

    // Old format
    // -------------------------
    // {
    // Y
    // [Rest]

    
    // New format
    // -------------------------
    // (byte)4
    // block size in bytes 1 to 127
    // [Rest]
    

    // Rest (only ints)
    // -------------------
    // address of the extended configuration block, see YapConfigBlock
    // headerLock
    // YapClassCollection ID
    // FreeBySize ID

    
    private ConfigBlock    _configBlock;
    
    private PBootRecord _bootRecord;
    

    public void close() throws Db4oIOException {
        _configBlock.close();
    }
    
    protected FileHeader newOnSignatureMatch(LocalObjectContainer file, ByteArrayBuffer reader) {
        byte firstFileByte = reader.readByte();
        if (firstFileByte != Const4.YAPBEGIN) {
            if(firstFileByte != Const4.YAPFILEVERSION){
                return null;
            }
            file.blockSizeReadFromFile(reader.readByte());
        }else{
            if (reader.readByte() != Const4.YAPFILE) {
                return null;
            }
        }
        return new FileHeader0();
    }

    
    protected void readFixedPart(LocalObjectContainer file, ByteArrayBuffer reader) throws OldFormatException {
        _configBlock = ConfigBlock.forExistingFile(file, reader.readInt());
        skipConfigurationLockTime(reader);
        readClassCollectionAndFreeSpace(file, reader);
    }

    private void skipConfigurationLockTime(ByteArrayBuffer reader) {
        reader.incrementOffset(Const4.ID_LENGTH);
    }

    public void readVariablePart(LocalObjectContainer file){
        if (_configBlock._bootRecordID <= 0) {
            return;
        }
        Object bootRecord = Debug.readBootRecord ? getBootRecord(file) : null;
        
        if (! (bootRecord instanceof PBootRecord)) {
            initBootRecord(file);
            file.generateNewIdentity();
            return;
        }
        
        _bootRecord = (PBootRecord) bootRecord;
        file.activate(bootRecord, Integer.MAX_VALUE);
        file.setNextTimeStampId(_bootRecord.i_versionGenerator);
        
        file.systemData().identity(_bootRecord.i_db);
    }

	private Object getBootRecord(LocalObjectContainer file) {
		file.showInternalClasses(true);
		try {
			return file.getByID(file.systemTransaction(), _configBlock._bootRecordID);
		} finally {
			file.showInternalClasses(false);
		}
	}

    public void initNew(LocalObjectContainer file) throws Db4oIOException {
        _configBlock = ConfigBlock.forNewFile(file);
        initBootRecord(file);
    }
    
    private void initBootRecord(LocalObjectContainer file){
        
        file.showInternalClasses(true);
        try {
	        _bootRecord = new PBootRecord();
	        file.storeInternal(file.systemTransaction(), _bootRecord, false);
	        
	        _configBlock._bootRecordID = file.getID(file.systemTransaction(), _bootRecord);
	        writeVariablePart(file, 1);
        } finally {
        	file.showInternalClasses(false);
        }
    }

    public Transaction interruptedTransaction() {
        return _configBlock.getTransactionToCommit();
    }

    public void writeTransactionPointer(Transaction systemTransaction, int transactionAddress) {
        writeTransactionPointer(systemTransaction, transactionAddress, _configBlock.address(), ConfigBlock.TRANSACTION_OFFSET);
    }

    public MetaIndex getUUIDMetaIndex() {
        return _bootRecord.getUUIDMetaIndex();
    }

    public int length(){
        return HEADER_LENGTH;
    }

    public void writeFixedPart(LocalObjectContainer file, boolean startFileLockingThread, boolean shuttingDown, StatefulBuffer writer, int blockSize_, int freespaceID) {
        writer.writeByte(Const4.YAPFILEVERSION);
        writer.writeByte((byte)blockSize_);
        writer.writeInt(_configBlock.address());
        writer.writeInt((int)timeToWrite(_configBlock.openTime(), shuttingDown));
        writer.writeInt(file.systemData().classCollectionID());
        writer.writeInt(freespaceID);
        if (Debug.xbytes && Deploy.overwrite) {
            writer.setID(Const4.IGNORE_ID);
        }
        writer.write();
        file.syncFiles();
    }
    
    public void writeVariablePart(LocalObjectContainer file, int part) {
        if(part == 1){
            _configBlock.write();
        }else if(part == 2){
            _bootRecord.write(file);
        }
    }

}
