/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: NodeLogEntry.java,v 1.5.2.1 2007/02/01 14:49:48 cwl Exp $
 */

package com.sleepycat.je.log.entry;

/**
 * Implemented by all LogEntry classes that provide a node ID.
 */
public interface NodeLogEntry extends LogEntry {

    /**
     * Returns the node ID.  This value is redundant with the main item (Node)
     * of a log entry.  It is returned separately so that it can be obtained
     * when the entry's main item (Node) is not loaded.  Partial loading is an
     * optimization for recovery.
     */
    long getNodeId();
}
