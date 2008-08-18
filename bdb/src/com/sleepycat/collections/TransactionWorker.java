/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: TransactionWorker.java,v 1.17 2008/01/07 14:28:45 cwl Exp $
 */

package com.sleepycat.collections;

/**
 * The interface implemented to perform the work within a transaction.
 * To run a transaction, an instance of this interface is passed to the
 * {@link TransactionRunner#run} method.
 *
 * @author Mark Hayes
 */
public interface TransactionWorker {

    /**
     * Perform the work for a single transaction.
     *
     * @see TransactionRunner#run
     */
    void doWork()
        throws Exception;
}
