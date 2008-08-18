/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ReplicaConsistencyPolicy.java,v 1.1 2008/05/13 20:03:09 sam Exp $
 */

package com.sleepycat.je;

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

}
