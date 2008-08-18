/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: BtreeStats.java,v 1.15 2008/01/24 14:59:27 linda Exp $
 */

package com.sleepycat.je;

/**
 * The BtreeStats object is used to return Btree database statistics.
 */
public class BtreeStats extends DatabaseStats {

    /* Number of Bottom Internal Nodes in the database's btree. */
    private long binCount;

    /* Number of Duplicate Bottom Internal Nodes in the database's btree. */
    private long dbinCount;

    /* Number of deleted Leaf Nodes in the database's btree. */
    private long deletedLNCount;

    /* Number of duplicate Leaf Nodes in the database's btree. */
    private long dupCountLNCount;

    /*
     * Number of Internal Nodes in database's btree.  BIN's are not included.
     */
    private long inCount;

    /*
     * Number of Duplicate Internal Nodes in database's btree.  BIN's are not
     * included.
     */
    private long dinCount;

    /* Number of Leaf Nodes in the database's btree. */
    private long lnCount;

    /* Maximum depth of the in memory tree. */
    private int mainTreeMaxDepth;

    /* Maximum depth of the duplicate memory trees. */
    private int duplicateTreeMaxDepth;

    /* Histogram of INs by level. */
    private long[] insByLevel;

    /* Histogram of BINs by level. */
    private long[] binsByLevel;

    /* Histogram of DINs by level. */
    private long[] dinsByLevel;

    /* Histogram of DBINs by level. */
    private long[] dbinsByLevel;

    /**
     * Returns the number of Bottom Internal Nodes in the database tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of Bottom Internal Nodes in the database tree.
     */
    public long getBottomInternalNodeCount() {
        return binCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setBottomInternalNodeCount(long val) {
        binCount = val;
    }

    /**
     * Returns the number of Duplicate Bottom Internal Nodes in the database
     * tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of Duplicate Bottom Internal Nodes in the database tree.
     */
    public long getDuplicateBottomInternalNodeCount() {
        return dbinCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDuplicateBottomInternalNodeCount(long val) {
        dbinCount = val;
    }

    /**
     * Returns the number of deleted data records in the database tree that
     * are pending removal by the compressor.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of deleted data records in the database tree that are
     * pending removal by the compressor.
     */
    public long getDeletedLeafNodeCount() {
        return deletedLNCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDeletedLeafNodeCount(long val) {
        deletedLNCount = val;
    }

    /**
     * Returns the number of duplicate count leaf nodes in the database tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of duplicate count leaf nodes in the database tree.
     */
    public long getDupCountLeafNodeCount() {
        return dupCountLNCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDupCountLeafNodeCount(long val) {
        dupCountLNCount = val;
    }

    /**
     * Returns the number of Internal Nodes in the database tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of Internal Nodes in the database tree.
     */
    public long getInternalNodeCount() {
        return inCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setInternalNodeCount(long val) {
        inCount = val;
    }

    /**
     * Returns the number of Duplicate Internal Nodes in the database tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of Duplicate Internal Nodes in the database tree.
     */
    public long getDuplicateInternalNodeCount() {
        return dinCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDuplicateInternalNodeCount(long val) {
        dinCount = val;
    }

    /**
     * Returns the number of leaf nodes in the database tree, which can equal
     * the number of records. This is calculated without locks or transactions,
     * and therefore is only an accurate count of the current number of records
     * when the database is quiescent.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return number of leaf nodes in the database tree, which can equal the
     * number of records. This is calculated without locks or transactions, and
     * therefore is only an accurate count of the current number of records
     * when the database is quiescent.
     */
    public long getLeafNodeCount() {
        return lnCount;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLeafNodeCount(long val) {
        lnCount = val;
    }

    /**
     * Returns the maximum depth of the main database tree.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return maximum depth of the main database tree.
     */
    public int getMainTreeMaxDepth() {
        return mainTreeMaxDepth;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setMainTreeMaxDepth(int val) {
        mainTreeMaxDepth = val;
    }

    /**
     * Returns the maximum depth of the duplicate database trees.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return maximum depth of the duplicate database trees.
     */
    public int getDuplicateTreeMaxDepth() {
        return duplicateTreeMaxDepth;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDuplicateTreeMaxDepth(int val) {
        duplicateTreeMaxDepth = val;
    }

    /**
     * Returns the count of Internal Nodes per level, indexed by level.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return count of Internal Nodes per level, indexed by level.
     */
    public long[] getINsByLevel() {
        return insByLevel;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setINsByLevel(long[] insByLevel) {
	this.insByLevel = insByLevel;
    }

    /**
     * Returns the count of Bottom Internal Nodes per level, indexed by level.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return count of Bottom Internal Nodes per level, indexed by level.
     */
    public long[] getBINsByLevel() {
        return binsByLevel;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setBINsByLevel(long[] binsByLevel) {
	this.binsByLevel = binsByLevel;
    }

    /**
     * Returns the count of Duplicate Internal Nodes per level, indexed by
     * level.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return count of Duplicate Internal Nodes per level, indexed by level.
     */
    public long[] getDINsByLevel() {
        return dinsByLevel;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDINsByLevel(long[] dinsByLevel) {
	this.dinsByLevel = dinsByLevel;
    }

    /**
     * Returns the count of Duplicate Bottom Internal Nodes per level, indexed
     * by level.
     *
     * <p>The information is included only if the {@link
     * com.sleepycat.je.Database#getStats Database.getStats} call was not
     * configured by the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method.</p>
     *
     * @return count of Duplicate Bottom Internal Nodes per level, indexed by
     * level.
     */
    public long[] getDBINsByLevel() {
        return dbinsByLevel;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setDBINsByLevel(long[] dbinsByLevel) {
	this.dbinsByLevel = dbinsByLevel;
    }

    private void arrayToString(long[] arr, StringBuffer sb) {
	for (int i = 0; i < arr.length; i++) {
	    long count = arr[i];
	    if (count > 0) {
		sb.append("  level ").append(i);
		sb.append(": count=").append(count).append("\n");
	    }
	}
    }

    /**
     * For convenience, the BtreeStats class has a toString method that lists
     * all the data fields.
     */
    @Override
    public String toString() {
	StringBuffer sb = new StringBuffer();
	if (binCount > 0) {
	    sb.append("numBottomInternalNodes=");
	    sb.append(binCount).append("\n");
	    arrayToString(binsByLevel, sb);
	}
	if (inCount > 0) {
	    sb.append("numInternalNodes=");
	    sb.append(inCount).append("\n");
	    arrayToString(insByLevel, sb);
	}
	if (dinCount > 0) {
	    sb.append("numDuplicateInternalNodes=");
	    sb.append(dinCount).append("\n");
	    arrayToString(dinsByLevel, sb);
	}
	if (dbinCount > 0) {
	    sb.append("numDuplicateBottomInternalNodes=");
	    sb.append(dbinCount).append("\n");
	    arrayToString(dbinsByLevel, sb);
	}
	sb.append("numLeafNodes=").append(lnCount).append("\n");
	sb.append("numDeletedLeafNodes=").
	    append(deletedLNCount).append("\n");
	sb.append("numDuplicateCountLeafNodes=").
	    append(dupCountLNCount).append("\n");
	sb.append("mainTreeMaxDepth=").
	    append(mainTreeMaxDepth).append("\n");
	sb.append("duplicateTreeMaxDepth=").
	    append(duplicateTreeMaxDepth).append("\n");

	return sb.toString();
    }
}
