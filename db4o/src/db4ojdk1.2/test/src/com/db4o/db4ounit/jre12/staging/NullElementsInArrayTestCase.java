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
package com.db4o.db4ounit.jre12.staging;

import com.db4o.*;
import com.db4o.internal.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;


public class NullElementsInArrayTestCase extends AbstractDb4oTestCase{

    public static void main(String[] args) {
        new NullElementsInArrayTestCase().runSolo();
    }
    
    public class Item {
        
        public Integer[] array;
        
    }
    
    public class ItemArrayHolder {
        
        public NamedItem[] child;
        
    }
    
    public class NamedItem {
        
        public String name;
        
        public NamedItem(){
            
        }
        
        public NamedItem(String name_){
            name = name_;
        }
        
    }
    
    private static Integer[] DATA = new Integer[]{ new Integer(1), null, new Integer(2) };
    
    protected void store() throws Exception {
        Item item = new Item();
        item.array = DATA;
        store(item);
        
        ItemArrayHolder holder = new ItemArrayHolder();
        holder.child = new NamedItem[] {
            new NamedItem("one"),
            null,
            new NamedItem("two"),
        };
        store(holder);
        
    }
    
    public void testRetrieve(){
        Item item = (Item) retrieveOnlyInstance(Item.class);
    }
    
    public void _testQueryIntegerNull(){
        Query query = newQuery(Item.class);
        query.descend("array").constrain(null);
        Assert.areEqual(1, query.execute().size());
    }
    
    public void testQuerySubNode() {
        Query query = newQuery(ItemArrayHolder.class);
        Query itemQuery = query.descend("child");
        itemQuery.descend("name").constrain("one");
        ObjectSet objectSet = itemQuery.execute();
        Assert.areEqual(2, objectSet.size());
    }
    
    

}
