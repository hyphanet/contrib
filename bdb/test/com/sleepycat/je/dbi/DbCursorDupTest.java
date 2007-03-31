/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbCursorDupTest.java,v 1.29.2.1 2007/02/01 14:50:09 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.IOException;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.StringDbt;

/**
 * Various unit tests for CursorImpl.dup().
 */
public class DbCursorDupTest extends DbCursorTestBase {

    public DbCursorDupTest() 
        throws DatabaseException {

        super();
    }

    public void testCursorDupAndCloseDb()
	throws DatabaseException {

        initEnv(false);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database myDb = exampleEnv.openDatabase(null, "fooDb", dbConfig);

	myDb.put(null, new StringDbt("blah"), new StringDbt("blort"));
	Cursor cursor = myDb.openCursor(null, null);
	OperationStatus status = cursor.getNext(new DatabaseEntry(),
                                                new DatabaseEntry(),
                                                LockMode.DEFAULT);
	Cursor cursorDup = cursor.dup(true);
	cursor.close();
	cursorDup.close();
	myDb.close();
    }

    public void testDupInitialized()
        throws DatabaseException {

        /* Open db. */
        initEnv(false);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database myDb = exampleEnv.openDatabase(null, "fooDb", dbConfig);

        /* Open uninitialized cursor. */
        Cursor c1 = myDb.openCursor(null, null);
        try {
            c1.getCurrent(new DatabaseEntry(), new DatabaseEntry(), null);
            fail();
        } catch (DatabaseException expected) {}

        /* Dup uninitialized cursor with samePosition=false. */
        Cursor c2 = c1.dup(false);
        try {
            c2.getCurrent(new DatabaseEntry(), new DatabaseEntry(), null);
            fail();
        } catch (DatabaseException expected) {}

        /* Dup uninitialized cursor with samePosition=true. */
        Cursor c3 = c1.dup(true);
        try {
            c3.getCurrent(new DatabaseEntry(), new DatabaseEntry(), null);
            fail();
        } catch (DatabaseException expected) {}

        /* Ensure dup'ed cursors are usable. */
        assertEquals(OperationStatus.SUCCESS,
                     c1.put(new DatabaseEntry(new byte[0]),
                            new DatabaseEntry(new byte[0])));
        assertEquals(OperationStatus.SUCCESS,
                     c2.getFirst(new DatabaseEntry(), new DatabaseEntry(),
                                 null));
        assertEquals(OperationStatus.NOTFOUND,
                     c2.getNext(new DatabaseEntry(), new DatabaseEntry(),
                                 null));
        assertEquals(OperationStatus.SUCCESS,
                     c3.getFirst(new DatabaseEntry(), new DatabaseEntry(),
                                 null));
        assertEquals(OperationStatus.NOTFOUND,
                     c3.getNext(new DatabaseEntry(), new DatabaseEntry(),
                                 null));

        /* Close db. */
        c3.close();
        c2.close();
        c1.close();
	myDb.close();
    }

    /**
     * Create some duplicate data.
     * 
     * Pass 1, walk over the data and with each iteration, dup() the
     * cursor at the same position.  Ensure that the dup points to the
     * same key/data pair.  Advance the dup'd cursor and ensure that
     * the data is different (key may be the same since it's a
     * duplicate set).  Then dup() the cursor without maintaining
     * position.  Ensure that getCurrent() throws a Cursor Not Init'd
     * exception.
     *
     * Pass 2, iterate through the data, and dup the cursor in the
     * same position.  Advance the original cursor and ensure that the
     * dup()'d points to the original data and the original cursor
     * points at new data.
     */
    public void testCursorDupSamePosition()
        throws IOException, DatabaseException {

        initEnv(true);
	createRandomDuplicateData(null, false);

	DataWalker dw = new DataWalker(null) {
		void perData(String foundKey, String foundData)
                    throws DatabaseException {
		    DatabaseEntry keyDbt = new DatabaseEntry();
		    DatabaseEntry dataDbt = new DatabaseEntry();
		    Cursor cursor2 = cursor.dup(true);
		    cursor2.getCurrent(keyDbt, dataDbt, LockMode.DEFAULT);
		    String c2Key = new String(keyDbt.getData());
		    String c2Data = new String(dataDbt.getData());
		    assertTrue(c2Key.equals(foundKey));
		    assertTrue(c2Data.equals(foundData));
		    if (cursor2.getNext(keyDbt,
					dataDbt,
					LockMode.DEFAULT) ==
                        OperationStatus.SUCCESS) {
			/* Keys can be the same because we have duplicates. */
			/*
			  assertFalse(new String(keyDbt.getData()).
			  equals(foundKey));
			*/
			assertFalse(new String(dataDbt.getData()).
				    equals(foundData));
		    }
		    cursor2.close();
		    try {
			cursor2 = cursor.dup(false);
			cursor2.getCurrent(keyDbt, dataDbt, LockMode.DEFAULT);
			fail("didn't catch Cursor not initialized exception");
		    } catch (DatabaseException DBE) {
		    }
		    cursor2.close();
		}
	    };
	dw.setIgnoreDataMap(true);
	dw.walkData();

	dw = new DataWalker(null) {
		void perData(String foundKey, String foundData)
                    throws DatabaseException {
		    DatabaseEntry keyDbt = new DatabaseEntry();
		    DatabaseEntry dataDbt = new DatabaseEntry();
		    DatabaseEntry key2Dbt = new DatabaseEntry();
		    DatabaseEntry data2Dbt = new DatabaseEntry();
		    Cursor cursor2 = cursor.dup(true);

		    OperationStatus status =
			cursor.getNext(keyDbt, dataDbt, LockMode.DEFAULT);

		    cursor2.getCurrent(key2Dbt, data2Dbt, LockMode.DEFAULT);
		    String c2Key = new String(key2Dbt.getData());
		    String c2Data = new String(data2Dbt.getData());
		    assertTrue(c2Key.equals(foundKey));
		    assertTrue(c2Data.equals(foundData));
		    if (status == OperationStatus.SUCCESS) {
			assertFalse(new String(dataDbt.getData()).
				    equals(foundData));
			assertFalse(new String(dataDbt.getData()).
				    equals(c2Data));
		    }
		    cursor2.close();
		}
	    };
	dw.setIgnoreDataMap(true);
	dw.walkData();
    }
}
