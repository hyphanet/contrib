/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: PreloadStatus.java,v 1.5.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class PreloadStatus implements Serializable {
    /* For toString */
    private String statusName;

    private PreloadStatus(String statusName) {
	this.statusName = statusName;
    }

    public String toString() {
	return "PreloadStatus." + statusName;
    }

    /* preload() was successful. */
    public static final PreloadStatus SUCCESS =
	new PreloadStatus("SUCCESS");

    /* preload() filled maxBytes of the cache. */
    public static final PreloadStatus FILLED_CACHE =
	new PreloadStatus("FILLED_CACHE");

    /* preload() took more than maxMillisecs. */
    public static final PreloadStatus EXCEEDED_TIME =
	new PreloadStatus("EXCEEDED_TIME");
}
