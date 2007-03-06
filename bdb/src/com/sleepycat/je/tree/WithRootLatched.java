/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: WithRootLatched.java,v 1.12 2006/10/30 21:14:26 bostic Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.DatabaseException;

public interface WithRootLatched {
    
    /**
     * doWork is called while the tree's root latch is held.
     */
    public IN doWork(ChildReference root)
	throws DatabaseException;
}
