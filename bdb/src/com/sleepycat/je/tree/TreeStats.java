/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TreeStats.java,v 1.14 2008/01/07 14:28:56 cwl Exp $
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
