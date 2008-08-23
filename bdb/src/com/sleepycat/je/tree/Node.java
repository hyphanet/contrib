/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Node.java,v 1.108 2008/06/10 02:52:14 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.LocalUtilizationTracker;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;

/**
 * A Node contains all the common base information for any JE B-Tree node.
 */
public abstract class Node implements Loggable {

    private static final String BEGIN_TAG = "<node>";
    private static final String END_TAG = "</node>";

    /* The unique id of this node. */
    private long nodeId;

    /**
     * Only for subclasses.
     */
    protected Node() {
    }

    /**
     * Create a new node, assigning it the next available node id.
     */
    protected Node(EnvironmentImpl envImpl,
                   boolean replicated) {
        if (replicated) {
            nodeId = envImpl.getNodeSequence().getNextReplicatedNodeId();
        } else {
            nodeId = envImpl.getNodeSequence().getNextLocalNodeId();
        }
    }

    /**
     * Initialize a node that has been faulted in from the log.
     */
    public void postFetchInit(DatabaseImpl db, long sourceLsn)
        throws DatabaseException {

        /* Nothing to do. */
    }

    public long getNodeId() {
	return nodeId;
    }

    /* For unit tests only. */
    void setNodeId(long nid) {
	nodeId = nid;
    }

    public void latchShared()
	throws DatabaseException {

    }

    public void latchShared(CacheMode ignore)
	throws DatabaseException {

    }

    public void releaseLatch()
	throws LatchNotHeldException {

    }

    public void verify(byte[] maxKey)
	throws DatabaseException {
    }

    /**
     * @return true if this node is a duplicate-bearing node type, false
     * if otherwise.
     */
    public boolean containsDuplicates() {
	return false;
    }

    /**
     * Cover for LN's and just return 0 since they'll always be at the bottom
     * of the tree.
     */
    public int getLevel() {
	return 0;
    }

    /*
     * Depth first search through a duplicate tree looking for an LN that has
     * nodeId.  When we find it, set location.bin and index and return true.
     * If we don't find it, return false.
     *
     * No latching is performed.
     */
    boolean matchLNByNodeId(TreeLocation location,
                            long nodeId,
                            CacheMode cachemode)
	throws DatabaseException {

	throw new DatabaseException("matchLNByNodeId called on non DIN/DBIN");
    }

    /**
     * Add yourself to the in memory list if you're a type of node that
     * should belong.
     */
    abstract void rebuildINList(INList inList)
        throws DatabaseException;

    /**
     * Remove yourself from the in memory list if you're a type of node that
     * is put there.
     */
    abstract void accountForSubtreeRemoval(INList inList,
                                           LocalUtilizationTracker
                                           localTracker)
        throws DatabaseException;

    /**
     * @return true if you're part of a deletable subtree.
     */
    abstract boolean isValidForDelete()
        throws DatabaseException;

    /**
     * @return true if you're an IN in the search path
     */
    abstract protected boolean isSoughtNode(long nid, CacheMode cacheMode)
        throws DatabaseException;

    /**
     * @return true if you can be the ancestor of the target IN.
     * Currently the determining factor is whether the target IN contains
     * duplicates.
     */
    abstract protected boolean canBeAncestor(boolean targetContainsDuplicates);

    /**
     * Return the approximate size of this node in memory, if this size should
     * be included in it's parents memory accounting.  For example, all INs
     * return 0, because they are accounted for individually. LNs must return a
     * count, they're not counted on the INList.
     */
    protected long getMemorySizeIncludedByParent() {
        return 0;
    }

    /*
     * Dumping
     */

    /**
     * Default toString method at the root of the tree.
     */
    @Override
    public String toString() {
        return this.dumpString(0, true);
    }

    private String beginTag() {
	return BEGIN_TAG;
    }

    private String endTag() {
	return END_TAG;
    }

    public void dump(int nSpaces) {
	System.out.print(dumpString(nSpaces, true));
    }

    String dumpString(int nSpaces, boolean dumpTags) {
        StringBuffer self = new StringBuffer();
        self.append(TreeUtils.indent(nSpaces));
	if (dumpTags) {
	    self.append(beginTag());
	}
        self.append(nodeId);
	if (dumpTags) {
	    self.append(endTag());
	}
        return self.toString();
    }

    public String shortDescription() {
	return "<" + getType() + "/" + getNodeId();
    }

    public String getType() {
	return getClass().getName();
    }

    /**
     */
    public abstract LogEntryType getLogType();

    /*
     * Logging support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return LogUtils.getPackedLongLogSize(nodeId);
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writePackedLong(logBuffer, nodeId);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryVersion)
	throws LogException {

        nodeId = LogUtils.readLong(itemBuffer, (entryVersion < 6));
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append(BEGIN_TAG);
        sb.append(nodeId);
        sb.append(END_TAG);
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }
}
