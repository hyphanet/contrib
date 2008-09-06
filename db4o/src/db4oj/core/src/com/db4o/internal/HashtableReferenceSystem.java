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
public class HashtableReferenceSystem implements ReferenceSystem{
    
    private Hashtable4       _hashCodeTable = new Hashtable4();
    
    private Hashtable4       _idTable = new Hashtable4();
    
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
        _hashCodeTable.put(hashCode(ref), ref);
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
        _idTable.put(ref.getID(), ref);
    }
    
    public ObjectReference referenceForId(int id){
        if(DTrace.enabled){
            DTrace.GET_YAPOBJECT.log(id);
        }
        if(! ObjectReference.isValidId(id)){
            return null;
        }
        return (ObjectReference) _idTable.get(id);
    }
    
    public ObjectReference referenceForObject(Object obj) {
        if(_hashCodeTable == null){
            return null;
        }
        return (ObjectReference) _hashCodeTable.get(hashCode(obj));
    }

    private static final int hashCode(Object obj) {
        return ObjectReference.hc_getCode(obj);
    }
    
    private static final int hashCode(ObjectReference ref) {
        return ref._hcHashcode;
    }

    public void removeReference(ObjectReference ref) {
        if(DTrace.enabled){
            DTrace.REFERENCE_REMOVED.log(ref.getID());
        }
        _hashCodeTable.remove(hashCode(ref));
        _idTable.remove(ref.getID());
    }

    public void rollback() {
        // do nothing
    }
    
    public void traverseReferences(final Visitor4 visitor) {
        Iterator4 i = _hashCodeTable.values();
        while(i.moveNext()){
            visitor.visit(i.current());
        }
    }


}
