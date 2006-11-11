/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DeletedDupLNLogEntry.java,v 1.24 2006/09/12 19:16:52 cwl Exp $
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.txn.Txn;

/**
 * DupDeletedLNEntry encapsulates a deleted dupe LN entry. This contains all
 * the regular transactional LN log entry fields and an extra key, which is the
 * nulled out data field of the LN (which becomes the key in the duplicate
 * tree.
 */
public class DeletedDupLNLogEntry extends LNLogEntry {

    /* 
     * Deleted duplicate LN must log an entra key in their log entries,
     * because the data field that is the "key" in a dup tree has been
     * nulled out because the LN is deleted.
     */
    private byte[] dataAsKey;

    /**
     * Constructor to read an entry.
     */
    public DeletedDupLNLogEntry(boolean isTransactional) {
        super(com.sleepycat.je.tree.LN.class, isTransactional);
    }

    /**
     * Constructor to make an object that can write this entry.
     */
    public DeletedDupLNLogEntry(LogEntryType entryType,
                                LN ln,
                                DatabaseId dbId,
                                byte[] key,
                                byte[] dataAsKey,
                                long abortLsn,
                                boolean abortKnownDeleted,
                                Txn txn) {
        super(entryType, ln, dbId, key, abortLsn, abortKnownDeleted, txn);
        this.dataAsKey = dataAsKey;
    }

    /**
     * Extends its super class to read in the extra dup key.
     * @see LNLogEntry#readEntry
     */
    public void readEntry(ByteBuffer entryBuffer,
			  int entrySize,
                          byte entryTypeVersion,
			  boolean readFullItem)
        throws DatabaseException {

        super.readEntry(entryBuffer, entrySize,
                        entryTypeVersion, readFullItem);

        /* Key */
        if (readFullItem) {
            dataAsKey = LogUtils.readByteArray(entryBuffer);
        } else {
            /* The LNLogEntry base class has already positioned to the end. */
            dataAsKey = null;
        }
    }

    /**
     * Extends super class to dump out extra key.
     * @see LNLogEntry#dumpEntry
     */
    public StringBuffer dumpEntry(StringBuffer sb, boolean verbose) {
        super.dumpEntry(sb, verbose);
        sb.append(Key.dumpString(dataAsKey, 0));
        return sb;
    }
    
    /*
     * Writing support
     */

    /**
     * Extend super class to add in extra key.
     * @see LNLogEntry#getLogSize
     */
    public int getLogSize() {
        return super.getLogSize() +
	    LogUtils.getByteArrayLogSize(dataAsKey);
    }

    /**
     * @see LNLogEntry#writeToLog
     */
    public void writeToLog(ByteBuffer destBuffer) {
        super.writeToLog(destBuffer);
        LogUtils.writeByteArray(destBuffer, dataAsKey);
    }

    /*
     * Accessors
     */

    /**
     * Get the data-as-key out of the entry.
     */
    public byte[] getDupKey() {
        return dataAsKey;
    }
}
