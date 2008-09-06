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
package com.db4o.test.legacy;

import java.util.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.query.*;
import com.db4o.test.*;


/**
 * 
 */
public class Db4oLinkedList {
    
    public static class Db4oLinkedListHelper{
        public Db4oLinkedListHelper i_child;
        public List i_childList;
        
    }
    
    static final int COUNT = 10;
    
    List i_list;
    Db4oLinkedListHelper i_helper;
    List i_subList;
    
    public void storeOne(){
        i_list = Test.objectContainer().collections().newLinkedList();
        setDefaultValues();
        i_helper = helper(10);
    }
    
    private static Db4oLinkedListHelper helper(int a_depth){
        if(a_depth > 0){
            Db4oLinkedListHelper helper = new Db4oLinkedListHelper();
            helper.i_childList = Test.objectContainer().collections().newLinkedList();
            helper.i_childList.add("hi");
            helper.i_child = helper(a_depth - 1);
            return helper;
        }
        return null;
    }
    
    private void setDefaultValues(){
        i_list.add(new Atom("wow"));
        i_list.add(new Atom("cool"));
        i_list.add(new Atom("great"));
    }
    
    public void testOne(){
        
        checkHelper(i_helper);
        runElementTest(true);
        
        Test.defragment();
        
       restoreMembers();
       checkHelper(i_helper);
       runElementTest(false);

    }
    
    
    private void runElementTest(boolean onOriginal){
        
        
        List otherList = new ArrayList();
        Iterator i = i_list.iterator();
        Atom atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        otherList.add(atom);
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        otherList.add(atom);
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("great"));
        otherList.add(atom);
        Test.ensure(i_list.size() == 3);
        Test.ensure(i_list.isEmpty() == false);
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        Test.ensure(((Atom)i_list.get(i_list.size() - 1)).name.equals("great"));
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        
        if(onOriginal){
	        Query q = Test.query();
	        Db4oLinkedList template = new Db4oLinkedList();
	        template.i_list = Test.objectContainer().collections().newLinkedList();
	        template.i_list.add(new Atom("cool"));
	        q.constrain(template);
	        ObjectSet qResult = q.execute();
	        Test.ensure(qResult.size() == 1);
	        Test.ensure(qResult.next() == this);
        }
        
        
        Test.ensure(i_list.containsAll(otherList));
        
        otherList.clear();
        
        Object[] arr = i_list.toArray();
        Test.ensure(arr.length ==3);
        atom = (Atom)arr[0];
        Test.ensure(atom.name.equals("wow"));
        atom = (Atom)arr[1];
        Test.ensure(atom.name.equals("cool"));
        atom = (Atom)arr[2];
        Test.ensure(atom.name.equals("great"));
        
        
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("great"));
        Test.ensure(i_list.size() == 3);
        Test.ensure(! i.hasNext());
        
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        Test.ensure(i_list.isEmpty() == false);
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        i_list.add(new Atom("yup"));
        Test.ensure(i_list.size() == 4);
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        Atom toRemove = (Atom)i.next();
        Test.ensure(toRemove.name.equals("great"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("yup"));
        Test.ensure(! i.hasNext());
        
        Test.ensure(i_list.remove(toRemove));
        Test.ensure(! i_list.remove(toRemove));
        
        
        
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        otherList.add(atom);
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("yup"));
        otherList.add(atom);
        Test.ensure(i_list.size() == 3);
        
        Test.ensure(i_list.removeAll(otherList));
        Test.ensure(! i_list.removeAll(otherList));
        Test.ensure(i_list.size() == 1);
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        Test.ensure(! i.hasNext());
        
        i_list.addAll(otherList);
        Test.ensure(i_list.size() == 3);
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("yup"));
        Test.ensure(! i.hasNext());
        
        
        Atom[] atarr = new Atom[1];
        atarr = (Atom[])i_list.toArray(atarr);
        Test.ensure(atarr[0].name.equals("wow"));
        Test.ensure(atarr[1].name.equals("cool"));
        Test.ensure(atarr[2].name.equals("yup"));
        
        Test.ensure(i_list.removeAll(otherList));
        
        i_list.addAll(0, otherList);
        i = i_list.iterator();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("cool"));
        i.remove();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("yup"));
        i.remove();
        atom = (Atom)i.next();
        Test.ensure(atom.name.equals("wow"));
        Test.ensure(! i.hasNext());
        Test.ensure(i_list.size() == 1);
        
        long start = System.currentTimeMillis();
        
        for (int j = 0; j < COUNT; j++) {
            i_list.add("more and more " + j);
        }
        long stop = System.currentTimeMillis();
        // System.out.println("Time to add " + COUNT + " elements: " + (stop - start) + "ms");
        Test.ensure(i_list.size() == COUNT + 1);
        lookupLast();
        
        Test.objectContainer().deactivate(i_list, Integer.MAX_VALUE);
        lookupLast();
        
        Test.reOpen();
        restoreMembers();
        
        lookupLast();
        
        String str = (String)i_list.set(10, new Atom("yo"));
        Test.ensure(str.equals("more and more 9"));
        
        atom = (Atom)i_list.remove(10);
        Test.ensure(atom.name.equals("yo"));
        
        i_list.add(5, new Atom("sure"));
        Test.ensure(i_list.size() == COUNT + 1);
        atom = (Atom)i_list.remove(5);
        Test.ensure(atom.name.equals("sure"));
        
        i_list.add(0, new Atom("sure"));
        Test.ensure(((Atom)i_list.get(0)).name.equals("sure"));
        Test.ensure(i_list.size() == COUNT + 1);
        i_list.add(i_list.size(), new Atom("sure"));
        Test.ensure(i_list.size() == COUNT + 2);
        Test.ensure(((Atom)i_list.get(i_list.size() - 1)).name.equals("sure"));
        
        atom = (Atom)i_list.set(0, "huh");
        Test.ensure(atom.name.equals("sure"));
        Test.ensure(i_list.size() == COUNT + 2);
        
        i_list.clear();
        Test.ensure(i_list.size() == 0);
        setDefaultValues();
    }
    
    private void restoreMembers(){
        Query q = Test.query();
        q.constrain(this.getClass());
        ObjectSet objectSet = q.execute();
        Db4oLinkedList dll = (Db4oLinkedList)objectSet.next();
        i_list = dll.i_list;
        i_helper = dll.i_helper;
    }
    
    private void lookupLast(){
        long start = System.currentTimeMillis();
        String str = (String)i_list.get(COUNT);
        long stop = System.currentTimeMillis();
        // System.out.println("Time to look up element " + COUNT + ": " + (stop - start) + "ms");
        Test.ensure(str.equals("more and more " + (COUNT - 1)));
    }
    
    void checkHelper(Db4oLinkedListHelper helper){
        ExtObjectContainer con = Test.objectContainer();
        if(con.isActive(helper)){
            Test.ensure(helper.i_childList.get(0).equals("hi"));
            checkHelper(helper.i_child);
        }
    }
    
    private String currentFileName() {
        return Test.isClientServer()
            ? Test.FILE_SERVER
            : Test.FILE_SOLO;
    }
    
    private void close() {
        Test.close();
        if (Test.isClientServer()) {
            Test.server().close();
        }
    }
    
    private void reOpen() {
        Test.reOpenServer();
    }

    

}
