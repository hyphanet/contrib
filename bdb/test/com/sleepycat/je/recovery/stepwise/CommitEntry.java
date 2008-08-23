/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: CommitEntry.java,v 1.8 2008/06/30 20:54:49 linda Exp $
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

    @Override
    public void updateExpectedSet
        (Set<TestData>  useExpected,
         Map<Long, Set<TestData>> newUncommittedRecords,
         Map<Long, Set<TestData>> deletedUncommittedRecords) {

        Long mapKey = new Long(txnId);

        /* Add any new records to the expected set. */
        Set<TestData> records = newUncommittedRecords.get(mapKey);
        if (records != null) {
            Iterator<TestData> iter = records.iterator();
            while (iter.hasNext()) {
                useExpected.add(iter.next());
            }
        }

        /* Remove any deleted records from expected set. */
        records = deletedUncommittedRecords.get(mapKey);
        if (records != null) {
            Iterator<TestData> iter = records.iterator();
            while (iter.hasNext()) {
                useExpected.remove(iter.next());
            }
        }
    }
}
