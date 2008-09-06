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

/**
 * @exclude
 */
public class TranslatorToTypehandlerMigrationTestCase implements TestLifeCycle{
	
	public static class Item {
		
		public Item(int id) {
			_id = id;
		}

		public int _id;
		
	}
	
	private String _fileName;
	
	ItemTranslator _translator;
	
	ItemTypeHandler _typeHandler;
	
	
	
	public static class ItemTranslator implements ObjectTranslator{

		public void onActivate(ObjectContainer container,
				Object applicationObject, Object storedObject) {
			_activateCalls ++;
			String str = (String) storedObject;
			Item item = (Item) applicationObject;
			item._id = Integer.parseInt(str);
		}

		public Object onStore(ObjectContainer container, Object applicationObject) {
			_storeCalls++;
			Item item = (Item)applicationObject;
			return String.valueOf(item._id);
		}

		public Class storedClass() {
			return String.class;
		}
		
		private int _activateCalls;
		
		private int _storeCalls;
		
		public void reset(){
			_activateCalls = 0;
			_storeCalls = 0;
		}
		
		public int activateCalls(){
			return _activateCalls;
		}
		
		public int storeCalls(){
			return _storeCalls;
		}
		
	}
	
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
		_translator = new ItemTranslator();
	}

	public void tearDown() throws Exception {
		File4.delete(_fileName);
	}
	
	public void testMigration(){
		_typeHandler = null;
		_translator = new ItemTranslator();
		store(new Item(42));
		assertTranslatorCalls(1, 0);
		
		Item item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTranslatorCalls(0, 1);
		
		_typeHandler = new ItemTypeHandler();
		
		item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTranslatorCalls(0, 1);
		assertTypeHandlerCalls(0, 0);
		
		updateItem();
		assertTranslatorCalls(0, 1);
		assertTypeHandlerCalls(1, 0);
		
		item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTranslatorCalls(0, 0);
		assertTypeHandlerCalls(0, 1);

	}
	
	public void testTranslator(){
		_typeHandler = null;
		_translator = new ItemTranslator();
		
		store(new Item(42));
		assertTranslatorCalls(1, 0);
		
		Item item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTranslatorCalls(0, 1);
		
		updateItem();
		assertTranslatorCalls(1, 1);
		
	}
	
	public void testTypeHandler(){
		_translator = null;
		_typeHandler = new ItemTypeHandler();
		
		store(new Item(42));
		assertTypeHandlerCalls(1, 0);
		
		Item item = retrieveOnlyItemInstance();
		Assert.areEqual(42, item._id);
		assertTypeHandlerCalls(0, 1);
		
		updateItem();
		assertTypeHandlerCalls(1, 1);
		
	}
	
	
	private void assertTranslatorCalls(int storeCalls, int activateCalls){
		Assert.areEqual(storeCalls, _translator.storeCalls());
		Assert.areEqual(activateCalls, _translator.activateCalls());
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
		if(_translator != null){
			_translator.reset();
		}
		if(_typeHandler != null){
			_typeHandler.reset();
		}
		Configuration configuration = Db4o.newConfiguration();
		if(_translator != null){
			configuration.objectClass(Item.class).translate(_translator);
		}
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
