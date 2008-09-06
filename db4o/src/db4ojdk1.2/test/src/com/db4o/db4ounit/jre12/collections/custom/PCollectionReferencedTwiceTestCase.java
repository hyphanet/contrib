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
package com.db4o.db4ounit.jre12.collections.custom;

import java.util.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class PCollectionReferencedTwiceTestCase extends AbstractDb4oTestCase {

	/**
	 * @deprecated using deprecated api
	 */
    protected void store(){
        
        ExtObjectContainer oc = db();
        
        PCRTHolder one = new PCRTHolder();
        
        one._list = oc.collections().newLinkedList();
        
        one._list.add("Hi");
        
        oc.store(one);
        
        PCRTHolder two = new PCRTHolder();
        
        two._list = one._list;
        
        oc.store(two);
    }
    
    public void test() throws Exception{
        
        tListIdentity();
        
        reopen();
        
        tListIdentity();
        
    }
    
    private void tListIdentity(){
        ExtObjectContainer oc = db();
        
        Query q = oc.query();
        q.constrain(PCRTHolder.class);
        
        ObjectSet res = q.execute();
        
        Assert.areEqual(2,res.size());
        
        PCRTHolder one = (PCRTHolder) res.next();
        PCRTHolder two = (PCRTHolder) res.next();
        
        Assert.areSame(one._list,two._list);
        
    }
    
    
    public static class PCRTHolder {
        
        public List _list; 
        
    }
    
    

}
