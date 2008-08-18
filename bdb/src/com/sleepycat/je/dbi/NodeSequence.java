/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: NodeSequence.java,v 1.1 2008/05/06 18:01:32 linda Exp $
 */

package com.sleepycat.je.dbi;

import java.util.concurrent.atomic.AtomicLong;

/**
 * NodeSequence encapsulates the generation and maintenance of a sequence for
 * generating node ids.
 */
public class NodeSequence {

    /*
     * Node Ids: We need to ensure that local and replicated nodes use
     * different number spaces for their ids, so there can't be any possible
     * conflicts.  Local, non replicated nodes use positive values, replicated
     * nodes use negative values. On top of that, there is the notion of
     * transient node ids, which are used for cases like the eof node used for
     * Serializable isolation and the lock used for api lockout. Transient node
     * ids are used to provide unique locks, and are only used during the life
     * of an environment, for non-persistent objects. We use the descending
     * sequence of positive values, starting from Long.MAX_VALUE.
     *
     * The transient node sequence must be initialized before the DbTree
     * uber-tree is created, because they are used at DatabaseImpl
     * construction.  The local and replicated node id sequences are
     * initialized by the first pass of recovery, after the log has been
     * scanned for the latest used node id.
     */
    private AtomicLong lastAllocatedLocalNodeId = null;
    private AtomicLong lastAllocatedReplicatedNodeId = null;
    private AtomicLong lastAllocatedTransientNodeId = null;

    /**
     * Initialize the counters in these methods rather than a constructor
     * so we can control the initialization more precisely.
     */
    void initTransientNodeId() {
        lastAllocatedTransientNodeId = new AtomicLong(Long.MAX_VALUE);
    }

    /**
     * Initialize the counters in these methods rather than a constructor
     * so we can control the initialization more precisely.
     */
    void initRealNodeId() {
        lastAllocatedLocalNodeId = new AtomicLong(0);
        lastAllocatedReplicatedNodeId = new AtomicLong(0);
    }

    /**
     * The last allocated local and replicated node ids are used for ckpts.
     */
    public long getLastLocalNodeId() {
        return lastAllocatedLocalNodeId.get();
    }

    public long getLastReplicatedNodeId() {
        return lastAllocatedReplicatedNodeId.get();
    }

    /**
     * We get a new node id of the appropriate kind when creating a new node.
     */
    public long getNextLocalNodeId() {
        return lastAllocatedLocalNodeId.incrementAndGet();
    }

    public long getNextReplicatedNodeId() {
        return lastAllocatedReplicatedNodeId.decrementAndGet();
    }

    public long getNextTransientNodeId() {
        /* Assert that the two sequences haven't overlapped. */
        assert (noOverlap()) : "transient=" +
            lastAllocatedTransientNodeId.get();
        return lastAllocatedTransientNodeId.decrementAndGet();
    }

    private boolean noOverlap() {
        if (lastAllocatedLocalNodeId != null) {
            return (lastAllocatedTransientNodeId.get() - 1) >
                lastAllocatedLocalNodeId.get();
        } else {
            return true;
        }
    }

    /**
     * Initialize the node ids, from recovery. No need to initialize
     * the transient node ids, since those can be reused each time the
     * environment is recreated.
     */
    public void setLastNodeId(long lastReplicatedNodeId,
                              long lastLocalNodeId) {
        lastAllocatedReplicatedNodeId.set(lastReplicatedNodeId);
        lastAllocatedLocalNodeId.set(lastLocalNodeId);
    }

    /* 
     * Only set the replicated node id if the replayNodeId represents a
     * newer, later value in the replication stream. If the replayNodeId is
     * earlier than this node's lastAllocatedReplicateNodeId, don't bother
     * updating the sequence;
     */
    public void updateFromReplay(long replayNodeId) {

        assert replayNodeId < 0 : 
            "replay node id is unexpectedly positive " + replayNodeId;

        while (true) {
            long currentVal = lastAllocatedReplicatedNodeId.get();
            if (replayNodeId < currentVal) {
                /* 
                 * This replayNodeId is newer than any other replicatedNodeId
                 * known by this node.
                 */
                boolean ok = lastAllocatedReplicatedNodeId.weakCompareAndSet
                    (currentVal, replayNodeId);
                if (ok) {
                    break;
                }
            } else {
                break;
            }
        }
    }
}
