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
package com.db4o.internal.activation;

import com.db4o.internal.*;


/**
 * @exclude
 */
public class ActivationContext4 {
    
    private final Transaction _transaction;
    
    private final Object _targetObject;
    
    private final ActivationDepth _depth;
    
    public ActivationContext4(Transaction transaction, Object obj, ActivationDepth depth){
        _transaction = transaction;
        _targetObject = obj;
        _depth = depth;
    }

    public void cascadeActivationToTarget(ClassMetadata classMetadata, boolean doDescend) {
        ActivationDepth depth = doDescend ? _depth.descend(classMetadata) : _depth; 
        cascadeActivation(classMetadata, targetObject(), depth);
    }
    
    public void cascadeActivationToChild(Object obj) {
        if(obj == null){
            return;
        }
        ClassMetadata classMetadata = container().classMetadataForObject(obj);
        if(classMetadata == null || classMetadata.isPrimitive()){
            return;
        }
        ActivationDepth depth = _depth.descend(classMetadata);
        cascadeActivation(classMetadata, obj, depth);
    }
    
    private void cascadeActivation(ClassMetadata classMetadata, Object obj, ActivationDepth depth) {
        if (! depth.requiresActivation()) {
            return;
        }
        if (depth.mode().isDeactivate()) {
            container().stillToDeactivate(_transaction, obj, depth, false);
        } else {
            // FIXME: [TA] do we need to check for isValueType here?
            if(classMetadata.isValueType()){
                classMetadata.activateFields(_transaction, obj, depth);
            }else{
                container().stillToActivate(_transaction, obj, depth);
            }
        }
    }

    public ObjectContainerBase container(){
        return _transaction.container();
    }

    public Object targetObject() {
        return _targetObject;
    }

}
