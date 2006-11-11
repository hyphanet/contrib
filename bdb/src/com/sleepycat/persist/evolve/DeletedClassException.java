/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DeletedClassException.java,v 1.5 2006/09/21 13:35:57 mark Exp $
 */

package com.sleepycat.persist.evolve;

import com.sleepycat.je.DatabaseException;

/**
 * While reading from an index, an instance of a deleted class version was
 * encountered.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public class DeletedClassException extends RuntimeException {

    public DeletedClassException(String msg) {
        super(msg);
    }
}
