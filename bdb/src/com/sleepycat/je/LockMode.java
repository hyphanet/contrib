/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: LockMode.java,v 1.19 2006/09/12 19:16:43 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class LockMode {
    private String lockModeName;

    private LockMode(String lockModeName) {
	this.lockModeName = lockModeName;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public static final LockMode DEFAULT = new LockMode("DEFAULT");

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public static final LockMode READ_UNCOMMITTED =
        new LockMode("READ_UNCOMMITTED");

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     * @deprecated
     */
    public static final LockMode DIRTY_READ = READ_UNCOMMITTED;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public static final LockMode READ_COMMITTED =
        new LockMode("READ_COMMITTED");

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public static final LockMode RMW = new LockMode("RMW");

    public String toString() {
	return "LockMode." + lockModeName;
    }
}
