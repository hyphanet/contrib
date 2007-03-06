/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: TxnAbort.java,v 1.19 2006/10/30 21:14:27 bostic Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.log.LogEntryType;

/**
 * This class writes out a transaction commit or transaction end record.
 */
public class TxnAbort extends TxnEnd {
    public TxnAbort(long id, long lastLsn) {
        super(id, lastLsn);
    }
    
    /**
     * For constructing from the log.
     */
    public TxnAbort() {
    }

    /*
     * Log support
     */

    /**
     * @see TxnEnd#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_TXN_ABORT;
    }

    protected String getTagName() {
        return "TxnAbort";
    }
}
