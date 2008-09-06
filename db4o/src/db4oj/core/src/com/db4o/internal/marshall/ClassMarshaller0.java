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

import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.classindex.*;
import com.db4o.internal.convert.conversions.*;


/**
 * @exclude
 */
public class ClassMarshaller0 extends ClassMarshaller{
    
    protected void readIndex(ObjectContainerBase stream, ClassMetadata clazz, ByteArrayBuffer reader) {
        int indexID = reader.readInt();
        if(! stream.maintainsIndices() || ! (stream instanceof LocalObjectContainer)){
            return;
        }
        if(btree(clazz) != null){
            return;
        }
        clazz.index().read(stream, validIndexId(indexID));
        if(isOldClassIndex(indexID)){
            new ClassIndexesToBTrees_5_5().convert((LocalObjectContainer)stream, indexID, btree(clazz));
            stream.setDirtyInSystemTransaction(clazz);
        }
    }

    private BTree btree(ClassMetadata clazz) {
        return BTreeClassIndexStrategy.btree(clazz);
    }

    private int validIndexId(int indexID) {
        return isOldClassIndex(indexID) ? 0 : -indexID;
    }

    private boolean isOldClassIndex(int indexID) {
        return indexID > 0;
    }
    
    protected int indexIDForWriting(int indexID){
        return indexID;
    }
}
