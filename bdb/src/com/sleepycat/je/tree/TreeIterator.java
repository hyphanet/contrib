/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TreeIterator.java,v 1.23.2.1 2007/02/01 14:49:52 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.latch.LatchNotHeldException;

/**
 * TreeIterator implements an Iterator over Tree's.  Not protected
 * against insertions like cursors.
 */
public final class TreeIterator implements Iterator {
    private Tree tree;
    private BIN nextBin;
    private int index;

    public TreeIterator(Tree tree)
	throws DatabaseException {

	nextBin = (BIN) tree.getFirstNode();
	if (nextBin != null) {
	    nextBin.releaseLatch();
	}
	index = -1;
	this.tree = tree;
    }

    public boolean hasNext() {
	boolean ret = false;
        try {
            if (nextBin != null) {
                nextBin.latch();
            }
            advance();
            ret = (nextBin != null) && (index < nextBin.getNEntries());
        } catch (DatabaseException e) {
            // eat exception
        } finally {
            try {
                if (nextBin != null) {
                    nextBin.releaseLatch();
                }
            } catch (LatchNotHeldException e) {
		/* Klockwork - ok */
            }
        }
        return ret;
    }

    public Object next() {

        Object ret = null;
        try {
            if (nextBin == null) {
                throw new NoSuchElementException();
            }
            nextBin.latch();
            ret = nextBin.getKey(index);
        } catch (DatabaseException e) {
	    // eat exception, return null;
        } finally {
            try {
                if (nextBin != null) {
                    nextBin.releaseLatch();
                }
            } catch (LatchNotHeldException e) {
		/* Klockwork - ok */
            }
        }
	return ret;
    }

    public void remove() {
	throw new UnsupportedOperationException();
    }

    private void advance()
	throws DatabaseException {

	while (nextBin != null) {
	    if (++index < nextBin.getNEntries()) {
		return;
	    }
	    nextBin = tree.getNextBin(nextBin, false /* traverseWithinDupTree */);
	    index = -1;
	}
    }
}
