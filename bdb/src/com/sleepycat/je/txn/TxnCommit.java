/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TxnCommit.java,v 1.28 2008/06/27 18:30:32 linda Exp $
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

        TxnCommit otherCommit = (TxnCommit) other;

        return ((id == otherCommit.id) && 
                (repMasterNodeId == otherCommit.repMasterNodeId));
    }
}
