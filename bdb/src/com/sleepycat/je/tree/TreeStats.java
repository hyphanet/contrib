/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: TreeStats.java,v 1.11 2006/09/12 19:16:57 cwl Exp $
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
