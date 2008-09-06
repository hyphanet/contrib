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

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.internal.*;
import com.db4o.query.*;
import com.db4o.typehandlers.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class NamedArrayListTypeHandlerTestCase extends AbstractDb4oTestCase{
    
    private static String NAME = "listname";
    
    private static Object[] DATA = new Object[] {
        "one", "two", new Integer(3), new Long(4), null  
    };
    
    protected void store() throws Exception {
        store(createNamedArrayList());
    }
    
    protected void configure(Configuration config) throws Exception {
    	config.registerTypeHandler(
	            new SingleClassTypeHandlerPredicate(ArrayList.class), 
	            new ListTypeHandler());
    }
    
    private NamedArrayList createNamedArrayList(){
        NamedArrayList namedArrayList = new NamedArrayList();
        namedArrayList.name = NAME;
        for (int i = 0; i < DATA.length; i++) {
            namedArrayList.add(DATA[i]); 
        }
        return namedArrayList;
    }
    
    private void assertRetrievedOK(NamedArrayList namedArrayList){
        Assert.areEqual(NAME, namedArrayList.name);
        Object[] listElements = new Object[namedArrayList.size()];
        int idx =  0;
        Iterator i = namedArrayList.iterator();
        while(i.hasNext()){
            listElements[idx++] = i.next();
        }
        ArrayAssert.areEqual(DATA, listElements);
    }
    
    
    public void testRetrieve(){
        NamedArrayList namedArrayList = (NamedArrayList) retrieveOnlyInstance(NamedArrayList.class);
        assertRetrievedOK(namedArrayList);
    }
    
    public void testQuery() {
        Query query = newQuery(NamedArrayList.class);
        query.descend("name").constrain(NAME);
        ObjectSet objectSet = query.execute();
        Assert.areEqual(1, objectSet.size());
        NamedArrayList namedArrayList = (NamedArrayList) objectSet.next();
        assertRetrievedOK(namedArrayList);
    }

}
