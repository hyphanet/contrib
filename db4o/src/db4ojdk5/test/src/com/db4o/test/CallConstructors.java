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
package com.db4o.test;

import java.util.*;

import com.db4o.*;
import com.db4o.query.*;



/**
 * 
 */
public class CallConstructors {
    
    static Hashtable constructorCalledByClass = new Hashtable();
    
    static void constructorCalled(Object obj){
        constructorCalledByClass.put(obj.getClass(), obj);
    }
    
    static Object[] cases = new Object[]{
        new CallGlobal(),
        new CallLocalYes(),
        new CallLocalNo()
    };
    
    public void configure(){
        Db4o.configure().callConstructors(false);
        Db4o.configure().objectClass(new CallLocalYes()).callConstructor(true);
        Db4o.configure().objectClass(new CallLocalNo()).callConstructor(false);
    }
    
    public void store(){
        for (int i = 0; i < cases.length; i++) {
            Test.store(cases[i]);
        }
    }
    
    public void test(){
        if(! Test.clientServer){
	        check(new CallLocalYes(), true);
	        check(new CallLocalNo(), false);
	        check(new CallGlobal(), false);
        }
        Db4o.configure().callConstructors(true);
        Test.reOpen();
        check(new CallLocalYes(), true);
        check(new CallLocalNo(), false);
        check(new CallGlobal(), true);
        Db4o.configure().callConstructors(false);
        Test.reOpen();
        check(new CallLocalYes(), true);
        check(new CallLocalNo(), false);
        check(new CallGlobal(), false);
        
    }
    
    private void check(Object obj, boolean expected){
        constructorCalledByClass.clear();
        Query q = Test.query();
        q.constrain(obj.getClass());
        ObjectSet os = q.execute();
        Test.ensure(os.hasNext());
        while(os.hasNext()){
            os.next();
        }
        boolean called = constructorCalledByClass.get(obj.getClass()) != null;
        Test.ensure(called == expected);
    }
    
    
    public static class CallCommonBase{
        public CallCommonBase(){
            constructorCalled(this);
        }
    }
    
    public static class CallGlobal extends CallCommonBase{
    }
    
    public static class CallLocalYes extends CallCommonBase{
    }
    
    public static class CallLocalNo extends CallCommonBase{
    }
}
