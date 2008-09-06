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
import com.db4o.query.*;


public class ObjectSetAsList {
    
    public String name;
    
    public ObjectSetAsList(){
    }
    
    public ObjectSetAsList(String name){
        this.name = name;
    }
    
    public void store(){
        Test.deleteAllInstances(this);
        Test.store(new ObjectSetAsList("one"));
        Test.store(new ObjectSetAsList("two"));
        Test.store(new ObjectSetAsList("three"));
    }
    
    public void testContent(){
        Query q = Test.query();
        q.constrain(ObjectSetAsList.class);
        List list = q.execute();
        Test.ensure(list.size() == 3);
        Iterator i = list.iterator();
        boolean found = false;
        while(i.hasNext()){
            ObjectSetAsList osil = (ObjectSetAsList)i.next();
            if(osil.name.equals("two")){
                found = true;
            }
        }
        Test.ensure(found);
    }

	public void testAccessOrder() {
		Query query=Test.query();
		query.constrain(getClass());
		ObjectSet result=query.execute();
		Test.ensureEquals(3,result.size());
		for(int i=0;i<3;i++) {
			Test.ensure(result.get(i)==result.next());
		}
		Test.ensure(!result.hasNext());
	}
}
