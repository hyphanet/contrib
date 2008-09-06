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
package com.db4o.db4ounit.jre12.ta.collections;

import java.util.*;

import com.db4o.db4ounit.common.ta.*;
import com.db4o.db4ounit.common.ta.nonta.*;
import com.db4o.db4ounit.common.ta.ta.*;

import db4ounit.*;

public class TAMapTestCase extends ItemTestCaseBase {

    public static void main(String[] args) {
        new TAMapTestCase().runAll();
    }
    
    protected void assertItemValue(Object obj) throws Exception {
        HashMap item = (HashMap) obj;
        IntItem intItem = (IntItem) item.get(IntItem.class.getName());
        TAIntItem taIntItem = (TAIntItem) item.get(TAIntItem.class.getName());
        
        Assert.areEqual(100, intItem.value());
        Assert.areEqual(new Integer(200), intItem.integerValue());
        Assert.areEqual(new Integer(300), intItem.object());

        Assert.areEqual(100, taIntItem.value());
        Assert.areEqual(new Integer(200), taIntItem.integerValue());
        Assert.areEqual(new Integer(300), taIntItem.object());
    }

    protected void assertRetrievedItem(Object obj) throws Exception {
        HashMap item = (HashMap) obj;
        IntItem intItem = (IntItem) item.get(IntItem.class.getName());
        TAIntItem taIntItem = (TAIntItem) item.get(TAIntItem.class.getName());
        
        Assert.isNotNull(intItem);
        Assert.isNotNull(taIntItem);
        
        Assert.areEqual(100, intItem.value);
        Assert.areEqual(new Integer(200), intItem.i);
        Assert.areEqual(new Integer(300), intItem.obj);

        Assert.areEqual(0, taIntItem.value);
        isPrimitiveNull(taIntItem.i);
        Assert.isNull(taIntItem.obj);
    }

    protected Object createItem() throws Exception {
        IntItem intItem = new IntItem();
        intItem.value = 100;
        intItem.i = new Integer(200);
        intItem.obj = new Integer(300);
        
        TAIntItem taIntItem = new TAIntItem();
        taIntItem.value = 100;
        taIntItem.i = new Integer(200);
        taIntItem.obj = new Integer(300);
        
        HashMap item = new HashMap();
        item.put(intItem.getClass().getName(), intItem);
        item.put(taIntItem.getClass().getName(), taIntItem);
        return item;
    }

}
