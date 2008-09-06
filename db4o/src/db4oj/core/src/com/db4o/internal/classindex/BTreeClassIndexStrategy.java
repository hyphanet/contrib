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
package com.db4o.internal.classindex;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;

/**
 * @exclude
 */
public class BTreeClassIndexStrategy extends AbstractClassIndexStrategy {
	
	private BTree _btreeIndex;
	
	public BTreeClassIndexStrategy(ClassMetadata yapClass) {
		super(yapClass);
	}	
	
	public BTree btree() {
		return _btreeIndex;
	}

	public int entryCount(Transaction ta) {
		return _btreeIndex != null
			? _btreeIndex.size(ta)
			: 0;
	}

	public void initialize(ObjectContainerBase stream) {
		createBTreeIndex(stream, 0);
	}

	public void purge() {
	}

	public void read(ObjectContainerBase stream, int indexID) {
		readBTreeIndex(stream, indexID);
	}

	public int write(Transaction trans) {
		if (_btreeIndex == null){
            return 0;
        } 
        _btreeIndex.write(trans);
        return _btreeIndex.getID();
	}
	
	public void traverseAll(Transaction ta,Visitor4 command) {
		// better alternatives for this null check? (has been moved as is from YapFile)
		if(_btreeIndex!=null) {
			_btreeIndex.traverseKeys(ta,command);
		}
	}

	private void createBTreeIndex(final ObjectContainerBase stream, int btreeID){
        if (stream.isClient()) {
        	return;
        }
        _btreeIndex = ((LocalObjectContainer)stream).createBTreeClassIndex(btreeID);
        _btreeIndex.setRemoveListener(new Visitor4() {
            public void visit(Object obj) {
                int id = ((Integer)obj).intValue();
                stream.referenceSystemRegistry().removeId(id);
            }
        });
    }
	
	private void readBTreeIndex(ObjectContainerBase stream, int indexId) {
		if(! stream.isClient() && _btreeIndex == null){
            createBTreeIndex(stream, indexId);
		}
	}

	protected void internalAdd(Transaction trans, int id) {
		_btreeIndex.add(trans, new Integer(id));
	}

	protected void internalRemove(Transaction ta, int id) {
		_btreeIndex.remove(ta, new Integer(id));
	}

	public void dontDelete(Transaction transaction, int id) {
	}
	
	public void defragReference(ClassMetadata classMetadata, DefragmentContextImpl context,int classIndexID) {
		int newID = -classIndexID;
		context.writeInt(newID);
	}
	
	public int id() {
		return _btreeIndex.getID();
	}

	public Iterator4 allSlotIDs(Transaction trans) {
        return _btreeIndex.allNodeIds(trans);
	}

	public void defragIndex(DefragmentContextImpl context) {
		_btreeIndex.defragIndex(context);
	}
	
	public static BTree btree(ClassMetadata clazz) {
		ClassIndexStrategy index = clazz.index();
		if(! (index instanceof BTreeClassIndexStrategy)){
			throw new IllegalStateException();
		}
		return ((BTreeClassIndexStrategy)index).btree();
	}
	
	public static Iterator4 iterate(ClassMetadata clazz, Transaction trans) {
		return btree(clazz).asRange(trans).keys();
	}
	
	
	
}
