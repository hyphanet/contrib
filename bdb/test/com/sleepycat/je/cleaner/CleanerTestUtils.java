/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: CleanerTestUtils.java,v 1.14 2008/03/27 17:06:38 linda Exp $
 */

package com.sleepycat.je.cleaner;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Package utilities.
 */
public class CleanerTestUtils {

    /**
     * Gets the file of the LSN at the cursor position, using internal methods.
     */
    static long getLogFile(TestCase test, Cursor cursor)
        throws DatabaseException {

        CursorImpl impl = DbTestProxy.dbcGetCursorImpl(cursor);
        int index;
        BIN bin = impl.getDupBIN();
        if (bin != null) {
            index = impl.getDupIndex();
        } else {
            bin = impl.getBIN();
            TestCase.assertNotNull(bin);
            index = impl.getIndex();
        }
        TestCase.assertNotNull(bin.getTarget(index));
        long lsn = bin.getLsn(index);
        TestCase.assertTrue(lsn != DbLsn.NULL_LSN);
        long file = DbLsn.getFileNumber(lsn);
        return file;
    }
}
