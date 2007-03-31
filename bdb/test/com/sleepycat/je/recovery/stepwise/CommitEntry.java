/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CommitEntry.java,v 1.4.2.1 2007/02/01 14:50:18 cwl Exp $
 */

package com.sleepycat.je.recovery.stepwise;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/*
 * A Commit entry signals that some records should be moved from the
 * not-yet-committed sets to the expected set. 
 *
 * Note that this uses key and data values rather than node ids to check
 * existence, so a test which re-used the same key and data values may
 * not work out correctly. This could be enhanced in the future to use
 * node ids.
 */

public class CommitEntry extends LogEntryInfo {
    private long txnId;

    CommitEntry(long lsn, long txnId) {
        super(lsn, 0, 0);
        this.txnId = txnId;
    }

    public void updateExpectedSet(Set useExpected,
                                  Map newUncommittedRecords,
                                  Map deletedUncommittedRecords) {

        Long mapKey = new Long(txnId);

        /* Add any new records to the expected set. */
        Set records = (Set) newUncommittedRecords.get(mapKey);
        if (records != null) {
            Iterator iter = records.iterator();
            while (iter.hasNext()) {
                useExpected.add((TestData) iter.next());
            }
        }

        /* Remove any deleted records from expected set. */
        records = (Set) deletedUncommittedRecords.get(mapKey);
        if (records != null) {
            Iterator iter = records.iterator();
            while (iter.hasNext()) {
                useExpected.remove((TestData) iter.next());
            }
        } 
    }
}
