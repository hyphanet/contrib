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
public class OrClassConstraintInList {
    
    int cnt;
    List list;
    
    public void store(){
        OrClassConstraintInList occ = new OrClassConstraintInList();
        occ.list = Test.objectContainer().collections().newLinkedList();
        occ.cnt = 0;
        occ.list.add(new Atom());
        Test.store(occ);
        occ = new OrClassConstraintInList();
        occ.list = Test.objectContainer().collections().newLinkedList();
        occ.cnt = 1;
        occ.list.add(new Atom());
        Test.store(occ);
        occ = new OrClassConstraintInList();
        occ.cnt = 1;
        occ.list = Test.objectContainer().collections().newLinkedList();
        Test.store(occ);
        occ = new OrClassConstraintInList();
        occ.cnt = 2;
        occ.list = Test.objectContainer().collections().newLinkedList();
        occ.list.add(new OrClassConstraintInList());
        Test.store(occ);
    }
    
    public void test(){
        Query q = Test.query();
        q.constrain(OrClassConstraintInList.class);
        Constraint c1 = q.descend("list").constrain(Atom.class);
        Constraint c2 = q.descend("cnt").constrain(new Integer(1));
        c1.or(c2);
        Test.ensure(q.execute().size() == 3);
    }
}
