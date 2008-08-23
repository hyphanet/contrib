/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: CursorConfig.java,v 1.25 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Specifies the attributes of database cursor.  An instance created with the
 * default constructor is initialized with the system's default settings.
 */
public class CursorConfig implements Cloneable {

    /**
     * Default configuration used if null is passed to methods that create a
     * cursor.
     */
    public static final CursorConfig DEFAULT = new CursorConfig();

    /**
     * A convenience instance to configure read operations performed by the
     * cursor to return modified but not yet committed data.
     */
    public static final CursorConfig READ_UNCOMMITTED = new CursorConfig();

    /**
     * A convenience instance to configure read operations performed by the
     * cursor to return modified but not yet committed data.
     *
     * @deprecated This has been replaced by {@link #READ_UNCOMMITTED} to
     * conform to ANSI database isolation terminology.
     */
    public static final CursorConfig DIRTY_READ = READ_UNCOMMITTED;

    /**
     * A convenience instance to configure a cursor for read committed
     * isolation.
     *
     * This ensures the stability of the current data item read by the cursor
     * but permits data read by this cursor to be modified or deleted prior to
     * the commit of the transaction.
     */
    public static final CursorConfig READ_COMMITTED = new CursorConfig();

    static {
        READ_UNCOMMITTED.setReadUncommitted(true);
        READ_COMMITTED.setReadCommitted(true);
    }

    private boolean readUncommitted = false;
    private boolean readCommitted = false;

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public CursorConfig() {
    }

    /**
     * Configures read operations performed by the cursor to return modified
     * but not yet committed data.
     *
     * @param readUncommitted If true, configure read operations performed by
     * the cursor to return modified but not yet committed data.
     *
     * @see LockMode#READ_UNCOMMITTED
     */
    public void setReadUncommitted(boolean readUncommitted) {
        this.readUncommitted = readUncommitted;
    }

    /**
     * Returns true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @return true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @see LockMode#READ_UNCOMMITTED
     */
    public boolean getReadUncommitted() {
        return readUncommitted;
    }

    /**
     * Configures read operations performed by the cursor to return modified
     * but not yet committed data.
     *
     * @param dirtyRead If true, configure read operations performed by the
     * cursor to return modified but not yet committed data.
     *
     * @deprecated This has been replaced by {@link #setReadUncommitted} to
     * conform to ANSI database isolation terminology.
     */
    public void setDirtyRead(boolean dirtyRead) {
        setReadUncommitted(dirtyRead);
    }

    /**
     * Returns true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @return true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @deprecated This has been replaced by {@link #getReadUncommitted} to
     * conform to ANSI database isolation terminology.
     */
    public boolean getDirtyRead() {
        return getReadUncommitted();
    }

    /**
     * Configures read operations performed by the cursor to return modified
     * but not yet committed data.
     *
     * @param readCommitted If true, configure read operations performed by
     * the cursor to return modified but not yet committed data.
     *
     * @see LockMode#READ_COMMITTED
     */
    public void setReadCommitted(boolean readCommitted) {
        this.readCommitted = readCommitted;
    }

    /**
     * Returns true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @return true if read operations performed by the cursor are configured
     * to return modified but not yet committed data.
     *
     * @see LockMode#READ_COMMITTED
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

    /**
     * Returns the values for each configuration attribute.
     *
     * @return the values for each configuration attribute.
     */
    @Override
    public String toString() {
        return "readUncommitted=" + readUncommitted +
            "\nreadCommitted=" + readCommitted +
            "\n";
    }
}
