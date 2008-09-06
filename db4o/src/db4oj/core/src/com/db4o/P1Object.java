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
package com.db4o;

import com.db4o.ext.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;

/**
 * base class for all database aware objects
 * @exclude
 * @persistent
 */
public class P1Object implements Db4oTypeImpl{
    
    private transient Transaction i_trans;
    private transient ObjectReference i_yapObject;
    
    public P1Object(){
    }
    
    P1Object(Transaction a_trans){
        i_trans = a_trans;
    }
    
    public void activate (Object a_obj, int a_depth){
        if(i_trans == null){
        	return;
        }
        if(a_depth < 0){
            stream().activate(i_trans, a_obj);
        }else{
            stream().activate(i_trans, a_obj, new LegacyActivationDepth(a_depth));
        }
    }
    
    public boolean canBind() {
        return false;
    }
    
    public void checkActive(){
        if(i_trans == null){
        	return;
        }
	    if(i_yapObject == null){
	        
	        i_yapObject = i_trans.referenceForObject(this);
	        if(i_yapObject == null){
	            stream().store(i_trans, this);
	            i_yapObject = i_trans.referenceForObject(this);
	        }
	    }
	    if(validYapObject()){
	    	i_yapObject.activate(i_trans, this, activationDepth(ActivationMode.ACTIVATE));
	    }
    }

	private LegacyActivationDepth activationDepth(ActivationMode mode) {
		return new LegacyActivationDepth(3, mode);
	}

    public Object createDefault(Transaction a_trans) {
        throw Exceptions4.virtualException();
    }
    
    void deactivate(){
        if(validYapObject()){
            i_yapObject.deactivate(i_trans, activationDepth(ActivationMode.DEACTIVATE));
        }
    }
    
    void delete(){
        if(i_trans == null){
        	return;
        }
        if(i_yapObject == null){
            i_yapObject = i_trans.referenceForObject(this);
        }
        if(validYapObject()){
            stream().delete2(i_trans,i_yapObject,this, 0, false);
        }
    }
    
    protected void delete(Object a_obj){
        if(i_trans != null){
            stream().delete(i_trans, a_obj);
        }
    }
    
    protected long getIDOf(Object a_obj){
        if(i_trans == null){
            return 0;
        }
        return stream().getID(i_trans, a_obj);
    }
    
    protected Transaction getTrans(){
        return i_trans;
    }
    
    public boolean hasClassIndex() {
        return false;
    }
    
    public void preDeactivate(){
        // virtual, do nothing
    }	

    public void setTrans(Transaction a_trans){
        i_trans = a_trans;
    }

    public void setObjectReference(ObjectReference a_yapObject) {
        i_yapObject = a_yapObject;
    }
    
    protected void store(Object a_obj){
        if(i_trans != null){
            stream().storeInternal(i_trans, a_obj, true);
        }
    }
    
    public Object storedTo(Transaction a_trans){
        i_trans = a_trans;
        return this;
    }
    
    Object streamLock(){
        if(i_trans != null){
	        stream().checkClosed();
	        return stream().lock();
        }
        return this;
    }
    
    public void store(int a_depth){
        if(i_trans == null){
        	return;
        }
        if(i_yapObject == null){
            i_yapObject = i_trans.referenceForObject(this);
            if(i_yapObject == null){
                i_trans.container().storeInternal(i_trans, this, true);
                i_yapObject = i_trans.referenceForObject(this);
                return;
            }
        }
        update(a_depth);
    }
    
    void update(){
    	// FIXME: [TA] normalize update depth usage as well?
        update(2); // activationDepth());
    }
    
    void update(int depth){
        if(validYapObject()){
            ObjectContainerBase stream = stream();
            stream.beginTopLevelSet();
            try{
	            i_yapObject.writeUpdate(i_trans, depth);
	            stream.checkStillToSet();
	            stream.completeTopLevelSet();
            } catch(Db4oException e) {
            	stream.completeTopLevelCall();
				throw e;
            } finally{
            	stream.endTopLevelSet(i_trans);
            }
        }
    }
    
    void updateInternal(){
    	// FIXME: [TA] consider ActivationDepth approach for update too
        updateInternal(2); //activationDepth());
    }
    
    void updateInternal(int depth){
        if(validYapObject()){
            i_yapObject.writeUpdate(i_trans, depth);
            stream().flagAsHandled(i_yapObject);
            stream().checkStillToSet();
        }
    }
    
    private boolean validYapObject(){
        return (i_trans != null) && (i_yapObject != null) && (i_yapObject.getID() > 0);
    }
    
    private ObjectContainerBase stream(){
    	return i_trans.container();
    }
    
}
