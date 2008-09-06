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
package com.db4o.internal.btree;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.handlers.*;
import com.db4o.marshall.*;


/**
 * @exclude
 */
public class FieldIndexKeyHandler implements Indexable4{
	
    private final Indexable4 _valueHandler;
    
    private final IntHandler _parentIdHandler;
    
    public FieldIndexKeyHandler(Indexable4 delegate_) {
        _parentIdHandler = new IDHandler();
        _valueHandler = delegate_;
    }

    public int linkLength() {
        return _valueHandler.linkLength() + Const4.INT_LENGTH;
    }

    public Object readIndexEntry(ByteArrayBuffer a_reader) {
        // TODO: could read int directly here with a_reader.readInt()
        int parentID = readParentID(a_reader);
        Object objPart = _valueHandler.readIndexEntry(a_reader);
        if (parentID < 0){
            objPart = null;
            parentID = - parentID;
        }
        return new FieldIndexKey(parentID, objPart);
    }

	private int readParentID(ByteArrayBuffer a_reader) {
		return ((Integer)_parentIdHandler.readIndexEntry(a_reader)).intValue();
	}

    public void writeIndexEntry(ByteArrayBuffer writer, Object obj) {
        FieldIndexKey composite = (FieldIndexKey)obj;
        int parentID = composite.parentID();
        Object value = composite.value();
        if (value == null){
            parentID = - parentID;
        }
        _parentIdHandler.write(parentID, writer);
        _valueHandler.writeIndexEntry(writer, composite.value());
    }
    
    public Indexable4 valueHandler() {
    	return _valueHandler;
    }
    
	public void defragIndexEntry(DefragmentContextImpl context) {
		_parentIdHandler.defragIndexEntry(context);
        _valueHandler.defragIndexEntry(context);
	}

	public PreparedComparison prepareComparison(Context context, Object fieldIndexKey) {
		final FieldIndexKey source = (FieldIndexKey)fieldIndexKey;
        final PreparedComparison preparedValueComparison = _valueHandler.prepareComparison(context, source.value());
        final PreparedComparison preparedParentIdComparison = _parentIdHandler.newPrepareCompare(source.parentID());
		return new PreparedComparison() {
			public int compareTo(Object obj) {
				FieldIndexKey target = (FieldIndexKey) obj;
		        try{
		        	int delegateResult = preparedValueComparison.compareTo(target.value());  
		            if(delegateResult != 0 ){
		                return delegateResult;
		            }
		        }catch (IllegalComparisonException ex){
		            // can happen, is expected
		        }
		        return preparedParentIdComparison.compareTo(new Integer(target.parentID()));
			}
		};
	}
}

