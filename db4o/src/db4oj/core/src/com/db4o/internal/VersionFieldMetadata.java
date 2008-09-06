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
import com.db4o.internal.delete.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;

/**
 * @exclude
 */
public class VersionFieldMetadata extends VirtualFieldMetadata {

    VersionFieldMetadata() {
        super(Handlers4.LONG_ID, new LongHandler());
        setName(VirtualField.VERSION);
    }
    
    public void addFieldIndex(ObjectIdContextImpl context, Slot oldSlot)  throws FieldIndexException{
        StatefulBuffer buffer = (StatefulBuffer) context.buffer();
        buffer.writeLong(context.transaction().container().generateTimeStampId());
    }
    
    public void delete(DeleteContextImpl context, boolean isUpdate){
        context.seek(context.offset() + linkLength());
    }

    void instantiate1(ObjectReferenceContext context) {
        context.objectReference().virtualAttributes().i_version = context.readLong();
    }

    void marshall(Transaction trans, ObjectReference ref, WriteBuffer buffer, boolean isMigrating, boolean isNew) {
        VirtualAttributes attr = ref.virtualAttributes();
        if (! isMigrating) {
            attr.i_version = trans.container()._parent.generateTimeStampId();
        }
        if(attr == null){
            buffer.writeLong(0);
        }else{
            buffer.writeLong(attr.i_version);
        }
    }

    public int linkLength() {
        return Const4.LONG_LENGTH;
    }
    
    void marshallIgnore(WriteBuffer buffer) {
        buffer.writeLong(0);
    }


}