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
import com.db4o.internal.btree.*;
import com.db4o.internal.query.processor.*;

/**
 * @exclude
 */
public class IndexedLeaf extends IndexedNodeBase implements IndexedNodeWithRange {
	
	private final BTreeRange _range;
    
    public IndexedLeaf(QConObject qcon) {
    	super(qcon);
    	_range = search();
    }
    
    private BTreeRange search() {
		final BTreeRange range = search(constraint().getObject());
        final QEBitmap bitmap = QEBitmap.forQE(constraint().evaluator());
        if (bitmap.takeGreater()) {        
            if (bitmap.takeEqual()) {
                return range.extendToLast();
            }            
            final BTreeRange greater = range.greater();
            if (bitmap.takeSmaller()) {
            	return greater.union(range.smaller());
            }
			return greater;
        }
        if (bitmap.takeSmaller()) {
        	if (bitmap.takeEqual()) {
        		return range.extendToFirst();
        	}
        	return range.smaller();
        }
        return range;
    }

	public int resultSize() {
        return _range.size();
    }

	public Iterator4 iterator() {
		return _range.keys();
	}

	public BTreeRange getRange() {
		return _range;
	}
}
