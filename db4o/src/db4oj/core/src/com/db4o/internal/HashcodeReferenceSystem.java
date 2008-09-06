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
import com.db4o.foundation.*;


/**
 * @exclude
 */
public class HashcodeReferenceSystem implements ReferenceSystem {
	
	private ObjectReference       _hashCodeTree;
	
	private ObjectReference       _idTree;
	
	public void addNewReference(ObjectReference ref){
		addReference(ref);
	}

	public void addExistingReference(ObjectReference ref){
		addReference(ref);
	}

	private void addReference(ObjectReference ref){
		ref.ref_init();
		idAdd(ref);
		hashCodeAdd(ref);
	}
	
	public void commit() {
		// do nothing
	}

	private void hashCodeAdd(ObjectReference ref){
		if (Deploy.debug) {
		    Object obj = ref.getObject();
		    if (obj != null) {
		        ObjectReference existing = referenceForObject(obj);
		        if (existing != null) {
		            System.out.println("Duplicate alarm hc_Tree");
		        }
		    }
		}
		if(_hashCodeTree == null){
			_hashCodeTree = ref;
			return;
		}
		_hashCodeTree = _hashCodeTree.hc_add(ref);
	}
	
	private void idAdd(ObjectReference ref){
		if(DTrace.enabled){
		    DTrace.ID_TREE_ADD.log(ref.getID());
		}
		if (Deploy.debug) {
		    ObjectReference existing = referenceForId(ref.getID());
		    if (existing != null) {
		        System.out.println("Duplicate alarm id_Tree:" + ref.getID());
		    }
		}
		if(_idTree == null){
			_idTree = ref;
			return;
		}
		_idTree = _idTree.id_add(ref);
	}
	
	public ObjectReference referenceForId(int id){
        if(DTrace.enabled){
            DTrace.GET_YAPOBJECT.log(id);
        }
        if(_idTree == null){
        	return null;
        }
        if(! ObjectReference.isValidId(id)){
            return null;
        }
        return _idTree.id_find(id);
	}
	
	public ObjectReference referenceForObject(Object obj) {
		if(_hashCodeTree == null){
			return null;
		}
		return _hashCodeTree.hc_find(obj);
	}

	public void removeReference(ObjectReference ref) {
        if(DTrace.enabled){
            DTrace.REFERENCE_REMOVED.log(ref.getID());
        }
        if(_hashCodeTree != null){
        	_hashCodeTree = _hashCodeTree.hc_remove(ref);
        }
        if(_idTree != null){
        	_idTree = _idTree.id_remove(ref.getID());
        }
	}

	public void rollback() {
		// do nothing
	}
	
	public void traverseReferences(final Visitor4 visitor) {
		if(_hashCodeTree == null){
			return;
		}
		_hashCodeTree.hc_traverse(visitor);
	}
	
}
