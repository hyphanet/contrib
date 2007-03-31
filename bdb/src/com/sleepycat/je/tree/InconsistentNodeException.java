/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: InconsistentNodeException.java,v 1.14.2.1 2007/02/01 14:49:51 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.DatabaseException;

/**
 * Error to indicate that something is out of wack in the tree.
 */
public class InconsistentNodeException extends DatabaseException {
    public InconsistentNodeException() {
	super();
    }

    public InconsistentNodeException(String message) {
	super(message);
    }
}
