/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: Generation.java,v 1.12 2006/10/30 21:14:26 bostic Exp $
 */

package com.sleepycat.je.tree;

public final class Generation {
    static private long nextGeneration = 0;

    static long getNextGeneration() {
	return nextGeneration++;
    }
}
