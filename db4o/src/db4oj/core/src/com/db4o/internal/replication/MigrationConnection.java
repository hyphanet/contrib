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
package com.db4o.internal.replication;

import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * @exclude
 */
public class MigrationConnection {
    
    public final ObjectContainerBase _peerA;
    public final ObjectContainerBase _peerB;

    private final Hashtable4 _referenceMap;
    private final Hashtable4 _identityMap;

    public MigrationConnection(ObjectContainerBase peerA, ObjectContainerBase peerB) {
        _referenceMap = new Hashtable4();
        _identityMap = new Hashtable4();
        _peerA = peerA;
        _peerB = peerB;
    }

    public void mapReference(Object obj, ObjectReference ref) {
        
        // FIXME: Identityhashcode is not unique
        
        // ignored for now, since it is on most VMs.
        
        // This should be fixed by adding 
        // putIdentity and getIdentity methods to Hashtable4,
        // using the actual object as the parameter and 
        // checking for object identity in addition to the
        // hashcode
        
        _referenceMap.put(System.identityHashCode(obj), ref);
    }
    
    public void mapIdentity(Object obj, Object otherObj) {
        _identityMap.put(System.identityHashCode(obj), otherObj);
    }


    public ObjectReference referenceFor(Object obj) {
        int hcode = System.identityHashCode(obj);
        ObjectReference ref = (ObjectReference) _referenceMap.get(hcode);
        _referenceMap.remove(hcode);
        return ref;
    }
    
    public Object identityFor(Object obj) {
        int hcode = System.identityHashCode(obj);
        return _identityMap.get(hcode);
    }

    
    public void terminate(){
        _peerA.migrateFrom(null);
        _peerB.migrateFrom(null);
    }
    
    public ObjectContainerBase peer(ObjectContainerBase stream){
        if(_peerA == stream){
            return _peerB;
        }
        return _peerA;
    }
    
    

}
