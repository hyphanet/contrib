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
package com.db4o.internal.query;

import com.db4o.ext.*;
import com.db4o.internal.query.result.*;
import com.db4o.query.*;

// TODO implement basic object methods (equals,..) for consistency with jdk1.2 version inheriting from AbstractList
/**
 * @exclude 
 * @sharpen.ignore
 */
public class ObjectSetFacade implements ExtObjectSet {
    
	// TODO: encapsulate field
    public final StatefulQueryResult _delegate;
    
    public ObjectSetFacade(QueryResult queryResult){
        _delegate = new StatefulQueryResult(queryResult);
    }

    public Object get(int index) {
        return _delegate.get(index);
    }
    
    public long[] getIDs() {
        return _delegate.getIDs();
    }

    public ExtObjectSet ext() {
        return this;
    }

    public boolean hasNext() {
        return _delegate.hasNext();
    }

    public Object next() {
        return _delegate.next();
    }

    public void reset() {
        _delegate.reset();
    }

    public int size() {
        return _delegate.size();
    }

	public void sort(QueryComparator cmp) {
		_delegate.sort(cmp);
	}	
}
