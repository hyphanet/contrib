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

import com.db4o.foundation.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.marshall.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public class TypeHandlerAspect extends ClassAspect {
    
    public final TypeHandler4 _typeHandler;
    
    public TypeHandlerAspect(TypeHandler4 typeHandler){
        _typeHandler = typeHandler;
    }
    
    public boolean equals(Object obj) {
        if(obj == this){
            return true;
        }
        if(obj == null || obj.getClass() != getClass()){
            return false;
        }
        TypeHandlerAspect other = (TypeHandlerAspect) obj;
        return _typeHandler.equals(other._typeHandler);
    }
    
    public int hashCode() {
        return _typeHandler.hashCode();
    }

    public String getName() {
        return _typeHandler.getClass().getName();
    }

    public void cascadeActivation(Transaction trans, Object obj, ActivationDepth depth) {
    	if(_typeHandler instanceof FirstClassHandler){
            ActivationContext4 context = new ActivationContext4(trans, obj, depth);
    		((FirstClassHandler)_typeHandler).cascadeActivation(context);
    	}
    }

    public void collectIDs(CollectIdContext context) {
        throw new NotImplementedException();
    }

    public void defragAspect(final DefragmentContext context) {
    	context.slotFormat().doWithSlotIndirection(context, new Closure4() {
			public Object run() {
				_typeHandler.defragment(context);
				return null;
			}
		
		});
    }

    public int linkLength() {
        return Const4.INDIRECTION_LENGTH;
    }

    public void marshall(MarshallingContext context, Object obj) {
    	context.createIndirectionWithinSlot();
        _typeHandler.write(context, obj);
    }

    public AspectType aspectType() {
        return AspectType.TYPEHANDLER;
    }

    public void instantiate(final UnmarshallingContext context) {
    	if(! checkEnabled(context)){
    		return;
    	}
    	final Object oldObject = context.persistentObject();
    	context.slotFormat().doWithSlotIndirection(context, new Closure4() {
			public Object run() {
		        Object readObject = _typeHandler.read(context);
		        if(readObject != null && oldObject != readObject){
		        	context.persistentObject(readObject);
		        }
				return null;
			}
		});
    }

	public void delete(final DeleteContextImpl context, boolean isUpdate) {
    	context.slotFormat().doWithSlotIndirection(context, new Closure4() {
			public Object run() {
				_typeHandler.delete(context);
				return null;
			}
		});
	}

	public void deactivate(Transaction trans, Object obj, ActivationDepth depth) {
		cascadeActivation(trans, obj, depth);
	}

	public boolean canBeDisabled() {
		return true;
	}

}
