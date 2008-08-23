/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DbTree.java,v 1.206 2008/06/10 02:52:10 cwl Exp $
 */

package com.sleepycat.je.dbi;

import static com.sleepycat.je.log.entry.DbOperationType.CREATE;
import static com.sleepycat.je.log.entry.DbOperationType.RENAME;
import static com.sleepycat.je.log.entry.DbOperationType.REMOVE;
import static com.sleepycat.je.log.entry.DbOperationType.TRUNCATE;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.tree.NameLN;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeUtils;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.utilint.DbLsn;

/**
 * DbTree represents the database directory for this environment. DbTree is
 * itself implemented through two databases. The nameDatabase maps
 * databaseName-> an internal databaseId. The idDatabase maps
 * databaseId->DatabaseImpl.
 *
 * For example, suppose we have two databases, foo and bar. We have the
 * following structure:
 *
 *           nameDatabase                          idDatabase
 *               IN                                    IN
 *                |                                     |
 *               BIN                                   BIN
 *    +-------------+--------+            +---------------+--------+
 *  .               |        |            .               |        |
 * NameLNs         NameLN    NameLN      MapLNs for   MapLN        MapLN
 * for internal    key=bar   key=foo     internal dbs key=53       key=79
 * dbs             data=     data=                    data=        data=
 *                 dbId79    dbId53                   DatabaseImpl DatabaseImpl
 *                                                        |            |
 *                                                   Tree for foo  Tree for bar
 *                                                        |            |
 *                                                     root IN       root IN
 *
 * Databases, Cursors, the cleaner, compressor, and other entities have
 * references to DatabaseImpls. It's important that object identity is properly
 * maintained, and that all constituents reference the same DatabaseImpl for
 * the same db, lest they develop disparate views of the in-memory database;
 * corruption would ensue. To ensure that, all entities must obtain their
 * DatabaseImpl by going through the idDatabase.
 *
 * DDL type operations such as create, rename, remove and truncate get their
 * transactional semantics by transactionally locking the NameLN appropriately.
 * A read-lock on the NameLN, called a handle lock, is maintained for all DBs
 * opened via the public API (openDatabase).  This prevents them from being
 * renamed or removed while open.
 *
 * However, for internal database operations, no handle lock on the NameLN is
 * acquired and MapLNs are locked with short-lived non-transactional Lockers.
 * An entity that is trying to get a reference to the DatabaseImpl gets a short
 * lived read lock just for the fetch of the MapLN. A write lock on the MapLN
 * is taken when the database is created, deleted, or when the MapLN is
 * evicted. (see DatabaseImpl.isInUse())
 *
 * The nameDatabase operates pretty much as a regular application database in
 * terms of eviction and recovery. The idDatabase requires special treatment
 * for both eviction and recovery.
 *
 * The issues around eviction of the idDatabase center on the need to ensure
 * that there are no other current references to the DatabaseImpl other than
 * that held by the mapLN. The presence of a current reference would both make
 * the DatabaseImpl not GC'able, and more importantly, would lead to object
 * identify confusion later on. For example, if the MapLN is evicted while
 * there is a current reference to its DatabaseImpl, and then refetched, there
 * will be two in-memory versions of the DatabaseImpl. Since locks on the
 * idDatabase are short lived, DatabaseImpl.useCount acts as a reference count
 * of active current references. DatabaseImpl.useCount must be modified and
 * read in conjunction with appropropriate locking on the MapLN. See
 * DatabaseImpl.isInUse() for details.
 *
 * This reference count checking is only needed when the entire MapLN is
 * evicted. It's possible to evict only the root IN of the database in
 * question, since that doesn't interfere with the DatabaseImpl object
 * identity.
 */
public class DbTree implements Loggable {

    /* The id->DatabaseImpl tree is always id 0 */
    public static final DatabaseId ID_DB_ID = new DatabaseId(0);
    /* The name->id tree is always id 1 */
    public static final DatabaseId NAME_DB_ID = new DatabaseId(1);

    /* Internal databases - the database mapping tree and utilization info. */
    private static final String ID_DB_NAME = "_jeIdMap";
    private static final String NAME_DB_NAME = "_jeNameMap";
    public static final String UTILIZATION_DB_NAME = "_jeUtilization";
    public static final String REP_OPERATIONS_NAME = "_jeRepOp";
    public static final String VLSN_MAP_DB_NAME = "_vlsnMapDb";

    /* Reserved database names. */
    private static final String[] RESERVED_DB_NAMES = {
        ID_DB_NAME,
        NAME_DB_NAME,
        UTILIZATION_DB_NAME,
        REP_OPERATIONS_NAME,
        VLSN_MAP_DB_NAME
    };

    /*
     * Database Ids:
     * We need to ensure that local and replicated databases use different
     * number spaces for their ids, so there can't be any possible conflicts.
     * Local, non replicated databases use positive values, replicated
     * databases use negative values.  -1 thru -5 are reserved for future
     * special use.
     */
    public static final int NEG_DB_ID_START = -256;
    private AtomicInteger lastAllocatedLocalDbId;
    private AtomicInteger lastAllocatedReplicatedDbId;

    private DatabaseImpl idDatabase;          // map db ids -> databases
    private DatabaseImpl nameDatabase;        // map names -> dbIds

    /* The flags byte holds a variety of attributes. */
    private byte flags;

    /*
     * The replicated bit is set for environments that are opened with
     * replication. The behavior is as follows:
     *
     * Env is     Env is     Persistent          Follow-on action
     * replicated brand new  value of
     *                       DbTree.isReplicated
     *
     * 0             1         n/a               replicated bit = 0;
     * 0             0           0               none
     * 0             0           1               illegal, exception thrown
     * 1             1          n/a              replicated bit = 1
     * 1             0           0               require config of all dbs
     * 1             0           1               none
     */
    private static final byte REPLICATED_BIT = 0x1;

    private EnvironmentImpl envImpl;

    /**
     * Create a dbTree from the log.
     */
    public DbTree()
        throws DatabaseException {
                
        this.envImpl = null;
        idDatabase = new DatabaseImpl();
        idDatabase.setDebugDatabaseName(ID_DB_NAME);

        /* 
         * The default is false, but just in case we ever turn it on globally
         * for testing this forces it off.
         */
        idDatabase.clearKeyPrefixing();
        nameDatabase = new DatabaseImpl();
        nameDatabase.clearKeyPrefixing();
        nameDatabase.setDebugDatabaseName(NAME_DB_NAME);

        /* These sequences are initialized by readFromLog. */
        lastAllocatedLocalDbId = new AtomicInteger();
        lastAllocatedReplicatedDbId = new AtomicInteger();
    }

    /**
     * Create a new dbTree for a new environment.
     */
    public DbTree(EnvironmentImpl env, boolean replicationIntended)
        throws DatabaseException {

        this.envImpl = env;

        /*
         * Sequences must be initialized before any databases are created.  0
         * and 1 are reserved, so we start at 2. We've put -1 to
         * NEG_DB_ID_START asided for the future.
         */
        lastAllocatedLocalDbId = new AtomicInteger(1);
        lastAllocatedReplicatedDbId = new AtomicInteger(NEG_DB_ID_START);

        /* The id database is local */
        DatabaseConfig idConfig = new DatabaseConfig();
        DbInternal.setDbConfigReplicated(idConfig, false /* replicated */);

        /* 
         * The default is false, but just in case we ever turn it on globally
         * for testing this forces it off.
         */
        idConfig.setKeyPrefixing(false);
        idDatabase = new DatabaseImpl(ID_DB_NAME,
                                      new DatabaseId(0),
                                      env,
                                      idConfig);
        /* Force a reset if enabled globally. */
        idDatabase.clearKeyPrefixing();

        DatabaseConfig nameConfig = new DatabaseConfig();
        nameConfig.setKeyPrefixing(false);
        nameDatabase = new DatabaseImpl(NAME_DB_NAME,
					new DatabaseId(1),
					env,
                                        nameConfig);
        /* Force a reset if enabled globally. */
        nameDatabase.clearKeyPrefixing();

        if (replicationIntended) {
            setIsReplicated();
        }
    }

    /**
     * The last allocated local and replicated db ids are used for ckpts.
     */
    public int getLastLocalDbId() {
        return lastAllocatedLocalDbId.get();
    }

    public int getLastReplicatedDbId() {
        return lastAllocatedReplicatedDbId.get();
    }

    /**
     * We get a new database id of the appropriate kind when creating a new
     * database.
     */
    private int getNextLocalDbId() {
        return lastAllocatedLocalDbId.incrementAndGet();
    }

    private int getNextReplicatedDbId() {
        return lastAllocatedReplicatedDbId.decrementAndGet();
    }

    /**
     * Initialize the db ids, from recovery.
     */
    public void setLastDbId(int lastReplicatedDbId, int lastLocalDbId) {
        lastAllocatedReplicatedDbId.set(lastReplicatedDbId);
        lastAllocatedLocalDbId.set(lastLocalDbId);
    }

    /**
     * @return true if this id is for a replicated db.
     */
    private boolean isReplicatedId(int id) {
        return id < NEG_DB_ID_START;
    }

    /* 
     * Only set the replicated db id if the replayDbId represents a
     * newer, later value in the replication stream. If the replayDbId is
     * earlier than this node's lastAllocatedReplicateDbId, don't bother
     * updating the sequence;
     */
    public void updateFromReplay(DatabaseId replayDbId) {

        int replayVal = replayDbId.getId();

        assert replayVal < 0 : 
            "replay node id is unexpectedly positive " + replayDbId;

        while (true) {
            int currentVal = lastAllocatedReplicatedDbId.get();
            if (replayVal < currentVal) {
                /* 
                 * This replayDbId is newer than any other replicated db id
                 * known by this node.
                 */
                boolean ok = lastAllocatedReplicatedDbId.weakCompareAndSet
                    (currentVal, replayVal);
                if (ok) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    /**
     * Initialize the db tree during recovery, after instantiating the tree
     * from the log.
     * a. set up references to the environment impl
     * b. check for replication rules.
     */
    void initExistingEnvironment(EnvironmentImpl envImpl,
                                 boolean replicationIntended)
        throws DatabaseException {

        if (replicationIntended) {
            if (!isReplicated()) {
                throw new UnsupportedOperationException
                    ("This environment must be converted for replication." +
                     "Conversion isn't supported yet.");
            }
        } else {
            if (isReplicated() && (!envImpl.isReadOnly())) {
                throw new DatabaseException
                    ("This environment was previously opened for replication."+
                     " It cannot be re-opened for in read/write mode for" +
                     " standalone operation.");
            }
        }

        this.envImpl = envImpl;
        idDatabase.setEnvironmentImpl(envImpl);
        nameDatabase.setEnvironmentImpl(envImpl);
    }

    /**
     * Creates a new database object given a database name.
     *
     * Increments the use count of the new DB to prevent it from being evicted.
     * releaseDb should be called when the returned object is no longer used,
     * to allow it to be evicted.  See DatabaseImpl.isInUse.  [#13415]
     */
    public DatabaseImpl createDb(Locker locker,
                                 String databaseName,
                                 DatabaseConfig dbConfig,
                                 Database databaseHandle)
        throws DatabaseException {

        return doCreateDb(locker,
                          databaseName,
                          dbConfig,
                          databaseHandle,
                          null,  // replicatedLN
                          null); // repContext, to be decided by new db
    }

    /**
     * Create a database for internal use that will never be replicated.
     */
    public DatabaseImpl createInternalDb(Locker locker,
                                         String databaseName,
                                         DatabaseConfig dbConfig)
        throws DatabaseException {

        DbInternal.setDbConfigReplicated(dbConfig, false);
        /* Force all internal databases to not use key prefixing. */
        dbConfig.setKeyPrefixing(false);
        DatabaseImpl ret = doCreateDb(locker,
                                      databaseName,
                                      dbConfig,
                                      null,  // databaseHandle,
                                      null,  // replicatedLN
                                      ReplicationContext.NO_REPLICATE);
        /* Force a reset if enabled globally. */
        ret.clearKeyPrefixing();
        return ret;
    }

    /**
     * Create a replicated database on this client node.
     */
    public DatabaseImpl createClientDb(Locker locker,
                                       String databaseName,
                                       DatabaseConfig dbConfig,
                                       NameLN replicatedLN,
                                       ReplicationContext repContext)
        throws DatabaseException {

        return doCreateDb(locker,
                          databaseName,
                          dbConfig,
                          null, // databaseHndle
                          replicatedLN,
                          repContext);
    }

    /**
     * Create a database.
     *
     * Increments the use count of the new DB to prevent it from being evicted.
     * releaseDb should be called when the returned object is no longer used,
     * to allow it to be evicted.  See DatabaseImpl.isInUse.  [#13415]
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     */
    private synchronized DatabaseImpl doCreateDb(Locker nameLocker,
                                                 String databaseName,
                                                 DatabaseConfig dbConfig,
                                                 Database databaseHandle,
                                                 NameLN replicatedLN,
                                                 ReplicationContext repContext)
        throws DatabaseException {

        /* Create a new database object. */
        DatabaseId newId = null;
        if (replicatedLN != null) {

            /*
             * This database was created on a master node and is being
             * propagated to this client node.
             */
            newId = replicatedLN.getId();
        } else {

            /*
             * This database has been created locally, either because this is
             * a  non-replicated node or this is the replicated group master.
             */
            if (envImpl.isReplicated() &&
                DbInternal.getDbConfigReplicated(dbConfig)) {
                newId = new DatabaseId(getNextReplicatedDbId());
            } else {
                newId = new DatabaseId(getNextLocalDbId());
            }
        }

        DatabaseImpl newDb =
            new DatabaseImpl(databaseName, newId, envImpl, dbConfig);
        CursorImpl idCursor = null;
        CursorImpl nameCursor = null;
        boolean operationOk = false;
        Locker idDbLocker = null;
        try {
            /* Insert it into name -> id db. */
            nameCursor = new CursorImpl(nameDatabase, nameLocker);
            LN nameLN = null;
            if (replicatedLN != null) {
                nameLN = replicatedLN;
            } else {
                nameLN = new NameLN(newId, envImpl, newDb.isReplicated());
            }

            ReplicationContext useRepContext = repContext;
            if (repContext == null) {
                useRepContext = newDb.getOperationRepContext(CREATE);
            }
            nameCursor.putLN(databaseName.getBytes("UTF-8"),// key
                             nameLN,
                             false,                         // allowDuplicates
                             useRepContext);

            /*
             * If this is a non-handle use, no need to record any handle locks.
             */
            if (databaseHandle != null) {
                nameLocker.addToHandleMaps(Long.valueOf(nameLN.getNodeId()),
                                           databaseHandle);
            }

            /* Insert it into id -> name db, in auto commit mode. */
            idDbLocker = BasicLocker.createBasicLocker(envImpl);
            idCursor = new CursorImpl(idDatabase, idDbLocker);
            idCursor.putLN(newId.getBytes(), // key
                           new MapLN(newDb), // ln
                           false,            // allowDuplicates
                           ReplicationContext.NO_REPLICATE);
            /* Increment DB use count with lock held. */
            newDb.incrementUseCount();
            operationOk = true;
        } catch (UnsupportedEncodingException UEE) {
            throw new DatabaseException(UEE);
        } finally {
            if (idCursor != null) {
                idCursor.close();
            }

            if (nameCursor != null) {
                nameCursor.close();
            }

            if (idDbLocker != null) {
                idDbLocker.operationEnd(operationOk);
            }
        }

        return newDb;
    }

    /**
     * Check deferred write settings before writing the MapLN.
     * @param db the database represented by this MapLN
     */
    public void optionalModifyDbRoot(DatabaseImpl db)
        throws DatabaseException {
        
        if (db.isDeferredWriteMode()) {
            return;
        }

        modifyDbRoot(db);
    }

    /**
     * Write the MapLN to disk.
     * @param db the database represented by this MapLN
     */
    public void modifyDbRoot(DatabaseImpl db)
        throws DatabaseException {

        modifyDbRoot(db, DbLsn.NULL_LSN /*ifBeforeLsn*/, true /*mustExist*/);
    }

    /**
     * Write a MapLN to the log in order to:
     *  - propagate a root change
     *  - save per-db utilization information
     *  - save database config information.
     * Any MapLN writes must be done through this method, in order to ensure
     * that the root latch is taken, and updates to the rootIN are properly
     * safeguarded. See MapN.java for more detail.
     *
     * @param db the database whose root is held by this MapLN
     *
     * @param ifBeforeLsn if argument is not NULL_LSN, only do the write if
     * this MapLN's current LSN is before isBeforeLSN.
     *
     * @param if true, throw DatabaseException if the DB does not exist; if
     * false, silently do nothing.
     */
    public void modifyDbRoot(DatabaseImpl db,
                             long ifBeforeLsn,
                             boolean mustExist)
        throws DatabaseException {

        if (db.getId().equals(ID_DB_ID) ||
            db.getId().equals(NAME_DB_ID)) {
            envImpl.logMapTreeRoot();
        } else {
            DatabaseEntry keyDbt = new DatabaseEntry(db.getId().getBytes());

            /*
             * Retry indefinitely in the face of lock timeouts since the
             * lock on the MapLN is only supposed to be held for short
             * periods.
             */
            while (true) {
                Locker idDbLocker = null;
                CursorImpl cursor = null;
                boolean operationOk = false;
                try {
                    idDbLocker = BasicLocker.createBasicLocker(envImpl);
                    cursor = new CursorImpl(idDatabase, idDbLocker);
                    boolean searchOk = (cursor.searchAndPosition
                                        (keyDbt, new DatabaseEntry(),
                                         SearchMode.SET, LockType.WRITE) &
                                        CursorImpl.FOUND) != 0;
                    if (!searchOk) {
                        if (mustExist) {
                            throw new DatabaseException(
                                "can't find database " + db.getId());
                        } else {
                            /* Do nothing silently. */
                            break;
                        }
                    }
                    /* Check BIN LSN while latched. */
                    if (ifBeforeLsn == DbLsn.NULL_LSN ||
                        DbLsn.compareTo
                            (cursor.getBIN().getLsn(cursor.getIndex()),
                             ifBeforeLsn) < 0) {
                        MapLN mapLN = (MapLN) cursor.getCurrentLNAlreadyLatched
                            (LockType.WRITE);
                        assert mapLN != null; /* Should be locked. */
                        /* Perform rewrite. */
                        RewriteMapLN writeMapLN = new RewriteMapLN(cursor);
                        mapLN.getDatabase().getTree().
                            withRootLatchedExclusive(writeMapLN);
                        operationOk = true;
                    }
                    break;
                } catch (DeadlockException DE) {
                    /* Continue loop and retry. */
                } finally {
                    if (cursor != null) {
                        cursor.releaseBIN();
                        cursor.close();
                    }
                    if (idDbLocker != null) {
                        idDbLocker.operationEnd(operationOk);
                    }
                }
            }
        }
    }

    private static class RewriteMapLN implements WithRootLatched {
        private CursorImpl cursor;

        RewriteMapLN(CursorImpl cursor) {
            this.cursor = cursor;
        }

        public IN doWork(ChildReference root)
            throws DatabaseException {

            DatabaseEntry dataDbt = new DatabaseEntry(new byte[0]);
            cursor.putCurrent(dataDbt,
                              null,  // foundKey
                              null,  // foundData
                              ReplicationContext.NO_REPLICATE);
            return null;
        }
    }

    /*
     * Helper for database operations. This method positions a cursor
     * on the NameLN that represents this database and write locks it.
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     */
    private NameLockResult lockNameLN(Locker locker,
                                      String databaseName,
                                      String action)
        throws DatabaseException {

        /*
         * We have to return both a cursor on the nameing tree and a
         * reference to the found DatabaseImpl.
         */
        NameLockResult result = new NameLockResult();

        /* Find the existing DatabaseImpl and establish a cursor. */
        result.dbImpl = getDb(locker, databaseName, null);
        if (result.dbImpl == null) {
            throw new DatabaseNotFoundException
                ("Attempted to " + action + " non-existent database " +
                 databaseName);
        }
        boolean success = false;
        try {
            result.nameCursor = new CursorImpl(nameDatabase, locker);

            /* Position the cursor at the specified NameLN. */
            DatabaseEntry key =
                new DatabaseEntry(databaseName.getBytes("UTF-8"));
            /* See [#16210]. */
            boolean found =
                (result.nameCursor.searchAndPosition(key, null, SearchMode.SET,
                                                     LockType.WRITE) &
                 CursorImpl.FOUND) != 0;
            if (!found) {
                result.nameCursor.releaseBIN();
                result.nameCursor.close();
                result.nameCursor = null;
                return result;
            }

            /* Call getCurrentLN to write lock the nameLN. */
            result.nameLN = (NameLN)
                result.nameCursor.getCurrentLNAlreadyLatched(LockType.WRITE);
            assert result.nameLN != null; /* Should be locked. */

            /*
             * Check the open handle count after we have the write lock and no
             * other transactions can open.
             */
            int handleCount = result.dbImpl.getReferringHandleCount();
            if (handleCount > 0) {
                throw new DatabaseException("Can't " + action + " database " +
                                            databaseName + "," + handleCount +
                                            " open Dbs exist");
            }
            success = true;
        } catch (UnsupportedEncodingException UEE) {
            throw new DatabaseException(UEE);
        } finally {
            if (!success) {
                releaseDb(result.dbImpl);
                if (result.nameCursor != null) {
                    result.nameCursor.releaseBIN();
                    result.nameCursor.close();
                }
            }
        }

        return result;
    }

    private static class NameLockResult {
        CursorImpl nameCursor;
        DatabaseImpl dbImpl;
        NameLN nameLN;
    }

    /**
     * Return true if the operation succeeded, false otherwise.
     */
    public boolean dbRename(Locker locker,
                            String databaseName,
                            String newName)
        throws DatabaseException {

        CursorImpl nameCursor = null;
        NameLockResult result = lockNameLN(locker, databaseName, "rename");
        try {
            nameCursor = result.nameCursor;
            if (nameCursor == null) {
                return false;
            } else {

                /*
                 * Rename simply deletes the one entry in the naming
                 * tree and replaces it with a new one. Remove the
                 * oldName->dbId entry and insert newName->dbId.
                 */
                nameCursor.latchBIN();
                nameCursor.delete
                    (result.dbImpl.getOperationRepContext(RENAME));
                nameCursor.putLN(newName.getBytes("UTF-8"),
                                 new NameLN(result.dbImpl.getId(),
                                            envImpl,
                                            result.dbImpl.isReplicated()),
                                 false,  // allowDuplicates
                                 result.dbImpl.getOperationRepContext(RENAME));
                result.dbImpl.setDebugDatabaseName(newName);
                return true;
            }
        } catch (UnsupportedEncodingException UEE) {
            throw new DatabaseException(UEE);
        } finally {
            releaseDb(result.dbImpl);
            if (nameCursor != null) {
                nameCursor.releaseBIN();
                nameCursor.close();
            }
        }
    }

    /**
     * Remove the database by deleting the nameLN.  Does nothing if the
     * non-null checkId argument does not match the database identified by
     * databaseName.  Does nothing if the database name does not exist.
     */
    public void dbRemove(Locker locker,
                         String databaseName,
                         DatabaseId checkId)
        throws DatabaseException {

        CursorImpl nameCursor = null;
        NameLockResult result = lockNameLN(locker, databaseName, "remove");
        try {
            nameCursor = result.nameCursor;
            if ((nameCursor == null) ||
                (checkId != null &&
                 !checkId.equals(result.nameLN.getId()))) {
                return;
            } else {

                /*
                 * Delete the NameLN. There's no need to mark any Database
                 * handle invalid, because the handle must be closed when we
                 * take action and any further use of the handle will re-look
                 * up the database.
                 */
                nameCursor.latchBIN();
                nameCursor.delete
                    (result.dbImpl.getOperationRepContext(REMOVE));

                /*
                 * Schedule database for final deletion during commit. This
                 * should be the last action taken, since this will take
                 * effect immediately for non-txnal lockers.
                 *
                 * Do not call releaseDb here on result.dbImpl, since that is
                 * taken care of by markDeleteAtTxnEnd.
                 */
                locker.markDeleteAtTxnEnd(result.dbImpl, true);
            }
        } finally {
            if (nameCursor != null) {
                nameCursor.releaseBIN();
                nameCursor.close();
            }
        }
    }

    /**
     * To truncate, remove the database named by databaseName and
     * create a new database in its place.
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     *
     * @param returnCount if true, must return the count of records in the
     * database, which can be an expensive option.
     */
    public long truncate(Locker locker,
                         String databaseName,
                         boolean returnCount)
        throws DatabaseException {

        CursorImpl nameCursor = null;
        Locker idDbLocker = null;
        NameLockResult result = lockNameLN(locker, databaseName, "truncate");
        try {
            nameCursor = result.nameCursor;
            if (nameCursor == null) {
                return 0;
            } else {

                /*
                 * Make a new database with an empty tree. Make the nameLN
                 * refer to the id of the new database. If this database is
                 * replicated, the new one should also be replicated, and vice
                 * versa.
                 */
                DatabaseImpl oldDb = result.dbImpl;
                DatabaseId newId = null;
                if (isReplicatedId(oldDb.getId().getId())) {
                    newId = new DatabaseId(getNextReplicatedDbId());
                } else {
                    newId = new DatabaseId(getNextLocalDbId());
                }

                DatabaseImpl newDb = oldDb.cloneDatabase();
                newDb.incrementUseCount();
                newDb.setId(newId);
                newDb.setTree(new Tree(newDb));

                /*
                 * Insert the new MapLN into the id tree. Do not use
                 * a transaction on the id databaase, because we can not
                 * hold long term locks on the mapLN.
                 */
                CursorImpl idCursor = null;
                boolean operationOk = false;
                try {
                    idDbLocker = BasicLocker.createBasicLocker(envImpl);
                    idCursor = new CursorImpl(idDatabase, idDbLocker);
                    idCursor.putLN(newId.getBytes(), // key
                                   new MapLN(newDb), // ln
                                   false,            // allowDuplicates
                                   ReplicationContext.NO_REPLICATE);
                    operationOk = true;
                } finally {
                    if (idCursor != null) {
                        idCursor.close();
                    }

                    if (idDbLocker != null) {
                        idDbLocker.operationEnd(operationOk);
                    }
                }
                result.nameLN.setId(newDb.getId());

                /* If required, count the number of records in the database. */
                long recordCount = 0;
                if (returnCount) {
                    recordCount = oldDb.count();
                }

                /* log the nameLN. */
                DatabaseEntry dataDbt = new DatabaseEntry(new byte[0]);
                nameCursor.putCurrent(dataDbt,
                                      null,  // foundKey
                                      null,  // foundData
                                      oldDb.getOperationRepContext(TRUNCATE));

                /*
                 * Marking the lockers should be the last action, since it
                 * takes effect immediately for non-txnal lockers.
                 *
                 * Do not call releaseDb here on oldDb or newDb, since that is
                 * taken care of by markDeleteAtTxnEnd.
                 */

                /* Schedule old database for deletion if txn commits. */
                locker.markDeleteAtTxnEnd(oldDb, true);

                /* Schedule new database for deletion if txn aborts. */
                locker.markDeleteAtTxnEnd(newDb, false);

                return recordCount;
            }
        } finally {
            if (nameCursor != null) {
                nameCursor.releaseBIN();
                nameCursor.close();
            }
        }
    }

    /*
     * Remove the mapLN that refers to this database.
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     */
    void deleteMapLN(DatabaseId id)
        throws DatabaseException {

        /*
         * Retry indefinitely in the face of lock timeouts since the lock on
         * the MapLN is only supposed to be held for short periods.
         */
        boolean done = false;
        while (!done) {
            Locker idDbLocker = null;
            CursorImpl idCursor = null;
            boolean operationOk = false;
            try {
                idDbLocker = BasicLocker.createBasicLocker(envImpl);
                idCursor = new CursorImpl(idDatabase, idDbLocker);
                boolean found =
                    (idCursor.searchAndPosition
                        (new DatabaseEntry(id.getBytes()), null,
                         SearchMode.SET, LockType.WRITE) &
                     CursorImpl.FOUND) != 0;
                if (found) {

                    /*
                     * If the database is in use by an internal JE operation
                     * (checkpointing, cleaning, etc), release the lock (done
                     * in the finally block) and retry.  [#15805]
                     */
                    MapLN mapLN = (MapLN)
                        idCursor.getCurrentLNAlreadyLatched(LockType.WRITE);
                    assert mapLN != null;
                    DatabaseImpl dbImpl = mapLN.getDatabase();
                    if (!dbImpl.isInUseDuringDbRemove()) {
                        idCursor.latchBIN();
                        idCursor.delete(ReplicationContext.NO_REPLICATE);
                        done = true;
                    }
                } else {
                    /* MapLN does not exist. */
                    done = true;
                }
                operationOk = true;
            } catch (DeadlockException DE) {
                /* Continue loop and retry. */
            } finally {
                if (idCursor != null) {
                    idCursor.releaseBIN();
                    idCursor.close();
                }
                if (idDbLocker != null) {
                    idDbLocker.operationEnd(operationOk);
                }
            }
        }
    }

    /**
     * Get a database object given a database name.  Increments the use count
     * of the given DB to prevent it from being evicted.  releaseDb should be
     * called when the returned object is no longer used, to allow it to be
     * evicted.  See DatabaseImpl.isInUse.
     * [#13415]
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     *
     * @param nameLocker is used to access the NameLN. As always, a NullTxn
     *  is used to access the MapLN.
     * @param databaseName target database
     * @return null if database doesn't exist
     */
    public DatabaseImpl getDb(Locker nameLocker,
                              String databaseName,
                              Database databaseHandle)
        throws DatabaseException {

        try {
            /* Use count is not incremented for idDatabase and nameDatabase. */
            if (databaseName.equals(ID_DB_NAME)) {
                return idDatabase;
            } else if (databaseName.equals(NAME_DB_NAME)) {
                return nameDatabase;
            }

            /*
             * Search the nameDatabase tree for the NameLn for this name.
             */
            CursorImpl nameCursor = null;
            DatabaseId id = null;
            try {
                nameCursor = new CursorImpl(nameDatabase, nameLocker);
                DatabaseEntry keyDbt =
                    new DatabaseEntry(databaseName.getBytes("UTF-8"));
                boolean found =
                    (nameCursor.searchAndPosition(keyDbt, null,
                                                  SearchMode.SET,
                                                  LockType.READ) &
                     CursorImpl.FOUND) != 0;

                if (found) {
                    NameLN nameLN = (NameLN)
                        nameCursor.getCurrentLNAlreadyLatched(LockType.READ);
                    assert nameLN != null; /* Should be locked. */
                    id = nameLN.getId();

                    /*
                     * If this is a non-handle use, no need to record any
                     * handle locks.
                     */
                    if (databaseHandle != null) {
                        nameLocker.addToHandleMaps
                            (Long.valueOf(nameLN.getNodeId()), databaseHandle);
                    }
                }
            } finally {
                if (nameCursor != null) {
                    nameCursor.releaseBIN();
                    nameCursor.close();
                }
            }

            /*
             * Now search the id tree.
             */
            if (id == null) {
                return null;
            } else {
                return getDb(id, -1, databaseName);
            }
        } catch (UnsupportedEncodingException UEE) {
            throw new DatabaseException(UEE);
        }
    }

    /**
     * Get a database object based on an id only.  Used by recovery, cleaning
     * and other clients who have an id in hand, and don't have a resident
     * node, to find the matching database for a given log entry.
     */
    public DatabaseImpl getDb(DatabaseId dbId)
        throws DatabaseException {

        return getDb(dbId, -1);
    }

    /**
     * Get a database object based on an id only. Specify the lock timeout to
     * use, or -1 to use the default timeout.  A timeout should normally only
     * be specified by daemons with their own timeout configuration.  public
     * for unit tests.
     */
    public DatabaseImpl getDb(DatabaseId dbId, long lockTimeout)
        throws DatabaseException {

        return getDb(dbId, lockTimeout, (String) null);
    }

    /**
     * Get a database object based on an id only, caching the id-db mapping in
     * the given map.
     */
    public DatabaseImpl getDb(DatabaseId dbId, 
                              long lockTimeout, 
                              Map<DatabaseId,DatabaseImpl> dbCache)
        throws DatabaseException {

        if (dbCache.containsKey(dbId)) {
            return dbCache.get(dbId);
        } else {
            DatabaseImpl db = getDb(dbId, lockTimeout, (String) null);
            dbCache.put(dbId, db);
            return db;
        }
    }

    /**
     * Get a database object based on an id only. Specify the lock timeout to
     * use, or -1 to use the default timeout.  A timeout should normally only
     * be specified by daemons with their own timeout configuration.  public
     * for unit tests.
     *
     * Increments the use count of the given DB to prevent it from being
     * evicted.  releaseDb should be called when the returned object is no
     * longer used, to allow it to be evicted.  See DatabaseImpl.isInUse.
     * [#13415]
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     */
    public DatabaseImpl getDb(DatabaseId dbId,
                              long lockTimeout,
                              String dbNameIfAvailable)
        throws DatabaseException {

        if (dbId.equals(idDatabase.getId())) {
            /* We're looking for the id database itself. */
            return idDatabase;
        } else if (dbId.equals(nameDatabase.getId())) {
            /* We're looking for the name database itself. */
            return nameDatabase;
        } else {
            /* Scan the tree for this db. */
            DatabaseImpl foundDbImpl = null;

            /*
             * Retry indefinitely in the face of lock timeouts.  Deadlocks may
             * be due to conflicts with modifyDbRoot.
             */
            while (true) {
                Locker locker = null;
                CursorImpl idCursor = null;
                boolean operationOk = false;
                try {
                    locker = BasicLocker.createBasicLocker(envImpl);
                    if (lockTimeout != -1) {
                        locker.setLockTimeout(lockTimeout);
                    }
                    idCursor = new CursorImpl(idDatabase, locker);
                    DatabaseEntry keyDbt = new DatabaseEntry(dbId.getBytes());
                    boolean found =
                        (idCursor.searchAndPosition
                         (keyDbt, new DatabaseEntry(), SearchMode.SET,
                          LockType.READ) &
                         CursorImpl.FOUND) != 0;
                    if (found) {
                        MapLN mapLN = (MapLN)
                            idCursor.getCurrentLNAlreadyLatched(LockType.READ);
                        assert mapLN != null; /* Should be locked. */
                        foundDbImpl =  mapLN.getDatabase();
                        /* Increment DB use count with lock held. */
                        foundDbImpl.incrementUseCount();
                    }
                    operationOk = true;
                    break;
                } catch (DeadlockException DE) {
                    /* Continue loop and retry. */
                } finally {
                    if (idCursor != null) {
                        idCursor.releaseBIN();
                        idCursor.close();
                    }
                    if (locker != null) {
                        locker.operationEnd(operationOk);
                    }
                }
            }

            /*
             * Set the debugging name in the databaseImpl, but only after
             * recovery had finished setting up the tree.
             */
            if (envImpl.isOpen()) {
                setDebugNameForDatabaseImpl(foundDbImpl, dbNameIfAvailable);
            }

            return foundDbImpl;
        }
    }

    /**
     * Decrements the use count of the given DB, allowing it to be evicted if
     * the use count reaches zero.  Must be called to release a DatabaseImpl
     * that was returned by a method in this class.  See DatabaseImpl.isInUse.
     * [#13415]
     */
    public void releaseDb(DatabaseImpl db) {
        /* Use count is not incremented for idDatabase and nameDatabase. */
        if (db != null &&
            db != idDatabase &&
            db != nameDatabase) {
            db.decrementUseCount();
        }
    }

    /**
     * Calls releaseDb for all DBs in the given map of DatabaseId to
     * DatabaseImpl.  See getDb(DatabaseId, long, Map). [#13415]
     */
    public void releaseDbs(Map<DatabaseId,DatabaseImpl> dbCache) {
        if (dbCache != null) {
            for (Iterator<DatabaseImpl> i = dbCache.values().iterator(); 
                 i.hasNext();) {
                releaseDb(i.next());
            }
        }
    }

    /*
     * We need to cache a database name in the dbImpl for later use in error
     * messages, when it may be unsafe to walk the mapping tree.  Finding a
     * name by id is slow, so minimize the number of times we must set the
     * debug name.  The debug name will only be uninitialized when an existing
     * databaseImpl is faulted in.
     */
    private void setDebugNameForDatabaseImpl(DatabaseImpl dbImpl,
                                             String dbName)
        throws DatabaseException {
        
        if (dbImpl != null) {
            if (dbName != null) {
                /* If a name was provided, use that. */
                dbImpl.setDebugDatabaseName(dbName);
            } else if (dbImpl.getDebugName() == null) {
                /*
                 * Only worry about searching for a name if the name
                 * is uninitialized.
                 */
                dbImpl.setDebugDatabaseName(getDbName(dbImpl.getId()));
            }
        }
    }

    /**
     * Rebuild the IN list after recovery.
     */
    public void rebuildINListMapDb()
        throws DatabaseException {

        idDatabase.getTree().rebuildINList();
    }

    /*
     * Verification, must be run while system is quiescent.
     */
    public boolean verify(final VerifyConfig config, PrintStream out)
        throws DatabaseException {

        boolean ret = true;
        try {
            /* For now, verify all databases. */
            boolean ok = idDatabase.verify(config,
                                           idDatabase.getEmptyStats());
            if (!ok) {
                ret = false;
            }

            ok = nameDatabase.verify(config,
                                     nameDatabase.getEmptyStats());
            if (!ok) {
                ret = false;
            }
        } catch (DatabaseException DE) {
            ret = false;
        }

        synchronized (envImpl.getINCompressor()) {

            /*
             * Get a cursor on the id tree. Use objects at the dbi layer rather
             * than at the public api, in order to retrieve objects rather than
             * Dbts. Note that we don't do cursor cloning here, so any failures
             * from each db verify invalidate the cursor.  Use dirty read
             * (LockMode.NONE) because locks on the MapLN should never be held
             * for long, as that will cause deadlocks with splits and
             * checkpointing.
             */
            final LockType lockType = LockType.NONE;
            class Traversal implements CursorImpl.WithCursor {
                boolean allOk = true;

                public boolean withCursor(CursorImpl cursor,
                                          DatabaseEntry key,
                                          DatabaseEntry data)
                    throws DatabaseException {

                    MapLN mapLN = (MapLN) cursor.getCurrentLN(lockType);
                    if (mapLN != null && !mapLN.isDeleted()) {
                        DatabaseImpl dbImpl = mapLN.getDatabase();
                        boolean ok = dbImpl.verify(config,
                                                   dbImpl.getEmptyStats());
                        if (!ok) {
                            allOk = false;
                        }
                    }
                    return true;
                }
            }
            Traversal traversal = new Traversal();
            CursorImpl.traverseDbWithCursor
                (idDatabase, lockType, true /*allowEviction*/, traversal);
            if (!traversal.allOk) {
                ret = false;
            }
        }

        return ret;
    }

    /**
     * Return the database name for a given db. Slow, must traverse. Called by
     * Database.getName.
     *
     * Do not evict (do not call CursorImpl.setAllowEviction(true)) during low
     * level DbTree operation. [#15176]
     */
    public String getDbName(final DatabaseId id)
        throws DatabaseException {

        if (id.equals(ID_DB_ID)) {
            return ID_DB_NAME;
        } else if (id.equals(NAME_DB_ID)) {
            return NAME_DB_NAME;
        }
        class Traversal implements CursorImpl.WithCursor {
            String name = null;

            public boolean withCursor(CursorImpl cursor,
                                      DatabaseEntry key,
                                      DatabaseEntry data)
                throws DatabaseException {

                NameLN nameLN = (NameLN) cursor.getCurrentLN(LockType.NONE);
                if (nameLN != null && nameLN.getId().equals(id)) {
                    try {
                        name = new String(key.getData(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new DatabaseException(e);
                    }
                    return false;
                }
                return true;
            }
        }
        Traversal traversal = new Traversal();
        CursorImpl.traverseDbWithCursor
            (nameDatabase, LockType.NONE, false /*allowEviction*/, traversal);
        return traversal.name;
    }

    /**
     * @return a map of database ids to database names (Strings).
     */
    public Map<DatabaseId,String> getDbNamesAndIds()
        throws DatabaseException {

        final Map<DatabaseId,String> nameMap =
            new HashMap<DatabaseId,String>();

        class Traversal implements CursorImpl.WithCursor {
            public boolean withCursor(CursorImpl cursor,
                                      DatabaseEntry key,
                                      DatabaseEntry data)
                throws DatabaseException {

                NameLN nameLN = (NameLN) cursor.getCurrentLN(LockType.NONE);
                DatabaseId id = nameLN.getId();
                try {
                    nameMap.put(id, new String(key.getData(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new DatabaseException(e);
                }
                return true;
            }
        }
        Traversal traversal = new Traversal();
        CursorImpl.traverseDbWithCursor
            (nameDatabase, LockType.NONE, false /*allowEviction*/, traversal);
        return nameMap;
    }

    /**
     * @return a list of database names held in the environment, as strings.
     */
    public List<String> getDbNames()
        throws DatabaseException {

        final List<String> nameList = new ArrayList<String>();

        CursorImpl.traverseDbWithCursor(nameDatabase,
                                        LockType.NONE,
                                        true /*allowEviction*/,
                                        new CursorImpl.WithCursor() {
            public boolean withCursor(CursorImpl cursor,
                                      DatabaseEntry key,
                                      DatabaseEntry data)
                throws DatabaseException {

                try {
                    String name = new String(key.getData(), "UTF-8");
                    if (!isReservedDbName(name)) {
                        nameList.add(name);
                    }
                    return true;
                } catch (UnsupportedEncodingException e) {
                    throw new DatabaseException(e);
                }
            }
        });

        return nameList;
    }

    /**
     * Return a list of the names of internally used databases that
     * don't get looked up through the naming tree.
     */
    public List<String> getInternalNoLookupDbNames() {
        List<String> names = new ArrayList<String>();
        names.add(ID_DB_NAME);
        names.add(NAME_DB_NAME);
        return names;
    }

    /**
     * Return a list of the names of internally used databases.
     * TODO: The internal replication DBs are not added here and therefore not
     * verified by DbVerify. Reassess for HA release.
     */
    public List<String> getInternalDbNames() {
        List<String> names = new ArrayList<String>();
        names.add(UTILIZATION_DB_NAME);
        return names;
    }

    /**
     * Returns true if the name is a reserved JE database name.
     */
    public static boolean isReservedDbName(String name) {
        for (int i = 0; i < RESERVED_DB_NAMES.length; i += 1) {
            if (RESERVED_DB_NAMES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the higest level node in the environment.
     */
    public int getHighestLevel()
        throws DatabaseException {

        /* The highest level in the map side */
        int idHighLevel = getHighestLevel(idDatabase);

        /* The highest level in the name side */
        int nameHighLevel = getHighestLevel(nameDatabase);

        return (nameHighLevel > idHighLevel) ? nameHighLevel : idHighLevel;
    }

    /**
     * @return the higest level node for this database.
     */
    public int getHighestLevel(DatabaseImpl dbImpl)
        throws DatabaseException {

        /* The highest level in the map side */
        RootLevel getLevel = new RootLevel(dbImpl);
        dbImpl.getTree().withRootLatchedShared(getLevel);
        return getLevel.getRootLevel();
    }

    private boolean isReplicated() {
        return (flags & REPLICATED_BIT) != 0;
    }

    private void setIsReplicated() {
        flags |= REPLICATED_BIT;
    }

    /**
     * Release resources and update memory budget. Should only be called
     * when this dbtree is closed and will never be accessed again.
     */
    public void close() {
        idDatabase.releaseTreeAdminMemory();
        nameDatabase.releaseTreeAdminMemory();
    }

    long getTreeAdminMemory() {
        return idDatabase.getTreeAdminMemory() +
            nameDatabase.getTreeAdminMemory();
    }

    /*
     * RootLevel lets us fetch the root IN within the root latch.
     */
    private static class RootLevel implements WithRootLatched {
        private DatabaseImpl db;
        private int rootLevel;

        RootLevel(DatabaseImpl db) {
            this.db = db;
            rootLevel = 0;
        }

        public IN doWork(ChildReference root)
            throws DatabaseException {

            IN rootIN = (IN) root.fetchTarget(db, null);
            rootLevel = rootIN.getLevel();
            return null;
        }

        int getRootLevel() {
            return rootLevel;
        }
    }

    /*
     * Logging support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return
            LogUtils.getIntLogSize() +  // last allocated local db id
            LogUtils.getIntLogSize() +  // last allocated replicated db id
            idDatabase.getLogSize() +   // id db
            nameDatabase.getLogSize() + // name db
            1;                          // 1 byte of flags
    }

    /**
     * This log entry type is configured to perform marshaling (getLogSize and
     * writeToLog) under the write log mutex.  Otherwise, the size could change
     * in between calls to these two methods as the result of utilizaton
     * tracking.
     *
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeInt(logBuffer,
                          lastAllocatedLocalDbId.get());      // last id
        LogUtils.writeInt(logBuffer,
                          lastAllocatedReplicatedDbId.get()); // last rep id
        idDatabase.writeToLog(logBuffer);                // id db
        nameDatabase.writeToLog(logBuffer);              // name db
        logBuffer.put(flags);
    }


    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryVersion)
        throws LogException {

        lastAllocatedLocalDbId.set(LogUtils.readInt(itemBuffer));
        if (entryVersion >= 6) {
            lastAllocatedReplicatedDbId.set(LogUtils.readInt(itemBuffer));
        }

        idDatabase.readFromLog(itemBuffer, entryVersion); // id db
        nameDatabase.readFromLog(itemBuffer, entryVersion); // name db

        if (entryVersion >= 6) {
            flags = itemBuffer.get();
        } else {
            flags = 0;
        }
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<dbtree lastLocalDbId = \"");
        sb.append(lastAllocatedLocalDbId);
        sb.append("\" lastReplicatedDbId = \"");
        sb.append(lastAllocatedReplicatedDbId);
        sb.append("\">");
        sb.append("<idDb>");
        idDatabase.dumpLog(sb, verbose);
        sb.append("</idDb><nameDb>");
        nameDatabase.dumpLog(sb, verbose);
        sb.append("</nameDb>");
        sb.append("</dbtree>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
        return 0;
    }

    /**
     * @see Loggable#logicalEquals
     * Always return false, this item should never be compared.
     */
    public boolean logicalEquals(Loggable other) {
        return false;
    }

    /*
     * For unit test support
     */

    String dumpString(int nSpaces) {
        StringBuffer self = new StringBuffer();
        self.append(TreeUtils.indent(nSpaces));
        self.append("<dbTree lastDbId =\"");
        self.append(lastAllocatedLocalDbId);
        self.append("\">");
        self.append('\n');
        self.append(idDatabase.dumpString(nSpaces + 1));
        self.append('\n');
        self.append(nameDatabase.dumpString(nSpaces + 1));
        self.append('\n');
        self.append("</dbtree>");
        return self.toString();
    }

    @Override
    public String toString() {
        return dumpString(0);
    }

    /**
     * For debugging.
     */
    public void dump()
        throws DatabaseException {

        idDatabase.getTree().dump();
        nameDatabase.getTree().dump();
    }
}
