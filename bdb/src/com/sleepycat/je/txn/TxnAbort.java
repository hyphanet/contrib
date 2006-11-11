/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: TxnAbort.java,v 1.18 2006/09/12 19:16:58 cwl Exp $
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
