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
package com.db4o.db4ounit.jre5.collections.typehandler;

import java.util.*;

import com.db4o.config.*;
import com.db4o.typehandlers.*;

import db4ounit.extensions.*;


/**
 * @exclude
 */
public class ListTypeHandlerCascadedDeleteTestCase extends AbstractDb4oTestCase{

    /**
     * @param args
     */
    public static void main(String[] args) {
        new ListTypeHandlerCascadedDeleteTestCase().runSolo();
    }
    
    public static class Item{
        
        public Object _untypedList;
        
        public ArrayList _typedList;
        
    }
    
    public static class Element{
        
    }
    
    @Override
    protected void configure(Configuration config) throws Exception {
        config.objectClass(Item.class).cascadeOnDelete(true);
        config.objectClass(ArrayList.class).cascadeOnDelete(true);
        config.registerTypeHandler(
            new SingleClassTypeHandlerPredicate(ArrayList.class), 
            new ListTypeHandler());
    }
    
    @Override
    protected void store() throws Exception {
        Item item = new Item();
        item._untypedList = new ArrayList();
        ((List)item._untypedList).add(new Element());
        item._typedList = new ArrayList();
        item._typedList.add(new Element());
        store(item);
    }
    
    public void testCascadedDelete(){
        Item item = (Item) retrieveOnlyInstance(Item.class);
        Db4oAssert.persistedCount(2, Element.class);
        db().delete(item);
        db().purge();
        db().commit();
        Db4oAssert.persistedCount(0, Item.class);
        Db4oAssert.persistedCount(0, ArrayList.class);
        Db4oAssert.persistedCount(0, Element.class);
    }
    
    public void testArrayListCount(){
        Db4oAssert.persistedCount(2, ArrayList.class);
    }

}
