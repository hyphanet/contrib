/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EvolveListener.java,v 1.6.2.2 2007/11/20 13:32:38 cwl Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * The listener interface called during eager entity evolution.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public interface EvolveListener {

    /**
     * The listener method called during eager entity evolution.
     *
     * @return true to continue evolution or false to stop.
     */
    boolean evolveProgress(EvolveEvent event);
}
