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
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.callbacks.*;
import com.db4o.internal.cs.*;
import com.db4o.internal.fieldhandlers.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.query.*;
import com.db4o.internal.query.processor.*;
import com.db4o.internal.query.result.*;
import com.db4o.internal.replication.*;
import com.db4o.internal.slots.*;
import com.db4o.query.*;
import com.db4o.reflect.*;
import com.db4o.reflect.core.*;
import com.db4o.reflect.generic.*;
import com.db4o.typehandlers.*;
import com.db4o.types.*;


/**
 * NOTE: This is just a 'partial' base class to allow for variant implementations
 * in db4oj and db4ojdk1.2. It assumes that itself is an instance of ObjectContainerBase
 * and should never be used explicitly.
 * 
 * @exclude
 * @sharpen.partial
 */
public abstract class PartialObjectContainer implements TransientClass, Internal4, ObjectContainerSpec, InternalObjectContainer {

    // Collection of all classes
    // if (_classCollection == null) the engine is down.
    protected ClassMetadataRepository      _classCollection;
    
    protected ClassInfoHelper _classMetaHelper = new ClassInfoHelper();

    // the Configuration context for this ObjectContainer
    protected Config4Impl             _config;

    // Counts the number of toplevel calls into YapStream
    private int           _stackDepth;

    private final ReferenceSystemRegistry _referenceSystemRegistry = new ReferenceSystemRegistry();
    
    private Tree            _justPeeked;

    public final Object            _lock;

    // currently used to resolve self-linking concurrency problems
    // in cylic links, stores only YapClass objects
    private List4           _pendingClassUpdates;

    //  the parent ObjectContainer for TransportObjectContainer or this for all
    //  others. Allows identifying the responsible Objectcontainer for IDs
    final ObjectContainerBase         _parent;

    // a value greater than 0 indicates class implementing the
    // "Internal" interface are visible in queries and can
    // be used.
    int                     _showInternalClasses = 0;
    
    private List4           _stillToActivate;
    private List4           _stillToDeactivate;
    private List4           _stillToSet;

    // used for ClassMetadata and ClassMetadataRepository
    // may be parent or equal to i_trans
    private Transaction             _systemTransaction;

    // used for Objects
    protected Transaction             _transaction;

    // This is a hack for P2Collection
    // Remove when P2Collection is no longer used.
    private boolean         _instantiating;

    // all the per-YapStream references that we don't
    // want created in YapobjectCarrier
    public HandlerRegistry             _handlers;

    // One of three constants in ReplicationHandler: NONE, OLD, NEW
    // Detailed replication variables are stored in i_handlers.
    // Call state has to be maintained here, so YapObjectCarrier (who shares i_handlers) does
    // not accidentally think it operates in a replication call. 
    int                 _replicationCallState;  

    // weak reference management
    WeakReferenceCollector           _references;

	private NativeQueryHandler _nativeQueryHandler;
    
	private final ObjectContainerBase _this;

	private Callbacks _callbacks = new com.db4o.internal.callbacks.NullCallbacks();
    
    protected final PersistentTimeStampIdGenerator _timeStampIdGenerator = new PersistentTimeStampIdGenerator();
    
    private int _topLevelCallId = 1;
    
    private IntIdGenerator _topLevelCallIdGenerator = new IntIdGenerator();

	private boolean _topLevelCallCompleted;

	protected PartialObjectContainer(Configuration config, ObjectContainerBase parent) {
    	_this = cast(this);
    	_parent = parent == null ? _this : parent;
    	_lock = parent == null ? new Object() : parent._lock;
    	_config = (Config4Impl)config;
    }

	public final void open() throws OldFormatException {
		boolean ok = false;
		synchronized (_lock) {
			try {
	        	initializeTransactions();
	            initialize1(_config);
	        	openImpl();
				initializePostOpen();
				ok = true;
			} finally {
				if(!ok) {
					shutdownObjectContainer();
				}
			}
		}
	}

	protected abstract void openImpl() throws Db4oIOException;
    
	public ActivationDepth defaultActivationDepth(ClassMetadata classMetadata) {
		return activationDepthProvider().activationDepthFor(classMetadata, ActivationMode.ACTIVATE);
	}

    public ActivationDepthProvider activationDepthProvider() {
    	return configImpl().activationDepthProvider();
	}
    
    public final void activate(Transaction trans, Object obj){
        synchronized (_lock) {
            activate(checkTransaction(trans), obj, defaultActivationDepthForObject(obj));
        }
    }
    
    public final void deactivate(Transaction trans, Object obj){
    	synchronized (_lock) {
            deactivate(checkTransaction(trans), obj, 1);
        }
    }
    
    private final ActivationDepth defaultActivationDepthForObject(Object obj) {
        ClassMetadata classMetadata = classMetadataForObject(obj);
        return defaultActivationDepth(classMetadata);
    }

	public final void activate(Transaction trans, Object obj, ActivationDepth depth) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
        	beginTopLevelCall();
            try {
                stillToActivate(trans, obj, depth);
                activatePending(trans);
                completeTopLevelCall();
            } catch(Db4oException e){
            	completeTopLevelCall(e);
            } finally{
        		endTopLevelCall();
        	}
        }
    }
    
    static final class PendingActivation {
    	public final ObjectReference ref;
    	public final ActivationDepth depth;
    	
    	public PendingActivation(ObjectReference ref_, ActivationDepth depth_) {
    		this.ref = ref_;
    		this.depth = depth_;
    	}
    }

	final void activatePending(Transaction ta){
        while (_stillToActivate != null) {

            // TODO: Optimize!  A lightweight int array would be faster.

            final Iterator4 i = new Iterator4Impl(_stillToActivate);
            _stillToActivate = null;

            while (i.moveNext()) {
            	final PendingActivation item = (PendingActivation) i.current();
                final ObjectReference ref = item.ref;
				final Object obj = ref.getObject();
                if (obj == null) {
                    ta.removeReference(ref);
                } else {
                    ref.activateInternal(ta, obj, item.depth);
                }
            }
        }
    }
    
    public final void bind(Transaction trans, Object obj, long id) throws ArgumentNullException, IllegalArgumentException {
        synchronized (_lock) {
            if(obj == null){
                throw new ArgumentNullException();
            }
            if(DTrace.enabled){
                DTrace.BIND.log(id, " ihc " + System.identityHashCode(obj));
            }
            trans = checkTransaction(trans);
            int intID = (int) id;
            Object oldObject = getByID(trans, id);
            if (oldObject == null) {
                throw new IllegalArgumentException("id");
            }
            ObjectReference ref = trans.referenceForId(intID);
            if(ref == null){
                throw new IllegalArgumentException("obj");
            }
            if (reflectorForObject(obj) == ref.classMetadata().classReflector()) {
                ObjectReference newRef = bind2(trans, ref, obj);
                newRef.virtualAttributes(trans, false);
            } else {
                throw new Db4oException(Messages.get(57));
            }
        }
    }
    
    public final ObjectReference bind2(Transaction trans, ObjectReference oldRef, Object obj){
        int id = oldRef.getID();
        trans.removeReference(oldRef);
        ObjectReference newRef = new ObjectReference(classMetadataForObject(obj), id);
        newRef.setObjectWeak(_this, obj);
        newRef.setStateDirty();
        trans.referenceSystem().addExistingReference(newRef);
        return newRef;
    }

	public ClassMetadata classMetadataForObject(Object obj) {
		return classMetadataForReflectClass(reflectorForObject(obj));
	}
    
    public abstract byte blockSize();
     
    public final int bytesToBlocks(long bytes) {
    	int blockLen = blockSize();
    	return (int) ((bytes + blockLen -1 )/ blockLen);
    }
    
    public final int blockAlignedBytes(int bytes) {
    	return bytesToBlocks(bytes) * blockSize();
    }
    
    public final int blocksToBytes(int blocks){
    	return blocks * blockSize();
    }
    
    private final boolean breakDeleteForEnum(ObjectReference reference, boolean userCall){
        if(Deploy.csharp){
            return false;
        }
        if(userCall){
            return false;
        }
        if(reference == null){
            return false;
        }
        return Platform4.isEnum(reflector(), reference.classMetadata().classReflector());
    }

    boolean canUpdate() {
        return true;
    }

    public final void checkClosed() throws DatabaseClosedException {
        if (_classCollection == null) {
            throw new DatabaseClosedException();
        }
    }
    
	protected final void checkReadOnly() throws DatabaseReadOnlyException {
		if(_config.isReadOnly()) {
    		throw new DatabaseReadOnlyException();
    	}
	}

    final void processPendingClassUpdates() {
		if (_pendingClassUpdates == null) {
			return;
		}
		Iterator4 i = new Iterator4Impl(_pendingClassUpdates);
		while (i.moveNext()) {
			ClassMetadata yapClass = (ClassMetadata) i.current();
			yapClass.setStateDirty();
			yapClass.write(_systemTransaction);
		}
		_pendingClassUpdates = null;
	}
    
    public final Transaction checkTransaction() {
        return checkTransaction(null);
    }

    public final Transaction checkTransaction(Transaction ta) {
        checkClosed();
        if (ta != null) {
            return ta;
        }
        return transaction();
    }

    final public boolean close() {
		synchronized (_lock) {
			callbacks().closeOnStarted(cast(this));
			if(DTrace.enabled){
				DTrace.CLOSE_CALLED.logStack(this.toString());
			}
			close1();
			return true;
		}
	}
    
    protected void handleExceptionOnClose(Exception exc) {
		fatalException(exc);
    }

    private void close1() {
        // this is set to null in close2 and is therefore our check for down.
        if (_classCollection == null) {
            return;
        }
        processPendingClassUpdates();
        if (stateMessages()) {
            logMsg(2, toString());
        }
        close2();
    }
    
    protected abstract void close2();
    

	public final void shutdownObjectContainer() {
		if (DTrace.enabled) {
			DTrace.CLOSE.log();
		}
		logMsg(3, toString());
		synchronized (_lock) {
			stopSession();
			shutdownDataStorage();
		}
	}

	protected abstract void shutdownDataStorage();
	
	/**
	 * @deprecated
	 */
    public Db4oCollections collections(Transaction trans) {
        synchronized (_lock) {
            return Platform4.collections(checkTransaction(trans));
        }
    }
    
    public final void commit(Transaction trans) throws DatabaseReadOnlyException, DatabaseClosedException {
        synchronized (_lock) {
            if(DTrace.enabled){
                DTrace.COMMIT.log();
            }
            trans = checkTransaction(trans);
            checkReadOnly();
            beginTopLevelCall();
            try{            	
            	commit1(trans);
            	trans.commitReferenceSystem();
            	completeTopLevelCall();
            } catch(Db4oException e){
            	completeTopLevelCall(e);
            } finally{
            	endTopLevelCall();
            }
        }
    }
    
	public abstract void commit1(Transaction trans);

    public Configuration configure() {
        return configImpl();
    }
    
    public Config4Impl config(){
        return configImpl();
    }
    
    public abstract int converterVersion();

    public abstract AbstractQueryResult newQueryResult(Transaction trans, QueryEvaluationMode mode);

    protected void createStringIO(byte encoding) {
    	stringIO(LatinStringIO.forEncoding(encoding));
    }

    final protected void initializeTransactions() {
        _systemTransaction = newTransaction(null, createReferenceSystem());
        _transaction = newUserTransaction();
    }

	public abstract Transaction newTransaction(Transaction parentTransaction, TransactionalReferenceSystem referenceSystem);
	
	public Transaction newUserTransaction(){
	    return newTransaction(systemTransaction(), null);
	}
	
    public abstract long currentVersion();
    
    public boolean createClassMetadata(ClassMetadata classMeta, ReflectClass clazz, ClassMetadata superClassMeta) {
        return classMeta.init(_this, superClassMeta, clazz);
    }

    /**
     * allows special handling for all Db4oType objects.
     * Redirected here from #set() so only instanceof check is necessary
     * in the #set() method. 
     * @return object if handled here and #set() should not continue processing
     */
    public Db4oType db4oTypeStored(Transaction trans, Object obj) {
        if (!(obj instanceof Db4oDatabase)) {
        	return null;
        }
        Db4oDatabase database = (Db4oDatabase) obj;
        if (trans.referenceForObject(obj) != null) {
            return database;
        }
        showInternalClasses(true);
        try {
        	return database.query(trans);
        } finally {
        	showInternalClasses(false);
        }
    }
    
    public final void deactivate(Transaction trans, Object obj, int depth) throws DatabaseClosedException {
        synchronized (_lock) {
            trans = checkTransaction(trans);
        	beginTopLevelCall();
        	try{
        		deactivateInternal(trans, obj, activationDepthProvider().activationDepth(depth, ActivationMode.DEACTIVATE));
        		completeTopLevelCall();
        	} catch(Db4oException e){
        		completeTopLevelCall(e);
        	} finally{
        		endTopLevelCall();
        	}
        }
    }

    private final void deactivateInternal(Transaction trans, Object obj, ActivationDepth depth) {
        stillToDeactivate(trans, obj, depth, true);
        deactivatePending(trans);
    }

	private void deactivatePending(Transaction trans) {
		while (_stillToDeactivate != null) {
            Iterator4 i = new Iterator4Impl(_stillToDeactivate);
            _stillToDeactivate = null;
            while (i.moveNext()) {
                PendingActivation item = (PendingActivation) i.current();
				item.ref.deactivate(trans, item.depth);
            }
        }
	}

    public final void delete(Transaction trans, Object obj) throws DatabaseReadOnlyException, DatabaseClosedException {
        synchronized (_lock) {
        	trans = checkTransaction(trans);
        	checkReadOnly();
            delete1(trans, obj, true);
            trans.processDeletes();
        }
    }

    public final void delete1(Transaction trans, Object obj, boolean userCall) {
        if (obj == null) {
        	return;
        }
        ObjectReference ref = trans.referenceForObject(obj);
        if(ref == null){
        	return;
        }
        if(userCall){
        	generateCallIDOnTopLevel();
        }
        try {
        	beginTopLevelCall();
        	delete2(trans, ref, obj, 0, userCall);
        	completeTopLevelCall();
        } catch(Db4oException e) {
        	completeTopLevelCall(e);
        } finally {
        	endTopLevelCall();
        }
    }
    
    public final void delete2(Transaction trans, ObjectReference ref, Object obj, int cascade, boolean userCall) {
        
        // This check is performed twice, here and in delete3, intentionally.
        if(breakDeleteForEnum(ref, userCall)){
            return;
        }
        
        if(obj instanceof SecondClass){
        	if(! flagForDelete(ref)){
        		return;
        	}
            delete3(trans, ref, cascade, userCall);
            return;
        }
        
        trans.delete(ref, ref.getID(), cascade);
    }

    final void delete3(Transaction trans, ObjectReference ref, int cascade, boolean userCall) {
    	
        // The passed reference can be null, when calling from Transaction.
        if(ref == null  || ! ref.beginProcessing()){
        	return;
        }
                
        // This check is performed twice, here and in delete2, intentionally.
        if(breakDeleteForEnum(ref, userCall)){
        	ref.endProcessing();
            return;
        }
        
        if(! ref.isFlaggedForDelete()){
        	ref.endProcessing();
        	return;
        }
        
        ClassMetadata yc = ref.classMetadata();
        Object obj = ref.getObject();
        
        // We have to end processing temporarily here, otherwise the can delete callback
        // can't do anything at all with this object.
        
        ref.endProcessing();
        
        activateForDeletionCallback(trans, yc, ref, obj);
        
        if (!objectCanDelete(trans, yc, obj)) {
            return;
        }
        
        ref.beginProcessing();

        if(DTrace.enabled){
            DTrace.DELETE.log(ref.getID());
        }
        
        if(delete4(trans, ref, cascade, userCall)){
        	objectOnDelete(trans, yc, obj);
            if (configImpl().messageLevel() > Const4.STATE) {
                message("" + ref.getID() + " delete " + ref.classMetadata().getName());
            }
        }
        
        ref.endProcessing();
    }

	private void activateForDeletionCallback(Transaction trans, ClassMetadata classMetadata, ObjectReference ref, Object obj) {
		if (!ref.isActive() && (caresAboutDeleting(classMetadata) || caresAboutDeleted(classMetadata))) {
        	// Activate Objects for Callbacks, because in C/S mode Objects are not activated on the Server
			// FIXME: [TA] review activation depth
		    int depth = classMetadata.adjustCollectionDepthToBorders(1);
        	activate(trans, obj, new FixedActivationDepth(depth));
        }
	}
    
    private boolean caresAboutDeleting(ClassMetadata yc) {
    	return this._callbacks.caresAboutDeleting()
    		|| yc.hasEventRegistered(systemTransaction(), EventDispatcher.CAN_DELETE);
    }
    
    private boolean caresAboutDeleted(ClassMetadata yc) {
    	return this._callbacks.caresAboutDeleted()
    		|| yc.hasEventRegistered(systemTransaction(), EventDispatcher.DELETE);
    }
    
	private boolean objectCanDelete(Transaction transaction, ClassMetadata yc, Object obj) {
		return _this.callbacks().objectCanDelete(transaction, obj)
			&& yc.dispatchEvent(transaction, obj, EventDispatcher.CAN_DELETE);
	}
	
	private void objectOnDelete(Transaction transaction, ClassMetadata yc, Object obj) {
		_this.callbacks().objectOnDelete(transaction, obj);
		yc.dispatchEvent(transaction, obj, EventDispatcher.DELETE);
	}
	
    public abstract boolean delete4(Transaction ta, ObjectReference yapObject, int a_cascade, boolean userCall);
    
    Object descend(Transaction trans, Object obj, String[] path){
        synchronized (_lock) {
            trans = checkTransaction(trans);
            ObjectReference ref = trans.referenceForObject(obj);
            if(ref == null){
                return null;
            }
            
            final String fieldName = path[0];
            if(fieldName == null){
                return null;
            }
            ClassMetadata classMetadata = ref.classMetadata();
            final ByReference foundField = new ByReference();
            
            classMetadata.forEachField(new Procedure4() {
				public void apply(Object arg) {
                    FieldMetadata fieldMetadata = (FieldMetadata)arg;
                    if(fieldMetadata.canAddToQuery(fieldName)){
                    	foundField.value = fieldMetadata;
                    }
				}
			});
            FieldMetadata field = (FieldMetadata) foundField.value;
            if(field == null){
                return null;
            }
            
            Object child = ref.isActive()
            	? field.get(trans, obj)
                : descendMarshallingContext(trans, ref).readFieldValue(field);
            
            if(path.length == 1){
                return child;
            }
            if(child == null){
                return null;
            }
            String[] subPath = new String[path.length - 1];
            System.arraycopy(path, 1, subPath, 0, path.length - 1);
            return descend(trans, child, subPath);
        }
    }

	private UnmarshallingContext descendMarshallingContext(Transaction trans,
			ObjectReference ref) {
		final UnmarshallingContext context = new UnmarshallingContext(trans, ref, Const4.ADD_TO_ID_TREE, false);
		context.activationDepth(activationDepthProvider().activationDepth(1, ActivationMode.ACTIVATE));
		return context;
	}

    public boolean detectSchemaChanges() {
        // overriden in YapClient
        return configImpl().detectSchemaChanges();
    }
    
    public boolean dispatchsEvents() {
        return true;
    }

    protected boolean doFinalize() {
    	return true;
    }

    /*
	 * This method will be exuected on finalization, and vm exit if it's enabled
	 * by configuration.
	 */
    final void shutdownHook() {
		if(isClosed()) {
			return;
		}
		if (allOperationsCompleted()) {
			Messages.logErr(configImpl(), 50, toString(), null);
			close();
		} else {
			shutdownObjectContainer();
			if (operationIsProcessing()) {
				Messages.logErr(configImpl(), 24, null, null);
			}
		}
	}

	private boolean operationIsProcessing() {
		return _stackDepth > 0;
	}

	private boolean allOperationsCompleted() {
		return _stackDepth == 0;
	}

    void fatalException(int msgID) {
		fatalException(null,msgID);
    }

	final void fatalException(Throwable t) {
		fatalException(t,Messages.FATAL_MSG_ID);
    }

    final void fatalException(Throwable t, int msgID) {
    	if(DTrace.enabled){
    		DTrace.FATAL_EXCEPTION.log(t.toString());
    	}
		Messages.logErr(configImpl(), (msgID == Messages.FATAL_MSG_ID ? 18
				: msgID), null, t);
		if (!isClosed()) {
			shutdownObjectContainer();
		}
		throw new Db4oException(Messages.get(msgID));
	}

    /**
     * @sharpen.ignore
     */
    protected void finalize() {
		if (doFinalize() && configuredForAutomaticShutDown()) {
			shutdownHook();
		}
	}

	private boolean configuredForAutomaticShutDown() {
		return (configImpl() == null || configImpl().automaticShutDown());
	}

    void gc() {
        _references.pollReferenceQueue();
    }
    
    public final ObjectSet queryByExample(Transaction trans, Object template) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            QueryResult res = null;
    		try {
    			beginTopLevelCall();
    			res = queryByExampleInternal(trans, template);
    			completeTopLevelCall();
    		} catch (Db4oException e) {
    			completeTopLevelCall(e);
    		} finally {
    			endTopLevelCall();
    		}
            return new ObjectSetFacade(res);
        }
    }

    private final QueryResult queryByExampleInternal(Transaction trans, Object template) {
        if (template == null || template.getClass() == Const4.CLASS_OBJECT) {
            return queryAllObjects(trans);
        } 
        Query q = query(trans);
        q.constrain(template);
        return executeQuery((QQuery)q);
    }
    
    public abstract AbstractQueryResult queryAllObjects(Transaction ta);

    public final Object getByID(Transaction ta, long id) throws DatabaseClosedException, InvalidIDException {
        synchronized (_lock) {
            if (id <= 0 || id >= Integer.MAX_VALUE) {
                throw new IllegalArgumentException();
            }
            checkClosed();
            ta = checkTransaction(ta);
            beginTopLevelCall();
            try {
                Object obj = getByID2(ta, (int) id);
                completeTopLevelCall();
                return obj;
            } catch(Db4oException e) {
            	completeTopLevelCall(new InvalidIDException(e));
            } catch(ArrayIndexOutOfBoundsException aiobe){
            	completeTopLevelCall(new InvalidIDException(aiobe));
            } finally {
            	
            	// Never shut down for getById()
            	// There may be OutOfMemoryErrors or similar
            	// The user may want to catch and continue working.
            	_topLevelCallCompleted = true;

            	endTopLevelCall();
            }
            // only to make the compiler happy
            return null;
        }
    }
    
    public Object getByID2(Transaction ta, int id) {
		Object obj = ta.objectForIdFromCache(id);
		if (obj != null) {
			// Take care about handling the returned candidate reference.
			// If you loose the reference, weak reference management might
			// also.
			return obj;

		}
		return new ObjectReference(id).read(ta, new LegacyActivationDepth(0), Const4.ADD_TO_ID_TREE, true);
	}
    
    public final Object getActivatedObjectFromCache(Transaction ta, int id){
        Object obj = ta.objectForIdFromCache(id);
        if(obj == null){
            return null;
        }
        activate(ta, obj);
        return obj;
    }
    
    public final Object readActivatedObjectNotInCache(Transaction ta, int id){
        Object obj = null;
    	beginTopLevelCall();
        try {
            obj = new ObjectReference(id).read(ta, UnknownActivationDepth.INSTANCE, Const4.ADD_TO_ID_TREE, true);
            completeTopLevelCall();
        } catch(Db4oException e) {
        	completeTopLevelCall(e);
        } finally{
        	endTopLevelCall();
        }
        activatePending(ta);
        return obj;
    }
    
    public final Object getByUUID(Transaction trans, Db4oUUID uuid){
        synchronized (_lock) {
            if(uuid == null){
                return null;
            }
            trans = checkTransaction(trans);
            HardObjectReference hardRef = trans.getHardReferenceBySignature(
            					uuid.getLongPart(),
            					uuid.getSignaturePart());
            return hardRef._object; 
        }
    }

    public final int getID(Transaction trans, Object obj) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            checkClosed();
    
            if(obj == null){
                return 0;
            }
    
            ObjectReference yo = trans.referenceForObject(obj);
            if (yo != null) {
                return yo.getID();
            }
            return 0;
        }
    }
    
    public final ObjectInfo getObjectInfo (Transaction trans, Object obj){
        synchronized(_lock){
            trans = checkTransaction(trans);
            return trans.referenceForObject(obj);
        }
    }
    
    public final HardObjectReference getHardObjectReferenceById(Transaction trans, int id) {
        if (id <= 0) {
        	return HardObjectReference.INVALID;
        }
        	
        ObjectReference ref = trans.referenceForId(id);
        if (ref != null) {

            // Take care about handling the returned candidate reference.
            // If you loose the reference, weak reference management might also.

            Object candidate = ref.getObject();
            if (candidate != null) {
            	return new HardObjectReference(ref, candidate);
            }
            trans.removeReference(ref);
        }
        ref = new ObjectReference(id);
        Object readObject = ref.read(trans, new LegacyActivationDepth(0), Const4.ADD_TO_ID_TREE, true);
        
        if(readObject == null){
            return HardObjectReference.INVALID;
        }
        
        // check class creation side effect and simply retry recursively
        // if it hits:
        if(readObject != ref.getObject()){
            return getHardObjectReferenceById(trans, id);
        }
        
        return new HardObjectReference(ref, readObject);
    }

    public final StatefulBuffer getWriter(Transaction a_trans, int a_address, int a_length) {
        if (Debug.exceedsMaximumBlockSize(a_length)) {
            return null;
        }
        return new StatefulBuffer(a_trans, a_address, a_length);
    }

    public final Transaction systemTransaction() {
        return _systemTransaction;
    }

    public final Transaction transaction() {
        return _transaction;
    }
    
    public final ClassMetadata classMetadataForReflectClass(ReflectClass claxx){
    	if(hideClassForExternalUse(claxx)){
    		return null;
    	}
        ClassMetadata classMetadata = _handlers.classMetadataForClass(claxx);
        if (classMetadata != null) {
            return classMetadata;
        }
        return _classCollection.classMetadataForReflectClass(claxx);
    }
    
    public final TypeHandler4 typeHandlerForObject(Object obj){
        return typeHandlerForReflectClass(reflectorForObject(obj));
    }
    
    public final TypeHandler4 typeHandlerForReflectClass(ReflectClass claxx){
        if(hideClassForExternalUse(claxx)){
            return null;
        }
        if (Platform4.isTransient(claxx)) {
        	return null;
        }
        TypeHandler4 typeHandler = _handlers.typeHandlerForClass(claxx);
        if (typeHandler != null) {
            return typeHandler;
        }
        ClassMetadata classMetadata = _classCollection.produceClassMetadata(claxx);
        if(classMetadata == null){
            return null;
        }
        // TODO: consider to return classMetadata
        return classMetadata.typeHandler();
    }
    
    // TODO: Some ReflectClass implementations could hold a 
    // reference to ClassMetadata to improve lookup performance here.
    public ClassMetadata produceClassMetadata(ReflectClass claxx) {
    	if(hideClassForExternalUse(claxx)){
    		return null;
    	}
        ClassMetadata classMetadata = _handlers.classMetadataForClass(claxx);
        if (classMetadata != null) {
            return classMetadata;
        }
        return _classCollection.produceClassMetadata(claxx);
    }
    
    /**
     * Differentiating getActiveClassMetadata from getYapClass is a tuning 
     * optimization: If we initialize a YapClass, #set3() has to check for
     * the possibility that class initialization associates the currently
     * stored object with a previously stored static object, causing the
     * object to be known afterwards.
     * 
     * In this call we only return active YapClasses, initialization
     * is not done on purpose
     */
    final ClassMetadata getActiveClassMetadata(ReflectClass claxx) {
    	if(hideClassForExternalUse(claxx)){
    		return null;
    	}
        return _classCollection.getActiveClassMetadata(claxx);
    }
    
    private final boolean hideClassForExternalUse(ReflectClass claxx){
        if (claxx == null) {
            return true;
        }
        if ((!showInternalClasses()) && _handlers.ICLASS_INTERNAL.isAssignableFrom(claxx)) {
            return true;
        }
        return false;
    }
    
    public int classMetadataIdForName(String name) {
        return _classCollection.classMetadataIdForName(name);
    }

    public ClassMetadata classMetadataForName(String name) {
    	return classMetadataForId(classMetadataIdForName(name));
    }
    
    public ClassMetadata classMetadataForId(int id) {
    	if(DTrace.enabled){
    		DTrace.CLASSMETADATA_BY_ID.log(id);
    	}
        if (id == 0) {
            return null;
        }
        ClassMetadata classMetadata = _handlers.classMetadataForId(id);
        if (classMetadata != null) {
            return classMetadata;
        }
        return _classCollection.getClassMetadata(id);
    }
    
    public HandlerRegistry handlers(){
    	return _handlers;
    }

    public boolean needsLockFileThread() {
		if(! Debug.lockFile){
			return false;
		}
        if (!Platform4.hasLockFileThread()) {
            return false;
        }
        if (Platform4.hasNio()) {
            return false;
        }
        if (configImpl().isReadOnly()) {
            return false;
        }
        return configImpl().lockFile();
    }

    protected boolean hasShutDownHook() {
        return configImpl().automaticShutDown();
    }

    protected void initialize1(Configuration config) {
        _config = initializeConfig(config);
        _handlers = new HandlerRegistry(_this, configImpl().encoding(), configImpl().reflector());
        
        if (_references != null) {
            gc();
            _references.stopTimer();
        }

        _references = new WeakReferenceCollector(_this);

        if (hasShutDownHook()) {
            Platform4.addShutDownHook(this);
        }
        _handlers.initEncryption(configImpl());
        initialize2();
        _stillToSet = null;
    }

	private Config4Impl initializeConfig(Configuration config) {
		Config4Impl impl=((Config4Impl)config);
		impl.stream(_this);
		impl.reflector().setTransaction(systemTransaction());
		impl.reflector().configuration(new ReflectorConfigurationImpl(impl));
		return impl;
	}

    /**
     * before file is open
     */
    void initialize2() {
        initialize2NObjectCarrier();
    }

    public final TransactionalReferenceSystem createReferenceSystem() {
        TransactionalReferenceSystem referenceSystem = new TransactionalReferenceSystem();
        _referenceSystemRegistry.addReferenceSystem(referenceSystem);
        return referenceSystem;
    }

    /**
     * overridden in YapObjectCarrier
     */
    void initialize2NObjectCarrier() {
        _classCollection = new ClassMetadataRepository(_systemTransaction);
        _references.startTimer();
    }

    private void initializePostOpen() {
        _showInternalClasses = 100000;
        initializePostOpenExcludingTransportObjectContainer();
        _showInternalClasses = 0;
    }
    
    protected void initializePostOpenExcludingTransportObjectContainer() {
        initializeEssentialClasses();
		rename(configImpl());
		_classCollection.initOnUp(_systemTransaction);
        if (configImpl().detectSchemaChanges()) {
            _systemTransaction.commit();
        }
        configImpl().applyConfigurationItems(cast(_this));
    }

    void initializeEssentialClasses(){
        if(Debug.staticIdentity){
            return;
        }
        for (int i = 0; i < Const4.ESSENTIAL_CLASSES.length; i++) {
            produceClassMetadata(reflector().forClass(Const4.ESSENTIAL_CLASSES[i]));    
        }
    }

    final void instantiating(boolean flag) {
        _instantiating = flag;
    }

    final boolean isActive(Transaction trans, Object obj) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            if (obj != null) {
                ObjectReference ref = trans.referenceForObject(obj);
                if (ref != null) {
                    return ref.isActive();
                }
            }
            return false;
        }
    }
    
    public boolean isCached(Transaction trans, long id) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            return trans.objectForIdFromCache((int)id) != null;
        }
    }

    /**
     * overridden in YapClient
     * This method will make it easier to refactor than
     * an "instanceof YapClient" check.
     */
    public boolean isClient() {
        return false;
    }

    public final boolean isClosed() {
        synchronized (_lock) {
            return _classCollection == null;
        }
    }

    public final boolean isInstantiating() {
        return _instantiating;
    }

    boolean isServer() {
        return false;
    }

    public final boolean isStored(Transaction trans, Object obj) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            if (obj == null) {
                return false;
            }
            ObjectReference ref = trans.referenceForObject(obj);
            if (ref == null) {
                return false;
            }
            return !trans.isDeleted(ref.getID());
        }
    }
    
    public ReflectClass[] knownClasses(){
        synchronized(_lock){
            checkClosed();
            return reflector().knownClasses();
        }
    }
    
    public FieldHandler fieldHandlerForId(int id) {
        if (id < 1) {
            return null;
        }
        if (_handlers.isSystemHandler(id)) {
            return _handlers.fieldHandlerForId(id);
        } 
        return classMetadataForId(id);
    }
    
    public int fieldHandlerIdForFieldHandler(FieldHandler fieldHandler) {
        if(fieldHandler instanceof ClassMetadata){
            return ((ClassMetadata)fieldHandler).getID();
        }
        return _handlers.fieldHandlerIdForFieldHandler(fieldHandler);
    }
    
    public FieldHandler fieldHandlerForClass(ReflectClass claxx) {
        if(hideClassForExternalUse(claxx)){
            return null;
        }
        FieldHandler fieldHandler = _handlers.fieldHandlerForClass(claxx);
        if(fieldHandler != null){
            return fieldHandler;
        }
        return _classCollection.produceClassMetadata(claxx);
    }
    
    public TypeHandler4 typeHandlerForId(int id) {
        if (id < 1) {
            return null;
        }
        if (_handlers.isSystemHandler(id)) {
            return _handlers.typeHandlerForID(id);
        } 
        ClassMetadata classMetadata = classMetadataForId(id);
        if(classMetadata == null){
            return null;
        }
        // TODO: consider to return classMetadata
        return classMetadata.typeHandler();
    }

    public Object lock() {
        return _lock;
    }

    public final void logMsg(int code, String msg) {
        Messages.logMsg(configImpl(), code, msg);
    }

    public boolean maintainsIndices() {
        return true;
    }

    void message(String msg) {
        new Message(_this, msg);
    }

    public void migrateFrom(ObjectContainer objectContainer) {
        if(objectContainer == null){
            if(_replicationCallState == Const4.NONE){
                return;
            }
            _replicationCallState = Const4.NONE;
            if(_handlers.i_migration != null){
                _handlers.i_migration.terminate();
            }
            _handlers.i_migration = null;
        }else{
            ObjectContainerBase peer = (ObjectContainerBase)objectContainer;
            _replicationCallState = Const4.OLD;
            peer._replicationCallState = Const4.OLD;
            _handlers.i_migration = new MigrationConnection(_this, (ObjectContainerBase)objectContainer);
            peer._handlers.i_migration = _handlers.i_migration;
        }
    }

    public final void needsUpdate(ClassMetadata a_yapClass) {
        _pendingClassUpdates = new List4(_pendingClassUpdates, a_yapClass);
    }
    
    public long generateTimeStampId() {
        return _timeStampIdGenerator.next();
    }

    public abstract int newUserObject();
    
    public final Object peekPersisted(Transaction trans, Object obj, ActivationDepth depth, boolean committed) throws DatabaseClosedException {
    	
    	// TODO: peekPersisted is not stack overflow safe, if depth is too high. 
    	
        synchronized (_lock) {
        	checkClosed();
            beginTopLevelCall();
            try{
                trans = checkTransaction(trans);
                ObjectReference ref = trans.referenceForObject(obj);
                trans = committed ? _systemTransaction : trans;
                Object cloned = null;
                if (ref != null) {
                    cloned = peekPersisted(trans, ref.getID(), depth, true);
                }
                completeTopLevelCall();
                return cloned;
            } catch(Db4oException e) {
            	completeTopLevelCall(e);
            	return null;
            } finally{
                endTopLevelCall();
            }
        }
    }

    public final Object peekPersisted(Transaction trans, int id, ActivationDepth depth, boolean resetJustPeeked) {
        if(resetJustPeeked){
            _justPeeked = null;
        }else{
            TreeInt ti = new TreeInt(id);
            TreeIntObject tio = (TreeIntObject) Tree.find(_justPeeked, ti);
            if(tio != null){
                return tio._object;
            }
        }
        Object res = new ObjectReference(id).peekPersisted(trans, depth);
        if(resetJustPeeked){
            _justPeeked = null;
        }
        return res; 
    }

    void peeked(int id, Object obj) {
        _justPeeked = Tree
            .add(_justPeeked, new TreeIntObject(id, obj));
    }

    public void purge() {
        synchronized (_lock) {
            checkClosed();
            System.gc();
            System.runFinalization();
            System.gc();
            gc();
            _classCollection.purge();
        }
    }
    
    public final void purge(Transaction trans, Object obj) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            trans.removeObjectFromReferenceSystem(obj);
        }
    }

    final void removeFromAllReferenceSystems(Object obj) {
        if (obj == null) {
        	return;
        }
        if (obj instanceof ObjectReference) {
            _referenceSystemRegistry.removeReference((ObjectReference) obj);
            return;
        }
        _referenceSystemRegistry.removeObject(obj);
    }
    
    public final NativeQueryHandler getNativeQueryHandler() {
        synchronized(_lock){
        	if (null == _nativeQueryHandler) {
        		_nativeQueryHandler = new NativeQueryHandler(cast(_this));
        	}
        	return _nativeQueryHandler;
        }
    }
    
    public final ObjectSet query(Transaction trans, Predicate predicate){
        return query(trans, predicate,(QueryComparator)null);
    }
    
    public final ObjectSet query(Transaction trans, Predicate predicate,QueryComparator comparator){
        synchronized (_lock) {
            trans = checkTransaction(trans);
            return getNativeQueryHandler().execute(query(trans), predicate,comparator);
        }
    }

    public final ObjectSet query(Transaction trans, Class clazz) {
        return queryByExample(trans, clazz);
    }

    public final Query query(Transaction ta) {
        return new QQuery(checkTransaction(ta), null, null);
    }

    public abstract void raiseVersion(long a_minimumVersion);

    public abstract void readBytes(byte[] a_bytes, int a_address, int a_length) throws Db4oIOException;

    public abstract void readBytes(byte[] bytes, int address, int addressOffset, int length) throws Db4oIOException;

    public final ByteArrayBuffer bufferByAddress(int address, int length)
			throws Db4oIOException {
		checkAddress(address);

		ByteArrayBuffer reader = new ByteArrayBuffer(length);
		readBytes(reader._buffer, address, length);
		_handlers.decrypt(reader);
		return reader;
	}

	private void checkAddress(int address) throws IllegalArgumentException {
		if (address <= 0) {
			throw new IllegalArgumentException("Invalid address offset: "
					+ address);
		}
	}

    public final StatefulBuffer readWriterByAddress(Transaction a_trans,
        int address, int length) throws Db4oIOException {
    	checkAddress(address);
        StatefulBuffer reader = getWriter(a_trans, address, length);
        reader.readEncrypt(_this, address);
        return reader;
    }

    public abstract StatefulBuffer readWriterByID(Transaction a_ta, int a_id);
    
    public abstract StatefulBuffer readWriterByID(Transaction a_ta, int a_id, boolean lastCommitted);

    public abstract ByteArrayBuffer readReaderByID(Transaction a_ta, int a_id);
    
    public abstract ByteArrayBuffer readReaderByID(Transaction a_ta, int a_id, boolean lastCommitted);
    
    public abstract StatefulBuffer[] readWritersByIDs(Transaction a_ta, int[] ids);

    private void reboot() {
        commit(null);
        close();
        open();
    }
    
    public GenericReflector reflector(){
        return _handlers._reflector;
    }
    
    public final void refresh(Transaction trans, Object obj, int depth) {
        synchronized (_lock) {
        	activate(trans, obj, refreshActivationDepth(depth));
        }
    }

	private ActivationDepth refreshActivationDepth(int depth) {
		return activationDepthProvider().activationDepth(depth, ActivationMode.REFRESH);
	}

    final void refreshClasses() {
        synchronized (_lock) {
            _classCollection.refreshClasses();
        }
    }

    public abstract void releaseSemaphore(String name);
    
    public void flagAsHandled(ObjectReference ref){
    	ref.flagAsHandled(_topLevelCallId);
    }
    
    boolean flagForDelete(ObjectReference ref){
    	if(ref == null){
    		return false;
    	}
    	if(handledInCurrentTopLevelCall(ref)){
    		return false;
    	}
    	ref.flagForDelete(_topLevelCallId);
    	return true;
    }
    
    public abstract void releaseSemaphores(Transaction ta);

    void rename(Config4Impl config) {
        boolean renamedOne = false;
        if (config.rename() != null) {
            renamedOne = rename1(config);
        }
        _classCollection.checkChanges();
        if (renamedOne) {
        	reboot();
        }
    }

    protected boolean rename1(Config4Impl config) {
		boolean renamedOne = false;
		Iterator4 i = config.rename().iterator();
		while (i.moveNext()) {
			Rename ren = (Rename) i.current();
			if (queryByExample(systemTransaction(), ren).size() == 0) {
				boolean renamed = false;
				boolean isField = ren.rClass.length() > 0;
				ClassMetadata yapClass = _classCollection
						.getClassMetadata(isField ? ren.rClass : ren.rFrom);
				if (yapClass != null) {
					if (isField) {
						renamed = yapClass.renameField(ren.rFrom, ren.rTo);
					} else {
						ClassMetadata existing = _classCollection
								.getClassMetadata(ren.rTo);
						if (existing == null) {
							yapClass.setName(ren.rTo);
							renamed = true;
						} else {
							logMsg(9, "class " + ren.rTo);
						}
					}
				}
				if (renamed) {
					renamedOne = true;
					setDirtyInSystemTransaction(yapClass);

					logMsg(8, ren.rFrom + " to " + ren.rTo);

					// delete all that rename from the new name
					// to allow future backswitching
					ObjectSet backren = queryByExample(systemTransaction(), new Rename(ren.rClass, null,
							ren.rFrom));
					while (backren.hasNext()) {
						delete(systemTransaction(), backren.next());
					}

					// store the rename, so we only do it once
					store(systemTransaction(), ren);
				}
			}
		}

		return renamedOne;
	}
    
    public final boolean handledInCurrentTopLevelCall(ObjectReference ref){
    	return ref.isFlaggedAsHandled(_topLevelCallId);
    }

    public abstract void reserve(int byteCount);
    
    public final void rollback(Transaction trans) {
        synchronized (_lock) {
        	trans = checkTransaction(trans);
        	checkReadOnly();
        	rollback1(trans);
        	trans.rollbackReferenceSystem();
        }
    }

    public abstract void rollback1(Transaction trans);

    /** @param obj */
    public void send(Object obj) {
        // TODO: implement
        throw new NotSupportedException();
    }

    public final void store(Transaction trans, Object obj)
			throws DatabaseClosedException, DatabaseReadOnlyException {
    	store(trans, obj, Const4.UNSPECIFIED);
    }    
    
	public final void store(Transaction trans, Object obj, int depth)
			throws DatabaseClosedException, DatabaseReadOnlyException {
		synchronized (_lock) {
            storeInternal(trans, obj, depth, true);
        }
	}
    
    public final int storeInternal(Transaction trans, Object obj,
			boolean checkJustSet) throws DatabaseClosedException,
			DatabaseReadOnlyException {
       return storeInternal(trans, obj, Const4.UNSPECIFIED, checkJustSet);
    }
    
    public int storeInternal(Transaction trans, Object obj, int depth,
			boolean checkJustSet) throws DatabaseClosedException,
			DatabaseReadOnlyException {
    	trans = checkTransaction(trans);
    	checkReadOnly();
    	beginTopLevelSet();
    	try{
	        int id = storeAfterReplication(trans, obj, depth, checkJustSet);
	        completeTopLevelSet();
			return id;
    	} catch(Db4oException e) {
    		completeTopLevelCall();
			throw e;
    	} finally{
    		endTopLevelSet(trans);
    	}
    }
    
    public final int storeAfterReplication(Transaction trans, Object obj, int depth,  boolean checkJust) {
        
        if (obj instanceof Db4oType) {
            Db4oType db4oType = db4oTypeStored(trans, obj);
            if (db4oType != null) {
                return getID(trans, db4oType);
            }
        }
        
        return store2(trans, obj, depth, checkJust);
    }
    
    public final void storeByNewReplication(Db4oReplicationReferenceProvider referenceProvider, Object obj){
        synchronized(_lock){
            _replicationCallState = Const4.NEW;
            _handlers._replicationReferenceProvider = referenceProvider;
            
            try {
            	store2(checkTransaction(), obj, 1, false);
            } finally {
            	_replicationCallState = Const4.NONE;
            	_handlers._replicationReferenceProvider = null;
	        }
        }
    }
    
    private final int store2(Transaction trans, Object obj, int depth, boolean checkJust) {
        int id = store3(trans, obj, depth, checkJust);
        if(stackIsSmall()){
            checkStillToSet();
        }
        return id;
    }
    
    public void checkStillToSet() {
        List4 postponedStillToSet = null;
        while (_stillToSet != null) {
            Iterator4 i = new Iterator4Impl(_stillToSet);
            _stillToSet = null;
            while (i.moveNext()) {
                PendingSet item = (PendingSet)i.current();
                
                ObjectReference ref = item.ref;
                Transaction trans = item.transaction;
                
                if(! ref.continueSet(trans, item.depth)) {
                    postponedStillToSet = new List4(postponedStillToSet, item);
                }
            }
        }
        _stillToSet = postponedStillToSet;
    }
    
    void notStorable(ReflectClass claxx, Object obj){
        if(! configImpl().exceptionsOnNotStorable()){
            return;
        }
        
        if(claxx != null){
            throw new ObjectNotStorableException(claxx);
        }
        
        throw new ObjectNotStorableException(obj.toString());
    }
    

    public final int store3(Transaction trans, Object obj, int updateDepth, boolean checkJustSet) {
        if (obj == null || (obj instanceof TransientClass)) {
            return 0;
        }
        	
        if (obj instanceof Db4oTypeImpl) {
            ((Db4oTypeImpl) obj).storedTo(trans);
        }
        
        ObjectAnalyzer analyzer = new ObjectAnalyzer(this, obj);
        analyzer.analyze(trans);
        if(analyzer.notStorable()){
            return 0;
        }
        
        ObjectReference ref = analyzer.objectReference();
        
		if (ref == null) {
            ClassMetadata classMetadata = analyzer.classMetadata();
            if(classMetadata.isSecondClass()){
            	analyzer.notStorable();
            	return 0;
            }
            if (!objectCanNew(trans, classMetadata, obj)) {
                return 0;
            }
            ref = new ObjectReference();
            ref.store(trans, classMetadata, obj);
            trans.addNewReference(ref);
			if(obj instanceof Db4oTypeImpl){
			    ((Db4oTypeImpl)obj).setTrans(trans);
			}
			if (configImpl().messageLevel() > Const4.STATE) {
				message("" + ref.getID() + " new " + ref.classMetadata().getName());
			}
			
			flagAsHandled(ref);
			stillToSet(trans, ref, updateDepth);

        } else {
            if (canUpdate()) {
                if(checkJustSet){
                    if( (! ref.isNew())  && handledInCurrentTopLevelCall(ref)){
                        return ref.getID();
                    }
                }
                if (updateDepthSufficient(updateDepth)) {
                    flagAsHandled(ref);
                    ref.writeUpdate(trans, updateDepth);
                }
            }
        }
        processPendingClassUpdates();
        return ref.getID();
    }

    private final boolean updateDepthSufficient(int updateDepth){
    	return (updateDepth == Const4.UNSPECIFIED) || (updateDepth > 0);
    }

	private boolean objectCanNew(Transaction transaction, ClassMetadata yc, Object obj) {
		return callbacks().objectCanNew(transaction, obj)
			&& yc.dispatchEvent(transaction, obj, EventDispatcher.CAN_NEW);
	}

    public abstract void setDirtyInSystemTransaction(PersistentBase a_object);

    public abstract boolean setSemaphore(String name, int timeout);

    void stringIO(LatinStringIO io) {
        _handlers.stringIO(io);
    }

    final boolean showInternalClasses() {
        return isServer() || _showInternalClasses > 0;
    }

    /**
     * Objects implementing the "Internal4" marker interface are
     * not visible to queries, unless this flag is set to true.
     * The caller should reset the flag after the call.
     */
    public synchronized void showInternalClasses(boolean show) {
        if (show) {
            _showInternalClasses++;
        } else {
            _showInternalClasses--;
        }
        if (_showInternalClasses < 0) {
            _showInternalClasses = 0;
        }
    }
    
    private final boolean stackIsSmall(){
        return _stackDepth < Const4.MAX_STACK_DEPTH;
    }

    boolean stateMessages() {
        return true; // overridden to do nothing in YapObjectCarrier
    }

    final List4 stillTo1(Transaction trans, List4 still, Object obj, ActivationDepth depth, boolean forceUnknownDeactivate) {
    	
        if (obj == null || !depth.requiresActivation()) {
        	return still;
        }
        
        ObjectReference ref = trans.referenceForObject(obj);
        if (ref != null) {
        	if(handledInCurrentTopLevelCall(ref)){
        		return still;
        	}
        	flagAsHandled(ref);
            return new List4(still, new PendingActivation(ref, depth));
        } 
        final ReflectClass clazz = reflectorForObject(obj);
		if (clazz.isArray()) {
			if (!clazz.getComponentType().isPrimitive()) {
                Iterator4 arr = ArrayHandler.iterator(clazz, obj);
                while (arr.moveNext()) {
                	final Object current = arr.current();
                    if(current == null){
                        continue;
                    }
                    ClassMetadata classMetadata = classMetadataForObject(current);
                    still = stillTo1(trans, still, current, depth.descend(classMetadata), forceUnknownDeactivate);
                }
			}
        } else {
            if (obj instanceof Entry) {
                still = stillTo1(trans, still, ((Entry) obj).key, depth, false);
                still = stillTo1(trans, still, ((Entry) obj).value, depth, false);
            } else {
                if (forceUnknownDeactivate) {
                    // Special handling to deactivate Top-Level unknown objects only.
                    ClassMetadata yc = classMetadataForObject(obj);
                    if (yc != null) {
                        yc.deactivate(trans, obj, depth);
                    }
                }
            }
        }
        return still;
    }
    
    public final void stillToActivate(Transaction trans, Object obj, ActivationDepth depth) {

        // TODO: We don't want the simple classes to search the hc_tree
        // Kick them out here.

        //		if (a_object != null) {
        //			Class clazz = a_object.getClass();
        //			if(! clazz.isPrimitive()){
        
        if(processedByImmediateActivation(trans, obj, depth)){
            return;
        }

        _stillToActivate = stillTo1(trans, _stillToActivate, obj, depth, false);
    }

    private boolean processedByImmediateActivation(Transaction trans, Object obj, ActivationDepth depth) {
        if(! stackIsSmall()){
            return false;
        }
        if (obj == null || !depth.requiresActivation()) {
            return true;
        }
        ObjectReference ref = trans.referenceForObject(obj);
        if(ref == null){
            return false;
        }
        if(handledInCurrentTopLevelCall(ref)){
            return true;
        }
        flagAsHandled(ref);
        _stackDepth++;
        try{
            ref.activateInternal(trans, obj, depth);
        } finally {
            _stackDepth--;
        }
        return true;
    }

    public final void stillToDeactivate(Transaction trans, Object a_object, ActivationDepth a_depth,
        boolean a_forceUnknownDeactivate) {
        _stillToDeactivate = stillTo1(trans, _stillToDeactivate, a_object, a_depth, a_forceUnknownDeactivate);
    }
    
    static class PendingSet {
    	public final Transaction transaction;
    	public final ObjectReference ref;
    	public final int depth;
    	
    	public PendingSet(Transaction transaction_, ObjectReference ref_, int depth_) {
    		this.transaction = transaction_;
    		this.ref = ref_;
    		this.depth = depth_;
		}
    }

    void stillToSet(Transaction transaction, ObjectReference ref, int updateDepth) {
        if(stackIsSmall()){
            if(ref.continueSet(transaction, updateDepth)){
                return;
            }
        }
        _stillToSet = new List4(_stillToSet, new PendingSet(transaction, ref, updateDepth));
    }

    protected final void stopSession() {
        if (hasShutDownHook()) {
            Platform4.removeShutDownHook(this);
        }
        _classCollection = null;
        if(_references != null){
        	_references.stopTimer();
        }
        _systemTransaction = null;
        _transaction = null;
    }
    
    public final StoredClass storedClass(Transaction trans, Object clazz) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            ReflectClass claxx = ReflectorUtils.reflectClassFor(reflector(), clazz);
            if (claxx == null) {
            	return null;
            }
            ClassMetadata classMetadata = classMetadataForReflectClass(claxx);
            if(classMetadata == null){
                return null;
            }
            return new StoredClassImpl(trans, classMetadata);
        }
    }
    
    public StoredClass[] storedClasses(Transaction trans) {
        synchronized (_lock) {
            trans = checkTransaction(trans);
            StoredClass[] classMetadata = _classCollection.storedClasses();
            StoredClass[] storedClasses = new StoredClass[classMetadata.length];
            for (int i = 0; i < classMetadata.length; i++) {
                storedClasses[i] = new StoredClassImpl(trans, (ClassMetadata)classMetadata[i]);
            }
            return storedClasses;
        }
    }
		
    public LatinStringIO stringIO(){
    	return _handlers.stringIO();
    }
    
    public abstract SystemInfo systemInfo();
    
    private final void beginTopLevelCall(){
    	if(DTrace.enabled){
    		DTrace.BEGIN_TOP_LEVEL_CALL.log();
    	}
    	generateCallIDOnTopLevel();
    	if(_stackDepth == 0){
    		_topLevelCallCompleted = false;
    	}
    	_stackDepth++;
    }
    
    public final void beginTopLevelSet(){
    	beginTopLevelCall();
    }
    
    /*
	 * This method has to be invoked in the end of top level call to indicate
	 * it's ended as expected
	 */
    public final void completeTopLevelCall() {
    	if(_stackDepth == 1){
    		_topLevelCallCompleted = true;
    	}
    }

    private void completeTopLevelCall(Db4oException e) throws Db4oException {
    	completeTopLevelCall();
		throw e;
	}
    
    /*
	 * This method has to be invoked in the end of top level of set call to
	 * indicate it's ended as expected
	 */
    public final void completeTopLevelSet() {
    	completeTopLevelCall();
    }
    
    private final void endTopLevelCall(){
    	if(DTrace.enabled){
    		DTrace.END_TOP_LEVEL_CALL.log();
    	}
    	_stackDepth--;
    	generateCallIDOnTopLevel();
    	if(_stackDepth == 0){
	    	if(!_topLevelCallCompleted) {
	    		shutdownObjectContainer();
	    	}
    	}
    }
    
    public final void endTopLevelSet(Transaction trans){
    	endTopLevelCall();
    	if(_stackDepth == 0 && _topLevelCallCompleted){
    		trans.processDeletes();
    	}
    }
    
    private final void generateCallIDOnTopLevel(){
    	if(_stackDepth == 0){
    		_topLevelCallId = _topLevelCallIdGenerator.next();
    	}
    }
    
    public int stackDepth(){
    	return _stackDepth;
    }
    
    public void stackDepth(int depth){
    	_stackDepth = depth;
    }
    
    public int topLevelCallId(){
    	return _topLevelCallId;
    }
    
    public void topLevelCallId(int id){
    	_topLevelCallId = id;
    }

    public long version(){
    	synchronized(_lock){
    		return currentVersion();
    	}
    }

    public abstract void shutdown();

    public abstract void writeDirty();

    public abstract void writeNew(Transaction trans, Pointer4 pointer, ClassMetadata classMetadata, ByteArrayBuffer buffer);

    public abstract void writeTransactionPointer(int address);

    public abstract void writeUpdate(Transaction trans, Pointer4 pointer, ClassMetadata classMetadata, ByteArrayBuffer buffer);

    // cheat emulating '(YapStream)this'
    private static ExternalObjectContainer cast(PartialObjectContainer obj) {
    	return (ExternalObjectContainer)obj;
    }
    
    public Callbacks callbacks() {
    	return _callbacks;
    }
    
    public void callbacks(Callbacks cb) {
		if (cb == null) {
			throw new IllegalArgumentException();
		}
		_callbacks = cb;
    }

    public Config4Impl configImpl() {
        return _config;
    }
    
	public UUIDFieldMetadata uUIDIndex() {
		return _handlers.indexes()._uUID;
	}
	
	public VersionFieldMetadata versionIndex() {
		return _handlers.indexes()._version;
	}

    public ClassMetadataRepository classCollection() {
        return _classCollection;
    }
    
    public ClassInfoHelper getClassMetaHelper() {
    	return _classMetaHelper;
    }
    
    public abstract long[] getIDsForClass(Transaction trans, ClassMetadata clazz);
    
	public abstract QueryResult classOnlyQuery(Transaction trans, ClassMetadata clazz);
	
	public abstract QueryResult executeQuery(QQuery query);
	
	public void replicationCallState(int state) {
		_replicationCallState = state;
	}

	public abstract void onCommittedListener();
	
	public ReferenceSystemRegistry referenceSystemRegistry(){
	    return _referenceSystemRegistry;
	}
	   
    public ObjectContainerBase container(){
        return _this;
    }
    
	public void deleteByID(Transaction transaction, int id, int cascadeDeleteDepth) {
		if(id <= 0){
			return;
		}
        if (cascadeDeleteDepth <= 0) {
        	return;
        }
        Object obj = getByID2(transaction, id);
        if(obj == null){
        	return;
        }
        cascadeDeleteDepth--;
        ReflectClass claxx = reflectorForObject(obj);
		if (claxx.isCollection()) {
            cascadeDeleteDepth += reflector().collectionUpdateDepth(claxx) - 1;
        }
        ObjectReference ref = transaction.referenceForId(id);
        if (ref == null) {
        	return;
        }
        delete2(transaction, ref, obj,cascadeDeleteDepth, false);
	}
	
	ReflectClass reflectorForObject(Object obj){
	    return reflector().forObject(obj);
	}

    
    public Object syncExec(Closure4 block) {
    	synchronized(_lock) {
    		return block.run();
    	}
    }

}