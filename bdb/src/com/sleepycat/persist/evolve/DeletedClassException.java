/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DeletedClassException.java,v 1.9 2008/01/07 14:28:58 cwl Exp $
 */

package com.sleepycat.persist.evolve;


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
