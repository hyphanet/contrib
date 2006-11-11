/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *	Oracle Corporation.  All rights reserved.
 *
 * $Id: TestKeyAssigner.java,v 1.19 2006/09/12 19:17:10 cwl Exp $
 */

package com.sleepycat.collections.test;

import com.sleepycat.bind.RecordNumberBinding;
import com.sleepycat.collections.PrimaryKeyAssigner;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;

/**
 * @author Mark Hayes
 */
class TestKeyAssigner implements PrimaryKeyAssigner {

    private byte next = 1;
    private boolean isRecNum;

    TestKeyAssigner(boolean isRecNum) {

        this.isRecNum = isRecNum;
    }

    public void assignKey(DatabaseEntry keyData)
        throws DatabaseException {

        if (isRecNum) {
            RecordNumberBinding.recordNumberToEntry(next, keyData);
        } else {
            keyData.setData(new byte[] { next }, 0, 1);
        }
        next += 1;
    }

    void reset() {

        next = 1;
    }
}
