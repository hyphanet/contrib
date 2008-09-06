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

public class CollectionSubQuery {
	private final static String ID="X";
	
	public static class Data {
		public String id;

		public Data(String id) {
			this.id = id;
		}
	}
	
	public List list;
	
    public void storeOne(){
    	this.list=new ArrayList();
    	this.list.add(new Data(ID));
    }
    
    public void test(){
        Query q = Test.query();
        q.constrain(CollectionSubQuery.class);
        Query sub=q.descend("list");
        // Commenting out this constraint doesn't effect behavior
        sub.constrain(Data.class);
        // If this subsub constraint is commented out, the result
        // contains a Data instance as expected. With this constraint,
        // we get the containing ArrayList.
        Query subsub=sub.descend("id");
        subsub.constrain(ID);
        ObjectSet result=sub.execute();
        Test.ensure(result.size()==1);
        Test.ensure(result.next().getClass()==Data.class);
    }
    
    public static void main(String[] args) {
		AllTests.run(CollectionSubQuery.class);
	}
}
