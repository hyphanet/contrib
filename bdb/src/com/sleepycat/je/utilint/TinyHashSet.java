/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TinyHashSet.java,v 1.9 2008/05/15 01:52:44 linda Exp $
 */

package com.sleepycat.je.utilint;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * TinyHashSet is used to optimize (for speed, not space) the case where a
 * HashSet generally holds a single element.  This saves us the cost of
 * creating the HashSet and related elements as well as call Object.hashCode().
 *
 * If single != null, it's the only element in the TinyHashSet.  If set != null
 * then there are multiple elements in the TinyHashSet.  It should never be
 * true that (single != null) && (set != null).
 */
public class TinyHashSet<T> {

    private Set<T> set;
    private T single;

    /*
     * Will return a fuzzy value if the not under synchronized control.
     */
    public int size() {
        if (single != null) {
            return 1;
        } else if (set != null) {
            return set.size();
        } else {
            return 0;
        }
    }

    public boolean remove(T o) {
        assert (single == null) || (set == null);
        if (single != null) {
            if (single == o ||
                single.equals(o)) {
                single = null;
                return true;
            } else {
                return false;
            }
        } else if (set != null) {
            return set.remove(o);
        } else {
            return false;
        }
    }

    public boolean add(T o) {
        assert (single == null) || (set == null);
        if (set != null) {
            return set.add(o);
        } else if (single == null) {
            single = o;
            return true;
        } else {
            set = new HashSet<T>();
            set.add(single);
            single = null;
            return set.add(o);
        }
    }

    public Set<T> copy() {
        assert (single == null) || (set == null);
        if (set != null) {
            return new HashSet<T>(set);
        } else {
            Set<T> ret = new HashSet<T>();
            if (single != null) {
                ret.add(single);
            }
            return ret;
        }
    }

    public Iterator<T> iterator() {
        assert (single == null) || (set == null);
        if (set != null) {
            return set.iterator();
        } else {
            return new SingleElementIterator<T>(single, this);
        }
    }

    /*
     * Iterator that is used to just return one element.
     */
    public static class SingleElementIterator<T> implements Iterator<T> {
        T theObject;
        TinyHashSet<T> theSet;
        boolean returnedTheObject = false;

        SingleElementIterator(T o, TinyHashSet<T> theSet) {
            theObject = o;
            this.theSet = theSet;
            returnedTheObject = (o == null);
        }

        public boolean hasNext() {
            return !returnedTheObject;
        }

        public T next() {
            if (returnedTheObject) {
                throw new NoSuchElementException();
            }

            returnedTheObject = true;
            return theObject;
        }

        public void remove() {
            if (theObject == null ||
                !returnedTheObject) {
                throw new IllegalStateException();
            }
            theSet.remove(theObject);
        }
    }
}
