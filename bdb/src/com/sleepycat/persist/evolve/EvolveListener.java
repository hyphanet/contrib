/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EvolveListener.java,v 1.9 2008/01/07 14:28:58 cwl Exp $
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
