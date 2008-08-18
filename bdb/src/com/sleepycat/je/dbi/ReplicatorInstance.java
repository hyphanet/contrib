/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ReplicatorInstance.java,v 1.21 2008/05/13 20:03:11 sam Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.VLSN;

/**
 * The ReplicatorInstance is the sole conduit of replication functionality
 * available to the core JE code. All references to any classes from
 * com.sleepycat.je.rep* should be funnelled through this interface.
 *
 * Keeping a strict boundary serves to maintain the reliability of the
 * standalone node. All ReplicatorInstance methods are prohibited from
 * blocking, and should be examine carefully to determine whether they can
 * throw exceptions or have any side effects which would diminish the
 * reliability of the non-replication code paths.
 *
 * The ReplicatorInstance also allows us to package JE without the additional
 * replication classes.
 */
public interface ReplicatorInstance {

    /**
     * Record the vlsn->lsn mapping for this just-logged log entry. This method
     * is synchronized on the VLSNMap, and must be called outside the log write
     * latch.
     * @param lsn lsn of the target log entry
     * @param header of the target log entry, which contains the VLSN and
     * log entry type.
     */
    public void registerVLSN(long lsn, LogEntryHeader header);

    /**
     * Increment and get the next VLSN.
     */
    public VLSN bumpVLSN();

    /**
     * Decrement the vlsn if there was a problem logging the entry
     */
    public void decrementVLSN();

    /**
     * @return true if this node is the replication master.
     */
    public boolean isMaster();

    /**
     * Do any work that must be included as part of the checkpoint process.
     * @throws DatabaseException if any activity fails
     */
    public void preCheckpointEndFlush() throws DatabaseException;


    /**
     * Create an appropriate type of Replicated transaction. Specifically,
     * it creates a MasterTxn, if the node is currently a Master, a ReadonlyTxn
     * otherwise, that is, if the node is a Replica, or it's currently in a
     * DETACHED state.
     *
     * Note that a ReplicaTxn, used for transaction replay on a Replica is not
     * created on this path. It's created explicitly in the Replay loop by a
     * Replica.
     *
     * @param envImpl the environment associated with the transaction
     * @param config  the transaction configuration
     *
     * @return an instance of MasterTxn or ReadonlyTxn
     * @throws DatabaseException
     */
    public Txn createRepTxn(EnvironmentImpl envImpl,
                            TransactionConfig config)
        throws DatabaseException;

    /**
     * A form used primarily for auto commit transactions.
     *
     * @see com.sleepycat.je.txn.Txn
     * (com.sleepycat.je.dbi.EnvironmentImpl,
     *  com.sleepycat.je.TransactionConfig,
     *  boolean,
     *  long)
     *
     */
    public Txn createRepTxn(EnvironmentImpl envImpl,
                            TransactionConfig config,
                            boolean noAPIReadLock,
                            long mandatedId)
        throws DatabaseException;

    /**
     * A variation of the above used for testing; it arranges for a
     * ReplicationContext to be passed in for testing purposes.
     */
    public Txn createRepTxn(EnvironmentImpl envImpl,
                         TransactionConfig config,
                         ReplicationContext repContext)
        throws DatabaseException;



}
