/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Java5LatchImpl.java,v 1.9.2.1 2007/02/01 14:49:46 cwl Exp $
 */

package com.sleepycat.je.latch;

import java.util.concurrent.locks.ReentrantLock;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Java5LatchImpl provides an implementation of the Latch interface.  By using
 * a wrapper class we can avoid link errors when we run in Java 1.4 JVMs.
 * LatchSupport.java will only reference this class if it knows that the
 * ReentrantLock class is available at runtime through Class.forName().
 * LatchSupport only references this class through the Latch interface and only
 * constructs an instance using
 *
 *    Class.forName("Java5LatchImpl").newInstance();
 */
class Java5LatchImpl implements Latch {

    /*
     * Required because getOwner() is protected (for unknown reasons) and can't
     * be accessed except by a subclass of ReentrantLock.
     */
    static private class JEReentrantLock extends ReentrantLock {
	JEReentrantLock(boolean fair) {
	    super(fair);
	}

	protected Thread getOwner() {
	    return super.getOwner();
	}
    }

    private JEReentrantLock lock;
    private String name;
    private LatchStats stats = new LatchStats();

    Java5LatchImpl() {
	lock = new JEReentrantLock(EnvironmentImpl.getFairLatches());
    }

    /**
     * Set the latch name, used for latches in objects instantiated from
     * the log.
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Acquire a latch for exclusive/write access.
     *
     * <p>Wait for the latch if some other thread is holding it.  If there are
     * threads waiting for access, they will be granted the latch on a FIFO
     * basis.  When the method returns, the latch is held for exclusive
     * access.</p>
     *
     * @throws LatchException if the latch is already held by the calling
     * thread.
     */
    public void acquire()
	throws DatabaseException {

        try {
	    if (lock.isHeldByCurrentThread()) {
		stats.nAcquiresSelfOwned++;
		throw new LatchException(name + " already held");
	    }

	    if (lock.isLocked()) {
		stats.nAcquiresWithContention++;
	    } else {
		stats.nAcquiresNoWaiters++;
	    }

	    lock.lock();

            assert noteLatch(); // intentional side effect;
	} finally {
	    assert EnvironmentImpl.maybeForceYield();
	}
    }

    /**
     * Acquire a latch for exclusive/write access, but do not block if it's not
     * available.
     *
     * @return true if the latch was acquired, false if it is not available.
     *
     * @throws LatchException if the latch is already held by the calling
     * thread.
     */
    public boolean acquireNoWait()
	throws LatchException {
        try {
	    if (lock.isHeldByCurrentThread()) {
		stats.nAcquiresSelfOwned++;
		throw new LatchException(name + " already held");
	    }

	    boolean ret = lock.tryLock();
	    if (ret) {
		assert noteLatch();
		stats.nAcquireNoWaitSuccessful++;
	    } else {
		stats.nAcquireNoWaitUnsuccessful++;
	    }
	    return ret;
	} finally {
	    assert EnvironmentImpl.maybeForceYield();
	}
    }

    /**
     * Release the latch.  If there are other thread(s) waiting for the latch,
     * one is woken up and granted the latch. If the latch was not owned by 
     * the caller, just return;
     */
    public void releaseIfOwner() {
	doRelease(false);
    }

    /**
     * Release the latch.  If there are other thread(s) waiting for the latch,
     * they are woken up and granted the latch.
     *
     * @throws LatchNotHeldException if the latch is not currently held.
     */
    public void release()
	throws LatchNotHeldException {

	if (doRelease(true)) {
            throw new LatchNotHeldException(name + " not held");
        }
    }

    /**
     * Do the work of releasing the latch. Wake up any waiters.
     *
     * @returns true if this latch was not owned by the caller.
     */
    private boolean doRelease(boolean checkHeld) {

	try {
	    if (!lock.isHeldByCurrentThread()) {
		return true;
	    }
	    lock.unlock();
	    stats.nReleases++;
	    assert unNoteLatch(checkHeld); // intentional side effect.
	} catch (IllegalMonitorStateException IMSE) {
	    return true;
	}
	return false;
    }

    /**
     * Return true if the current thread holds this latch.
     *
     * @return true if we hold this latch.  False otherwise.
     */
    public boolean isOwner() {
	return lock.isHeldByCurrentThread();
    }

    /**
     * Used only for unit tests.
     *
     * @return the thread that currently holds the latch for exclusive access.
     */
    public Thread owner() {
	return lock.getOwner();
    }

    /**
     * Return the number of threads waiting.
     *
     * @return the number of threads waiting for the latch.
     */
    public int nWaiters() {
	return lock.getQueueLength();
    }

    /**
     * @return a LatchStats object with information about this latch.
     */
    public LatchStats getLatchStats() {
	LatchStats s = null;
	try {
	    s = (LatchStats) stats.clone();
	} catch (CloneNotSupportedException e) {
	    /* Klockwork - ok */
	}
	return s;
    }

    /**
     * Formats a latch owner and waiters.
     */
    public String toString() {
	return lock.toString();
    }

    /**
     * Only call under the assert system. This records latching by thread.
     */
    private boolean noteLatch()
	throws LatchException {

        return LatchSupport.latchTable.noteLatch(this);
    }

    /**
     * Only call under the assert system. This records latching by thread.
     */
    private boolean unNoteLatch(boolean checkHeld) {
        
        /* Only return a false status if we are checking for latch ownership.*/
        if (checkHeld) {
            return LatchSupport.latchTable.unNoteLatch(this, name);
        } else {
            LatchSupport.latchTable.unNoteLatch(this, name);
            return true;
        }
    }
}
