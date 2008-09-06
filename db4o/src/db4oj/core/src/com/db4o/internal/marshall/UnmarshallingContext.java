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

import com.db4o.internal.*;
import com.db4o.internal.activation.*;


/**
 * Wraps the low-level details of reading a Buffer, which in turn is a glorified byte array.
 * 
 * @exclude
 */
public class UnmarshallingContext extends ObjectReferenceContext implements HandlerVersionContext{
    
    private Object _object;
    
    private int _addToIDTree;
    
    private boolean _checkIDTree;
    
    public UnmarshallingContext(Transaction transaction, ByteArrayBuffer buffer, ObjectReference ref, int addToIDTree, boolean checkIDTree) {
        super(transaction, buffer, null, ref);
        _addToIDTree = addToIDTree;
        _checkIDTree = checkIDTree;
    }
    
    public UnmarshallingContext(Transaction transaction, ObjectReference ref, int addToIDTree, boolean checkIDTree) {
        this(transaction, null, ref, addToIDTree, checkIDTree);
    }
    
    public Object read(){
        return readInternal(false);
    }
    
    public Object readPrefetch(){
        return readInternal(true);
    }
    
    private final Object readInternal(boolean doAdjustActivationDepthForPrefetch){
        if(! beginProcessing()){
            return _object;
        }
        
        readBuffer(objectID());
        
        if(buffer() == null){
            endProcessing();
            return _object;
        }
        
        ClassMetadata classMetadata = readObjectHeader();
        if(classMetadata == null){
            endProcessing();
            return _object;
        }
        
        _reference.classMetadata(classMetadata);
        
        adjustActivationDepth(doAdjustActivationDepthForPrefetch);
        
        if(_checkIDTree){
            Object objectInCacheFromClassCreation = transaction().objectForIdFromCache(objectID());
            if(objectInCacheFromClassCreation != null){
                _object = objectInCacheFromClassCreation;
                endProcessing();
                return _object;
            }
        }
        
        if(peekPersisted()){
            _object = classMetadata().instantiateTransient(this);
        }else{
            _object = classMetadata().instantiate(this);
        }
        
        endProcessing();
        return _object;
    }

	private void adjustActivationDepth(boolean doAdjustActivationDepthForPrefetch) {
		if(doAdjustActivationDepthForPrefetch){
            adjustActivationDepthForPrefetch();
        } else {
        	if (UnknownActivationDepth.INSTANCE == _activationDepth) {
        		_activationDepth = container().defaultActivationDepth(classMetadata());
        	}
        }
	}

    private void adjustActivationDepthForPrefetch() {
        activationDepth(activationDepthProvider().activationDepthFor(classMetadata(), ActivationMode.PREFETCH));
    }
    
    private ActivationDepthProvider activationDepthProvider() {
    	return container().activationDepthProvider();
	}

	public Object readFieldValue (FieldMetadata field){
        readBuffer(objectID());
        if(buffer() == null){
            return null;
        }
        ClassMetadata classMetadata = readObjectHeader(); 
        if(classMetadata == null){
            return null;
        }
        return readFieldValue(classMetadata, field);
    }

	private ClassMetadata readObjectHeader() {
        _objectHeader = new ObjectHeader(container(), byteArrayBuffer());
        ClassMetadata classMetadata = _objectHeader.classMetadata();
        if(classMetadata == null){
            return null;
        }
        return classMetadata;
    }

    private void readBuffer(int id) {
        if (buffer() == null && id > 0) {
            buffer(container().readReaderByID(transaction(), id)); 
        }
    }
    
    private boolean beginProcessing() {
        return _reference.beginProcessing();
    }
    
    private void endProcessing() {
        _reference.endProcessing();
    }

    public void setStateClean() {
        _reference.setStateClean();
    }

    public Object persistentObject() {
        return _object;
    }

    public void setObjectWeak(Object obj) {
        _reference.setObjectWeak(container(), obj);
    }

    protected boolean peekPersisted() {
        return _addToIDTree == Const4.TRANSIENT;
    }
    
    public Config4Class classConfig() {
        return classMetadata().config();
    }

    public void persistentObject(Object obj) {
        _object = obj;
    }

    
}

