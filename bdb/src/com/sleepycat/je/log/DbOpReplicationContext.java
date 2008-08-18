/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DbOpReplicationContext.java,v 1.5 2008/01/07 14:28:51 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.ReplicatedDatabaseConfig;
import com.sleepycat.je.log.entry.DbOperationType;
import com.sleepycat.je.log.entry.NameLNLogEntry;


/**
 * This subclass of ReplicationContext adds information specific to database
 * operations to the replication context passed from operation-aware code down
 * the the logging layer. It's a way to transport enough information though the
 * NameLNLogEntry to logically replicate database operations.
 */
public class DbOpReplicationContext extends ReplicationContext {

    /*
     * Convenience static instance used when you know this database operation
     * will not be replicated, either because it's executing on a
     * non-replicated node or it's a local operation for a local database.
     */
    public static DbOpReplicationContext NO_REPLICATE =
        new DbOpReplicationContext(false, // inReplicationStream
                                   DbOperationType.NONE);

    private DbOperationType opType;
    private ReplicatedDatabaseConfig createConfig;
    private DatabaseId truncateOldDbId;

    /**
     * Create a replication context for logging a database operation NameLN on
     * the master.
     */
    public DbOpReplicationContext(boolean inReplicationStream,
                                  DbOperationType opType) {
        super(inReplicationStream);
        this.opType = opType;
    }

    /**
     * Create a repContext for executing a databaseOperation on the client.
     */
    public DbOpReplicationContext(LogEntryHeader header,
                                  NameLNLogEntry nameLNEntry) {

        /*
         * Initialize the context with the VLSN that was shipped with the
         * replicated log entry.
         */
        super(header.getVLSN());
        this.opType = nameLNEntry.getOperationType();
        if (opType == DbOperationType.CREATE) {
            createConfig = nameLNEntry.getReplicatedCreateConfig();
        }
    }

    @Override
    public DbOperationType getDbOperationType() {
        return opType;
    }

    public void setCreateConfig(ReplicatedDatabaseConfig createConfig) {
    	this.createConfig = createConfig;
    }

    public ReplicatedDatabaseConfig getCreateConfig() {
        return createConfig;
    }

    public void setTruncateOldDbId(DatabaseId truncateOldDbId) {
        this.truncateOldDbId = truncateOldDbId;
    }

    public DatabaseId getTruncateOldDbId() {
        return truncateOldDbId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append("opType=").append(opType);
        sb.append("truncDbId=").append(truncateOldDbId);
        return sb.toString();
    }
}
