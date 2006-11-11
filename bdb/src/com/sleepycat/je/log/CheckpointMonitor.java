/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: CheckpointMonitor.java,v 1.7 2006/09/12 19:16:50 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * The checkpoint monitor saves information about log writes to decide when a
 * checkpoint is needed.
 */
class CheckpointMonitor {
    private int bytesWritten;
    private long periodInBytes;
    private EnvironmentImpl envImpl;

    CheckpointMonitor(EnvironmentImpl envImpl)
        throws DatabaseException {
        	
        bytesWritten = 0;
        periodInBytes = envImpl.getConfigManager().getLong
	    (EnvironmentParams.CHECKPOINTER_BYTES_INTERVAL);

        /*
         * The period is reset each activation and is not synchronized with the
         * interval counted by the Checkpointer.  Use a small enough period to
         * invoke the Checkpointer within 10% of its interval.
         */
        periodInBytes /= 10;
        this.envImpl = envImpl;
    }

    /**
     * Update checkpoint driving information. Call from within the log write
     * latch.
     *
     * @return true if a checkpoint is needed.
     */
    boolean recordLogWrite(int entrySize, LoggableObject item) {
        bytesWritten += entrySize;
        return (bytesWritten >= periodInBytes);
    }

    /**
     * Wake up the checkpointer. Note that the check on bytesWritten is
     * actually within a latched period.
     */
    void activate() {
        envImpl.getCheckpointer().wakeup();
        bytesWritten = 0;
    }            
}
