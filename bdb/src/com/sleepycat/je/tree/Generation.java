/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Generation.java,v 1.14 2008/01/07 14:28:56 cwl Exp $
 */

package com.sleepycat.je.tree;

public final class Generation {
    static private long nextGeneration = 0;

    static long getNextGeneration() {
	return nextGeneration++;
    }
}
