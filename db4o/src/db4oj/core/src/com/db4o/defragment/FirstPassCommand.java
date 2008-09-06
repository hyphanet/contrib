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
package com.db4o.defragment;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.slots.*;
		
/**
 * First step in the defragmenting process: Allocates pointer slots in the target file for
 * each ID (but doesn't fill them in, yet) and registers the mapping from source pointer address
 * to target pointer address.
 * 
 * @exclude
 */
final class FirstPassCommand implements PassCommand {
	private final static int ID_BATCH_SIZE=4096;

	private TreeInt _ids;
	
	void process(DefragmentServicesImpl context, int objectID, boolean isClassID) {
		if(batchFull()) {
			flush(context);
		}
		_ids=TreeInt.add(_ids,(isClassID ? -objectID : objectID));
	}

	private boolean batchFull() {
		return _ids!=null&&_ids.size()==ID_BATCH_SIZE;
	}

	public void processClass(final DefragmentServicesImpl context, ClassMetadata classMetadata,int id,int classIndexID) {
		process(context,id, true);
		classMetadata.forEachField(new Procedure4() {
            public void apply(Object arg) {
                FieldMetadata field = (FieldMetadata) arg;
                if(!field.isVirtual()&&field.hasIndex()) {
                    processBTree(context,field.getIndex(context.systemTrans()));
                }
            }
        });
	}

	public void processObjectSlot(DefragmentServicesImpl context, ClassMetadata yapClass, int sourceID) {
		process(context,sourceID, false);
	}

	public void processClassCollection(DefragmentServicesImpl context) throws CorruptionException {
		process(context,context.sourceClassCollectionID(), false);
	}

	public void processBTree(final DefragmentServicesImpl context, final BTree btree) {
		process(context,btree.getID(), false);
		context.traverseAllIndexSlots(btree, new Visitor4() {
			public void visit(Object obj) {
				int id=((Integer)obj).intValue();
				process(context,id, false);
			}
		});
	}

	public void flush(DefragmentServicesImpl context) {
		if(_ids==null) {
			return;
		}
		int blockSize = context.blockSize();
		boolean overlapping=(Const4.POINTER_LENGTH%blockSize>0);
		int blocksPerPointer=Const4.POINTER_LENGTH/blockSize;
		if(overlapping) {
			blocksPerPointer++;
		}
		int bytesPerPointer = blocksPerPointer * blockSize;
		int batchSize = _ids.size() * bytesPerPointer;
		Slot pointerSlot = context.allocateTargetSlot(batchSize);
		int pointerAddress=pointerSlot.address();
		Iterator4 idIter=new TreeKeyIterator(_ids);
		while(idIter.moveNext()) {
			int objectID=((Integer)idIter.current()).intValue();
			boolean isClassID=false;
			if(objectID<0) {
				objectID=-objectID;
				isClassID=true;
			}
			
			if(DefragmentConfig.DEBUG){
				int mappedID = context.mappedID(objectID, -1);
				// seen object ids don't come by here anymore - any other candidates?
				if(mappedID>=0) {
					throw new IllegalStateException();
				}
			}
			
			context.mapIDs(objectID,pointerAddress, isClassID);
			pointerAddress+=blocksPerPointer;
		}
		_ids=null;
	}
}