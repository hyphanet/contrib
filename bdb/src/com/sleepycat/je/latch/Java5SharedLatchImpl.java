/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Java5SharedLatchImpl.java,v 1.8.2.1 2007/02/01 14:49:46 cwl Exp $
 */

package com.sleepycat.je.latch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Java5SharedLatchImpl provides an implementation of the SharedLatch
 * interface.  By using a wrapper class we can avoid link errors when we run in
 * Java 1.4 JVMs.  LatchSupport will only reference this class if it knows that
 * the ReentrantReadWriteLock class is available at runtime through
 * Class.forName().  LatchSupport only references this class through the
 * SharedLatch interface and only constructs this using
 *
 *    Class.forName("Java5SharedLatchImpl").newInstance();
 */
class Java5SharedLatchImpl
    extends ReentrantReadWriteLock
    implements SharedLatch {

    private String name;
    private boolean noteLatch;
    private List readers;

    /**
     * If true, this shared latch is only ever latched exclusively.  Used for
     * BINs.
     */
    private boolean exclusiveOnly;

    Java5SharedLatchImpl() {
	super(EnvironmentImpl.getFairLatches());
	assert
	    (readers = Collections.synchronizedList(new ArrayList())) != null;
	exclusiveOnly = false;
    }

    public void setExclusiveOnly(boolean exclusiveOnly) {
	this.exclusiveOnly = exclusiveOnly;
    }

    /**
     * Set the latch name, used for latches in objects instantiated from the
     * log.
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * If noteLatch is true, then track latch usage in the latchTable.
     * Always return true so this can be called as an assert.
     */
    public boolean setNoteLatch(boolean noteLatch) {
	this.noteLatch = noteLatch;
	return true;
    }

    /**
     * Acquire a latch for exclusive/write access.
     *
     * Wait for the latch if some other thread is holding it.  If there are
     * threads waiting for access, they will be granted the latch on a FIFO
     * basis if fair latches are set. When the method returns, the latch is
     * held for exclusive access.
     *
     * @throws LatchException if the latch is already held by the current
     * thread for exclusive access.
     */
    public void acquireExclusive()
	throws DatabaseException {

        try {
	    if (isWriteLockedByCurrentThread()) {
		throw new LatchException(name + " already held");
	    }

	    writeLock().lock();

            assert (noteLatch ? noteLatch() : true);// intentional side effect;
	} finally {
	    assert EnvironmentImpl.maybeForceYield();
	}
    }

    public boolean acquireExclusiveNoWait()
	throws DatabaseException {

        try {
	    if (isWriteLockedByCurrentThread()) {
		throw new LatchException(name + " already held");
	    }

	    boolean ret = writeLock().tryLock();

	    /* Intentional side effect. */
            assert ((noteLatch & ret) ? noteLatch() : true);
	    return ret;
	} finally {
	    assert EnvironmentImpl.maybeForceYield();
	}
    }

    /**
     * Acquire a latch for shared/read access.
     */
    public void acquireShared()
        throws DatabaseException {

	if (exclusiveOnly) {
	    acquireExclusive();
	    return;
	}

        try {
	    boolean assertionsEnabled = false;
	    assert assertionsEnabled = true;
	    if (assertionsEnabled) {
		if (readers.add(Thread.currentThread())) {
		    readLock().lock();
		} else {
		    /* Already latched, do nothing. */
		}
	    } else {
		readLock().lock();
	    }

            assert (noteLatch ?  noteLatch() : true);// intentional side effect
	} finally {
	    assert EnvironmentImpl.maybeForceYield();
	}
    }

    public boolean isOwner() {
	boolean assertionsEnabled = false;
	assert assertionsEnabled = true;
	if (assertionsEnabled && !exclusiveOnly) {
	    return readers.contains(Thread.currentThread()) ||
		isWriteLockedByCurrentThread();
	} else {
	    return isWriteLockedByCurrentThread();
	}
    }

    /**
     * Release an exclusive or shared latch.  If there are other thread(s)
     * waiting for the latch, they are woken up and granted the latch.
     */
    public void release()
	throws LatchNotHeldException {

	try {
	    if (isWriteLockedByCurrentThread()) {
		writeLock().unlock();
                /* Intentional side effect. */
                assert (noteLatch ? unNoteLatch() : true);
		return;
	    }

	    if (exclusiveOnly) {
		return;
	    }

	    boolean assertionsEnabled = false;
	    assert assertionsEnabled = true;
	    if (assertionsEnabled) {
		if (readers.remove(Thread.currentThread())) {
		    readLock().unlock();
		} else {
		    throw new LatchNotHeldException(name + " not held");
		}		
	    } else {

		/*
		 * There's no way to tell if a readlock is held by the current
		 * thread so just try unlocking it.
		 */
		readLock().unlock();
	    }
	    /* Intentional side effect. */
	    assert (noteLatch ? unNoteLatch() : true);
	} catch (IllegalMonitorStateException IMSE) {
	    IMSE.printStackTrace();
	    return;
	}
    }

    public void releaseIfOwner()
	throws LatchNotHeldException {

	if (isWriteLockedByCurrentThread()) {
	    writeLock().unlock();
	    assert (noteLatch ? unNoteLatch() : true);
	    return;
	}

	if (exclusiveOnly) {
	    return;
	}

	assert (getReadLockCount() > 0);
	boolean assertionsEnabled = false;
	assert assertionsEnabled = true;
	if (assertionsEnabled) {
	    if (readers.contains(Thread.currentThread())) {
		readLock().unlock();
		assert (noteLatch ? unNoteLatch() : true);
	    }
	} else {

	    /*
	     * There's no way to tell if a readlock is held by the current
	     * thread so just try unlocking it.
	     */
	    readLock().unlock();
	}
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
    private boolean unNoteLatch() {
        
	return LatchSupport.latchTable.unNoteLatch(this, name);
    }
}
