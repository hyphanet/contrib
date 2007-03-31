/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbCursorDuplicateValidationTest.java,v 1.21.2.1 2007/02/01 14:50:09 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.util.Enumeration;
import java.util.Hashtable;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DBIN;

public class DbCursorDuplicateValidationTest extends DbCursorTestBase {

    public DbCursorDuplicateValidationTest() 
        throws DatabaseException {

        super();
    }

    public void testValidateCursors() 
	throws Throwable {

        initEnv(true);
	Hashtable dataMap = new Hashtable();
	createRandomDuplicateData(10, 1000, dataMap, false, false);

	Hashtable bins = new Hashtable();

	DataWalker dw = new DataWalker(bins) {
		void perData(String foundKey, String foundData)
		    throws DatabaseException {
                    CursorImpl cursorImpl = DbTestProxy.dbcGetCursorImpl(cursor);
		    BIN lastBin = cursorImpl.getBIN();
		    DBIN lastDupBin = cursorImpl.getDupBIN();
		    if (rnd.nextInt(10) < 8) {
			cursor.delete();
		    }
                    dataMap.put(lastBin, lastBin);
                    dataMap.put(lastDupBin, lastDupBin);
		}
	    };
	dw.setIgnoreDataMap(true);
	dw.walkData();
	dw.close();
	Enumeration e = bins.keys();
	while (e.hasMoreElements()) {
	    BIN b = (BIN) e.nextElement();
	    assertFalse(b.getCursorSet().size() > 0);
	}
    }
}
