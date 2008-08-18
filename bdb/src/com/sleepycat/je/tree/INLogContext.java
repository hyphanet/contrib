/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: INLogContext.java,v 1.1 2008/04/18 22:57:37 mark Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.log.LogContext;

/**
 * Extends LogContext to add fields used by IN.beforeLog and afterLog methods.
 */
public class INLogContext extends LogContext {

    /**
     * Whether a BINDelta may be logged.  A BINDelta is logged rather than a BIN
     * if this field is true and other qualifications are met for a delta.
     * Used by BIN.beforeLog.
     *
     * Set by caller.
     */
    public boolean allowDeltas;

    /**
     * Whether LNs are migrated proactively by the log cleaner as part of
     * logging a BIN.  Used by BIN.beforeLog.
     *
     * Set by caller.
     */
    public boolean proactiveMigration;
}
