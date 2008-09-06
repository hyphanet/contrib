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

import com.db4o.ext.*;
import com.db4o.types.*;

/**
 * 
 */
public class StringInLists {

    public List arrayList;
    public List db4oLinkedList;
    public Map  hashMap;
    public Map  db4oHashMap;

    public void storeOne() {

        ExtObjectContainer oc = Test.objectContainer();
        Db4oCollections col = oc.collections();

        arrayList = new ArrayList();
        fillList(arrayList);
        
        db4oLinkedList = col.newLinkedList();
        fillList(db4oLinkedList);
        
        hashMap = new HashMap();
        fillMap(hashMap);
        
        db4oHashMap = col.newHashMap(1);
        fillMap(db4oHashMap);
    }

    public void testOne() {
        checkList(arrayList);
        checkList(db4oLinkedList);
        checkMap(hashMap);
        checkMap(db4oHashMap);
    }

    private void fillList(List list) {
        list.add("One");
        list.add("Two");
        list.add("Three");
    }

    private void fillMap(Map map) {
        map.put("One", "One");
        map.put("Two", "Two");
        map.put("Three", "Three");
    }

    private void checkList(List list) {
        Test.ensure(list.size() == 3);
        Test.ensure(list.get(0).equals("One"));
        Test.ensure(list.get(1).equals("Two"));
        Test.ensure(list.get(2).equals("Three"));
    }
    
    private void checkMap(Map map){
        Test.ensure(map.size() == 3);
        Test.ensure(map.get("One").equals("One"));
        Test.ensure(map.get("Two").equals("Two"));
        Test.ensure(map.get("Three").equals("Three"));
    }

}