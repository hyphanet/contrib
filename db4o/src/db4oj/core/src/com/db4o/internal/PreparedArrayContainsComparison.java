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

import com.db4o.foundation.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;

/**
 * @exclude
 */
public class PreparedArrayContainsComparison implements PreparedComparison {
	
	private final ArrayHandler _arrayHandler;
	
	private final PreparedComparison _preparedComparison; 
	
	private ObjectContainerBase _container;
	
	public PreparedArrayContainsComparison(Context context, ArrayHandler arrayHandler, TypeHandler4 typeHandler, Object obj){
		_arrayHandler = arrayHandler;
		_preparedComparison = typeHandler.prepareComparison(context, obj);
		_container = context.transaction().container();
	}

	public int compareTo(Object obj) {
		// We never expect this call
		// TODO: The callers of this class should be refactored to pass a matcher and
		//       to expect a PreparedArrayComparison.
		throw new IllegalStateException();
	}
	
    public boolean IsEqual(Object array) {
    	return isMatch(array, IntMatcher.ZERO);
    }

    public boolean isGreaterThan(Object array) {
    	return isMatch(array, IntMatcher.POSITIVE);
    }

    public boolean isSmallerThan(Object array) {
    	return isMatch(array, IntMatcher.NEGATIVE);
    }
    
    private boolean isMatch(Object array, IntMatcher matcher){
        if(array == null){
            return false;
        }
        Iterator4 i = _arrayHandler.allElements(_container, array);
        while (i.moveNext()) {
        	if(matcher.match(_preparedComparison.compareTo(i.current()))){
        		return true;
        	}
        }
        return false;
    }

}
