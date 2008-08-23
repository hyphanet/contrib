/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Durability.java,v 1.5 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

/**
 * @hidden
 *
 * Durability defines the overall durability characteristics associated with a
 * transaction. When operating on a local environment the durability of a
 * transaction is completely determined by the local SyncPolicy that is in
 * effect. In a replicated environment, the overall durability is additionally
 * a function of the ReplicaAclPolicy used by the master and the SyncPolicy in
 * effect at each Replica.
 */
public class Durability {

    /**
     * Defines the synchronization policy to be used when committing a
     * transaction.
     */
    public enum SyncPolicy {

        /**
         *  Write and synchronously flush the log on transaction commit.
         *  Transactions exhibit all the ACID (atomicity, consistency,
         *  isolation, and durability) properties.
         *
         *  This is the default.
         */
        SYNC,

        /**
         * Do not write or synchronously flush the log on transaction commit.
         * Transactions exhibit the ACI (atomicity, consistency, and isolation)
         * properties, but not D (durability); that is, database integrity will
         * be maintained, but if the application or system fails, it is
         * possible some number of the most recently committed transactions may
         * be undone during recovery. The number of transactions at risk is
         * governed by how many log updates can fit into the log buffer, how
         * often the operating system flushes dirty buffers to disk, and how
         * often the log is checkpointed.
         */
        NO_SYNC,

        /**
         * Write but do not synchronously flush the log on transaction commit.
         * Transactions exhibit the ACI (atomicity, consistency, and isolation)
         * properties, but not D (durability); that is, database integrity will
         * be maintained, but if the operating system fails, it is possible
         * some number of the most recently committed transactions may be
         * undone during recovery. The number of transactions at risk is
         * governed by how often the operating system flushes dirty buffers to
         * disk, and how often the log is checkpointed.
         */
        WRITE_NO_SYNC
    };

    /**
     * A replicated environment makes it possible to increase an application's
     * transaction commit guarantees by committing changes to its replicas on
     * the network. ReplicaAckPolicy defines the policy for how such network
     * commits are handled.
     *
     * The choice of a ReplicaAckPolicy must be consistent across all the
     * replicas in a replication group, to ensure that the policy is
     * consistently enforced in the event of an election.
     */
    public enum ReplicaAckPolicy {

        /**
         * All replicas must acknowledge that they have committed the
         * transaction. This policy should be selected only if your replication
         * group has a small number of replicas, and those replicas are on
         * extremely reliable networks and servers.
         */
        ALL,

        /**
         * No transaction commit acknowledgments are required and the master
         * will never wait for replica acknowledgments. In this case,
         * transaction durability is determined entirely by the type of commit
         * that is being performed on the master.
         */
        NONE,

        /**
         * A quorum of replicas must acknowledge that they have committed the
         * transaction. A quorum is reached when acknowledgments are received
         * from the minimum number of environments needed to ensure that the
         * transaction remains durable if an election is held. That is, the
         * master wants to hear from enough replicas that they have committed
         * the transaction so that if an election is held, the modifications
         * will exist even if a new master is selected.
         *
         * This is the default.
         */
        QUORUM;

        /**
         * Returns the minimum number of replication nodes required to
         * implement the ReplicaAckPolicy for a given group size.
         *
         * @param groupSize the size of the replication group.
         *
         * @return the number of nodes that are needed
         */
        public int requiredNodes(int groupSize) {
            switch (this) {
            case ALL:
                return groupSize;
            case NONE:
                return 1;
            case QUORUM:
                return (groupSize <= 2) ? 1 : (groupSize / 2 + 1);
            }
            assert false : "unreachable";
            return Integer.MAX_VALUE;
        }
    }

    /* The sync policy in effect on the local node. */
    final private SyncPolicy localSync;

    /* The sync policy in effect on a replica. */
    final private SyncPolicy replicaSync;

    /* The replica acknowledgment policy to be used. */
    final private ReplicaAckPolicy replicaAck;

    /**
     * Creates an instance of a Durability specification.
     *
     * @param localSync the SyncPolicy to be used when committing the
     * transaction locally.
     * @param replicaSync the SyncPolicy to be used remotely, as part of a
     * transaction acknowledgment, at a Replica node.
     * @param replicaAck the acknowledgment policy used when obtaining
     * transaction acknowledgments from Replicas.
     */
    public Durability(SyncPolicy localSync,
                      SyncPolicy replicaSync,
                      ReplicaAckPolicy replicaAck) {
        this.localSync = localSync;
        this.replicaSync = replicaSync;
        this.replicaAck = replicaAck;
    }

    /**
     * Returns the transaction synchronization policy to be used locally when
     * committing a transaction.
     */
    public SyncPolicy getLocalSync() {
        return localSync;
    }

    /**
     * Returns the transaction synchronization policy to be used by the replica
     * as it replays a transaction that needs an acknowledgment.
     */
    public SyncPolicy getReplicaSync() {
        return replicaSync;
    }

    /**
     * Returns the replica acknowledgment policy used by the master when
     * committing changes to a replicated environment.
     */
    public ReplicaAckPolicy getReplicaAck() {
        return replicaAck;
    }
}
