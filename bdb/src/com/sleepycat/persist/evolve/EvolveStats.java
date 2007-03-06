/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: EvolveStats.java,v 1.6 2006/10/30 21:14:31 bostic Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * Statistics accumulated during eager entity evolution.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public class EvolveStats {

    private int nRead;
    private int nConverted;

    EvolveStats() {
    }

    void add(int nRead, int nConverted) {
        this.nRead += nRead;
        this.nConverted += nConverted;
    }

    /**
     * The total number of entities read during eager evolution.
     */
    public int getNRead() {
        return nRead;
    }

    /**
     * The total number of entities converted during eager evolution.
     */
    public int getNConverted() {
        return nConverted;
    }
}
