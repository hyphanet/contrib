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
package com.db4o.internal.fieldindex;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.query.processor.*;

public class FieldIndexProcessorResult {
	
	public static final FieldIndexProcessorResult NO_INDEX_FOUND = new FieldIndexProcessorResult(null);

	public static final FieldIndexProcessorResult FOUND_INDEX_BUT_NO_MATCH = new FieldIndexProcessorResult(null);
	
	private final IndexedNode _indexedNode;
	
	public FieldIndexProcessorResult(IndexedNode indexedNode) {
		_indexedNode = indexedNode;
	}
	
	public Tree toQCandidate(QCandidates candidates){
		return TreeInt.toQCandidate(toTreeInt(), candidates);
	}
	
	public TreeInt toTreeInt(){
		if(foundMatch()){
			return _indexedNode.toTreeInt();
		}
		return null;
	}
	
	public boolean foundMatch(){
		return foundIndex() && ! noMatch();
	}
	
	public boolean foundIndex(){
		return this != NO_INDEX_FOUND;
	}
	
	public boolean noMatch(){
		return this == FOUND_INDEX_BUT_NO_MATCH;
	}
	
	public Iterator4 iterateIDs(){
		return new MappingIterator(_indexedNode.iterator()) {
			protected Object map(Object current) {
			    FieldIndexKey composite = (FieldIndexKey)current;
				return new Integer(composite.parentID());
			}
		};
	}
	
}