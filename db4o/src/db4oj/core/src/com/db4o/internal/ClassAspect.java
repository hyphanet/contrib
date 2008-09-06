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

import com.db4o.internal.activation.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;


/**
 * @exclude
 */
public abstract class ClassAspect {
    
    // used for identification when sending in C/S mode 
	protected int              _handle;
    
    private int _disabledFromAspectCountVersion = AspectVersionContextImpl.ALWAYS_ENABLED.aspectCount();
    
    public abstract AspectType aspectType();
    
    public abstract String getName();
    
    public abstract void cascadeActivation(Transaction trans, Object obj, ActivationDepth depth);
    
    public abstract int linkLength();
    
    public final void incrementOffset(ReadBuffer buffer) {
        buffer.seek(buffer.offset() + linkLength());
    }

    public abstract void defragAspect(DefragmentContext context);

    public abstract void marshall(MarshallingContext context, Object child);

    public abstract void collectIDs(CollectIdContext context);
    
    public void setHandle(int handle) {
        _handle = handle;
    }

    public abstract void instantiate(UnmarshallingContext context);

	public abstract void delete(DeleteContextImpl context, boolean isUpdate);
	
	public abstract boolean canBeDisabled();
	
    protected boolean checkEnabled(AspectVersionContext context){
    	if(! enabled(context)){
    		incrementOffset((ReadBuffer)context);
    		return false;
    	}
    	return true;
    }

	
	public void disableFromAspectCountVersion(int aspectCount) {
		if(! canBeDisabled()){
			return;
		}
		if(aspectCount < _disabledFromAspectCountVersion){
			_disabledFromAspectCountVersion = aspectCount;
		}
	}
	
	public boolean enabled(AspectVersionContext context){
		return _disabledFromAspectCountVersion  > context.aspectCount();	
	}

	public abstract void deactivate(Transaction trans, Object obj, ActivationDepth depth);

}
