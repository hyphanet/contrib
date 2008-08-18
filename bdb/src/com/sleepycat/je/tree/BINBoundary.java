/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: BINBoundary.java,v 1.7 2008/01/07 14:28:55 cwl Exp $:
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
