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

import com.db4o.ext.*;

public class FrozenObjectInfo implements ObjectInfo {
	
    private final Db4oDatabase _sourceDatabase;
    private final long _uuidLongPart;
	private final long _id;
	private final long _version;
	private final Object _object;
	
    public FrozenObjectInfo(Object object, long id, Db4oDatabase sourceDatabase, long uuidLongPart, long version) {
        _sourceDatabase = sourceDatabase;
        _uuidLongPart = uuidLongPart;
        _id = id;
        _version = version;
        _object = object;
    }

    private FrozenObjectInfo(ObjectReference ref, VirtualAttributes virtualAttributes) {
        this(
            ref == null ? null : ref.getObject(), 
            ref == null ? -1 :ref.getID(), 
            virtualAttributes == null ? null : virtualAttributes.i_database, 
            virtualAttributes == null ? -1 : virtualAttributes.i_uuid,  
            ref == null ? 0 :ref.getVersion());      
    }

	public FrozenObjectInfo(Transaction trans, ObjectReference ref) {
	    this(ref, ref == null ? null : ref.virtualAttributes(trans, true));
	}
	
	public long getInternalID() {
		return _id;
	}

	public Object getObject() {
		return _object;
	}

	public Db4oUUID getUUID() {
	    if(_sourceDatabase == null ){
	        return null;
	    }
	    return new Db4oUUID(_uuidLongPart, _sourceDatabase.getSignature());
	}

	public long getVersion() {
		return _version;
	}

    public long sourceDatabaseId(Transaction trans) {
        if(_sourceDatabase == null){
            return -1;
        }
        return _sourceDatabase.getID(trans);
    }

    public long uuidLongPart() {
        return _uuidLongPart;
    }
}