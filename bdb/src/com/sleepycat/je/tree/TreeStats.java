/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: TreeStats.java,v 1.12 2006/10/30 21:14:26 bostic Exp $
 */

package com.sleepycat.je.tree;

/**
 * A class that provides interesting stats about a particular tree.
 */
public final class TreeStats {

    /**
     * Number of times the root was split.
     */
    public int nRootSplits = 0;
}
