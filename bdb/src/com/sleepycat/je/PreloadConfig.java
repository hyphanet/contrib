/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: PreloadConfig.java,v 1.4.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class PreloadConfig implements Cloneable {

    /*
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public static final PreloadConfig DEFAULT = new PreloadConfig();

    private long maxBytes;
    private long maxMillisecs;
    private boolean loadLNs;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public PreloadConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setMaxBytes(long maxBytes) {
	this.maxBytes = maxBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setMaxMillisecs(long maxMillisecs) {
	this.maxMillisecs = maxMillisecs;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getMaxMillisecs() {
        return maxMillisecs;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setLoadLNs(boolean loadLNs) {
	this.loadLNs = loadLNs;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getLoadLNs() {
        return loadLNs;
    }

    /**
     * Used by Database to create a copy of the application
     * supplied configuration. Done this way to provide non-public cloning.
     */
    DatabaseConfig cloneConfig() {
        try {
            return (DatabaseConfig) super.clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }
}
