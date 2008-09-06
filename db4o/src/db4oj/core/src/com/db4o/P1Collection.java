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

import com.db4o.types.*;

/**
 * base class for database aware collections
 * @exclude 
 * @persistent
 * @sharpen.ignore
 * @deprecated since 7.0
 */
public abstract class P1Collection extends P1Object implements Db4oCollection{
    
    // This is an off-by-one variable. 
    // 0 means default, use standard activation depth
    // a value greater than 0 means (value - 1)
    private transient int i_activationDepth;
    
    transient boolean i_deleteRemoved;
    
    public void activationDepth(int a_depth){
        i_activationDepth = a_depth + 1;
    }
    
    public void deleteRemoved(boolean a_flag){
        i_deleteRemoved = a_flag;
    }
    
    int elementActivationDepth(){
        return i_activationDepth - 1;
    }

}
