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
package com.db4o.db4ounit.common.io;

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.io.*;
import com.db4o.io.*;

import db4ounit.*;

public abstract class DiskFullTestCaseBase implements TestLifeCycle {

	protected static final String FILENAME = Path4.getTempFileName();

	protected abstract ThrowCondition createThrowCondition(Object conditionConfig);

	protected abstract void configureForFailure(ThrowCondition condition);

	private ObjectContainer _db;
	private ThrowCondition _throwCondition;

	public DiskFullTestCaseBase() {
		super();
	}

	public void setUp() throws Exception {
		new File(FILENAME).delete();
	}

	public void tearDown() throws Exception {
		if(_db != null) {
			_db.close();
			_db = null;
		}
		new File(FILENAME).delete();
	}

	protected void storeOneAndFail(Object conditionConfig, boolean doCache) {
		openDatabase(conditionConfig, false, doCache);
		_db.store(new Item(42));
		_db.commit();
		triggerDiskFullAndClose();
	}

	protected void storeNAndFail(Object conditionConfig, int numObjects, int commitInterval, boolean doCache) {
		openDatabase(conditionConfig, false, doCache);
		for(int objIdx = 0; objIdx < numObjects; objIdx++) {
			_db.store(new Item(objIdx));
			if(objIdx % commitInterval == commitInterval - 1) {
				_db.commit();
			}
		}
		triggerDiskFullAndClose();
	}

	protected void assertItemsStored(int numItems, Object conditionConfig, boolean readOnly) {
		Assert.isNull(_db);
		openDatabase(conditionConfig, readOnly, false);
		Assert.areEqual(numItems, _db.query(Item.class).size());
		closeDb();
	}

	protected void triggerDiskFullAndClose() {
		configureForFailure(_throwCondition);
		Assert.expect(Db4oIOException.class, new CodeBlock() {
			public void run() throws Throwable {
				_db.store(new Item(42));
				_db.commit();
			}
		});
		Assert.expect(Db4oIOException.class, new CodeBlock() {
			public void run() throws Throwable {
				closeDb();			
			}
		});
	}

	public void openDatabase(Object conditionConfig, boolean readOnly, boolean doCache) {
		Configuration config = Db4o.newConfiguration();
		_throwCondition = createThrowCondition(conditionConfig);
		config.freespace().discardSmallerThan(Integer.MAX_VALUE);
		config.readOnly(readOnly);
		configureIoAdapter(config, _throwCondition, doCache);
		_db = Db4o.openFile(config, FILENAME);
	}

	private void configureIoAdapter(Configuration config, ThrowCondition throwCondition, boolean doCache) {
		IoAdapter ioAdapter = new RandomAccessFileAdapter();
		ioAdapter = new ThrowingIoAdapter(ioAdapter, throwCondition);
		if(doCache) {
			ioAdapter = new CachedIoAdapter(ioAdapter, 256, 2);
		}
		config.io(ioAdapter);
	}

	protected void closeDb() {
		try {
			_db.close();
		}
		finally {
			_db = null;
		}
	}

}