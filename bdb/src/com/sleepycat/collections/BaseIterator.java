/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: BaseIterator.java,v 1.6 2008/05/27 15:30:34 mark Exp $
 */

package com.sleepycat.collections;

import java.util.ListIterator;

/**
 * Common interface for BlockIterator and StoredIterator.
 */
interface BaseIterator<E> extends ListIterator<E> {

    /**
     * @hidden
     * Duplicate a cursor.  Called by StoredCollections.iterator.
     */
    ListIterator<E> dup();

    /**
     * @hidden
     * Returns whether the given data is the current iterator data.  Called by
     * StoredMapEntry.setValue.
     */
    boolean isCurrentData(Object currentData);

    /**
     * @hidden
     * Initializes a list iterator at the given index.  Called by
     * StoredList.iterator(int).
     */
    boolean moveToIndex(int index);
}
