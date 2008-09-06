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

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public abstract class AbstractReadContext extends AbstractBufferContext implements InternalReadContext {
    
    protected ActivationDepth _activationDepth = UnknownActivationDepth.INSTANCE;
    
    protected AbstractReadContext(Transaction transaction, ReadBuffer buffer){
    	super(transaction, buffer);
    }
    
    protected AbstractReadContext(Transaction transaction){
    	this(transaction, null);
    }
    
    public final Object read(TypeHandler4 handlerType) {
        return readObject(handlerType);
    }
    
    public final Object readObject(TypeHandler4 handlerType) {
        final TypeHandler4 handler = Handlers4.correctHandlerVersion(this, handlerType);
        return slotFormat().doWithSlotIndirection(this, handler, new Closure4() {
            public Object run() {
                return readAtCurrentSeekPosition(handler);
            }
        
        });
    }
    
    public Object readAtCurrentSeekPosition(TypeHandler4 handler){
        if(handler instanceof ClassMetadata){
            ClassMetadata classMetadata = (ClassMetadata) handler;
            if(classMetadata.isValueType()){
                return classMetadata.readValueType(transaction(), readInt(), activationDepth().descend(classMetadata));
            }
        }
        if(useDedicatedSlot(handler)){
            return readObject();
        }
        return handler.read(this);
    }

	public boolean useDedicatedSlot(TypeHandler4 handler) {
		return FieldMetadata.useDedicatedSlot(this, handler);
	}

    public final Object readObject() {
        int id = readInt();
        if (id == 0) {
        	return null;
        }
        
        final ClassMetadata classMetadata = classMetadataForId(id);
        if (null == classMetadata) {
        	// TODO: throw here
        	return null;
        }
        
		ActivationDepth depth = activationDepth().descend(classMetadata);
        if (peekPersisted()) {
            return container().peekPersisted(transaction(), id, depth, false);
        }

        Object obj = container().getByID2(transaction(), id);
        if (null == obj) {
        	return null;
        }

        // this is OK for primitive YapAnys. They will not be added
        // to the list, since they will not be found in the ID tree.
        container().stillToActivate(transaction(), obj, depth);

        return obj;
    }

    private ClassMetadata classMetadataForId(int id) {
        
        // TODO: This method is *very* costly as is, since it reads
        //       the whole slot once and doesn't reuse it. Optimize.
        
    	HardObjectReference hardRef = container().getHardObjectReferenceById(transaction(), id);
    	if (null == hardRef || hardRef._reference == null) {
    		// com.db4o.db4ounit.common.querying.CascadeDeleteDeleted
    		return null;
    	}
		return hardRef._reference.classMetadata();
	}

	protected boolean peekPersisted() {
        return false;
    }
    
    public ActivationDepth activationDepth() {
        return _activationDepth;
    }
    
    public void activationDepth(ActivationDepth depth){
        _activationDepth = depth;
    }
    
    public ReadWriteBuffer readIndirectedBuffer() {
        int address = readInt();
        int length = readInt();
        if(address == 0){
            return null;
        }
        return container().bufferByAddress(address, length);
    }

}
