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

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.query.*;


public class QueryForStringKeyInMap {
    
    String name;
    Map map;
    
    public void store(){
        store1("one");
        store1("two");
        store1("three");
    }
    
    private void store1(String key){
        ExtObjectContainer oc = Test.objectContainer();
        QueryForStringKeyInMap holder = new QueryForStringKeyInMap();
        oc.store(holder);
        holder.map = oc.collections().newHashMap(1);
        holder.map.put("somethingelse", "somethingelse");
        holder.map.put(key, key);
        holder.name = key;
    }
    
    public void test(){
        t1("one");
        t1("two");
        t1("three");
    }
    
    private void t1(String key){
        Query q = Test.query();
        q.constrain(QueryForStringKeyInMap.class);
        q.descend("map").constrain(key);
        ObjectSet objectSet = q.execute();
        Test.ensure(objectSet.size() == 1);
        QueryForStringKeyInMap holder = (QueryForStringKeyInMap)objectSet.next();
        Test.ensure(holder.map.get(key).equals(key));
        Test.ensure(holder.name.equals(key));
    }

}
