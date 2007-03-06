/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: BINBoundary.java,v 1.5 2006/10/30 21:14:25 bostic Exp $:
 */

package com.sleepycat.je.tree;

/**
 * Contains information about the BIN returned by a search.
 */
public class BINBoundary {
    /** The last BIN was returned. */
    public boolean isLastBin;
    /** The first BIN was returned. */
    public boolean isFirstBin;
}
