/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INList.java,v 1.48.2.1 2007/02/01 14:49:44 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.tree.IN;

/**
 * The INList is a list of in-memory INs for a given environment. 
 */
public class INList {
    private static final String DEBUG_NAME = INList.class.getName();
    private SortedSet ins = null; 
    private Set addedINs = null;
    private EnvironmentImpl envImpl;

    /* If both latches are acquired, major must always be acquired first. */
    private Latch majorLatch;
    private Latch minorLatch;

    private boolean updateMemoryUsage;

    INList(EnvironmentImpl envImpl) {
        this.envImpl = envImpl;
        ins = new TreeSet();
	addedINs = new HashSet();
        majorLatch =
	    LatchSupport.makeLatch(DEBUG_NAME + " Major Latch", envImpl);
        minorLatch =
	    LatchSupport.makeLatch(DEBUG_NAME + " Minor Latch", envImpl);
        updateMemoryUsage = true;
    }

    /**
     * Used only by tree verifier when validating INList. Must be called with
     * orig.majorLatch acquired.
     */
    public INList(INList orig, EnvironmentImpl envImpl)
	throws DatabaseException {

	ins = new TreeSet(orig.getINs());
	addedINs = new HashSet();	
        this.envImpl = envImpl;
        majorLatch =
	    LatchSupport.makeLatch(DEBUG_NAME + " Major Latch", envImpl);
        minorLatch =
	    LatchSupport.makeLatch(DEBUG_NAME + " Minor Latch", envImpl);
        updateMemoryUsage = false;
    }

    /* 
     * We ignore latching on this method because it's only called from validate
     * which ignores latching anyway.
     */
    public SortedSet getINs() {
	return ins;
    }

    /* 
     * Don't require latching, ok to be imprecise.
     */
    public int getSize() {
	return ins.size();
    }

    /**
     * An IN has just come into memory, add it to the list.
     */
    public void add(IN in)
	throws DatabaseException {

	boolean enteredWithLatchHeld = majorLatch.isOwner();
        boolean addToMajor = true;
        try {
            if (enteredWithLatchHeld) {

                /* 
                 * Don't try to acquire the major latch twice, that's not
                 * supported. If we do hold the latch, don't modify the major
                 * list, we may be faulting in an IN while iterating over the
                 * list from the evictor.
                 */
                addToMajor = false;
            } else {
                if (!(majorLatch.acquireNoWait())) {
                    /* We couldn't acquire the latch. */
                    addToMajor = false;
                }
            }

            if (addToMajor) {
                addAndSetMemory(ins, in);
            } else {
                minorLatch.acquire();
                try {
                    addAndSetMemory(addedINs, in);
                } finally {
                    minorLatch.release();
                }

                /*
                 * The holder of the majorLatch may have released it.  If so,
                 * try to put the minor list into the major list so no INs are
                 * orphaned.
                 */
                if (!enteredWithLatchHeld) {
                    if (majorLatch.acquireNoWait()) {
                        try {
                            latchMinorAndDumpAddedINs();
                        } finally {
                            releaseMajorLatch();
                        }
                    }
                }
            }
        } finally {
            if (addToMajor) {
                releaseMajorLatchIfHeld();
            }
        }
    }
    
    private void addAndSetMemory(Set set, IN in) {
        boolean addOk  = set.add(in);

        assert addOk : "failed adding in " + in.getNodeId();

        if (updateMemoryUsage) {
            MemoryBudget mb =  envImpl.getMemoryBudget();
            mb.updateTreeMemoryUsage(in.getInMemorySize());
            in.setInListResident(true);
        }
    }

    /**
     * An IN is getting evicted or is displaced by recovery.  Caller is
     * responsible for acquiring the major latch before calling this and
     * releasing it when they're done.
     */
    public void removeLatchAlreadyHeld(IN in)
	throws DatabaseException {

	assert majorLatch.isOwner();
        boolean removeDone = ins.remove(in);
        if (!removeDone) {
            /* Look in addedINs set. */
            minorLatch.acquire();
            try {
                removeDone = addedINs.remove(in);
                dumpAddedINsIntoMajorSet();
            } finally {
                minorLatch.release();
            }
        }

        assert removeDone;

        if (updateMemoryUsage) {
            envImpl.getMemoryBudget().
                updateTreeMemoryUsage(in.getAccumulatedDelta() -
				      in.getInMemorySize());
            in.setInListResident(false);
        }
    }

    /**
     * An IN is getting swept or is displaced by recovery.
     */
    public void remove(IN in)
	throws DatabaseException {

        assert LatchSupport.countLatchesHeld() == 0;
        majorLatch.acquire();
        try {
            removeLatchAlreadyHeld(in);
        } finally {
            releaseMajorLatch();
        }
    }

    public SortedSet tailSet(IN in)
	throws DatabaseException {

	assert majorLatch.isOwner();
	return ins.tailSet(in);
    }

    public IN first()
	throws DatabaseException {

	assert majorLatch.isOwner();
	return (IN) ins.first();
    }

    /**
     * Return an iterator over the main 'ins' set.  Returned iterator will not
     * show the elements in addedINs.
     *
     * The major latch should be held before entering.  The caller is
     * responsible for releasing the major latch when they're finished with the
     * iterator.
     *
     * @return an iterator over the main 'ins' set.
     */
    public Iterator iterator() {
	assert majorLatch.isOwner();
	return ins.iterator();
    }

    /**
     * Clear the entire list during recovery and at shutdown.
     */
    public void clear()
	throws DatabaseException {

        assert LatchSupport.countLatchesHeld() == 0;
        majorLatch.acquire();
	minorLatch.acquire();
        ins.clear();
	addedINs.clear();
	minorLatch.release();
        releaseMajorLatch();

        if (updateMemoryUsage) {
            envImpl.getMemoryBudget().refreshTreeMemoryUsage(0);
        }
    }

    public void dump() {
        System.out.println("size=" + getSize());
	Iterator iter = ins.iterator();
	while (iter.hasNext()) {
	    IN theIN = (IN) iter.next();
	    System.out.println("db=" + theIN.getDatabase().getId() +
                               " nid=: " + theIN.getNodeId() + "/" +
			       theIN.getLevel());
	}
    }

    /**
     * The locking hierarchy is:
     *   1. INList major latch.
     *   2. IN latch.
     * In other words, the INList major latch must be taken before any IN
     * latches to avoid deadlock. 
     */
    public void latchMajor()
	throws DatabaseException {

        assert LatchSupport.countLatchesHeld() == 0;
	majorLatch.acquire();
    }

    public void releaseMajorLatchIfHeld()
	throws DatabaseException {

	if (majorLatch.isOwner()) {
	    releaseMajorLatch();
	}
    }

    public void releaseMajorLatch()
	throws DatabaseException {

	/*
	 * Before we release the major latch, take a look at addedINs and see
	 * if anything has been added to it while we held the major latch.  If
	 * so, added it to ins.
	 */
        latchMinorAndDumpAddedINs();
	majorLatch.release();
    }

    private void dumpAddedINsIntoMajorSet() {
	if (addedINs.size() > 0) {
	    ins.addAll(addedINs);
	    addedINs.clear();
	}
    }

    void latchMinorAndDumpAddedINs() 
        throws DatabaseException {

	latchMinor();
        try {
            dumpAddedINsIntoMajorSet();
        } finally {
            releaseMinorLatch();
        }
    }

    private void latchMinor()
	throws DatabaseException {

	minorLatch.acquire();
    }

    private void releaseMinorLatch()
	throws DatabaseException {

	minorLatch.release();
    }
}
