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
package com.db4o.defragment;

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.io.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.classindex.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.mapping.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.slots.*;
import com.db4o.io.*;
import com.db4o.typehandlers.*;

/**
 * @exclude
 */
public class DefragmentServicesImpl implements DefragmentServices {	

	public static abstract class DbSelector {
		DbSelector() {
		}
		
		abstract LocalObjectContainer db(DefragmentServicesImpl context);

		Transaction transaction(DefragmentServicesImpl context) {
			return db(context).systemTransaction();
		}
	}
	
	public final static DbSelector SOURCEDB=new DbSelector() {
		LocalObjectContainer db(DefragmentServicesImpl context) {
			return context._sourceDb;
		}
	};

	public final static DbSelector TARGETDB=new DbSelector() {
		LocalObjectContainer db(DefragmentServicesImpl context) {
			return context._targetDb;
		}
	};

	private static final long CLASSCOLLECTION_POINTER_ADDRESS = 2+2*Const4.INT_LENGTH;
	
	public final LocalObjectContainer _sourceDb;
	final LocalObjectContainer _targetDb;
	private final ContextIDMapping _mapping;
	private DefragmentListener _listener;
	private Queue4 _unindexed=new NonblockingQueue();
	private final Hashtable4 _hasFieldIndexCache = new Hashtable4();

	private DefragmentConfig _defragConfig;
	

	public DefragmentServicesImpl(DefragmentConfig defragConfig,DefragmentListener listener) {
		_listener=listener;
		Config4Impl originalConfig =  (Config4Impl) defragConfig.db4oConfig();
		Configuration sourceConfig=(Configuration) originalConfig.deepClone(null);
		sourceConfig.weakReferences(false);
		sourceConfig.io(new NonFlushingIoAdapter(sourceConfig.io()));
		sourceConfig.readOnly(defragConfig.readOnly());
		_sourceDb=(LocalObjectContainer)Db4o.openFile(sourceConfig,defragConfig.tempPath()).ext();
		_sourceDb.showInternalClasses(true);
		_targetDb = freshYapFile(defragConfig);
		_mapping=defragConfig.mapping();
		_mapping.open();
		_defragConfig = defragConfig;
	}
	
	static LocalObjectContainer freshYapFile(String fileName,int blockSize) {
		File4.delete(fileName);
		return (LocalObjectContainer)Db4o.openFile(DefragmentConfig.vanillaDb4oConfig(blockSize),fileName).ext();
	}
	
	static LocalObjectContainer freshYapFile(DefragmentConfig  config) {
		File4.delete(config.origPath());
		return (LocalObjectContainer)Db4o.openFile(config.clonedDb4oConfig(),config.origPath()).ext();
	}
	
	public int mappedID(int oldID,int defaultID) {
		int mapped=internalMappedID(oldID,false);
		return (mapped!=0 ? mapped : defaultID);
	}

	public int mappedID(int oldID) throws MappingNotFoundException {
		int mapped=internalMappedID(oldID,false);
		if(mapped==0) {
			throw new MappingNotFoundException(oldID);
		}
		return mapped;
	}

	public int mappedID(int id,boolean lenient) throws MappingNotFoundException {
		if(id == 0){
			return 0;
		}
		int mapped = internalMappedID(id,lenient);
		if(mapped==0) {
			_listener.notifyDefragmentInfo(new DefragmentInfo("No mapping found for ID "+id));
			return 0;
		}
		return mapped;
	}

	private int internalMappedID(int oldID,boolean lenient) throws MappingNotFoundException {
		if(oldID==0) {
			return 0;
		}
		if(_sourceDb.handlers().isSystemHandler(oldID)) {
			return oldID;
		}
		return _mapping.mappedID(oldID,lenient);
	}

	public void mapIDs(int oldID,int newID, boolean isClassID) {
		_mapping.mapIDs(oldID,newID, isClassID);
	}

	public void close() {
		_sourceDb.close();
		_targetDb.close();
		_mapping.close();
	}
	
	public ByteArrayBuffer bufferByID(DbSelector selector,int id) {
		Slot slot=readPointer(selector, id);
		return bufferByAddress(selector,slot.address(),slot.length());
	}

	public ByteArrayBuffer sourceBufferByAddress(int address,int length) throws IOException {
		return bufferByAddress(SOURCEDB, address, length);
	}

	public ByteArrayBuffer targetBufferByAddress(int address,int length) throws IOException {
		return bufferByAddress(TARGETDB, address, length);
	}

	public ByteArrayBuffer bufferByAddress(DbSelector selector,int address,int length) {
		return selector.db(this).bufferByAddress(address,length);
	}

	public StatefulBuffer targetStatefulBufferByAddress(int address,int length) throws IllegalArgumentException {
		return _targetDb.readWriterByAddress(TARGETDB.transaction(this),address,length);
	}
	
	public Slot allocateTargetSlot(int length) {
		return _targetDb.getSlot(length);
	}

	public void targetWriteBytes(DefragmentContextImpl context,int address) {
		context.write(_targetDb,address);
	}

	public void targetWriteBytes(ByteArrayBuffer reader,int address) {
		_targetDb.writeBytes(reader,address,0);
	}

	public StoredClass[] storedClasses(DbSelector selector) {
		LocalObjectContainer db = selector.db(this);
		db.showInternalClasses(true);
		try {
			return db.classCollection().storedClasses();
		} finally {
			db.showInternalClasses(false);
		}
	}
	
	public LatinStringIO stringIO() {
		return _sourceDb.stringIO();
	}
	
	public void targetCommit() {
		_targetDb.commit();
	}
	
	public TypeHandler4 sourceHandler(int id) {
	    return _sourceDb.typeHandlerForId(id);
	}
	
	public int sourceClassCollectionID() {
		return _sourceDb.classCollection().getID();
	}

	public static void targetClassCollectionID(String file,int id) throws IOException {
		RandomAccessFile raf=new RandomAccessFile(file,"rw");
		try {
			ByteArrayBuffer reader=new ByteArrayBuffer(Const4.INT_LENGTH);

			raf.seek(CLASSCOLLECTION_POINTER_ADDRESS);			
			reader._offset=0;
			reader.writeInt(id);
			raf.write(reader._buffer);
		}
		finally {
			raf.close();
		}
	}

	private Hashtable4 _classIndices=new Hashtable4(16);

	public int classIndexID(ClassMetadata yapClass) {
		return classIndex(yapClass).id();
	}

	public void traverseAll(ClassMetadata yapClass,Visitor4 command) {
		if(!yapClass.hasClassIndex()) {
			return;
		}
		yapClass.index().traverseAll(SOURCEDB.transaction(this), command);
	}
	
	public void traverseAllIndexSlots(ClassMetadata yapClass,Visitor4 command) {
		Iterator4 slotIDIter=yapClass.index().allSlotIDs(SOURCEDB.transaction(this));
		while(slotIDIter.moveNext()) {
			command.visit(slotIDIter.current());
		}
	}

	public void traverseAllIndexSlots(BTree btree,Visitor4 command) {
		Iterator4 slotIDIter=btree.allNodeIds(SOURCEDB.transaction(this));
		while(slotIDIter.moveNext()) {
			command.visit(slotIDIter.current());
		}
	}

	public int databaseIdentityID(DbSelector selector) {
		LocalObjectContainer db = selector.db(this);
		Db4oDatabase identity = db.identity();
		if(identity==null) {
			return 0;
		}
		return identity.getID(selector.transaction(this));
	}
	
	private ClassIndexStrategy classIndex(ClassMetadata yapClass) {
		ClassIndexStrategy classIndex=(ClassIndexStrategy)_classIndices.get(yapClass);
		if(classIndex==null) {
			classIndex=new BTreeClassIndexStrategy(yapClass);
			_classIndices.put(yapClass,classIndex);
			classIndex.initialize(_targetDb);
		}
		return classIndex;
	}

	public Transaction systemTrans() {
		return SOURCEDB.transaction(this);
	}

	public void copyIdentity() {
		_targetDb.setIdentity(_sourceDb.identity());
	}

	public void targetClassCollectionID(int newClassCollectionID) {
		_targetDb.systemData().classCollectionID(newClassCollectionID);
	}

	public ByteArrayBuffer sourceBufferByID(int sourceID)  {
		return bufferByID(SOURCEDB,sourceID);
	}
	
	public BTree sourceUuidIndex() {
		if(sourceUuidIndexID()==0) {
			return null;
		}
		return _sourceDb.uUIDIndex().getIndex(systemTrans());
	}
	
	public void targetUuidIndexID(int id) {
		_targetDb.systemData().uuidIndexId(id);
	}

	public int sourceUuidIndexID() {
		return _sourceDb.systemData().uuidIndexId();
	}
	
	public ClassMetadata classMetadataForId(int id) {
		return _sourceDb.classMetadataForId(id);
	}
	
	public void registerUnindexed(int id) {
		_unindexed.add(new Integer(id));
	}

	public IdSource unindexedIDs() {
		return new IdSource(_unindexed);
	}

	public ObjectHeader sourceObjectHeader(ByteArrayBuffer buffer) {
		return new ObjectHeader(_sourceDb, buffer);
	}
	
	private Slot readPointer(DbSelector selector,int id) {
		ByteArrayBuffer reader=bufferByAddress(selector, id, Const4.POINTER_LENGTH);
        if(Deploy.debug){
            reader.readBegin(Const4.YAPPOINTER);    
        }
		int address=reader.readInt();
		int length=reader.readInt();
        if(Deploy.debug){
            reader.readEnd();
        }
		return new Slot(address,length);
	}
	
	public boolean hasFieldIndex(ClassMetadata clazz) {
		// actually only two states are used here, the third is implicit in null
		TernaryBool cachedHasFieldIndex = ((TernaryBool) _hasFieldIndexCache.get(clazz));
		if(cachedHasFieldIndex != null) {
			return cachedHasFieldIndex.definiteYes();
		}
		final BooleanByRef hasFieldIndex = new BooleanByRef(false);
		ClassMetadata curClazz = clazz;
		while(!hasFieldIndex.value && curClazz != null) {
			curClazz.forEachDeclaredField(new Procedure4() {
				public void apply(Object arg) {
					FieldMetadata curField = (FieldMetadata)arg;
					if (curField.hasIndex()
							&& (curField.getHandler() instanceof StringHandler)) {
						hasFieldIndex.value = true;
					}
				}
			});
			curClazz = curClazz.getAncestor();
		}
		_hasFieldIndexCache.put(clazz, TernaryBool.forBoolean(hasFieldIndex.value));
		return hasFieldIndex.value;
	}

	public int blockSize() {
		return _sourceDb.config().blockSize();
	}

	public int sourceAddressByID(int sourceID) {
		return readPointer(SOURCEDB, sourceID).address();
	}

	public boolean accept(StoredClass klass) {
		return this._defragConfig.storedClassFilter().accept(klass);
	}
	
}