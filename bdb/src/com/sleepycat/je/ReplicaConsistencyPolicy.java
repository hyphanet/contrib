/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ReplicaConsistencyPolicy.java,v 1.3 2008/06/27 18:30:28 linda Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.ReplicatorInstance;

/**
 * @hidden
 * Feature not yet available.
 *
 * The interface for Consistency policies used to provide consistency
 * guarantees at a Replica. A transaction initiated at a replica will wait in
 * the Environment.beginTransaction method until the required consistency
 * policy is satisfied.
 */
public interface ReplicaConsistencyPolicy {

    /**
     * Ensures that the replica is within the constraints specified by this
     * policy. If it isn't the method waits until the constraint is satisfied
     * by the replica.
     */
    public void ensureConsistency(ReplicatorInstance repInstance)
        throws InterruptedException, DatabaseException;
}
