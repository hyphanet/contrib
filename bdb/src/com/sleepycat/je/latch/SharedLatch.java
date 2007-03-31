/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SharedLatch.java,v 1.17.2.1 2007/02/01 14:49:46 cwl Exp $
 */

package com.sleepycat.je.latch;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.RunRecoveryException;

/**
 * Simple thread-based non-transactional reader-writer/shared-exclusive latch.
 * 
 * Latches provide simple exclusive or shared transient locks on objects.
 * Latches are expected to be held for short, defined periods of time.  No
 * deadlock detection is provided so it is the caller's responsibility to
 * sequence latch acquisition in an ordered fashion to avoid deadlocks.
 */
public interface SharedLatch {

    /**
     * Set the latch name, used for latches in objects instantiated from the
     * log.
     */
    public void setName(String name);

    /**
     * Indicate whether this latch should be tracked in the debugging
     * LatchSupport.latchTable.
     * Always return true so this can be called under an assert.
     */
    public boolean setNoteLatch(boolean noteLatch);

    /**
     * Indicate whether this latch can only be set exclusively (not shared).
     * Used for BIN latches that are Shared, but should only be latched
     * exclusively.
     */
    public void setExclusiveOnly(boolean exclusiveOnly);

    /**
     * Acquire a latch for exclusive/write access.  If the thread already holds
     * the latch for shared access, it cannot be upgraded and LatchException
     * will be thrown.
     *
     * Wait for the latch if some other thread is holding it.  If there are
     * threads waiting for access, they will be granted the latch on a FIFO
     * basis if fair latches are enabled.  When the method returns, the latch
     * is held for exclusive access.
     *
     * @throws LatchException if the latch is already held by the current
     * thread for shared access.
     */
    public void acquireExclusive()
	throws DatabaseException;

    /**
     * Probe a latch for exclusive access, but don't block if it's not
     * available.
     *
     * @return true if the latch was acquired, false if it is not available.
     *
     * @throws LatchException if the latch is already held by the calling
     * thread.
     */
    public boolean acquireExclusiveNoWait()
	throws DatabaseException;

    /**
     * Acquire a latch for shared/read access.  Nesting is allowed, that is,
     * the latch may be acquired more than once by the same thread.
     *
     * @throws RunRecoveryException if an InterruptedException exception
     * occurs.
     */
    public void acquireShared()
        throws DatabaseException;

    /**
     * Release an exclusive or shared latch.  If there are other thread(s)
     * waiting for the latch, they are woken up and granted the latch.
     */
    public void release()
	throws LatchNotHeldException;

    public boolean isWriteLockedByCurrentThread();

    /**
     * Release the latch. If there are other thread(s) waiting for the latch,
     * one is woken up and granted the latch.  If the latch was not owned by
     * the caller, just return.
     */
    public void releaseIfOwner()
	throws LatchNotHeldException;

    /**
     * Return true if this thread is an owner, reader, or write.
     */
    public boolean isOwner();
}
