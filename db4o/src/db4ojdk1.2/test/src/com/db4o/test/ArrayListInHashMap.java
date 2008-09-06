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

public class ArrayListInHashMap {
    
    HashMap hm;
    
    public void storeOne(){
        hm = new HashMap();
        ArrayList lOne = new ArrayList();
        lOne.add("OneOne");
        lOne.add("OneTwo");
        hm.put("One", lOne);
        ArrayList lTwo = new ArrayList();
        lTwo.add("TwoOne");
        lTwo.add("TwoTwo");
        lTwo.add("TwoThree");
        hm.put("Two", lTwo);
    }
    
    public void testOne(){
        ArrayList lOne = tContent();
        Test.objectContainer().deactivate(lOne, Integer.MAX_VALUE);
        Test.store(hm);
        Test.objectContainer().activate(this, Integer.MAX_VALUE);
        tContent();
    }
    
    private ArrayList tContent(){
        Test.ensure(hm.size() == 2);
        ArrayList lOne = (ArrayList)hm.get("One");
        Test.ensure(lOne.size() == 2);
        Test.ensure(lOne.get(0).equals("OneOne"));
        Test.ensure(lOne.get(1).equals("OneTwo"));
        ArrayList lTwo = (ArrayList)hm.get("Two");
        Test.ensure(lTwo.size() == 3);
        Test.ensure(lTwo.get(0).equals("TwoOne"));
        Test.ensure(lTwo.get(1).equals("TwoTwo"));
        Test.ensure(lTwo.get(2).equals("TwoThree"));
        return lOne;
    }
}


