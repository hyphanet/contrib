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

import com.db4o.*;
import com.db4o.db4ounit.common.sampledata.*;
import com.db4o.ext.*;
import com.db4o.query.*;

import db4ounit.*;
import db4ounit.extensions.*;

public class Db4oLinkedListTestCase extends AbstractDb4oTestCase {
    
    public static class Db4oLinkedListHelper{
        public Db4oLinkedListHelper i_child;
        public List i_childList;
        
    }
    
    static final int COUNT = 10;

    static class Data {
	    List i_list;
	    Db4oLinkedListHelper i_helper;
	    List i_subList;
    }
    
	/**
	 * @deprecated using deprecated api
	 */
    private List createList() {
    	return db().collections().newLinkedList();
    }
    
    protected void store(){
    	Data data=new Data();
        data.i_list = createList();
        setDefaultValues(data);
        data.i_helper = helper(10);
        store(data);
    }
    
    private Db4oLinkedListHelper helper(int a_depth){
        if(a_depth > 0){
            Db4oLinkedListHelper helper = new Db4oLinkedListHelper();
            helper.i_childList = createList();
            helper.i_childList.add("hi");
            helper.i_child = helper(a_depth - 1);
            return helper;
        }
        return null;
    }
    
    private void setDefaultValues(Data data){
        data.i_list.add(new AtomData("wow"));
        data.i_list.add(new AtomData("cool"));
        data.i_list.add(new AtomData("great"));
    }
    
    public void test() throws Exception{
        Data data=(Data) retrieveOnlyInstance(Data.class);
        checkHelper(data.i_helper);
        runElementTest(data,true);

        reopen();
        
       restoreMembers(data);
       checkHelper(data.i_helper);
       runElementTest(data,false);

    }
    
    
    private void runElementTest(Data data,boolean onOriginal) throws Exception{
        
        
        List otherList = new ArrayList();
        Iterator i = data.i_list.iterator();
        AtomData atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        otherList.add(atom);
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        otherList.add(atom);
        atom = (AtomData)i.next();
        Assert.areEqual("great",atom.name);
        otherList.add(atom);
        Assert.areEqual(3,data.i_list.size());
        Assert.isFalse(data.i_list.isEmpty());
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        Assert.areEqual("great",((AtomData)data.i_list.get(data.i_list.size() - 1)).name);
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        
        if(onOriginal){
	        Query q = newQuery();
	        Data template = new Data();
	        template.i_list = createList();
	        template.i_list.add(new AtomData("cool"));
	        q.constrain(template);
	        ObjectSet qResult = q.execute();
	        Assert.areEqual(1,qResult.size());
	        Assert.areEqual(data,qResult.next());
        }
        
        
        Assert.isTrue(data.i_list.containsAll(otherList));
        
        otherList.clear();
        
        Object[] arr = data.i_list.toArray();
        Assert.areEqual(3,arr.length);
        atom = (AtomData)arr[0];
        Assert.areEqual("wow",atom.name);
        atom = (AtomData)arr[1];
        Assert.areEqual("cool",atom.name);
        atom = (AtomData)arr[2];
        Assert.areEqual("great",atom.name);
        
        
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("great",atom.name);
        Assert.areEqual(3,data.i_list.size());
        Assert.isFalse(i.hasNext());
        
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        Assert.isFalse(data.i_list.isEmpty());
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        data.i_list.add(new AtomData("yup"));
        Assert.areEqual(4,data.i_list.size());
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        AtomData toRemove = (AtomData)i.next();
        Assert.areEqual("great",toRemove.name);
        atom = (AtomData)i.next();
        Assert.areEqual("yup",atom.name);
        Assert.isFalse(i.hasNext());
        
        Assert.isTrue(data.i_list.remove(toRemove));
        Assert.isFalse(data.i_list.remove(toRemove));
        
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        otherList.add(atom);
        atom = (AtomData)i.next();
        Assert.areEqual("yup",atom.name);
        otherList.add(atom);
        Assert.areEqual(3,data.i_list.size());
        
        Assert.isTrue(data.i_list.removeAll(otherList));
        Assert.isFalse(data.i_list.removeAll(otherList));
        Assert.areEqual(1,data.i_list.size());
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        Assert.isFalse(i.hasNext());
        
        data.i_list.addAll(otherList);
        Assert.areEqual(3,data.i_list.size());
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        atom = (AtomData)i.next();
        Assert.areEqual("yup",atom.name);
        Assert.isFalse(i.hasNext());
        
        
        AtomData[] atarr = new AtomData[1];
        atarr = (AtomData[])data.i_list.toArray(atarr);
        Assert.areEqual("wow",atarr[0].name);
        Assert.areEqual("cool",atarr[1].name);
        Assert.areEqual("yup",atarr[2].name);
        
        Assert.isTrue(data.i_list.removeAll(otherList));
        
        data.i_list.addAll(0, otherList);
        i = data.i_list.iterator();
        atom = (AtomData)i.next();
        Assert.areEqual("cool",atom.name);
        i.remove();
        atom = (AtomData)i.next();
        Assert.areEqual("yup",atom.name);
        i.remove();
        atom = (AtomData)i.next();
        Assert.areEqual("wow",atom.name);
        Assert.isFalse(i.hasNext());
        Assert.areEqual(1,data.i_list.size());
        
        for (int j = 0; j < COUNT; j++) {
            data.i_list.add("more and more " + j);
        }
        Assert.areEqual(COUNT + 1,data.i_list.size());
        lookupLast(data);
        
        db().deactivate(data.i_list, Integer.MAX_VALUE);
        lookupLast(data);
        
        reopen();
        restoreMembers(data);
        
        lookupLast(data);
        
        String str = (String)data.i_list.set(10, new AtomData("yo"));
        Assert.areEqual("more and more 9",str);
        
        atom = (AtomData)data.i_list.remove(10);
        Assert.areEqual("yo",atom.name);
        
        data.i_list.add(5, new AtomData("sure"));
        Assert.areEqual(COUNT+1,data.i_list.size());
        atom = (AtomData)data.i_list.remove(5);
        Assert.areEqual("sure",atom.name);
        
        data.i_list.add(0, new AtomData("sure"));
        Assert.areEqual("sure",((AtomData)data.i_list.get(0)).name);
        Assert.areEqual(COUNT + 1,data.i_list.size());
        data.i_list.add(data.i_list.size(), new AtomData("sure"));
        Assert.areEqual(COUNT + 2,data.i_list.size());
        Assert.areEqual("sure",((AtomData)data.i_list.get(data.i_list.size() - 1)).name);
        
        atom = (AtomData)data.i_list.set(0, "huh");
        Assert.areEqual("sure",atom.name);
        Assert.areEqual(COUNT + 2,data.i_list.size());
        
        data.i_list.clear();
        Assert.areEqual(0,data.i_list.size());
        setDefaultValues(data);
    }
    
    private void restoreMembers(Data data){
        Query q = newQuery();
        q.constrain(Data.class);
        ObjectSet objectSet = q.execute();
        Data dll = (Data)objectSet.next();
        data.i_list = dll.i_list;
        data.i_helper = dll.i_helper;
    }
    
    private void lookupLast(Data data){
        String str = (String)data.i_list.get(COUNT);
        Assert.areEqual("more and more " + (COUNT - 1),str);
    }
    
    void checkHelper(Db4oLinkedListHelper helper){
        ExtObjectContainer con = db();
        if(con.isActive(helper)){
            Assert.areEqual("hi",helper.i_childList.get(0));
            checkHelper(helper.i_child);
        }
    }
}
