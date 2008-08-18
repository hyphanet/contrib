/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TxnCommit.java,v 1.27 2008/05/13 01:57:01 linda Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.log.Loggable;


/**
 * This class writes out a transaction commit or transaction end record.
 */
public class TxnCommit extends TxnEnd {
    public TxnCommit(long id, long lastLsn, int masterId) {
        super(id, lastLsn, masterId);
    }

    /**
     * For constructing from the log.
     */
    public TxnCommit() {
    }

    /*
     * Log support
     */

    protected String getTagName() {
        return "TxnCommit";
    }

    /**
     * @see Loggable#logicalEquals
     */
    public boolean logicalEquals(Loggable other) {

        if (!(other instanceof TxnCommit))
            return false;

        return (id == ((TxnCommit) other).id);
    }
}
