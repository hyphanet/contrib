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

import db4ounit.extensions.*;


public class Db4oHashMapDeletedKeyTestCase extends AbstractDb4oTestCase {

	public static class Data {
		public Map _map;
	}
	
	/**
	 * @deprecated using deprecated api
	 */
    protected void store(){
    	Data data=new Data();
        data._map = db().collections().newHashMap(1);
        // _map = Test.objectContainer().collections().newIdentityHashMap(1);
        data._map.put(new DHMDKey("key"), "value");
        store(data);
    }
    
    public void test() throws Exception{
        DHMDKey key = (DHMDKey) retrieveOnlyInstance(DHMDKey.class);
        db().delete(key);
        reopen();
    }
    
    public static class DHMDKey{
        
        public String _name;
        
        public DHMDKey(String name){
            _name = name;
        }
        
        public int hashCode() {
            return _name.hashCode();
        }
        
    }

}
