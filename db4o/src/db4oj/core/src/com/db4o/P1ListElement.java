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
 * element of linked lists 
 * @exclude 
 * @persistent
 * @deprecated since 7.0
 */
public class P1ListElement extends P1Object{
    
    public P1ListElement i_next;
    public Object i_object;
    
    public P1ListElement(){
    }
    
    public P1ListElement(Transaction a_trans, P1ListElement a_next, Object a_object){
        super(a_trans);
        i_next = a_next;
        i_object = a_object;
    }
    
    Object activatedObject(int a_depth){
        
        // TODO: It may be possible to optimise away the following call
        checkActive();
        if (null == i_object) {
        	return null;
        }
        activate(i_object, a_depth);
        return i_object;
    }

    public Object createDefault(Transaction a_trans) {
        P1ListElement elem4 = new P1ListElement();
        elem4.setTrans(a_trans);
        return elem4;
    }
    
    void delete(boolean a_deleteRemoved){
        if(a_deleteRemoved){
            delete(i_object);
        }
        delete();
    }
    
    
}
