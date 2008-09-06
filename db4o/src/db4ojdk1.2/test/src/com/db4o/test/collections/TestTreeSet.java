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
package com.db4o.test.collections;

import java.util.*;

import com.db4o.*;
import com.db4o.test.*;



public class TestTreeSet {
    
    private static final String[] CONTENT = new String[]{
        "a","f","d","c","b"
    };
    
    SortedSet stringTreeSet;
    
    SortedSet objectTreeSet;
    
    
    public void storeOne(){
        stringTreeSet = new TreeSet();
        stringContentTo(stringTreeSet);
        
        objectTreeSet = new TreeSet();
        objectContentTo(objectTreeSet);
    }
    
    
    public void testOne(){
        
        TreeSet stringCompareTo = new TreeSet();
        stringContentTo(stringCompareTo);
        
        TreeSet objectCompareTo = new TreeSet();
        objectContentTo(objectCompareTo);
        
        Test.ensure(stringTreeSet instanceof TreeSet);
        Test.ensure(stringTreeSet.size() == stringCompareTo.size());
        
        Test.ensure(objectTreeSet instanceof TreeSet);
        Test.ensure(objectTreeSet.size() == objectCompareTo.size());
        
        Iterator i = stringTreeSet.iterator();
        Iterator j = stringCompareTo.iterator();
        while(i.hasNext()){
            Test.ensure(i.next().equals(j.next()));
        }
        i = objectTreeSet.iterator();
        j = objectCompareTo.iterator();
        while(i.hasNext()){
            Test.ensure(i.next().equals(j.next()));
        }
        
    }
    
    private void stringContentTo(SortedSet set){
        for (int i = 0; i < CONTENT.length; i++) {
            set.add(CONTENT[i]);
        }
    }
    
    private void objectContentTo(SortedSet set){
        for (int i = 0; i < CONTENT.length; i++) {
            set.add(new ComparableContent(CONTENT[i]));
        }
    }

    
    
    

}
