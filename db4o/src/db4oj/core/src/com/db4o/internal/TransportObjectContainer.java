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
import com.db4o.internal.convert.*;
import com.db4o.internal.slots.*;
import com.db4o.reflect.*;
import com.db4o.types.*;


/**
 * no reading
 * no writing
 * no updates
 * no weak references
 * navigation by ID only both sides need synchronised ClassCollections and
 * MetaInformationCaches
 * 
 * @exclude
 */
public class TransportObjectContainer extends InMemoryObjectContainer {
	
	public TransportObjectContainer (ObjectContainerBase serviceProvider, MemoryFile memoryFile) {
	    super(serviceProvider.config(),serviceProvider, memoryFile);
	    _showInternalClasses = serviceProvider._showInternalClasses;
	}
	
	protected void initialize1(Configuration config){
	    _handlers = _parent._handlers;
        _classCollection = _parent.classCollection();
		_config = _parent.configImpl();
		_references = new WeakReferenceCollector(this);
		initialize2();
	}
	
	void initialize2NObjectCarrier(){
		// do nothing
	}
	
	void initializeEssentialClasses(){
	    // do nothing
	}
	
	protected void initializePostOpenExcludingTransportObjectContainer(){
		// do nothing
	}
	
	void initNewClassCollection(){
	    // do nothing
	}
	
    boolean canUpdate(){
        return false;
    }
    
    public ClassMetadata classMetadataForId(int id) {
    	return _parent.classMetadataForId(id);
    }
    
	void configureNewFile() {
	    // do nothing
	}
    
    public int converterVersion() {
        return Converter.VERSION;
    }
		
    protected void dropReferences() {
        _config = null;
    }
    
    protected void handleExceptionOnClose(Exception exc) {
    	// do nothing here
    }

	final public Transaction newTransaction(Transaction parentTransaction, TransactionalReferenceSystem referenceSystem) {
		if (null != parentTransaction) {
			return parentTransaction;
		}
		return new TransactionObjectCarrier(this, null, referenceSystem);
	}
	
	public long currentVersion(){
	    return 0;
	}
    
    public Db4oType db4oTypeStored(Transaction a_trans, Object a_object) {
        return null;
    }
	
    public boolean dispatchsEvents() {
        return false;
    }
	
    protected void finalize() {
        // do nothing
    }
	
	
	public final void free(int a_address, int a_length){
		// do nothing
	}
	
	public final void free(Slot slot){
		// do nothing
	}
	
	public Slot getSlot(int length){
        return appendBlocks(length);
	}
	
	public Db4oDatabase identity() {
	    return ((ExternalObjectContainer) _parent).identity();
	}
	
	public boolean maintainsIndices(){
		return false;
	}
	
	void message(String msg){
		// do nothing
	}
	
	public ClassMetadata produceClassMetadata(ReflectClass claxx) {
		return _parent.produceClassMetadata(claxx);
	}
	
	public void raiseVersion(long a_minimumVersion){
	    // do nothing
	}
	
	void readThis(){
		// do nothing
	}
	
	boolean stateMessages(){
		return false; // overridden to do nothing in YapObjectCarrier
	}
    
	public void shutdown() {
		processPendingClassUpdates();
		writeDirty();
		transaction().commit();
	}
	
	final void writeHeader(boolean startFileLockingThread, boolean shuttingDown) {
	    // do nothing
	}
    
    protected void writeVariableHeader(){
        
    }
    
    public static class KnownObjectIdentity {
    	public int _id;
    	public KnownObjectIdentity(int id) {
			_id = id;
		}
    }
    
    public int storeInternal(Transaction trans, Object obj, int depth,
			boolean checkJustSet)
    		throws DatabaseClosedException, DatabaseReadOnlyException {
    	int id = _parent.getID(null, obj);
    	if(id > 0){
    		return super.storeInternal(trans, new KnownObjectIdentity(id), depth, checkJustSet);
    	}
    	return super.storeInternal(trans, obj, depth, checkJustSet);
    }
    
    public Object getByID2(Transaction ta, int id) {
    	Object obj = super.getByID2(ta, id);
    	if(obj instanceof KnownObjectIdentity){
    		KnownObjectIdentity oi = (KnownObjectIdentity)obj;
    		activate(oi);
    		obj = _parent.getByID(null, oi._id);
    	}
    	
    	return obj;
    }

}