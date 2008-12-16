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

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.reflect.*;


/**
 * @exclude
 */
public final class ClassMetadataRepository extends PersistentBase {

    private Collection4 _classes;
    private Hashtable4 _creating;
    
    private final Transaction _systemTransaction;

    private Hashtable4 _classMetadataByBytes;
    private Hashtable4 _classMetadataByClass;
    private Hashtable4 _classMetadataByName;
    private Hashtable4 _classMetadataByID;
    
    private int _classMetadataCreationDepth;
    private Queue4 _initClassMetadataOnUp;
	
	private final PendingClassInits _classInits; 


    ClassMetadataRepository(Transaction systemTransaction) {
        _systemTransaction = systemTransaction;
        _initClassMetadataOnUp = new NonblockingQueue();
		_classInits = new PendingClassInits(_systemTransaction);
    }

    public void addClassMetadata(ClassMetadata clazz) {
        container().setDirtyInSystemTransaction(this);
        _classes.add(clazz);
        if(clazz.stateUnread()){
            _classMetadataByBytes.put(clazz.i_nameBytes, clazz);
        }else{
            _classMetadataByClass.put(clazz.classReflector(), clazz);
        }
        if (clazz.getID() == 0) {
            clazz.write(_systemTransaction);
        }
        _classMetadataByID.put(clazz.getID(), clazz);
    }
    
    private byte[] asBytes(String str){
        return container().stringIO().write(str);
    }

    public void attachQueryNode(final String fieldName, final Visitor4 visitor) {
        ClassMetadataIterator i = iterator();
        while (i.moveNext()) {
            final ClassMetadata classMetadata = i.currentClass();
            if(! classMetadata.isInternal()){
                classMetadata.forEachField(new Procedure4() {
                    public void apply(Object obj) {
                        FieldMetadata field = (FieldMetadata)obj;
                        if(field.canAddToQuery(fieldName)){
                            visitor.visit(new Object[] {classMetadata, field});
                        }
                    }
                });

            }
        }
    }
    
    public void iterateTopLevelClasses(Visitor4 visitor){
        ClassMetadataIterator i = iterator();
        while (i.moveNext()) {
            final ClassMetadata classMetadata = i.currentClass();
            if(! classMetadata.isInternal()){
                if(classMetadata.getAncestor() == null){
                    visitor.visit(classMetadata);
                }
            }
        }
    }

    void checkChanges() {
        Iterator4 i = _classes.iterator();
        while (i.moveNext()) {
            ((ClassMetadata)i.current()).checkChanges();
        }
    }
    
    final boolean createClassMetadata(ClassMetadata clazz, ReflectClass reflectClazz) {
        _classMetadataCreationDepth++;
        ReflectClass parentReflectClazz = reflectClazz.getSuperclass();
        ClassMetadata parentClazz = null;
        if (parentReflectClazz != null && ! parentReflectClazz.equals(container()._handlers.ICLASS_OBJECT)) {
            parentClazz = produceClassMetadata(parentReflectClazz);
        }
        boolean ret = container().createClassMetadata(clazz, reflectClazz, parentClazz);
        _classMetadataCreationDepth--;
        initClassMetadataOnUp();
        return ret;
    }

	private void ensureAllClassesRead() {
		boolean allClassesRead=false;
    	while(!allClassesRead) {
	    	Collection4 unreadClasses=new Collection4();
			int numClasses=_classes.size();
	        Iterator4 classIter = _classes.iterator();
	        while(classIter.moveNext()) {
	        	ClassMetadata clazz=(ClassMetadata)classIter.current();
	        	if(clazz.stateUnread()) {
	        		unreadClasses.add(clazz);
	        	}
	        }
	        Iterator4 unreadIter=unreadClasses.iterator();
	        while(unreadIter.moveNext()) {
	        	ClassMetadata clazz=(ClassMetadata)unreadIter.current();
	        	clazz = readClassMetadata(clazz,null);
	            if(clazz.classReflector() == null){
	            	clazz.forceRead();
	            }
	        }
	        allClassesRead=(_classes.size()==numClasses);
    	}
		applyReadAs();
	}

    boolean fieldExists(String field) {
        ClassMetadataIterator i = iterator();
        while (i.moveNext()) {
            if (i.currentClass().fieldMetadataForName(field) != null) {
                return true;
            }
        }
        return false;
    }

    public Collection4 forInterface(ReflectClass claxx) {
        Collection4 col = new Collection4();
        ClassMetadataIterator i = iterator();
        while (i.moveNext()) {
            ClassMetadata clazz = i.currentClass();
            ReflectClass candidate = clazz.classReflector();
            if(! candidate.isInterface()){
                if (claxx.isAssignableFrom(candidate)) {
                    col.add(clazz);
                    Iterator4 j = new Collection4(col).iterator();
                    while (j.moveNext()) {
                        ClassMetadata existing = (ClassMetadata)j.current();
                        if(existing != clazz){
                            ClassMetadata higher = clazz.getHigherHierarchy(existing);
                            if (higher != null) {
                                if (higher == clazz) {
                                    col.remove(existing);
                                }else{
                                    col.remove(clazz);
                                }
                            }
                        }
                    }
                }
            }
        }
        return col;
    }

    public byte getIdentifier() {
        return Const4.YAPCLASSCOLLECTION;
    }
    
    ClassMetadata getActiveClassMetadata(ReflectClass reflectClazz) {
        return (ClassMetadata)_classMetadataByClass.get(reflectClazz);
    }
    
    ClassMetadata classMetadataForReflectClass (ReflectClass reflectClazz) {
    	ClassMetadata clazz = (ClassMetadata)_classMetadataByClass.get(reflectClazz);
        if (clazz != null) {
        	return clazz;
        }
        clazz = (ClassMetadata)_classMetadataByBytes.remove(getNameBytes(reflectClazz.getName()));
        return readClassMetadata(clazz, reflectClazz);
    }

    ClassMetadata produceClassMetadata(ReflectClass reflectClazz) {
    	
    	ClassMetadata classMetadata = classMetadataForReflectClass(reflectClazz);
    	
        if (classMetadata != null ) {
            return classMetadata;
        }
        
        classMetadata = (ClassMetadata)_creating.get(reflectClazz);
        
        if(classMetadata != null){
            return classMetadata;
        }
        
        classMetadata = new ClassMetadata(container(), reflectClazz);
        
        _creating.put(reflectClazz, classMetadata);
        
        if(! createClassMetadata(classMetadata, reflectClazz)){
            _creating.remove(reflectClazz);
            return null;
        }

        // ObjectContainerBase#createClassMetadata may add the ClassMetadata already,
        // so we have to check again
        
        boolean addMembers = false;
        
        if (_classMetadataByClass.get(reflectClazz) == null) {
            addClassMetadata(classMetadata);
            addMembers = true;
        }
        
        int id = classMetadata.getID();
        if(id == 0){
            classMetadata.write(container().systemTransaction());
            id = classMetadata.getID();
        }
        
        if(_classMetadataByID.get(id) == null){
            _classMetadataByID.put(id, classMetadata);
            addMembers = true;
        }
        
        if(addMembers || classMetadata.aspectsAreNull()){
			_classInits.process(classMetadata);
        }
        
        _creating.remove(reflectClazz);
        
        container().setDirtyInSystemTransaction(this);
        
        return classMetadata;
    }    
    
	ClassMetadata getClassMetadata(int id) {
        return readClassMetadata((ClassMetadata)_classMetadataByID.get(id), null);
    }
	
	public int classMetadataIdForName(String name) {
	    ClassMetadata classMetadata = (ClassMetadata)_classMetadataByBytes.get(getNameBytes(name));
	    if(classMetadata == null){
	        classMetadata = findInitializedClassByName(name);
	    }
	    if(classMetadata != null){
	        return classMetadata.getID();
	    }
	    return 0;
	}
	
    public ClassMetadata getClassMetadata(String name) {
        ClassMetadata classMetadata = (ClassMetadata)_classMetadataByBytes.remove(getNameBytes(name));
        if (classMetadata == null) {
            classMetadata = findInitializedClassByName(name);
        }
        if(classMetadata != null){
            classMetadata = readClassMetadata(classMetadata, null);
        }
        return classMetadata;
    }
    
    private ClassMetadata findInitializedClassByName(String name){
    	ClassMetadata classMetadata = (ClassMetadata) _classMetadataByName.get(name);
    	if(classMetadata != null){
    		return classMetadata;
    	}
        ClassMetadataIterator i = iterator();
        while (i.moveNext()) {
            classMetadata = (ClassMetadata)i.current();
            if (name.equals(classMetadata.getName())) {
            	_classMetadataByName.put(name, classMetadata);
                return classMetadata;
            }
        }
        return null;
    }
    
    public int getClassMetadataID(String name){
        ClassMetadata clazz = (ClassMetadata)_classMetadataByBytes.get(getNameBytes(name));
        if(clazz != null){
            return clazz.getID();
        }
        return 0;
    }

	byte[] getNameBytes(String name) {		
		return asBytes(resolveAliasRuntimeName(name));
	}

	private String resolveAliasRuntimeName(String name) {
		return container().configImpl().resolveAliasRuntimeName(name);
	}

    void initOnUp(Transaction systemTrans) {
        _classMetadataCreationDepth++;
        systemTrans.container().showInternalClasses(true);
        try {
	        Iterator4 i = _classes.iterator();
	        while (i.moveNext()) {
	            ((ClassMetadata)i.current()).initOnUp(systemTrans);
	        }
        } finally {
        	systemTrans.container().showInternalClasses(false);
        }
        _classMetadataCreationDepth--;
        initClassMetadataOnUp();
    }

    void initTables(int size) {
        _classes = new Collection4();
        _classMetadataByBytes = new Hashtable4(size);
        if (size < 16) {
            size = 16;
        }
        _classMetadataByClass = new Hashtable4(size);
        _classMetadataByName = new Hashtable4(size);
        _classMetadataByID = new Hashtable4(size);
        _creating = new Hashtable4(1);
    }
    
    private void initClassMetadataOnUp() {
        if(_classMetadataCreationDepth == 0){
            ClassMetadata clazz = (ClassMetadata)_initClassMetadataOnUp.next();
            while(clazz != null){
                clazz.initOnUp(_systemTransaction);
                clazz = (ClassMetadata)_initClassMetadataOnUp.next();
            }
        }
    }
    
    public ClassMetadataIterator iterator(){
        return new ClassMetadataIterator(this, new ArrayIterator4(_classes.toArray()));
    } 

    private static class ClassIDIterator extends MappingIterator {

		public ClassIDIterator(Collection4 classes) {
			super(classes.iterator());
		}
    	
    	protected Object map(Object current) {
    		return new Integer(((ClassMetadata)current).getID());
    	}
    }
    
    public Iterator4 ids(){
        return new ClassIDIterator(_classes);
    } 

    public int ownLength() {
        return Const4.OBJECT_LENGTH
            + Const4.INT_LENGTH
            + (_classes.size() * Const4.ID_LENGTH);
    }

    void purge() {
        Iterator4 i = _classes.iterator();
        while (i.moveNext()) {
            ((ClassMetadata)i.current()).purge();
        }
    }

    public final void readThis(Transaction trans, ByteArrayBuffer buffer) {
		int classCount = buffer.readInt();

		initTables(classCount);

		ObjectContainerBase container = container();
		int[] ids = new int[classCount];

		for (int i = 0; i < classCount; ++i) {
			ids[i] = buffer.readInt();
		}
		StatefulBuffer[] clazzWriters = container.readWritersByIDs(trans, ids);

		for (int i = 0; i < classCount; ++i) {
			ClassMetadata classMetadata = new ClassMetadata(container, null);
			classMetadata.setID(ids[i]);
			_classes.add(classMetadata);
			_classMetadataByID.put(ids[i], classMetadata);
			byte[] name = classMetadata.readName1(trans, clazzWriters[i]);
			if (name != null) {
				_classMetadataByBytes.put(name, classMetadata);
			}
		}

		applyReadAs();

	}

	Hashtable4 classByBytes(){
    	return _classMetadataByBytes;
    }
    
    private void applyReadAs(){
        final Hashtable4 readAs = container().configImpl().readAs();
        Iterator4 i = readAs.iterator();
        while(i.moveNext()){
        	Entry4 entry = (Entry4) i.current();
            String dbName = (String)entry.key();
            String useName = (String)entry.value();
            byte[] dbbytes = getNameBytes(dbName);
            byte[] useBytes = getNameBytes(useName);
            if(classByBytes().get(useBytes) == null){
                ClassMetadata clazz = (ClassMetadata)classByBytes().get(dbbytes);
                if(clazz != null){
                    clazz.i_nameBytes = useBytes;
                    clazz.setConfig(configClass(dbName));
                    classByBytes().remove(dbbytes);
                    classByBytes().put(useBytes, clazz);
                }
            }
        }
    }

    private Config4Class configClass(String name) {
        return container().configImpl().configClass(name);
    }

    public ClassMetadata readClassMetadata(ClassMetadata classMetadata, ReflectClass clazz) {
    	if(classMetadata == null){
    		return null;
    	}
        if (! classMetadata.stateUnread()) {
            return classMetadata;
        }
        _classMetadataCreationDepth++;
        
        String name = classMetadata.resolveName(clazz);
        
        classMetadata.createConfigAndConstructor(_classMetadataByBytes, clazz, name);
        ReflectClass claxx = classMetadata.classReflector();
        if(claxx != null){
            _classMetadataByClass.put(claxx, classMetadata);
            classMetadata.readThis();
            classMetadata.checkChanges();
            _initClassMetadataOnUp.add(classMetadata);
        }
        _classMetadataCreationDepth--;
        initClassMetadataOnUp();
        return classMetadata;
    }

    public void refreshClasses() {
        ClassMetadataRepository rereader = new ClassMetadataRepository(_systemTransaction);
        rereader._id = _id;
        rereader.read(container().systemTransaction());
        Iterator4 i = rereader._classes.iterator();
        while (i.moveNext()) {
            ClassMetadata clazz = (ClassMetadata)i.current();
            refreshClass(clazz);
        }
        i = _classes.iterator();
        while (i.moveNext()) {
            ClassMetadata clazz = (ClassMetadata)i.current();
            clazz.refresh();
        }
    }
    
    public void checkAllClassChanges(){
    	Iterator4 i = _classMetadataByID.keys();
    	while(i.moveNext()){
    		int classMetadataID = ((Integer)i.current()).intValue();
    		getClassMetadata(classMetadataID);
    	}
    }

	public void refreshClass(ClassMetadata clazz) {
		if (_classMetadataByID.get(clazz.getID()) == null) {
		    _classes.add(clazz);
			_classMetadataByID.put(clazz.getID(), clazz);
		    refreshClassCache(clazz, null);
		}
	}

	public void refreshClassCache(ClassMetadata clazz, ReflectClass oldReflector) {
		if(clazz.stateUnread()){
		    _classMetadataByBytes.put(clazz.readName(_systemTransaction), clazz);
		}else{
			if(oldReflector != null){
				_classMetadataByClass.remove(oldReflector);
			}	
		    _classMetadataByClass.put(clazz.classReflector(), clazz);
		}
	}

    void reReadClassMetadata(ClassMetadata clazz){
        if(clazz != null){
            reReadClassMetadata(clazz.i_ancestor);
            clazz.readName(_systemTransaction);
            clazz.forceRead();
            clazz.setStateClean();
            clazz.bitFalse(Const4.CHECKED_CHANGES);
            clazz.bitFalse(Const4.READING);
            clazz.bitFalse(Const4.CONTINUE);
            clazz.bitFalse(Const4.DEAD);
            clazz.checkChanges();
        }
    }
    
    public StoredClass[] storedClasses() {
    	ensureAllClassesRead();
        StoredClass[] sclasses = new StoredClass[_classes.size()];
        _classes.toArray(sclasses);
        return sclasses;
    }

    public void writeAllClasses(){
    	Collection4 deadClasses = new Collection4();
        StoredClass[] storedClasses = storedClasses();
        for (int i = 0; i < storedClasses.length; i++) {
            ClassMetadata clazz = (ClassMetadata)storedClasses[i];
            clazz.setStateDirty();
            if(clazz.stateDead()){
            	deadClasses.add(clazz);
            	clazz.setStateOK();
            }
        }
        for (int i = 0; i < storedClasses.length; i++) {
            ClassMetadata clazz = (ClassMetadata)storedClasses[i];
            clazz.write(_systemTransaction);
        }
        Iterator4 it = deadClasses.iterator();
        while(it.moveNext()){
        	((ClassMetadata)it.current()).setStateDead();
        }
    }

    public void writeThis(Transaction trans, ByteArrayBuffer buffer) {
        buffer.writeInt(_classes.size());
        Iterator4 i = _classes.iterator();
        while (i.moveNext()) {
            buffer.writeIDOf(trans, i.current());
        }
    }

	public String toString(){
		String str = "Active:\n";
		Iterator4 i = _classes.iterator();
		while(i.moveNext()){
			ClassMetadata clazz = (ClassMetadata)i.current();
			str += clazz.getID() + " " + clazz + "\n";
		}
		return str;
	}

    ObjectContainerBase container() {
        return _systemTransaction.container();
    }
    
    public void setID(int id) {
    	if (container().isClient()) {
    		super.setID(id);
    		return;
    	}
    	
        if(_id == 0) {        	
			systemData().classCollectionID(id);
        }
        super.setID(id);
    }

	private SystemData systemData() {
		return localSystemTransaction().file().systemData();
	}

	private LocalTransaction localSystemTransaction() {
		return ((LocalTransaction)_systemTransaction);
	}

}
