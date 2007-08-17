/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Database.java,v 1.216.2.2 2007/07/02 19:54:48 mark Exp $
 */

package com.sleepycat.je;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.GetMode;
import com.sleepycat.je.dbi.PutMode;
import com.sleepycat.je.dbi.TruncateResult;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.utilint.DatabaseUtil;
import com.sleepycat.je.utilint.TinyHashSet;
import com.sleepycat.je.utilint.Tracer;

public class Database {

    /*
     * DbState embodies the Database handle state.
     */
    static class DbState {
        private String stateName;

        DbState(String stateName) {
            this.stateName = stateName;
        }

        public String toString() {
            return "DbState." + stateName;
        }
    }

    static DbState OPEN = new DbState("OPEN");
    static DbState CLOSED = new DbState("CLOSED");
    static DbState INVALID = new DbState("INVALID");

    /* The current state of the handle. */
    private DbState state;

    /* Handles onto the owning environment and the databaseImpl object. */
    Environment envHandle;            // used by subclasses
    private DatabaseImpl databaseImpl;

    DatabaseConfig configuration;     // properties used at execution

    /* True if this handle permits write operations; */
    private boolean isWritable;

    /* Transaction that owns the db lock held while the Database is open. */
    Locker handleLocker;

    /* Set of cursors open against this db handle. */
    private TinyHashSet cursors = new TinyHashSet();

    /* 
     * DatabaseTrigger list.  The list is null if empty, and is checked for
     * null to avoiding read locking overhead when no triggers are present.
     * Access to this list is protected by the shared trigger latch in
     * EnvironmentImpl.
     */
    private List triggerList;

    private Logger logger;

    /**
     * Creates a database but does not open or fully initialize it.
     * Is protected for use in compat package.
     */
    protected Database(Environment env) {
        this.envHandle = env;
        handleLocker = null;
	logger = envHandle.getEnvironmentImpl().getLogger();
    }

    /**
     * Create a database, called by Environment.
     */
    void initNew(Environment env,
                 Locker locker,
                 String databaseName,
                 DatabaseConfig dbConfig)
        throws DatabaseException {

        dbConfig.validateForNewDb();

        init(env, dbConfig);

        /* Make the databaseImpl. */
        EnvironmentImpl environmentImpl =
            DbInternal.envGetEnvironmentImpl(envHandle);
        databaseImpl = environmentImpl.createDb(locker,
                                                databaseName,
                                                dbConfig,
                                                this);
        databaseImpl.addReferringHandle(this);
    }

    /**
     * Open a database, called by Environment.
     */
    void initExisting(Environment env,
                      Locker locker,
                      DatabaseImpl databaseImpl,
                      DatabaseConfig dbConfig)
        throws DatabaseException {

        /* 
         * Make sure the configuration used for the open is compatible with the
         * existing databaseImpl.
         */
        validateConfigAgainstExistingDb(dbConfig, databaseImpl);

        init(env, dbConfig);
        this.databaseImpl = databaseImpl;
        databaseImpl.addReferringHandle(this);

        /* 
         * Copy the duplicates and transactional properties of the underlying
         * database, in case the useExistingConfig property is set.
         */
        configuration.setSortedDuplicates(databaseImpl.getSortedDuplicates());
        configuration.setTransactional(databaseImpl.isTransactional());
    }

    private void init(Environment env,
                      DatabaseConfig config)
        throws DatabaseException {

        handleLocker = null;

        envHandle = env;
        configuration = config.cloneConfig();
        isWritable = !configuration.getReadOnly();
        state = OPEN;        
    }

    /**
     * See if this new handle's configuration is compatible with the
     * pre-existing database.
     */
    private void validateConfigAgainstExistingDb(DatabaseConfig config,
                                                 DatabaseImpl databaseImpl) 
        throws DatabaseException {

        /*
         * The allowDuplicates property is persistent and immutable.  It does
         * not need to be specified if the useExistingConfig property is set.
         */
        if (!config.getUseExistingConfig()) {
            if (databaseImpl.getSortedDuplicates() !=
                config.getSortedDuplicates()) {
                throw new DatabaseException
		    ("You can't open a Database with a duplicatesAllowed " +
		     "configuration of " +
		     config.getSortedDuplicates() +
		     " if the underlying database was created with a " +
		     "duplicatesAllowedSetting of " +
		     databaseImpl.getSortedDuplicates() + ".");
            }
        }

        /*
         * The transactional property is kept constant while any handles are
         * open, and set when the first handle is opened.  It does not need to
         * be specified if the useExistingConfig property is set.
         */
        if (databaseImpl.hasOpenHandles()) {
            if (!config.getUseExistingConfig()) {
                if (config.getTransactional() !=
                    databaseImpl.isTransactional()) {
                    throw new DatabaseException
                        ("You can't open a Database with a transactional " +
                         "configuration of " + config.getTransactional() +
                         " if the underlying database was created with a " +
                         "transactional configuration of " +
                         databaseImpl.isTransactional() + ".");
                }
            }
        } else {
            databaseImpl.setTransactional(config.getTransactional());
        }

        /*
         * The deferredWrite property is kept constant while any handles are
         * open, and set when the first handle is opened.  It does not need to
         * be specified if the useExistingConfig property is set.
         */
        if (databaseImpl.hasOpenHandles()) {
            if (!config.getUseExistingConfig()) {
                if (config.getDeferredWrite() !=
                    databaseImpl.isDeferredWrite()) {
                    throw new DatabaseException
                        ("You can't open a Database with a deferredWrite " +
                         "configuration of " + config.getDeferredWrite() +
                         " if the underlying database was created with a " +
                         "deferredWrite configuration of " +
                         databaseImpl.isDeferredWrite() + ".");
                }
            }
        } else {
            databaseImpl.setDeferredWrite(config.getDeferredWrite());
        }

        /* 
         * Only re-set the comparators if the override is allowed.
         */
        if (config.getOverrideBtreeComparator()) {
            databaseImpl.setBtreeComparator
                (config.getBtreeComparator(),
                 config.getBtreeComparatorByClassName());
        } 

        if (config.getOverrideDuplicateComparator()) {
            databaseImpl.setDuplicateComparator
		(config.getDuplicateComparator(),
		 config.getDuplicateComparatorByClassName());
        }
    }

    public synchronized void close()
        throws DatabaseException {

	try {
	    closeInternal();
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    private void closeInternal()
	throws DatabaseException {

        StringBuffer errors = null;

        checkEnv();
        checkProhibitedDbState(CLOSED, "Can't close Database:");

        trace(Level.FINEST, "Database.close: ", null, null);

        /* Disassociate triggers before closing. */
        removeAllTriggers();

        envHandle.removeReferringHandle(this);
        if (cursors.size() > 0) {
            errors = new StringBuffer
                ("There are open cursors against the database.\n");
            errors.append("They will be closed.\n");

            /* 
             * Copy the cursors set before iterating since the dbc.close()
             * mutates the set.
             */
            Iterator iter = cursors.copy().iterator();
            while (iter.hasNext()) {
                Cursor dbc = (Cursor) iter.next();

                try {
                    dbc.close();
                } catch (DatabaseException DBE) {
                    errors.append("Exception while closing cursors:\n");
                    errors.append(DBE.toString());
                }
            }
        }

        if (databaseImpl != null) {
            databaseImpl.removeReferringHandle(this);
            envHandle.getEnvironmentImpl().releaseDb(databaseImpl);
            databaseImpl = null;

            /* 
             * Tell our protecting txn that we're closing. If this type
             * of transaction doesn't live beyond the life of the handle,
             * it will release the db handle lock.
             */
            handleLocker.setHandleLockOwner(true, this, true);
            handleLocker.operationEnd(true);
            state = CLOSED;
        }

        if (errors != null) {
            throw new DatabaseException(errors.toString());
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void sync()
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.sync:");
        checkWritable("sync");
        trace(Level.FINEST, "Database.sync", null, null, null, null);

        databaseImpl.sync(true);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Sequence openSequence(Transaction txn,
                                 DatabaseEntry key,
                                 SequenceConfig config)
        throws DatabaseException {

	try {
	    checkEnv();
	    DatabaseUtil.checkForNullDbt(key, "key", true);
	    checkRequiredDbState(OPEN, "Can't call Database.openSequence:");
	    checkWritable("openSequence");
	    trace(Level.FINEST, "Database.openSequence", txn, key, null, null);

	    return new Sequence(this, txn, key, config);
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void removeSequence(Transaction txn, DatabaseEntry key)
        throws DatabaseException {

	try {
	    delete(txn, key);
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    public synchronized Cursor openCursor(Transaction txn,
                                          CursorConfig cursorConfig)
        throws DatabaseException {

	try {
	    checkEnv();
	    checkRequiredDbState(OPEN, "Can't open a cursor");
	    CursorConfig useConfig =
		(cursorConfig == null) ? CursorConfig.DEFAULT : cursorConfig;

	    if (useConfig.getReadUncommitted() &&
		useConfig.getReadCommitted()) {
		throw new IllegalArgumentException
		    ("Only one may be specified: " +
		     "ReadCommitted or ReadUncommitted");
	    }

	    trace(Level.FINEST, "Database.openCursor", txn, cursorConfig);
	    Cursor ret = newDbcInstance(txn, useConfig);

	    return ret;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }
 
    /**
     * Is overridden by SecondaryDatabase.
     */
    Cursor newDbcInstance(Transaction txn,
                          CursorConfig cursorConfig)
        throws DatabaseException {

        return new Cursor(this, txn, cursorConfig);
    }

    public OperationStatus delete(Transaction txn, DatabaseEntry key)
        throws DatabaseException {

	try {
	    checkEnv();
	    DatabaseUtil.checkForNullDbt(key, "key", true);
	    checkRequiredDbState(OPEN, "Can't call Database.delete:");
	    checkWritable("delete");
	    trace(Level.FINEST, "Database.delete", txn, key, null, null);

	    OperationStatus commitStatus = OperationStatus.NOTFOUND;
	    Locker locker = null;
	    try {
		locker = LockerFactory.getWritableLocker
		    (envHandle, txn, isTransactional());
		commitStatus = deleteInternal(locker, key, null);
		return commitStatus;
	    } finally {
		if (locker != null) {
		    locker.operationEnd(commitStatus);
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /*
     * This is commented out until we agree on whether this should even be in
     * the API.  See [14264].
    private OperationStatus delete(Transaction txn,
				   DatabaseEntry key,
				   DatabaseEntry data)
        throws DatabaseException {

	try {
	    checkEnv();
	    DatabaseUtil.checkForNullDbt(key, "key", true);
	    DatabaseUtil.checkForNullDbt(data, "data", true);
	    checkRequiredDbState(OPEN, "Can't call Database.delete:");
	    checkWritable("delete");
	    trace(Level.FINEST, "Database.delete", txn, key, data, null);

	    OperationStatus commitStatus = OperationStatus.NOTFOUND;
	    Locker locker = null;
	    try {
		locker = LockerFactory.getWritableLocker
		    (envHandle, txn, isTransactional());
		commitStatus = deleteInternal(locker, key, data);
		return commitStatus;
	    } finally {
		if (locker != null) {
		    locker.operationEnd(commitStatus);
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }
    */

    /**
     * Internal version of delete() that does no parameter checking.  Notify
     * triggers.  Deletes all duplicates.
     */
    OperationStatus deleteInternal(Locker locker,
				   DatabaseEntry key,
				   DatabaseEntry data)
        throws DatabaseException {

        Cursor cursor = null;
        try {
	    cursor = new Cursor(this, locker, null);
	    cursor.setNonCloning(true);
            OperationStatus commitStatus = OperationStatus.NOTFOUND;

            /* Position a cursor at the specified data record. */
            DatabaseEntry oldData;
            OperationStatus searchStatus;
	    if (data == null) {
		oldData = new DatabaseEntry();
                searchStatus =
		    cursor.search(key, oldData, LockMode.RMW, SearchMode.SET);
	    } else {
		oldData = data;
                searchStatus =
		    cursor.search(key, oldData, LockMode.RMW, SearchMode.BOTH);
	    }

            /* Delete all records with that key. */
            if (searchStatus == OperationStatus.SUCCESS) {
                do {

                    /*
                     * Notify triggers before the actual deletion so that a
                     * primary record never exists while secondary keys refer
                     * to it.  This is relied on by secondary read-uncommitted.
                     */
                    if (hasTriggers()) {
                        notifyTriggers(locker, key, oldData, null);
                    }
                    /* The actual deletion. */
                    commitStatus = cursor.deleteNoNotify();
                    if (commitStatus != OperationStatus.SUCCESS) {
                        return commitStatus;
                    }

		    if (data != null) {
			/* delete(key, data) called so only delete one item. */
			break;
		    }

                    /* Get another duplicate. */
                    if (databaseImpl.getSortedDuplicates()) {
                        searchStatus =
                            cursor.retrieveNext(key, oldData,
                                                LockMode.RMW,
                                                GetMode.NEXT_DUP);
                    } else {
                        searchStatus = OperationStatus.NOTFOUND;
                    }
                } while (searchStatus == OperationStatus.SUCCESS);
                commitStatus = OperationStatus.SUCCESS;
            } 
            return commitStatus;
        } finally {
	    if (cursor != null) {
		cursor.close();
	    }
        }
    }

    public OperationStatus get(Transaction txn,
                               DatabaseEntry key,
                               DatabaseEntry data,
                               LockMode lockMode)
        throws DatabaseException {

	try {
	    checkEnv();
	    DatabaseUtil.checkForNullDbt(key, "key", true);
	    DatabaseUtil.checkForNullDbt(data, "data", false);
	    checkRequiredDbState(OPEN, "Can't call Database.get:");
	    trace(Level.FINEST, "Database.get", txn, key, null, lockMode);

	    CursorConfig cursorConfig = CursorConfig.DEFAULT;
	    if (lockMode == LockMode.READ_COMMITTED) {
		cursorConfig = CursorConfig.READ_COMMITTED;
		lockMode = null;
	    }

	    Cursor cursor = null;
	    try {
		cursor = new Cursor(this, txn, cursorConfig);
		cursor.setNonCloning(true);
		return cursor.search(key, data, lockMode, SearchMode.SET);
	    } finally {
		if (cursor != null) {
		    cursor.close();
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    public OperationStatus getSearchBoth(Transaction txn,
                                         DatabaseEntry key,
                                         DatabaseEntry data,
                                         LockMode lockMode)
        throws DatabaseException {

	try {
	    checkEnv();
	    DatabaseUtil.checkForNullDbt(key, "key", true);
	    DatabaseUtil.checkForNullDbt(data, "data", true);
	    checkRequiredDbState(OPEN, "Can't call Database.getSearchBoth:");
	    trace(Level.FINEST, "Database.getSearchBoth", txn, key, data,
		  lockMode);

	    CursorConfig cursorConfig = CursorConfig.DEFAULT;
	    if (lockMode == LockMode.READ_COMMITTED) {
		cursorConfig = CursorConfig.READ_COMMITTED;
		lockMode = null;
	    }

	    Cursor cursor = null;
	    try {
		cursor = new Cursor(this, txn, cursorConfig);
		cursor.setNonCloning(true);
		return cursor.search(key, data, lockMode, SearchMode.BOTH);
	    } finally {
		if (cursor != null) {
		    cursor.close();
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    public OperationStatus put(Transaction txn,
                               DatabaseEntry key,
                               DatabaseEntry data)
        throws DatabaseException {

        checkEnv();
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkRequiredDbState(OPEN, "Can't call Database.put");
        checkWritable("put");
        trace(Level.FINEST, "Database.put", txn, key, data, null);

        return putInternal(txn, key, data, PutMode.OVERWRITE);
    }

    public OperationStatus putNoOverwrite(Transaction txn,
                                          DatabaseEntry key,
                                          DatabaseEntry data)
        throws DatabaseException {

        checkEnv();
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkRequiredDbState(OPEN, "Can't call Database.putNoOverWrite");
        checkWritable("putNoOverwrite");
        trace(Level.FINEST, "Database.putNoOverwrite", txn, key, data, null);

        return putInternal(txn, key, data, PutMode.NOOVERWRITE);
    }

    public OperationStatus putNoDupData(Transaction txn,
                                        DatabaseEntry key,
                                        DatabaseEntry data)
        throws DatabaseException {

        checkEnv();
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkRequiredDbState(OPEN, "Can't call Database.putNoDupData");
        checkWritable("putNoDupData");
        trace(Level.FINEST, "Database.putNoDupData", txn, key, data, null);

        return putInternal(txn, key, data, PutMode.NODUP);
    }

    /**
     * Internal version of put() that does no parameter checking.
     */
    OperationStatus putInternal(Transaction txn,
                                DatabaseEntry key,
				DatabaseEntry data,
				PutMode putMode)
        throws DatabaseException {

	try {
	    Locker locker = null;
	    Cursor cursor = null;
	    OperationStatus commitStatus = OperationStatus.KEYEXIST;
	    try {
		locker = LockerFactory.getWritableLocker
		    (envHandle, txn, isTransactional());

		cursor = new Cursor(this, locker, null);
		cursor.setNonCloning(true);
		commitStatus = cursor.putInternal(key, data, putMode);
		return commitStatus;
	    } finally {
		if (cursor != null) {
		    cursor.close();
		}
		if (locker != null) {
		    locker.operationEnd(commitStatus);
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /**
     */
    public JoinCursor join(Cursor[] cursors, JoinConfig config)
        throws DatabaseException {

	try {
	    checkEnv();
	    checkRequiredDbState(OPEN, "Can't call Database.join");
	    DatabaseUtil.checkForNullParam(cursors, "cursors");
	    if (cursors.length == 0) {
		throw new IllegalArgumentException
                    ("At least one cursor is required.");
	    }

	    /*
	     * Check that all cursors use the same locker, if any cursor is
	     * transactional.  And if non-transactional, that all databases are
	     * in the same environment.
	     */
	    Locker locker = cursors[0].getCursorImpl().getLocker();
	    if (!locker.isTransactional()) {
		EnvironmentImpl env = envHandle.getEnvironmentImpl();
		for (int i = 1; i < cursors.length; i += 1) {
		    Locker locker2 = cursors[i].getCursorImpl().getLocker();
		    if (locker2.isTransactional()) {
			throw new IllegalArgumentException
                            ("All cursors must use the same transaction.");
		    }
		    EnvironmentImpl env2 = cursors[i].getDatabaseImpl()
			.getDbEnvironment();
		    if (env != env2) {
			throw new IllegalArgumentException
                            ("All cursors must use the same environment.");
		    }
		}
		locker = null; /* Don't reuse a non-transactional locker. */
	    } else {
		for (int i = 1; i < cursors.length; i += 1) {
		    Locker locker2 = cursors[i].getCursorImpl().getLocker();
		    if (locker.getTxnLocker() != locker2.getTxnLocker()) {
			throw new IllegalArgumentException
                            ("All cursors must use the same transaction.");
		    }
		}
	    }

	    /* Create the join cursor. */
	    return new JoinCursor(locker, this, cursors, config);
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /**
     * @deprecated It has not been possible to implement this method with
     * correct transactional semantics without incurring a performance penalty
     * on all Database operations. Truncate functionality has been moved to
     * Environment.truncateDatabase(), which requires that all Database handles
     * on the database are closed before the truncate operation can execute.
     */
    public int truncate(Transaction txn, boolean countRecords)
        throws DatabaseException {

	try {
	    checkEnv();
	    checkRequiredDbState(OPEN, "Can't call Database.truncate");
	    checkWritable("truncate");
	    Tracer.trace(Level.FINEST, 
			 envHandle.getEnvironmentImpl(),
			 "Database.truncate: txnId=" + 
			 ((txn == null) ?
			  "null" :
			  Long.toString(txn.getId())));

	    Locker locker = null;
	    boolean triggerLock = false;
	    boolean operationOk = false;

	    try {
		locker = LockerFactory.getWritableLocker
		    (envHandle, txn, isTransactional(), true /*retainLocks*/,
		     null);

		/* 
		 * Pass true to always get a read lock on the triggers, so we
		 * are sure that no secondaries are added during truncation.
		 */
		acquireTriggerListReadLock();
		triggerLock = true;

		/* Truncate primary. */
		int count = truncateInternal(locker, countRecords);

		/* Truncate secondaries. */
		for (int i = 0; i < triggerList.size(); i += 1) {
		    Object obj = triggerList.get(i);
		    if (obj instanceof SecondaryTrigger) {
			SecondaryDatabase secDb =
			    ((SecondaryTrigger) obj).getDb();
			secDb.truncateInternal(locker, false);
		    }
		}

		operationOk = true;
		return count;
	    } finally {
		if (locker != null) {
		    locker.operationEnd(operationOk);
		}
		if (triggerLock) {
		    releaseTriggerListReadLock();
		}
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }
            
    /**
     * Internal unchecked truncate that optionally counts records.
     * @deprecated
     */
    int truncateInternal(Locker locker, boolean countRecords)
        throws DatabaseException {

        if (databaseImpl == null) {
            throw new DatabaseException
                ("couldn't find database - truncate");
        }
        databaseImpl.checkIsDeleted("truncate");

        /* 
         * Truncate must obtain a write lock. In order to do so, it assumes
         * ownership for the handle lock and transfers it from this Database
         * object to the txn.
         */
        if (handleLocker.isHandleLockTransferrable()) {
            handleLocker.transferHandleLock(this, locker, false);
        }

        boolean operationOk = false;
        try {

            /* 
             * truncate clones the existing database and returns a new one to
             * replace it with.  The old databaseImpl object is marked
             * 'deleted'.
             */
            TruncateResult result =
                envHandle.getEnvironmentImpl().truncate(locker, databaseImpl);
            databaseImpl = result.getDatabase();

            operationOk = true;
            return countRecords ? result.getRecordCount() : -1;
        } finally {

            /*
             * The txn will know if it's living past the end of this operation,
             * and if it needs to transfer the handle lock.  operationEnd()
             * will be called one level up by the public truncate() method.
             */
            locker.setHandleLockOwner(operationOk, this, false);
        }
    }

    /*
     * @deprecated As of JE 2.0.55, replaced by
     * {@link Database#preload(PreloadConfig)}. 
     */
    public void preload(long maxBytes)
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.preload");
        databaseImpl.checkIsDeleted("preload");

	PreloadConfig config = new PreloadConfig();
	config.setMaxBytes(maxBytes);
        databaseImpl.preload(config);
    }

    /*
     * @deprecated As of JE 2.1.1, replaced by
     * {@link Database#preload(PreloadConfig)}. 
     */
    public void preload(long maxBytes, long maxMillisecs)
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.preload");
        databaseImpl.checkIsDeleted("preload");

	PreloadConfig config = new PreloadConfig();
	config.setMaxBytes(maxBytes);
	config.setMaxMillisecs(maxMillisecs);
        databaseImpl.preload(config);
    }

    public PreloadStats preload(PreloadConfig config)
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.preload");
        databaseImpl.checkIsDeleted("preload");

        return databaseImpl.preload(config);
    }

    public long count()
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.count");
        databaseImpl.checkIsDeleted("count");

        return databaseImpl.count();
    }

    public DatabaseStats getStats(StatsConfig config)
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.stat");
        StatsConfig useConfig =
            (config == null) ? StatsConfig.DEFAULT : config;

        if (databaseImpl != null) {
            databaseImpl.checkIsDeleted("stat");
            return databaseImpl.stat(useConfig);
        }
        return null;
    }

    public DatabaseStats verify(VerifyConfig config)
        throws DatabaseException {

	try {
	    checkEnv();
	    checkRequiredDbState(OPEN, "Can't call Database.verify");
	    databaseImpl.checkIsDeleted("verify");
	    VerifyConfig useConfig =
		(config == null) ? VerifyConfig.DEFAULT : config;

	    DatabaseStats stats = databaseImpl.getEmptyStats();
	    databaseImpl.verify(useConfig, stats);
	    return stats;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    public String getDatabaseName()
        throws DatabaseException {

	try {
	    checkEnv();
	    if (databaseImpl != null) {
		return databaseImpl.getName();
	    } else {
		return null;
	    }
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /* 
     * Non-transactional database name, safe to access when creating error
     * messages.
     */
    String getDebugName() {
        if (databaseImpl != null) {
            return databaseImpl.getDebugName();
        } else {
            return null;
        }
    }

    public DatabaseConfig getConfig()
        throws DatabaseException {

	try {
	    DatabaseConfig showConfig = configuration.cloneConfig();

	    /* 
	     * Set the comparators from the database impl, they might have
	     * changed from another handle.
	     */
	    Comparator btComp = null;
	    Comparator dupComp = null;
	    boolean btCompByClass = false;
	    boolean dupCompByClass = false;
	    if (databaseImpl != null) {
		btComp = databaseImpl.getBtreeComparator();
		dupComp = databaseImpl.getDuplicateComparator();
		btCompByClass = databaseImpl.getBtreeComparatorByClass();
		dupCompByClass = databaseImpl.getDuplicateComparatorByClass();
	    }
	    showConfig.setBtreeComparatorInternal(btComp, btCompByClass);
	    showConfig.setDuplicateComparatorInternal(dupComp, dupCompByClass);
	    return showConfig;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /** 
     * Equivalent to getConfig().getTransactional() but cheaper.
     */
    boolean isTransactional()
        throws DatabaseException {

        return databaseImpl.isTransactional();
    }

    public Environment getEnvironment() 
        throws DatabaseException {

        return envHandle;
    }

    public List getSecondaryDatabases()
        throws DatabaseException {

	try {
	    List list = new ArrayList();
	    if (hasTriggers()) {
		acquireTriggerListReadLock();
		try {
		    for (int i = 0; i < triggerList.size(); i += 1) {
			Object obj = triggerList.get(i);
			if (obj instanceof SecondaryTrigger) {
			    list.add(((SecondaryTrigger) obj).getDb());
			}
		    }
		} finally {
		    releaseTriggerListReadLock();
		}
	    }
	    return list;
	} catch (Error E) {
	    DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
	    throw E;
	}
    }

    /* 
     * Helpers, not part of the public API
     */

    /**
     * @return true if the Database was opened read/write.
     */
    boolean isWritable() {
        return isWritable;
    }

    /**
     * Return the databaseImpl object instance.
     */
    DatabaseImpl getDatabaseImpl() {
        return databaseImpl;
    }

    /**
     * The handleLocker is the one that holds the db handle lock.
     */
    void setHandleLocker(Locker locker) {
        handleLocker = locker;
    }

    synchronized void removeCursor(Cursor dbc) {
        cursors.remove(dbc);
    }

    synchronized void addCursor(Cursor dbc) {
        cursors.add(dbc);
    }

    /**
     * @throws DatabaseException if the Database state is not this value.
     */
    void checkRequiredDbState(DbState required, String msg)
        throws DatabaseException {

        if (state != required) {
            throw new DatabaseException
                (msg + " Database state can't be " + state + 
                 " must be " + required);
        }
    }

    /**
     * @throws DatabaseException if the Database state is this value.
     */
    void checkProhibitedDbState(DbState prohibited, String msg) 
        throws DatabaseException {

        if (state == prohibited) {
            throw new DatabaseException
                (msg + " Database state must not be " + prohibited);
        }
    }

    /**
     * @throws RunRecoveryException if the underlying environment is
     * invalid
     */
    void checkEnv() 
        throws RunRecoveryException {

        EnvironmentImpl env = envHandle.getEnvironmentImpl();
        if (env != null) {
            env.checkIfInvalid();
        }
    }

    /**
     * Invalidate the handle, called by txn.abort by way of DbInternal.
     */
    synchronized void invalidate() {
        state = INVALID;
        envHandle.removeReferringHandle(this);
        if (databaseImpl != null) {
            databaseImpl.removeReferringHandle(this);
            envHandle.getEnvironmentImpl().releaseDb(databaseImpl);

            /*
             * Database.close may be called after an abort.  By setting the
             * databaseImpl field to null we ensure that close won't call
             * releaseDb or endOperation. [#13415]
             */
            databaseImpl = null;
        }
    }

    /**
     * Check that write operations aren't used on a readonly Database.
     */
    private void checkWritable(String operation)
        throws DatabaseException {

        if (!isWritable) {
            throw new DatabaseException
                ("Database is Read Only: " + operation);
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    void trace(Level level,
               String methodName,
               Transaction txn,
               DatabaseEntry key,
               DatabaseEntry data,
               LockMode lockMode) 
        throws DatabaseException {

        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(methodName);
            if (txn != null) {
                sb.append(" txnId=").append(txn.getId());
            }
            sb.append(" key=").append(key.dumpData());
            if (data != null) {
                sb.append(" data=").append(data.dumpData());
            }
            if (lockMode != null) {
                sb.append(" lockMode=").append(lockMode);
            }
            logger.log(level, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    void trace(Level level,
               String methodName,
               Transaction txn,
               CursorConfig config) 
        throws DatabaseException {

        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(methodName);
            sb.append(" name=" + getDebugName());
            if (txn != null) {
                sb.append(" txnId=").append(txn.getId());
            }
            if (config != null) {
                sb.append(" config=").append(config);
            }
            logger.log(level, sb.toString());
        }
    }

    /*
     * Manage triggers.
     */

    /**
     * Returns whether any triggers are currently associated with this primary.
     * Note that an update of the trigger list may be in progress and this
     * method does not wait for that update to be completed.
     */
    boolean hasTriggers() {

        return triggerList != null;
    }

    /**
     * Gets a read-lock on the list of triggers.  releaseTriggerListReadLock()
     * must be called to release the lock.  Called by all primary put and
     * delete operations.
     */
    private void acquireTriggerListReadLock()
        throws DatabaseException {

        EnvironmentImpl env = envHandle.getEnvironmentImpl();
        env.getTriggerLatch().acquireShared();
        if (triggerList == null) {
            triggerList = new ArrayList();
        }
    }

    /**
     * Releases a lock acquired by calling acquireTriggerListReadLock().
     */
    private void releaseTriggerListReadLock()
        throws DatabaseException {

        EnvironmentImpl env = envHandle.getEnvironmentImpl();
        env.getTriggerLatch().release();
    }

    /**
     * Gets a write lock on the list of triggers.  An empty list is created if
     * necessary, so null is never returned.  releaseTriggerListWriteLock()
     * must always be called to release the lock.
     */
    private void acquireTriggerListWriteLock()
        throws DatabaseException {

        EnvironmentImpl env = envHandle.getEnvironmentImpl();
        env.getTriggerLatch().acquireExclusive();
        if (triggerList == null) {
            triggerList = new ArrayList();
        }
    }

    /**
     * Releases a lock acquired by calling acquireTriggerListWriteLock().  If
     * the list is now empty then it is set to null, that is, hasTriggers()
     * will subsequently return false.
     */
    private void releaseTriggerListWriteLock()
        throws DatabaseException {

        if (triggerList.size() == 0) {
            triggerList = null;
        }
        EnvironmentImpl env = envHandle.getEnvironmentImpl();
        env.getTriggerLatch().release();
    }

    /**
     * Adds a given trigger to the list of triggers.  Called while opening
     * a SecondaryDatabase.
     *
     * @param insertAtFront true to insert at the front, or false to append.
     */
    void addTrigger(DatabaseTrigger trigger, boolean insertAtFront)
        throws DatabaseException {

        acquireTriggerListWriteLock();
        try {
            if (insertAtFront) {
                triggerList.add(0, trigger);
            } else {
                triggerList.add(trigger);
            }
            trigger.triggerAdded(this);
        } finally {
            releaseTriggerListWriteLock();
        }
    }

    /**
     * Removes a given trigger from the list of triggers.  Called by
     * SecondaryDatabase.close().
     */
    void removeTrigger(DatabaseTrigger trigger)
        throws DatabaseException {

        acquireTriggerListWriteLock();
        try {
            triggerList.remove(trigger);
            trigger.triggerRemoved(this);
        } finally {
            releaseTriggerListWriteLock();
        }
    }

    /**
     * Clears the list of triggers.  Called by close(), this allows closing the
     * primary before its secondaries, although we document that secondaries
     * should be closed first.
     */
    private void removeAllTriggers()
        throws DatabaseException {

        acquireTriggerListWriteLock();
        try {
            for (int i = 0; i < triggerList.size(); i += 1) {
                DatabaseTrigger trigger = (DatabaseTrigger) triggerList.get(i);
                trigger.triggerRemoved(this);
            }
            triggerList.clear();
        } finally {
            releaseTriggerListWriteLock();
        }
    }

    /**
     * Notifies associated triggers when a put() or delete() is performed on
     * the primary.  This method is normally called only if hasTriggers() has
     * returned true earlier.  This avoids acquiring a shared latch for
     * primaries with no triggers.  If a trigger is added during the update
     * process, there is no requirement to immediately start updating it.
     *
     * @param locker the internal locker.
     *
     * @param priKey the primary key.
     *
     * @param oldData the primary data before the change, or null if the record
     * did not previously exist.
     *
     * @param newData the primary data after the change, or null if the record
     * has been deleted.
     */
    void notifyTriggers(Locker locker,
                        DatabaseEntry priKey,
                        DatabaseEntry oldData,
                        DatabaseEntry newData)
        throws DatabaseException {

        acquireTriggerListReadLock();
        try {
            for (int i = 0; i < triggerList.size(); i += 1) {
                DatabaseTrigger trigger = (DatabaseTrigger) triggerList.get(i);

                /* Notify trigger. */
                trigger.databaseUpdated
                    (this, locker, priKey, oldData, newData);
            }
        } finally {
            releaseTriggerListReadLock();
        }
    }
}
