/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DbTree.java,v 1.165 2006/09/12 19:16:45 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogReadable;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LoggableObject;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.tree.NameLN;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeUtils;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.AutoTxn;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Locker;

/**
 * Represents the DatabaseImpl Naming Tree.
 */
public class DbTree implements LoggableObject, LogReadable {

    /* The id->DatabaseImpl tree is always id 0 */
    public static final DatabaseId ID_DB_ID = new DatabaseId(0);
    /* The name->id tree is always id 1 */
    public static final DatabaseId NAME_DB_ID = new DatabaseId(1);

    /* Internal databases - the database mapping tree and utilization info. */
    private static final String ID_DB_NAME = "_jeIdMap";
    private static final String NAME_DB_NAME = "_jeNameMap";
    public static final String UTILIZATION_DB_NAME = "_jeUtilization";

    /* Reserved database names. */
    private static final String[] RESERVED_DB_NAMES = {
        ID_DB_NAME,
        NAME_DB_NAME,
        UTILIZATION_DB_NAME,
    };

    /* Database id counter, must be accessed w/synchronization. */
    private int lastAllocatedDbId;        

    private DatabaseImpl idDatabase;          // map db ids -> databases
    private DatabaseImpl nameDatabase;        // map names -> dbIds
    private EnvironmentImpl envImpl; 

    /**
     * Create a dbTree from the log.
     */
    public DbTree()
        throws DatabaseException {
        	
        this.envImpl = null;
        idDatabase = new DatabaseImpl();
        idDatabase.setDebugDatabaseName(ID_DB_NAME);
        nameDatabase = new DatabaseImpl();
        nameDatabase.setDebugDatabaseName(NAME_DB_NAME);
    }

    /**
     * Create a new dbTree for a new environment.
     */
    public DbTree(EnvironmentImpl env)
        throws DatabaseException {

        this.envImpl = env;
        idDatabase = new DatabaseImpl(ID_DB_NAME,
				      new DatabaseId(0),
				      env,
				      new DatabaseConfig());
                                  
        nameDatabase = new DatabaseImpl(NAME_DB_NAME,
					new DatabaseId(1),
					env,
					new DatabaseConfig());
                                  
        lastAllocatedDbId = 1;
    }

    /**
     * Get the latest allocated id, for checkpoint info.
     */
    public synchronized int getLastDbId() {
        return lastAllocatedDbId;
    }

    /**
     * Get the next available database id.
     */
    private synchronized int getNextDbId() {
        return ++lastAllocatedDbId;
    }

    /**
     * Initialize the db id, from recovery.
     */
    public synchronized void setLastDbId(int maxDbId) {
        lastAllocatedDbId = maxDbId; 
    }

    private Locker createMapDbLocker(EnvironmentImpl envImpl)
	throws DatabaseException {

	if (envImpl.isNoLocking()) {
	    return new BasicLocker(envImpl);
	} else {
	    return new AutoTxn(envImpl, new TransactionConfig());
	}
    }

    /**
     * Set the db environment during recovery, after instantiating the tree
     * from the log.
     */
    void setEnvironmentImpl(EnvironmentImpl envImpl)
        throws DatabaseException {

        this.envImpl = envImpl;
        idDatabase.setEnvironmentImpl(envImpl);
        nameDatabase.setEnvironmentImpl(envImpl);
    }

    /**
     * Create a database.
     */
    public synchronized DatabaseImpl createDb(Locker locker, 
                                              String databaseName,
                                              DatabaseConfig dbConfig,
                                              Database databaseHandle)
        throws DatabaseException {

        return createDb(locker, databaseName, dbConfig, databaseHandle, true);
    }

    /**
     * Create a database.
     * @param locker owning locker
     * @param databaseName identifier for database
     * @param dbConfig
     * @param allowEviction is whether eviction is allowed during cursor
     * operations.
     */
    public synchronized DatabaseImpl createDb(Locker locker, 
                                              String databaseName,
                                              DatabaseConfig dbConfig,
                                              Database databaseHandle,
                                              boolean allowEviction)
        throws DatabaseException {

        /* Create a new database object. */
        DatabaseId newId = new DatabaseId(getNextDbId());
        DatabaseImpl newDb = new DatabaseImpl(databaseName,
					      newId,
					      envImpl,
					      dbConfig);
        CursorImpl idCursor = null;
        CursorImpl nameCursor = null;
        boolean operationOk = false;
        Locker idDbLocker = null;
        try {
            /* Insert it into name -> id db. */
            nameCursor = new CursorImpl(nameDatabase, locker);
            nameCursor.setAllowEviction(allowEviction);
            LN nameLN = new NameLN(newId);
            nameCursor.putLN(databaseName.getBytes("UTF-8"),
			     nameLN, false);

            /* 
             * If this is a non-handle use, no need to record any handle locks.
             */
            if (databaseHandle != null) {
                locker.addToHandleMaps(new Long(nameLN.getNodeId()),
                                       databaseHandle);
            }

            /* Insert it into id -> name db, in auto commit mode. */
            idDbLocker = new BasicLocker(envImpl);
            idCursor = new CursorImpl(idDatabase, idDbLocker);
            idCursor.setAllowEviction(allowEviction);
            idCursor.putLN(newId.getBytes(), new MapLN(newDb), false);
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
     * Called by the Tree to propagate a root change.  If the tree is a data
     * database, we will write the MapLn that represents this db to the log. If
     * the tree is one of the mapping dbs, we'll write the dbtree to the log.
     *
     * @param db the target db
     */
    public void optionalModifyDbRoot(DatabaseImpl db) 
        throws DatabaseException {
	
        if (db.isDeferredWrite()) {
            return;
        }
        
        modifyDbRoot(db);
    }

    /**
     * Called by the Tree to propagate a root change.  If the tree is a data
     * database and it's not a deferred write db, we will write the MapLn that
     * represents this db to the log. If the tree is one of the mapping dbs,
     * we'll write the dbtree to the log.
     *
     * @param db the target db
     */
    public void modifyDbRoot(DatabaseImpl db)
        throws DatabaseException {

        if (db.getId().equals(ID_DB_ID) ||
            db.getId().equals(NAME_DB_ID)) {
            envImpl.logMapTreeRoot();
        } else {
            Locker idDbLocker = new BasicLocker(envImpl);
            CursorImpl cursor = new CursorImpl(idDatabase, idDbLocker);
            boolean operationOk = false;
            try {
                DatabaseEntry keyDbt =
		    new DatabaseEntry(db.getId().getBytes());
		MapLN mapLN = null;

		/*
		 * Retry indefinitely in the face of lock timeouts since the
		 * lock on the MapLN is only supposed to be held for short
		 * periods.
		 */
		while (true) {
		    try {
			boolean searchOk = (cursor.searchAndPosition
					    (keyDbt, new DatabaseEntry(),
					     SearchMode.SET, LockType.WRITE) &
					    CursorImpl.FOUND) != 0;
			if (!searchOk) {
                            throw new DatabaseException(
                                "can't find database " + db.getId());
                        }
			mapLN = (MapLN)
			    cursor.getCurrentLNAlreadyLatched(LockType.WRITE);
                        assert mapLN != null; /* Should be locked. */
		    } catch (DeadlockException DE) {
			cursor.close();
			idDbLocker.operationEnd(false);
			idDbLocker = new BasicLocker(envImpl);
			cursor = new CursorImpl(idDatabase, idDbLocker);
			continue;
		    } finally {
			cursor.releaseBINs();
		    }
		    break;
		}

		RewriteMapLN writeMapLN = new RewriteMapLN(cursor);
		mapLN.getDatabase().getTree().
		    withRootLatchedExclusive(writeMapLN);

                operationOk = true;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }

                idDbLocker.operationEnd(operationOk);
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
	    cursor.putCurrent(dataDbt, null, null);
            return null;
        }
    }

    /*
     * Helper for database operations. This method positions a cursor
     * on the NameLN that represents this database and write locks it.
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
        result.nameCursor = new CursorImpl(nameDatabase, locker);

        try {
            /* Position the cursor at the specified NameLN. */
            DatabaseEntry key =
                new DatabaseEntry(databaseName.getBytes("UTF-8"));
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
             * other transactions can open. XXX, another handle using the same
             * txn could open ...
             */
            int handleCount = result.dbImpl.getReferringHandleCount();
            if (handleCount > 0) {
                throw new DatabaseException("Can't " + action + " database " +
                                            databaseName + "," + handleCount + 
                                            " open Dbs exist");
            }
	} catch (UnsupportedEncodingException UEE) {
            result.nameCursor.releaseBIN();
            result.nameCursor.close();
	    throw new DatabaseException(UEE);
        } catch (DatabaseException e) {
            result.nameCursor.releaseBIN();
            result.nameCursor.close();
	    throw e;
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
    boolean dbRename(Locker locker, String databaseName, String newName)
        throws DatabaseException {

        CursorImpl nameCursor = null;
        try {
            NameLockResult result = lockNameLN(locker, databaseName, "rename");
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
                nameCursor.delete();
                nameCursor.putLN(newName.getBytes("UTF-8"),
                                 new NameLN(result.dbImpl.getId()), false);
                result.dbImpl.setDebugDatabaseName(newName);
                return true;
            }
	} catch (UnsupportedEncodingException UEE) {
	    throw new DatabaseException(UEE);
        } finally {
            if (nameCursor != null) {
                nameCursor.releaseBIN();
                nameCursor.close();
            }
        }
    }

    /**
     * Remove the database by deleting the nameLN.
     */
    void dbRemove(Locker locker, String databaseName)
        throws DatabaseException {

        CursorImpl nameCursor = null;
        try {
            NameLockResult result = lockNameLN(locker, databaseName, "remove");
            nameCursor = result.nameCursor;
            if (nameCursor == null) {
                return;
            } else {

                /*
                 * Delete the NameLN. There's no need to mark any Database
                 * handle invalid, because the handle must be closed when we
                 * take action and any further use of the handle will re-look
                 * up the database.
                 */
		nameCursor.latchBIN();
                nameCursor.delete();

                /*
                 * Schedule database for final deletion during commit. This 
                 * should be the last action taken, since this will take
                 * effect immediately for non-txnal lockers.
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
     * @param returnCount if true, must return the count of records in the
     * database, which can be an expensive option.
     */
    long truncate(Locker locker, 
                  String databaseName,
                  boolean returnCount) 
        throws DatabaseException {

        CursorImpl nameCursor = null;
        Locker idDbLocker = null;
        try {
            NameLockResult result = lockNameLN(locker, databaseName,
                                               "truncate");
            nameCursor = result.nameCursor;
            if (nameCursor == null) {
                return 0;
            } else {

                /*
                 * Make a new database with an empty tree. Make the
                 * nameLN refer to the id of the new database.
                 */
                DatabaseId newId = new DatabaseId(getNextDbId());
                DatabaseImpl newDb = (DatabaseImpl) result.dbImpl.clone();
                newDb.setId(newId);
                newDb.setTree(new Tree(newDb));
            
                /* 
                 * Insert the new MapLN into the id tree. Always use
                 * an AutoTxn on the id databaase, because we can not
                 * hold long term locks on the mapLN.
                 */
                CursorImpl idCursor = null; 
                boolean operationOk = false;
                try {
		    idDbLocker = new BasicLocker(envImpl);
                    idCursor = new CursorImpl(idDatabase, idDbLocker);
                    idCursor.putLN(newId.getBytes(),
                                   new MapLN(newDb), false);
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
                    recordCount = result.dbImpl.count();
                }

                /* log the nameLN. */
                DatabaseEntry dataDbt = new DatabaseEntry(new byte[0]);
                nameCursor.putCurrent(dataDbt, null, null);

                /* 
                 * Marking the lockers should be the last action, since
                 * it takes effect immediately for non-txnal lockers.
                 */

                /* Schedule old database for deletion if txn commits. */
                locker.markDeleteAtTxnEnd(result.dbImpl, true);

                /* Schedule new database for deletion if txn aborts. */
                locker.markDeleteAtTxnEnd(newDb, false);

                return recordCount;
            }

	} catch (CloneNotSupportedException CNSE) {
	    throw new DatabaseException(CNSE);
        } finally {
            if (nameCursor != null) {
                nameCursor.releaseBIN();
                nameCursor.close();
            }
        }
    }

    /*
     * Remove the mapLN that refers to this database.
     */
    void deleteMapLN(DatabaseId id) 
        throws DatabaseException {

        Locker idDbLocker = null;
        boolean operationOk = false;
        CursorImpl idCursor = null;
        
        try {
	    idDbLocker = new BasicLocker(envImpl);
            idCursor = new CursorImpl(idDatabase, idDbLocker);
            boolean found =
                (idCursor.searchAndPosition(new DatabaseEntry(id.getBytes()),
                                            null,
                                            SearchMode.SET,
                                            LockType.WRITE) &
                 CursorImpl.FOUND) != 0;
            if (found) {
                idCursor.delete();
            }

            operationOk = true;
        } finally {
            if (idCursor != null) {
                idCursor.close();
            }

            if (idDbLocker != null) {
                idDbLocker.operationEnd(operationOk);
            }
        }
    }

    /**
     * Truncate a database named by databaseName. Return the new DatabaseImpl
     * object that represents the truncated database.  The old one is marked as
     * deleted.
     *
     * @deprecated This method used by Database.truncate()
     */
    TruncateResult truncate(Locker locker,
                            DatabaseImpl oldDatabase,
                            boolean returnCount)
        throws DatabaseException {

        CursorImpl nameCursor = new CursorImpl(nameDatabase, locker);

        try {
            String databaseName = getDbName(oldDatabase.getId());
            DatabaseEntry keyDbt =
                new DatabaseEntry(databaseName.getBytes("UTF-8"));
            boolean found =
                (nameCursor.searchAndPosition(keyDbt, null,
					      SearchMode.SET, LockType.WRITE) &
		 CursorImpl.FOUND) != 0;
            if (!found) {

                /* 
                 * Should be found, since truncate is instigated from
                 * Database.truncate();
                 */
                throw new DatabaseException
                    ("Database " + databaseName +  " not found in map tree");
            }

            /* Call getCurrentLN to write lock the nameLN. */
            NameLN nameLN = (NameLN)
                nameCursor.getCurrentLNAlreadyLatched(LockType.WRITE);
            assert nameLN != null; /* Should be locked. */

            /* 
             * Check the open handle count after we have the write lock and no
             * other transactions can open. XXX, another handle using the same
             * txn could open ...
             */
            int handleCount = oldDatabase.getReferringHandleCount();
            if (handleCount > 1) {
                throw new DatabaseException("Can't truncate database " +
					    databaseName + "," + handleCount + 
					    " open databases exist");
            }
            
            /*
             * Make a new database with an empty tree. Make the nameLN refer to
             * the id of the new database.
             */
            DatabaseImpl newDb;
            DatabaseId newId = new DatabaseId(getNextDbId());
	    newDb = (DatabaseImpl) oldDatabase.clone();
            newDb.setId(newId);
            newDb.setTree(new Tree(newDb));
            
            /* Insert the new db into id -> name map */
            CursorImpl idCursor = null; 
            boolean operationOk = false;
            Locker idDbLocker = null;
            try {
		idDbLocker = new BasicLocker(envImpl);
                idCursor = new CursorImpl(idDatabase, idDbLocker);
                idCursor.putLN(newId.getBytes(),
			       new MapLN(newDb), false);
                operationOk = true;
            } finally {
                if (idCursor != null) {
                    idCursor.close();
                }

                if (idDbLocker != null) {
                    idDbLocker.operationEnd(operationOk);
                }
            }
            nameLN.setId(newDb.getId());

            /* count records for the deleted database. */
            long count = 0;
            if (returnCount) {
                count = oldDatabase.count();
            }

            /* Schedule database for final deletion during commit. */
            locker.markDeleteAtTxnEnd(oldDatabase, true);

            /* log the nameLN. */
            DatabaseEntry dataDbt = new DatabaseEntry(new byte[0]);
            nameCursor.putCurrent(dataDbt, null, null);

            return new TruncateResult(newDb, (int) count);
	} catch (CloneNotSupportedException CNSE) {
	    throw new DatabaseException(CNSE);
	} catch (UnsupportedEncodingException UEE) {
	    throw new DatabaseException(UEE);
        } finally {
            nameCursor.releaseBIN();
            nameCursor.close();
        }
    }

    /**
     * Get a database object given a database name.
     */
    public DatabaseImpl getDb(Locker nameLocker,
                              String databaseName,
                              Database databaseHandle)
        throws DatabaseException {

        return getDb(nameLocker, databaseName, databaseHandle, true);
    }

    /**
     * Get a database object given a database name.
     * @param nameLocker is used to access the NameLN. As always, a NullTxn
     *  is used to access the MapLN.
     * @param databaseName target database
     * @return null if database doesn't exist
     * @param allowEviction is whether eviction is allowed during cursor
     * operations.
     */
    public DatabaseImpl getDb(Locker nameLocker,
                              String databaseName,
                              Database databaseHandle,
                              boolean allowEviction)
        throws DatabaseException {

        try {
            if (databaseName.equals(ID_DB_NAME)) {
                return idDatabase;
            } else if (databaseName.equals(NAME_DB_NAME)) {
                return nameDatabase;
            }

            /* 
             * Search the nameDatabase tree for the NameLn for this name.
             * Release locks before searching the id tree
             */
            CursorImpl nameCursor = null;
            DatabaseId id = null;
            try {

                nameCursor = new CursorImpl(nameDatabase, nameLocker);
                nameCursor.setAllowEviction(allowEviction);
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
                        nameLocker.addToHandleMaps(new Long(nameLN.getNodeId()),
                                                   databaseHandle);
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
                return getDb(id, -1, allowEviction, databaseName);
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

        return getDb(dbId, lockTimeout, true, null);
    }

    /**
     * Get a database object based on an id only, caching the id-db mapping in
     * the given map.
     */
    public DatabaseImpl getDb(DatabaseId dbId, long lockTimeout, Map dbCache)
        throws DatabaseException {

        if (dbCache.containsKey(dbId)) {
            return (DatabaseImpl) dbCache.get(dbId);
        } else {
            DatabaseImpl db = getDb(dbId, lockTimeout, true, null);
            dbCache.put(dbId, db);
            return db;
        }
    }

    /**
     * Get a database object based on an id only. Specify the lock timeout to
     * use, or -1 to use the default timeout.  A timeout should normally only
     * be specified by daemons with their own timeout configuration.  public
     * for unit tests.
     * @param allowEviction is whether eviction is allowed during cursor
     * operations.
     */
    public DatabaseImpl getDb(DatabaseId dbId,
                              long lockTimeout,
                              boolean allowEviction,
                              String dbNameIfAvailable)
        throws DatabaseException {

        if (dbId.equals(idDatabase.getId())) {
            /* We're looking for the id database itself. */
            return idDatabase;
        } else if (dbId.equals(nameDatabase.getId())) {
            /* We're looking for the name database itself. */
            return nameDatabase;
        } else {
            Locker locker = new BasicLocker(envImpl);
            if (lockTimeout != -1) {
                locker.setLockTimeout(lockTimeout);
            }

            /* Scan the tree for this db. */
            CursorImpl idCursor = null;
            DatabaseImpl foundDbImpl = null;

	    /*
	     * Retry in the face of lock timeouts.  Deadlocks may be due to
	     * conflicts with modifyDbRoot.
	     */
	    while (true) {
                idCursor = new CursorImpl(idDatabase, locker);
                idCursor.setAllowEviction(allowEviction);
		try {
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
                    } 
                    break;
		} catch (DeadlockException DE) {
		    idCursor.close();
		    locker.operationEnd(false);
		    locker = new BasicLocker(envImpl);
		    if (lockTimeout != -1) {
			locker.setLockTimeout(lockTimeout);
		    }
		    idCursor = new CursorImpl(idDatabase, locker);
                    idCursor.setAllowEviction(allowEviction);
		    continue;
		} finally {
		    idCursor.releaseBIN();
		    idCursor.close();
		    locker.operationEnd(true);
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
    public boolean verify(VerifyConfig config, PrintStream out)
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
	    Locker locker = null;
	    CursorImpl cursor = null;
            LockType lockType = LockType.NONE;
	    try {
		locker = new BasicLocker(envImpl);
		cursor = new CursorImpl(idDatabase, locker);
		if (cursor.positionFirstOrLast(true, null)) {
                    MapLN mapLN = (MapLN) cursor.
                        getCurrentLNAlreadyLatched(lockType);

                    DatabaseEntry keyDbt = new DatabaseEntry();
                    DatabaseEntry dataDbt = new DatabaseEntry();
                    while (true) {
                        if (mapLN != null && !mapLN.isDeleted()) {
                            DatabaseImpl dbImpl = mapLN.getDatabase();
                            boolean ok = dbImpl.verify(config,
                                                       dbImpl.getEmptyStats());
                            if (!ok) {
                                ret = false;
                            }
                        }
                        /* Go on to the next entry. */
                        OperationStatus status =
                            cursor.getNext(keyDbt, dataDbt, lockType,
                                           true,   // go forward
                                           false); // do need to latch
                        if (status != OperationStatus.SUCCESS) {
                            break;
                        }
                        mapLN = (MapLN) cursor.getCurrentLN(lockType);
                    }
                }
	    } catch (DatabaseException e) {
                e.printStackTrace(out);
		ret = false;
	    } finally {
		if (cursor != null) {
		    cursor.releaseBINs();
		    cursor.close();
		}
		if (locker != null) {
		    locker.operationEnd();
		}
	    }
	}

	return ret;
    }

    /**
     * Return the database name for a given db. Slow, must traverse. Used by
     * truncate and for debugging.
     */
    public String getDbName(DatabaseId id) 
        throws DatabaseException { 

        if (id.equals(ID_DB_ID)) {
            return ID_DB_NAME;
        } else if (id.equals(NAME_DB_ID)) {
            return NAME_DB_NAME;
        }

        Locker locker = null;
        CursorImpl cursor = null;
        try {
            locker = new BasicLocker(envImpl);
            cursor = new CursorImpl(nameDatabase, locker);
            /* Use dirty reads (LockType.NONE). */
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            String name = null;
            if (cursor.positionFirstOrLast(true, null)) {
                /* Fill in the key DatabaseEntry */
                OperationStatus status = cursor.getCurrentAlreadyLatched
                    (keyDbt, dataDbt, LockType.NONE, true);
                do {
                    if (status == OperationStatus.SUCCESS) {
                        NameLN nameLN = (NameLN) cursor.getCurrentLN
                            (LockType.NONE);
                        if (nameLN != null && nameLN.getId().equals(id)) {
                            name = new String(keyDbt.getData(), "UTF-8");
                            break;
                        }
                    }

                    /* Go on to the next entry. */
                    status = cursor.getNext(keyDbt, dataDbt, LockType.NONE,
                                            true,   // go forward
                                            false); // do need to latch
                } while (status == OperationStatus.SUCCESS);
            }
            return name;
	} catch (UnsupportedEncodingException UEE) {
	    throw new DatabaseException(UEE);
        } finally {
            if (cursor != null) {
                cursor.releaseBINs();
                cursor.close();
            }
            if (locker != null) {
                locker.operationEnd();
            }
        }
    }
    
    /**
     * @return a list of database names held in the environment, as strings.
     */
    public List getDbNames()
        throws DatabaseException {
        
        List nameList = new ArrayList();
        Locker locker = null;
        CursorImpl cursor = null;
        try {
            locker = new BasicLocker(envImpl);
            cursor = new CursorImpl(nameDatabase, locker);
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            if (cursor.positionFirstOrLast(true, null)) {
                OperationStatus status = cursor.getCurrentAlreadyLatched
                    (keyDbt, dataDbt, LockType.READ, true);
                do {
                    if (status == OperationStatus.SUCCESS) {
                        String name = new String(keyDbt.getData(), "UTF-8");
                        if (!isReservedDbName(name)) {
                            nameList.add(name);
                        }
                    }

                    /* Go on to the next entry. */
                    status = cursor.getNext(keyDbt, dataDbt, LockType.READ,
                                            true,   // go forward
                                            false); // do need to latch
                } while (status == OperationStatus.SUCCESS);
            }
            return nameList;
	} catch (UnsupportedEncodingException UEE) {
	    throw new DatabaseException(UEE);
        } finally {
            if (cursor != null) {
                cursor.close();
            }

            if (locker != null) {
                locker.operationEnd();
            }
        }
    }

    /**
     * Return a list of the names of internally used databases that 
     * don't get looked up through the naming tree.
     */
    public List getInternalNoLookupDbNames() {
        List names = new ArrayList();
        names.add(ID_DB_NAME);
        names.add(NAME_DB_NAME);
        return names;
    }

    /**
     * Return a list of the names of internally used databases.
     */
    public List getInternalDbNames() {
        List names = new ArrayList();
        names.add(UTILIZATION_DB_NAME);
        return names;
    }

    /**
     * Returns true if the name is a reserved JE database name.
     */
    public boolean isReservedDbName(String name) {
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
     * LoggableObject
     */

    /**
     * @see LoggableObject#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_ROOT;
    }

    /**
     * @see LoggableObject#marshallOutsideWriteLatch
     * Can be marshalled outside the log write latch.
     */
    public boolean marshallOutsideWriteLatch() {
        return true;
    }

    /**
     * @see LoggableObject#countAsObsoleteWhenLogged
     */
    public boolean countAsObsoleteWhenLogged() {
        return false;
    }

    /**
     * @see LoggableObject#getLogSize
     */
    public int getLogSize() {
        return
            LogUtils.getIntLogSize() +        // last allocated id
            idDatabase.getLogSize() + // id db
            nameDatabase.getLogSize(); // name db
    }

    /**
     * @see LoggableObject#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeInt(logBuffer,lastAllocatedDbId);  // last id
        idDatabase.writeToLog(logBuffer);                // id db
        nameDatabase.writeToLog(logBuffer);              // name db
    }

    /**
     * @see LoggableObject#postLogWork
     */
    public void postLogWork(long justLoggedLsn) 
        throws DatabaseException {
    }

    /*
     * LogReadable
     */

    /**
     * @see LogReadable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        lastAllocatedDbId = LogUtils.readInt(itemBuffer); // last id
        idDatabase.readFromLog(itemBuffer, entryTypeVersion); // id db
        nameDatabase.readFromLog(itemBuffer, entryTypeVersion); // name db
    }
    
    /**
     * @see LogReadable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<dbtree lastId = \"");
        sb.append(lastAllocatedDbId);
        sb.append("\">");
        sb.append("<idDb>");
        idDatabase.dumpLog(sb, verbose);
        sb.append("</idDb><nameDb>");
        nameDatabase.dumpLog(sb, verbose);
        sb.append("</nameDb>");
        sb.append("</dbtree>");
    }

    /**
     * @see LogReadable#logEntryIsTransactional.
     */
    public boolean logEntryIsTransactional() {
	return false;
    }

    /**
     * @see LogReadable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    /*
     * For unit test support
     */

    String dumpString(int nSpaces) {
        StringBuffer self = new StringBuffer();
        self.append(TreeUtils.indent(nSpaces));
        self.append("<dbTree lastDbId =\"");
        self.append(lastAllocatedDbId);
        self.append("\">");
        self.append('\n');
        self.append(idDatabase.dumpString(nSpaces + 1));
        self.append('\n');
        self.append(nameDatabase.dumpString(nSpaces + 1));
        self.append('\n');
        self.append("</dbtree>");
        return self.toString();
    }   
        
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
