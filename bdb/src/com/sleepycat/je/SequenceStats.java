/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2008 Oracle.  All rights reserved.
 *
 * $Id: SequenceStats.java,v 1.9 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

/**
 * A SequenceStats object is used to return sequence statistics.
 */
public class SequenceStats {

    private int nGets;
    private int nCachedGets;
    private long current;
    private long value;
    private long lastValue;
    private long min;
    private long max;
    private int cacheSize;

    SequenceStats(int nGets,
                  int nCachedGets,
                  long current,
                  long value,
                  long lastValue,
                  long min,
                  long max,
                  int cacheSize) {

        this.nGets = nGets;
        this.nCachedGets = nCachedGets;
        this.current = current;
        this.value = value;
        this.lastValue = lastValue;
        this.min = min;
        this.max = max;
        this.cacheSize = cacheSize;
    }

    /**
     * Returns the number of times that Sequence.get was called successfully.
     *
     * @return number of times that Sequence.get was called successfully.
     */
    public int getNGets() {
        return nGets;
    }

    /**
     * Returns the number of times that Sequence.get was called and a cached
     * value was returned.
     *
     * @return number of times that Sequence.get was called and a cached
     * value was returned.
     */
    public int getNCachedGets() {
        return nCachedGets;
    }

    /**
     * Returns the current value of the sequence in the database.
     *
     * @return current value of the sequence in the database.
     */
    public long getCurrent() {
        return current;
    }

    /**
     * Returns the current cached value of the sequence.
     *
     * @return current cached value of the sequence.
     */
    public long getValue() {
        return value;
    }

    /**
     * Returns the last cached value of the sequence.
     *
     * @return last cached value of the sequence.
     */
    public long getLastValue() {
        return lastValue;
    }

    /**
     * Returns the minimum permitted value of the sequence.
     *
     * @return minimum permitted value of the sequence.
     */
    public long getMin() {
        return min;
    }

    /**
     * Returns the maximum permitted value of the sequence.
     *
     * @return maximum permitted value of the sequence.
     */
    public long getMax() {
        return max;
    }

    /**
     * Returns the number of values that will be cached in this handle.
     *
     * @return number of values that will be cached in this handle.
     */
    public int getCacheSize() {
        return cacheSize;
    }

    @Override
    public String toString() {
        return "nGets=" + nGets
            + "\nnCachedGets=" + nCachedGets
            + "\ncurrent=" + current
            + "\nvalue=" + value
            + "\nlastValue=" + lastValue
            + "\nmin=" + min
            + "\nmax=" + max
            + "\ncacheSize=" + cacheSize
            ;
    }
}
