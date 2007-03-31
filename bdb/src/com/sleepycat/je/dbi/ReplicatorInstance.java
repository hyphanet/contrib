/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ReplicatorInstance.java,v 1.4.2.1 2007/02/01 14:49:44 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.utilint.VLSN;

/**
 * Replication functionality is available to the core JE code through this
 * interface. The replication packages use Java 1.5 features and this interface
 * lets us continue to support Java 1.4 for non-replicated environments.
 *
 * There should be no references to any classes from com.sleepycat.je.rep* 
 * except through this and other replication interfaces.
 */
public interface ReplicatorInstance {

    public void replicateOperation(Operation op, 
                                   ByteBuffer marshalledBuffer)
        throws DatabaseException;

    public VLSN bumpVLSN();
}
