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
import db4ounit.extensions.fixtures.*;

public class CollectionActivationTestCase
	extends AbstractDb4oTestCase
	implements OptOutDefragSolo, OptOutTA {
	
	public static final class Item {    
		public Item(List list) {
			this.list = list;
		}

		List list;
	}
	
	public static class CollectionActivationElement {
        public String name;

        public CollectionActivationElement(){}

        public CollectionActivationElement(String name){
            this.name = name;
        }
    }
	
	private long _elementId;
	
	/**
	 * @deprecated using deprecated apis
	 */
    protected void store() {
        Item item = new Item(db().collections().newLinkedList());        
        item.list.add(storeElement());
        store(item);
    }

	private CollectionActivationElement storeElement() {
		CollectionActivationElement cae = new CollectionActivationElement("test");
        store(cae);
        _elementId = db().getID(cae);
		return cae;
	}

    public void test() {
    	Item item = (Item)retrieveOnlyInstance(Item.class);
        db().activate(item, Integer.MAX_VALUE);
        
        CollectionActivationElement cae = (CollectionActivationElement)db().getByID(_elementId);
        Assert.isNull(cae.name);
        cae = (CollectionActivationElement)item.list.get(0);
        Assert.areEqual("test", cae.name);
    }
}
