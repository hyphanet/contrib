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
package com.db4o.db4ounit.jre5.enums;

import java.util.*;

import com.db4o.*;
import com.db4o.query.Query;

import db4ounit.Assert;
import db4ounit.extensions.AbstractDb4oTestCase;

public class EnumTestCase extends AbstractDb4oTestCase {
	private final static int NUMRUNS=1;
    
    @SuppressWarnings("unchecked")
	public void testSingleStoreRetrieve() throws Exception {     	
        // We make sure the Jdk5Enum class is already loaded, otherwise
        // we may get the side effect that storing it will load the class
        // and overwrite our changes exactly when we store them. 
        db().store(TypeCountEnum.A);
        
        EnumHolder data=new EnumHolder(TypeCountEnum.A);
        TypeCountEnum.A.reset();
        Assert.areEqual(0, TypeCountEnum.A.getCount());
        TypeCountEnum.A.incCount();
        
        // The Jdk5Enum object may already be stored on the server, so we
        // can't persist by reachability. We have to store the object
        // explicitely.
        db().store(TypeCountEnum.A);
        
        Assert.areEqual(1, TypeCountEnum.A.getCount());
        Assert.areEqual(0, TypeCountEnum.B.getCount());
        Assert.areEqual(TypeCountEnum.A, data.getType());
        
        db().store(data);
        reopen();        
        data=null;
        
        Query query=db().query();
        query.constrain(EnumHolder.class);
        Query sub=query.descend("type");
        sub.constrain(TypeCountEnum.class);
        sub.constrain(TypeCountEnum.A);
        sub.descend("type").constrain("A");
        sub.descend("count").constrain(Integer.valueOf(1));

        ObjectSet<EnumHolder> result=(ObjectSet<EnumHolder>)query.execute();
        Assert.areEqual(1, result.size());
        data=(EnumHolder)result.next();
        Assert.areEqual(data.getType(), TypeCountEnum.A);
        Assert.areEqual(TypeCountEnum.A.name(), data.getType().name());
        Assert.areEqual(1, result.size());

        ensureEnumInstancesInDB(db());
    }

	private static class TypeCountEnumComparator implements Comparator<TypeCountEnum> {
		public int compare(TypeCountEnum e1, TypeCountEnum e2) {
			return e1.name().compareTo(e2.name());
		}
	}

	private static class CollectionHolder {
		public List<TypeCountEnum> list; 
		public List<TypeCountEnum> db4olist;
		public Set<TypeCountEnum> set; 
		public Map<TypeCountEnum,String> keymap; 
		public Map<String,TypeCountEnum> valmap; 
		public Map<TypeCountEnum,String> db4okeymap; 
		public Map<String,TypeCountEnum> db4ovalmap; 
		public TypeCountEnum[] array; 
	}
        
	/**
	 * @deprecated testing deprecated api
	 */
    @SuppressWarnings("unchecked")    
	public void testEnumsInCollections() throws Exception {
    	final boolean withDb4oCollections=true;

    	CollectionHolder holder=new CollectionHolder();
    	holder.list=new ArrayList<TypeCountEnum>(NUMRUNS);
    	Comparator<TypeCountEnum> comp=new TypeCountEnumComparator();
    	holder.set=new TreeSet<TypeCountEnum>(comp);
    	holder.keymap=new HashMap<TypeCountEnum,String>(NUMRUNS);
    	holder.valmap=new HashMap<String,TypeCountEnum>(NUMRUNS);
    	holder.array=new TypeCountEnum[NUMRUNS];
    	holder.db4olist=db().ext().collections().newLinkedList();
    	holder.db4okeymap=db().ext().collections().newHashMap(2);
    	holder.db4ovalmap=db().ext().collections().newHashMap(2);
    	for(int i=0;i<NUMRUNS;i++) {
    		TypeCountEnum curenum=nthEnum(i);
			holder.list.add(curenum);
    		if(withDb4oCollections) {
        		holder.db4olist.add(curenum);
    		}
    		holder.array[i]=curenum;
    	}
		holder.set.add(TypeCountEnum.A);
		holder.set.add(TypeCountEnum.B);
		holder.keymap.put(TypeCountEnum.A,TypeCountEnum.A.name());
		holder.keymap.put(TypeCountEnum.B,TypeCountEnum.B.name());
		holder.valmap.put(TypeCountEnum.A.name(),TypeCountEnum.A);
		holder.valmap.put(TypeCountEnum.B.name(),TypeCountEnum.B);	
		if(withDb4oCollections) {
			holder.db4okeymap.put(TypeCountEnum.A,TypeCountEnum.A.name());
			holder.db4okeymap.put(TypeCountEnum.B,TypeCountEnum.B.name());
			holder.db4ovalmap.put(TypeCountEnum.A.name(),TypeCountEnum.A);
			holder.db4ovalmap.put(TypeCountEnum.B.name(),TypeCountEnum.B);
		}
    	db().store(holder);
    	
    	reopen();
    	ObjectSet result=db().queryByExample(CollectionHolder.class);
    	Assert.areEqual(1, result.size());
    	holder=(CollectionHolder)result.next();

    	Assert.areEqual(NUMRUNS, holder.list.size());
    	Assert.areEqual(2, holder.set.size());
    	Assert.areEqual(2, holder.keymap.size());
    	Assert.areEqual(2, holder.valmap.size());
    	Assert.areEqual(NUMRUNS, holder.array.length);
    	if(withDb4oCollections) {
    		Assert.areEqual(NUMRUNS, holder.db4olist.size());
    		Assert.areEqual(2, holder.db4okeymap.size());
    		Assert.areEqual(2, holder.db4ovalmap.size());
    	}
    	ensureEnumInstancesInDB(db());
    }
    
	@SuppressWarnings("unchecked")
	private void ensureEnumInstancesInDB(ObjectContainer db) {
		Query query;
		ObjectSet<TypeCountEnum> result;
		query=db.query();
		query.constrain(TypeCountEnum.class);
		result=(ObjectSet<TypeCountEnum>)query.execute();
		// We should have all enum members once in the database, since they're
        // statically referenced by the Enum subclass.
		if(result.size()!=2) {
			System.err.println("# instances in db: "+result.size());
			while(result.hasNext()) {
				TypeCountEnum curenum=(TypeCountEnum)result.next();
                long id = db.ext().getID(curenum);
                System.err.println(curenum+"  :  ihc "+System.identityHashCode(curenum) + "  : id " + id);
			}
			
		}
        Assert.areEqual(2, result.size());
	}
	
	private TypeCountEnum nthEnum(int n) {
		return (n%2==0 ? TypeCountEnum.A : TypeCountEnum.B);
	}
}
