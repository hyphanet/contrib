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
package com.db4o.db4ounit.jre12.collections;

import java.util.*;

import db4ounit.*;
import db4ounit.extensions.*;


/**
 * @exclude
 */
public class NestedListTestCase extends AbstractDb4oTestCase{
    
    public class Item {
        
        public List list;
        
        public boolean equals(Object obj) {
            if(! (obj instanceof Item)){
                return false;
            }
            Item otherItem = (Item) obj;
            if(list == null){
                return otherItem.list == null;
            }
            return list.equals(otherItem.list);
        }
        
    }
    
    protected void store() throws Exception {
        store(storedItem());
    }

    private Item storedItem() {
        Item item = new Item();
        item.list = newNestedList(10);
        return item;
    }
    
    private List newNestedList(int depth) {
        List list = new ArrayList();
        list.add("StringItem");
        if(depth > 0){
            list.add(newNestedList(depth - 1));
        }
        return list;
    }

    public void testNestedList(){
        Item item = (Item) retrieveOnlyInstance(Item.class);
        db().activate(item, Integer.MAX_VALUE);
        Assert.areEqual(storedItem(), item);
    }

}
