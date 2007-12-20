/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogScanConfig.java,v 1.1.2.2 2007/11/20 13:32:26 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class LogScanConfig implements Cloneable {

    /*
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public static final LogScanConfig DEFAULT = new LogScanConfig();

    private boolean forwards = true;
    private long startPosition;
    private long endPosition;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public LogScanConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setForwards(boolean forwards) {
        this.forwards = forwards;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getForwards() {
        return forwards;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public DatabaseConfig cloneConfig() {
        try {
            return (DatabaseConfig) super.clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }
}
