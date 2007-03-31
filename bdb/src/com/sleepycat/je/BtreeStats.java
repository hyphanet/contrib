/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BtreeStats.java,v 1.10.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
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
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getBottomInternalNodeCount() {
        return binCount;
    }

    /**
     * Internal use only.
     */
    public void setBottomInternalNodeCount(long val) {
        binCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getDuplicateBottomInternalNodeCount() {
        return dbinCount;
    }

    /**
     * Internal use only.
     */
    public void setDuplicateBottomInternalNodeCount(long val) {
        dbinCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getDeletedLeafNodeCount() {
        return deletedLNCount;
    }

    /**
     * Internal use only.
     */
    public void setDeletedLeafNodeCount(long val) {
        deletedLNCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getDupCountLeafNodeCount() {
        return dupCountLNCount;
    }

    /**
     * Internal use only.
     */
    public void setDupCountLeafNodeCount(long val) {
        dupCountLNCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getInternalNodeCount() {
        return inCount;
    }

    /**
     * Internal use only.
     */
    public void setInternalNodeCount(long val) {
        inCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getDuplicateInternalNodeCount() {
        return dinCount;
    }

    /**
     * Internal use only.
     */
    public void setDuplicateInternalNodeCount(long val) {
        dinCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getLeafNodeCount() {
        return lnCount;
    }

    /**
     * Internal use only.
     */
    public void setLeafNodeCount(long val) {
        lnCount = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getMainTreeMaxDepth() {
        return mainTreeMaxDepth;
    }

    /**
     * Internal use only.
     */
    public void setMainTreeMaxDepth(int val) {
        mainTreeMaxDepth = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getDuplicateTreeMaxDepth() {
        return duplicateTreeMaxDepth;
    }

    /**
     * Internal use only.
     */
    public void setDuplicateTreeMaxDepth(int val) {
        duplicateTreeMaxDepth = val;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long[] getINsByLevel() {
        return insByLevel;
    }

    /**
     * Internal use only.
     */
    public void setINsByLevel(long[] insByLevel) {
	this.insByLevel = insByLevel;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long[] getBINsByLevel() {
        return binsByLevel;
    }

    /**
     * Internal use only.
     */
    public void setBINsByLevel(long[] binsByLevel) {
	this.binsByLevel = binsByLevel;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long[] getDINsByLevel() {
        return dinsByLevel;
    }

    /**
     * Internal use only.
     */
    public void setDINsByLevel(long[] dinsByLevel) {
	this.dinsByLevel = dinsByLevel;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long[] getDBINsByLevel() {
        return dbinsByLevel;
    }

    /**
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
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
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
