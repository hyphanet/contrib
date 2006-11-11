/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DuplicateEntryException.java,v 1.12 2006/09/12 19:16:56 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.DatabaseException;

/**
 * Exception to indicate that an entry is already present in a node.
 */
public class DuplicateEntryException extends DatabaseException {
    public DuplicateEntryException() {
	super();
    }

    public DuplicateEntryException(String message) {
	super(message);
    }
}
