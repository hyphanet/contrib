/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: PreloadStatus.java,v 1.11 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

/**
 * Describes the result of the {@link com.sleepycat.je.Database#preload
 * Database.preload} operation.
 */
public class PreloadStatus implements Serializable {

	/* For toString. */
    private String statusName;

    private PreloadStatus(String statusName) {
	this.statusName = statusName;
    }

    @Override
    public String toString() {
	return "PreloadStatus." + statusName;
    }

    /**
     * {@link com.sleepycat.je.Database#preload Database.preload} 
     * was successful.
     */
    public static final PreloadStatus SUCCESS =
	new PreloadStatus("SUCCESS");

    /**
     * {@link com.sleepycat.je.Database#preload Database.preload} 
     * filled maxBytes of the cache.
     */
    public static final PreloadStatus FILLED_CACHE =
	new PreloadStatus("FILLED_CACHE");

    /**
     * {@link com.sleepycat.je.Database#preload Database.preload} 
     * took more than maxMillisecs.
     */
    public static final PreloadStatus EXCEEDED_TIME =
	new PreloadStatus("EXCEEDED_TIME");
}
