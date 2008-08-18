/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: WriteLockInfo.java,v 1.19 2008/01/07 14:28:56 cwl Exp $
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.utilint.DbLsn;

/*
 * Lock and abort LSN kept for each write locked node. Allows us to log with
 * the correct abort LSN.
 */
public class WriteLockInfo {

    /*
     * The original LSN. This is stored in the LN log entry.  May be null if
     * the node was created by this transaction.
     */
    long abortLsn = DbLsn.NULL_LSN;

    /*
     * The original setting of the knownDeleted flag.  It parallels abortLsn.
     */
    boolean abortKnownDeleted;

    /*
     * Size of the original log entry, or zero if abortLsn is NULL_LSN or if
     * the size is not known.  Used for obsolete counting during a commit.
     */
    int abortLogSize;

    /*
     * The database of the node, or null if abortLsn is NULL_LSN.  Used for
     * obsolete counting during a commit.
     */
    DatabaseImpl abortDb;

    /*
     * True if the node has never been locked before. Used so we can determine
     * when to set abortLsn.
     */
    boolean neverLocked;

    /*
     * True if the node was created this transaction.
     */
    boolean createdThisTxn;

    static final WriteLockInfo basicWriteLockInfo =
	new WriteLockInfo();

    public // for Sizeof
    WriteLockInfo() {
	abortLsn = DbLsn.NULL_LSN;
	abortKnownDeleted = false;
	neverLocked = true;
	createdThisTxn = false;
    }

    public boolean getAbortKnownDeleted() {
	return abortKnownDeleted;
    }

    public long getAbortLsn() {
	return abortLsn;
    }

    public void setAbortInfo(DatabaseImpl db, int logSize) {
        abortDb = db;
        abortLogSize = logSize;
    }

    public void copyAbortInfo(WriteLockInfo fromInfo) {
        abortDb = fromInfo.abortDb;
        abortLogSize = fromInfo.abortLogSize;
    }
}
