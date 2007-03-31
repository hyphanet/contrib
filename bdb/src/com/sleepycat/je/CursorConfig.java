/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CursorConfig.java,v 1.18.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class CursorConfig implements Cloneable {

    /**
     * Javadoc for this public instance is generated via
     * the doc templates in the doc_src directory.
     */
    public static final CursorConfig DEFAULT = new CursorConfig();

    /**
     * Javadoc for this public instance is generated via
     * the doc templates in the doc_src directory.
     */
    public static final CursorConfig READ_UNCOMMITTED = new CursorConfig();

    /**
     * Javadoc for this public instance is generated via
     * the doc templates in the doc_src directory.
     * @deprecated
     */
    public static final CursorConfig DIRTY_READ = READ_UNCOMMITTED;

    /**
     * Javadoc for this public instance is generated via
     * the doc templates in the doc_src directory.
     */
    public static final CursorConfig READ_COMMITTED = new CursorConfig();
    
    static {
        READ_UNCOMMITTED.setReadUncommitted(true);
        READ_COMMITTED.setReadCommitted(true);
    }

    private boolean readUncommitted = false;
    private boolean readCommitted = false;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public CursorConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setReadUncommitted(boolean readUncommitted) {
        this.readUncommitted = readUncommitted;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getReadUncommitted() {
        return readUncommitted;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     * @deprecated
     */
    public void setDirtyRead(boolean dirtyRead) {
        setReadUncommitted(dirtyRead);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     * @deprecated
     */
    public boolean getDirtyRead() {
        return getReadUncommitted();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setReadCommitted(boolean readCommitted) {
        this.readCommitted = readCommitted;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getReadCommitted() {
        return readCommitted;
    }

    /**
     * Internal method used by Cursor to create a copy of the application
     * supplied configuration. Done this way to provide non-public cloning.
     */
    CursorConfig cloneConfig() {
        try {
            return (CursorConfig) super.clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }
}
