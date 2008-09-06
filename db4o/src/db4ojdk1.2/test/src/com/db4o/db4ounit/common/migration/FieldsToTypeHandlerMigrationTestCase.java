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
package com.db4o.db4ounit.common.migration;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.io.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;

import db4ounit.*;

public class FieldsToTypeHandlerMigrationTestCase implements TestLifeCycle{
	
	public static class Item {
		
		public Item(int id) {
			_id = id;
		}

		public int _id;
		
	}
	
	private String _fileName;
	
	ItemTypeHandler _typeHandler;
	
	public static class ItemTypeHandler implements TypeHandler4 , FirstClassHandler, VariableLengthTypeHandler{
		
		private int _writeCalls;
		
		private int _readCalls;

		public void defragment(DefragmentContext context) {
			throw new NotImplementedException();
		}

		public void delete(DeleteContext context) throws Db4oIOException {
			throw new NotImplementedException();
		}

		public Object read(ReadContext context) {
			_readCalls ++;
			Item item = (Item) ((UnmarshallingContext) context).persistentObject();
			item._id = context.readInt() - 42;
			return item;
		}

		public void write(WriteContext context, Object obj) {
			_writeCalls ++;
			Item item = (Item) obj;
			context.writeInt(item._id + 42);
		}

		public PreparedComparison prepareComparison(Context context, Object obj) {
			throw new NotImplementedException();
		}

		public void cascadeActivation(ActivationContext4 context) {
			throw new NotImplementedException();
			
		}

		public void collectIDs(QueryingReadContext context) {
			throw new NotImplementedException();
		}

		public TypeHandler4 readCandidateHandler(QueryingReadContext context) {
			throw new NotImplementedException();
		}

		public int writeCalls() {
			return _writeCalls;
		}

		public int readCalls() {
			return _readCalls;
		}

		public void reset() {
			_writeCalls = 0;
			_readCalls = 0;
		}
		
	}
	
	

	public void setUp() throws Exception {
		_fileName = Path4.getTempFileName();
		File4.delete(_fileName);
	}

	public void tearDown() throws Exception {
		File4.delete(_fileName);
	}
	
	public void testMigration(){
		_typeHandler = null;
		store(new Item(42));
		
		Item item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		
		assertItemStoredField(new Integer(42));
		
		_typeHandler = new ItemTypeHandler();
		
		item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTypeHandlerCalls(0, 0);
		
		assertItemStoredField(new Integer(42));
		
		updateItem();
		assertTypeHandlerCalls(1, 0);
		
		assertItemStoredField(null);
		
		item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTypeHandlerCalls(0, 1);
		
		assertItemStoredField(null);

	}
	
	public void testTypeHandler(){
		_typeHandler = new ItemTypeHandler();
		
		store(new Item(42));
		assertTypeHandlerCalls(1, 0);
		
		Item item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTypeHandlerCalls(0, 1);
		
		updateItem();
		assertTypeHandlerCalls(1, 1);
		
	}
	
	private void assertItemStoredField(Object expectedValue){
		ObjectContainer db = openContainer();
		try{
			ObjectSet objectSet = db.query(Item.class);
			Assert.areEqual(1, objectSet.size());
			Item item = (Item) objectSet.next();
			StoredField storedField = db.ext().storedClass(Item.class).storedField("_id", null);
			Object actualValue = storedField.get(item);
			Assert.areEqual(expectedValue, actualValue);
		} finally {
			db.close();
		}
	}
	
	
	private void assertTypeHandlerCalls(int writeCalls, int readCalls){
		Assert.areEqual(writeCalls, _typeHandler.writeCalls());
		Assert.areEqual(readCalls, _typeHandler.readCalls());
	}

	private Item retrieveOnlyItemInstance() {
		ObjectContainer db = openContainer();
		try{
			ObjectSet objectSet = db.query(Item.class);
			Assert.areEqual(1, objectSet.size());
			Item item = (Item) objectSet.next();
			return item;
		} finally {
			db.close();
		}
	}

	private void store(Item item) {
		ObjectContainer db = openContainer();
		try{
			db.store(item);
		} finally {
			db.close();
		}
	}
	
	private void updateItem() {
		ObjectContainer db = openContainer();
		try {
		ObjectSet objectSet = db.query(Item.class);
		db.store(objectSet.next());
		} finally {
			db.close();
		}
	}

	private ObjectContainer openContainer() {
		if(_typeHandler != null){
			_typeHandler.reset();
		}
		Configuration configuration = Db4o.newConfiguration();
		if(_typeHandler != null){
			configuration.registerTypeHandler(new SingleClassTypeHandlerPredicate(Item.class), _typeHandler);
		}
		ObjectContainer db = Db4o.openFile(configuration, _fileName);
		return db;
	}

	public void defragment(DefragmentContext context) {
		// TODO Auto-generated method stub
		
	}

	public void delete(DeleteContext context) throws Db4oIOException {
		// TODO Auto-generated method stub
		
	}

	public Object read(ReadContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	public void write(WriteContext context, Object obj) {
		// TODO Auto-generated method stub
		
	}

	public PreparedComparison prepareComparison(Context context, Object obj) {
		// TODO Auto-generated method stub
		return null;
	}

	


}
