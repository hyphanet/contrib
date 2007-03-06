/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: TrackingInfo.java,v 1.11 2006/10/30 21:14:26 bostic Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.utilint.DbLsn;

/**
 * Tracking info packages some tree tracing info.
 */
public class TrackingInfo {
    private long lsn;
    private long nodeId;
    
    public TrackingInfo(long lsn, long nodeId) {
        this.lsn = lsn;
        this.nodeId = nodeId;
    }

    public String toString() {
        return "lsn=" + DbLsn.getNoFormatString(lsn) +
            " node=" + nodeId;
    }
}
