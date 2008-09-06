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
package com.db4o;

import com.db4o.internal.*;

/**
 * @exclude 
 * @persistent
 * @deprecated since 7.0
 */
public class P1HashElement extends P1ListElement {
    
    public Object i_key;
    public int i_hashCode;
    public int i_position;
    
    public P1HashElement(){
    }
    
    public P1HashElement(Transaction a_trans, P1ListElement a_next, Object a_key, int a_hashCode, Object a_object){
        super(a_trans, a_next, a_object);
        i_hashCode = a_hashCode;
        i_key = a_key;
    }
    
    Object activatedKey(int a_depth){
        
        // TODO: It may be possible to optimise away the following call.
        checkActive();

        
        // The pathologic case here:
        // No activation depth for the map.
        // Global activation depth of 0 during defragment
        // The key can't activate at all.
        
        // Let's make sure it has a depth of 1 at least, but of course that
        // may not be sufficient for more complex #hashCode calls.
        if(a_depth < 0){
            Transaction trans = getTrans();
            if(trans != null){
                if(trans.container().configImpl().activationDepth() < 1){
                    a_depth = 1;
                }
            }
        }
        
        activate(i_key, a_depth);
        return i_key;
    }
    
    void delete(boolean a_deleteRemoved){
        if(a_deleteRemoved){
            delete(i_key);
        }
        super.delete(a_deleteRemoved);
    }
}
