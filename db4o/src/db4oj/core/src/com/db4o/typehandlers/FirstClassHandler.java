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
package com.db4o.typehandlers;

import com.db4o.internal.activation.*;
import com.db4o.internal.marshall.*;


/**
 * TypeHandler for objects with own identity that support
 * activation and querying on members.
 */
public interface FirstClassHandler extends TypeHandler4{
    
	/**
	 * will be called during activation if the handled
	 * object is already active 
	 * @param context
	 */
    void cascadeActivation(ActivationContext4 context);
    
    /**
     * will be called during querying to ask for the handler
     * to be used to collect children of the handled object
     * @param context
     * @return
     */
    TypeHandler4 readCandidateHandler(QueryingReadContext context);
    
    /**
     * will be called during querying to ask for IDs of member
     * objects of the handled object.
     * @param context
     */
    public void collectIDs(QueryingReadContext context);

}
