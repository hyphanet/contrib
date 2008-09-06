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
package com.db4o.test;

import java.util.*;

import com.db4o.query.*;


/**
 * 
 */
public class NestedLists {
    
    static final int DEPTH = 10;
    
    List list;
    String name;
    
    public void storeOne() {
        nest(DEPTH);
        name = "root";
    }
    
    private void nest(int depth) {
        if(depth > 0) {
            list = Test.objectContainer().collections().newLinkedList();
            NestedLists nl = new NestedLists();
            nl.name = "nested";
            nl.nest(depth - 1);
            list.add(nl);
        }
    }
    
    
    
    public void test() {
        Query q = Test.query();
        q.constrain(NestedLists.class);
        q.descend("name").constrain("root");
        NestedLists nl = (NestedLists)q.execute().next();
        for (int i = 0; i < DEPTH - 1; i++) {
            nl = nl.checkNest();
        }
    }
    
    private NestedLists checkNest() {
        Test.ensure(list != null);
        NestedLists nl = (NestedLists)list.get(0);
        Test.ensure(nl.name.equals("nested"));
        return nl;
    }
    
    public String toString() {
        String str = "NestedList ";
        if(name != null) {
            str += name;
        }
        if(list != null) {
            str += " list valid";
        }else {
            str += " list INVALID";
        }
        return str;
    }
    

}
