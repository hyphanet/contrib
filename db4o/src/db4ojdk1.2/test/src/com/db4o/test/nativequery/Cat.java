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
package com.db4o.test.nativequery;

import com.db4o.*;
import com.db4o.query.*;
import com.db4o.test.*;


public class Cat {
    
    public String name;
    
    public Cat(){
        
    }
    
    public Cat(String name){
        this.name = name;
    }
    
    public void store(){
        Test.store(new Cat("Fritz"));
        Test.store(new Cat("Garfield"));
        Test.store(new Cat("Tom"));
        Test.store(new Cat("Occam"));
        Test.store(new Cat("Zora"));
    }
    
    public void test(){
        ObjectContainer objectContainer = Test.objectContainer();
        ObjectSet objectSet = objectContainer.query(new Predicate(){
            public boolean match(Cat cat){
                return cat.name.equals("Occam") || cat.name.equals("Zora"); 
            }
        });
        Test.ensure(objectSet.size() == 2);
        String[] lookingFor = new String[] {"Occam" , "Zora"};
        boolean[] found = new boolean[2];
        while(objectSet.hasNext()){
            Cat cat = (Cat)objectSet.next();
            for (int i = 0; i < lookingFor.length; i++) {
                if(cat.name.equals(lookingFor[i])){
                    found[i] = true;
                }
            }
        }
        for (int i = 0; i < found.length; i++) {
            Test.ensure(found[i]);
        }
    }
    
    

}
