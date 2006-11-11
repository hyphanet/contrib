/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: CleanerTestUtils.java,v 1.6 2006/09/12 19:17:13 cwl Exp $
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
