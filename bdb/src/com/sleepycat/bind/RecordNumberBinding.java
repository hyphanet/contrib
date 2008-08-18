/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: RecordNumberBinding.java,v 1.34 2008/05/27 15:30:32 mark Exp $
 */

package com.sleepycat.bind;

import com.sleepycat.compat.DbCompat;
import com.sleepycat.je.DatabaseEntry;

/**
 * <!-- begin JE only -->
 * @hidden
 * <!-- end JE only -->
 * An <code>EntryBinding</code> that treats a record number key entry as a
 * <code>Long</code> key object.
 *
 * <p>Record numbers are returned as <code>Long</code> objects, although on
 * input any <code>Number</code> object may be used.</p>
 *
 * @author Mark Hayes
 */
public class RecordNumberBinding implements EntryBinding<Long> {

    /**
     * Creates a byte array binding.
     */
    public RecordNumberBinding() {
    }

    // javadoc is inherited
    public Long entryToObject(DatabaseEntry entry) {

        return Long.valueOf(entryToRecordNumber(entry));
    }

    // javadoc is inherited
    public void objectToEntry(Long object, DatabaseEntry entry) {

        recordNumberToEntry(object, entry);
    }

    /**
     * Utility method for use by bindings to translate a entry buffer to an
     * record number integer.
     *
     * @param entry the entry buffer.
     *
     * @return the record number.
     */
    public static long entryToRecordNumber(DatabaseEntry entry) {

        return DbCompat.getRecordNumber(entry) & 0xFFFFFFFFL;
    }

    /**
     * Utility method for use by bindings to translate a record number integer
     * to a entry buffer.
     *
     * @param recordNumber the record number.
     *
     * @param entry the entry buffer to hold the record number.
     */
    public static void recordNumberToEntry(long recordNumber,
                                           DatabaseEntry entry) {
        entry.setData(new byte[4], 0, 4);
        DbCompat.setRecordNumber(entry, (int) recordNumber);
    }
}
