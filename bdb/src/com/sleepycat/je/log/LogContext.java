/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogContext.java,v 1.1 2008/04/18 22:57:36 mark Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.dbi.DatabaseImpl;

/**
 * Context parameters that apply to all logged items when multiple items are
 * logged in one log operation.  Passed to LogManager log methods and to
 * beforeLog and afterLog methods.
 */
public class LogContext {

    /**
     * Database of the node(s), or null if entry is not a node.  Used for per-
     * database utilization tracking.
     *
     * Set by caller.
     */
    public DatabaseImpl nodeDb = null;

    /**
     * Whether the log buffer(s) must be written to the file system.
     *
     * Set by caller.
     */
    public boolean flushRequired = false;

    /**
     * Whether a new log file must be created for containing the logged
     * item(s).
     *
     * Set by caller.
     */
    public boolean forceNewLogFile = false;

    /**
     * Whether an fsync must be performed after writing the item(s) to the log.
     *
     * Set by caller.
     */
    public boolean fsyncRequired = false;

    /**
     * Whether the write should be counted as background IO when throttling of
     * background IO is configured.
     *
     * Set by caller.
     */
    public boolean backgroundIO = false;

    /* Fields used internally by log method. */
    boolean wakeupCleaner = false;
    int totalNewSize = 0;
}
