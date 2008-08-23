/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TxnAbort.java,v 1.26 2008/06/27 18:30:32 linda Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.log.Loggable;


/**
 * This class writes out a transaction commit or transaction end record.
 */
public class TxnAbort extends TxnEnd {
    public TxnAbort(long id, long lastLsn, int masterId) {
        super(id, lastLsn, masterId);
    }

    /**
     * For constructing from the log.
     */
    public TxnAbort() {
    }

    /*
     * Log support
     */

    protected String getTagName() {
        return "TxnAbort";
    }

    /**
     * @see Loggable#logicalEquals
     */
    public boolean logicalEquals(Loggable other) {

        if (!(other instanceof TxnAbort))
            return false;

        TxnAbort otherAbort = (TxnAbort) other;

        return ((id == otherAbort.id) && 
                (repMasterNodeId == otherAbort.repMasterNodeId));
    }
}
