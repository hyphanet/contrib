/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Database.java,v 1.242 2008/06/10 02:52:08 cwl Exp $
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
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.utilint.DatabaseUtil;
import com.sleepycat.je.utilint.TinyHashSet;

/**
 * A database handle.
 *
 * <p>Database attributes are specified in the {@link
 * com.sleepycat.je.DatabaseConfig DatabaseConfig} class. Database handles are
 * free-threaded and may be used concurrently by multiple threads.</p>
 *
 * <p>To open an existing database with default attributes:</p>
 *
 * <blockquote><pre>
 *     Environment env = new Environment(home, null);
 *     Database myDatabase = env.openDatabase(null, "mydatabase", null);
 * </pre></blockquote>
 *
 * <p>To create a transactional database that supports duplicates:</p>
 *
 * <blockquote><pre>
 *     DatabaseConfig dbConfig = new DatabaseConfig();
 *     dbConfig.setTransactional(true);
 *     dbConfig.setAllowCreate(true);
 *     dbConfig.setSortedDuplicates(true);
 *     Database newlyCreateDb = env.openDatabase(txn, "mydatabase", dbConfig);
 * </pre></blockquote>
 */
public class Database {

    /*
     * DbState embodies the Database handle state.
     */
    static class DbState {
        private String stateName;

        DbState(String stateName) {
            this.stateName = stateName;
        }

        @Override
        public String toString() {
            return "DbState." + stateName;
        }
    }

    static DbState OPEN = new DbState("OPEN");
    static DbState CLOSED = new DbState("CLOSED");
    static DbState INVALID = new DbState("INVALID");

    /* The current state of the handle. */
    private volatile DbState state;

    /* Handles onto the owning environment and the databaseImpl object. */
    Environment envHandle;            // used by subclasses
    private DatabaseImpl databaseImpl;

    DatabaseConfig configuration;     // properties used at execution

    /* True if this handle permits write operations; */
    private boolean isWritable;

    /* Transaction that owns the db lock held while the Database is open. */
    Locker handleLocker;

    /* Set of cursors open against this db handle. */
    private TinyHashSet<Cursor> cursors = new TinyHashSet<Cursor>();

    /*
     * DatabaseTrigger list.  The list is null if empty, and is checked for
     * null to avoiding read locking overhead when no triggers are present.
     * Access to this list is protected by the shared trigger latch in
     * EnvironmentImpl.
     */
    private List<DatabaseTrigger> triggerList;

    private Logger logger;

    /**
     * Creates a database but does not open or fully initialize it.  Is
     * protected for use in compat package.
     * @param env
     */
    protected Database(Environment env) {
        this.envHandle = env;
        handleLocker = null;
        logger = envHandle.getEnvironmentImpl().getLogger();
    }

    /**
     * Creates a database, called by Environment.
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
        databaseImpl = environmentImpl.getDbTree().createDb
	    (locker, databaseName, dbConfig, this);
        databaseImpl.addReferringHandle(this);

        /*
         * Copy the replicated setting into the cloned handle configuration.
         */
        configuration.setReplicated(databaseImpl.isReplicated());
    }

    /**
     * Opens a database, called by Environment.
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
         * Copy the duplicates, transactional and replicated properties of the
         * underlying database, in case the useExistingConfig property is set.
         */
        configuration.setSortedDuplicates(databaseImpl.getSortedDuplicates());
        configuration.setTransactional(databaseImpl.isTransactional());
        configuration.setReplicated(databaseImpl.isReplicated());
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
     * Sees if this new handle's configuration is compatible with the
     * pre-existing database.
     */
    private void validateConfigAgainstExistingDb(DatabaseConfig config,
                                                 DatabaseImpl databaseImpl)
        throws DatabaseException {

        /*
         * The sortedDuplicates, temporary, and replicated properties are
         * persistent and immutable.  But they do not need to be specified if
         * the useExistingConfig property is set.
         */
        if (!config.getUseExistingConfig()) {
            validatePropertyMatches
                ("sortedDuplicates", databaseImpl.getSortedDuplicates(),
                 config.getSortedDuplicates());
            validatePropertyMatches
                ("temporary", databaseImpl.isTemporary(),
                 config.getTemporary());
            /* Only check replicated if the environment is replicated. */
            if (envHandle.getEnvironmentImpl().isReplicated()) {
                if (databaseImpl.unknownReplicated()) {
                    throw new UnsupportedOperationException("Conversion " +
                          "of standalone environments to replicated " +
                          "environments isn't supported yet");
                }
                validatePropertyMatches
                    ("replicated", databaseImpl.isReplicated(),
                     DbInternal.getDbConfigReplicated(config));
            }
        }

        /*
         * The transactional and deferredWrite properties are kept constant
         * while any handles are open, and set when the first handle is opened.
         * But if an existing handle is open and the useExistingConfig property
         * is set, then they do not need to be specified.
         */
        if (databaseImpl.hasOpenHandles()) {
            if (!config.getUseExistingConfig()) {
                validatePropertyMatches
                    ("transactional", databaseImpl.isTransactional(),
                     config.getTransactional());
                validatePropertyMatches
                    ("deferredWrite", databaseImpl.isDurableDeferredWrite(),
                     config.getDeferredWrite());
            }
        } else {
            databaseImpl.setTransactional(config.getTransactional());
            databaseImpl.setDeferredWrite(config.getDeferredWrite());
        }

        /*
         * Only re-set the comparators if the override is allowed.
         */
	boolean dbImplModified = false;
        if (config.getOverrideBtreeComparator()) {
	    dbImplModified |= databaseImpl.setBtreeComparator
                (config.getBtreeComparator(),
                 config.getBtreeComparatorByClassName());
        }

        if (config.getOverrideDuplicateComparator()) {
            dbImplModified |= databaseImpl.setDuplicateComparator
                (config.getDuplicateComparator(),
                 config.getDuplicateComparatorByClassName());
        }

        boolean newKeyPrefixing = config.getKeyPrefixing();
        if (newKeyPrefixing != databaseImpl.getKeyPrefixing()) {
            dbImplModified = true;
            if (newKeyPrefixing) {
                databaseImpl.setKeyPrefixing();
            } else {
                databaseImpl.clearKeyPrefixing();
            }
        }

	/* [#15743] */
	if (dbImplModified) {
	    EnvironmentImpl envImpl = envHandle.getEnvironmentImpl();

	    /* Dirty the root. */
	    envImpl.getDbTree().modifyDbRoot(databaseImpl);
	}
    }

    private void validatePropertyMatches(String propName,
                                         boolean existingValue,
                                         boolean newValue)
        throws IllegalArgumentException {

        if (newValue != existingValue) {
            throw new IllegalArgumentException
                ("You can't open a Database with a " + propName +
                 " configuration of " + newValue +
                 " if the underlying database was created with a " +
                 propName + " setting of " + existingValue + '.');
        }
    }

    /**
     * Discards the database handle.
     * <p>
     * When closing the last open handle for a deferred-write database, any
     * cached database information is flushed to disk as if {@link #sync} were
     * called.
     * <p>
     * The database handle should not be closed while any other handle that
     * refers to it is not yet closed; for example, database handles should not
     * be closed while cursor handles into the database remain open, or
     * transactions that include operations on the database have not yet been
     * committed or aborted.  Specifically, this includes {@link
     * com.sleepycat.je.Cursor Cursor} and {@link com.sleepycat.je.Transaction
     * Transaction} handles.
     * <p>
     * When multiple threads are using the {@link com.sleepycat.je.Database
     * Database} handle concurrently, only a single thread may call this
     * method.
     * <p>
     * The database handle may not be accessed again after this method is
     * called, regardless of the method's success or failure.
     * <p>
     * When called on a database that is the primary database for a secondary
     * index, the primary database should be closed only after all secondary
     * indices which reference it have been closed.
     *
     * @see DatabaseConfig#setDeferredWrite DatabaseConfig.setDeferredWrite
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void close()
        throws DatabaseException {

        try {
            closeInternal(true /* doSyncDw */);
        } catch (Error E) {
            DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
            throw E;
        }
    }

    /*
     * This method is private for now because it is incomplete.  To fully
     * implement it we must clear all dirty nodes for the database that is
     * closed, since otherwise they will be flushed during the next checkpoint.
     */
    @SuppressWarnings("unused")
    private void closeNoSync()
        throws DatabaseException {

        try {
            closeInternal(false /* doSyncDw */);
        } catch (Error E) {
            DbInternal.envGetEnvironmentImpl(envHandle).invalidate(E);
            throw E;
        }
    }

    private void closeInternal(boolean doSyncDw)
        throws DatabaseException {

        StringBuffer errors = null;
        DatabaseImpl dbClosed = null;

        synchronized (this) {
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
                Iterator<Cursor> iter = cursors.copy().iterator();
                while (iter.hasNext()) {
                    Cursor dbc = iter.next();

                    try {
                        dbc.close();
                    } catch (DatabaseException DBE) {
                        errors.append("Exception while closing cursors:\n");
                        errors.append(DBE.toString());
                    }
                }
            }

            if (databaseImpl != null) {
                dbClosed = databaseImpl;
                databaseImpl.removeReferringHandle(this);
                envHandle.getEnvironmentImpl().
                    getDbTree().releaseDb(databaseImpl);
                databaseImpl = null;

                /*
                 * Tell our protecting txn that we're closing. If this type of
                 * transaction doesn't live beyond the life of the handle, it
                 * will release the db handle lock.
                 */
                handleLocker.setHandleLockOwner(true, this, true);
                handleLocker.operationEnd(true);
                state = CLOSED;
            }
        }

        /*
         * Notify the database when a handle is closed.  This should not be
         * done while synchronized since it may perform database removal or
         * sync.  Statements above are synchronized obove but this section must
         * not be.
         */
        if (dbClosed != null) {
            dbClosed.handleClosed(doSyncDw);
        }

        if (errors != null) {
            throw new DatabaseException(errors.toString());
        }
    }

    /**
     * Flushes any cached information for this database to disk; only
     * applicable for deferred-write databases.
     * <p> Note that deferred-write databases are automatically flushed to disk
     * when the {@link #close} method is called.
     *
     * @see DatabaseConfig#setDeferredWrite DatabaseConfig.setDeferredWrite
     *
     * @throws DatabaseException if a failure occurs.
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
     * Opens a sequence in the database.
     *
     * @param txn For a transactional database, an explicit transaction may
     * be specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key The key {@link com.sleepycat.je.DatabaseEntry
     * DatabaseEntry} of the sequence.
     *
     * @param config The sequence attributes.  If null, default
     * attributes are used.
     *
     * @return A sequence handle.
     *
     * @throws DatabaseException if a failure occurs.
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
     * Removes the sequence from the database.  This method should not be
     * called if there are open handles on this sequence.
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key The key {@link com.sleepycat.je.DatabaseEntry
     * DatabaseEntry} of the sequence.
     * 
     * @throws DatabaseException
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

    /**
     * Returns a cursor into the database.
     *
     * @param txn To use a cursor for writing to a transactional database, an
     * explicit transaction must be specified.  For read-only access to a
     * transactional database, the transaction may be null.  For a
     * non-transactional database, the transaction must be null.
     *
     * <p>To transaction-protect cursor operations, cursors must be opened and
     * closed within the context of a transaction, and the txn parameter
     * specifies the transaction context in which the cursor will be used.</p>
     *
     * @param cursorConfig The cursor attributes.  If null, default
     * attributes are used.
     *
     * @return A database cursor.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Removes key/data pairs from the database.
     *
     * <p>The key/data pair associated with the specified key is discarded
     * from the database.  In the presence of duplicate key values, all
     * records associated with the designated key will be discarded.</p>
     *
     * <p>The key/data pair is also deleted from any associated secondary
     * databases.</p>
     *
     * @param txn For a transactional database, an explicit transaction may
     * be specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the key {@link com.sleepycat.je.DatabaseEntry DatabaseEntry}
     * operated on.
     *
     * @return The method will return {@link
     * com.sleepycat.je.OperationStatus#NOTFOUND OperationStatus.NOTFOUND} if
     * the specified key is not found in the database; otherwise the method
     * will return {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS}.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseException if a failure occurs.
     */
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
                    (envHandle, txn, isTransactional(),
                     databaseImpl.isReplicated()); // autoTxnIsReplicated
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

    /**
     * Retrieves the key/data pair with the given key.  If the matching key has
     * duplicate values, the first data item in the set of duplicates is
     * returned. Retrieval of duplicates requires the use of {@link Cursor}
     * operations.
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified to transaction-protect the operation, or null may be specified
     * to perform the operation without transaction protection.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the key used as input.  It must be initialized with a
     * non-null byte array by the caller.
     *
     * @param data the data returned as output.  Its byte array does not need
     * to be initialized by the caller.
     *
     * @param lockMode the locking attributes; if null, default attributes are
     * used.
     *
     * @return {@link com.sleepycat.je.OperationStatus#NOTFOUND
     * OperationStatus.NOTFOUND} if no matching key/data pair is found;
     * otherwise, {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS}.
     *
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Retrieves the key/data pair with the given key and data value, that is,
     * both the key and data items must match.
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified to transaction-protect the operation, or null may be specified
     * to perform the operation without transaction protection.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the keyused as input.  It must be initialized with a non-null
     * byte array by the caller.
     *
     * @param data the dataused as input.  It must be initialized with a
     * non-null byte array by the caller.
     *
     * @param lockMode the locking attributes; if null, default attributes are
     * used.
     *
     * @return {@link com.sleepycat.je.OperationStatus#NOTFOUND
     * OperationStatus.NOTFOUND} if no matching key/data pair is found;
     * otherwise, {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS}.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Stores the key/data pair into the database.
     *
     * <p>If the key already appears in the database and duplicates are not
     * configured, the existing key/data pair will be replaced.  If the key
     * already appears in the database and sorted duplicates are configured,
     * the new data value is inserted at the correct sorted location.</p>
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the key {@link com.sleepycat.je.DatabaseEntry DatabaseEntry}
     * operated on.
     *
     * @param data the data {@link com.sleepycat.je.DatabaseEntry
     * DatabaseEntry} stored.
     * 
     * @return {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS} if the operation succeeds.

     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Stores the key/data pair into the database if the key does not already
     * appear in the database.
     *
     * <p>This method will return {@link
     * com.sleepycat.je.OperationStatus#KEYEXIST OpeationStatus.KEYEXIST} if
     * the key already exists in the database, even if the database supports
     * duplicates.</p>
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the key {@link com.sleepycat.je.DatabaseEntry DatabaseEntry}
     * operated on.
     *
     * @param data the data {@link com.sleepycat.je.DatabaseEntry
     * DatabaseEntry} stored.
     *
     * @return {@link com.sleepycat.je.OperationStatus#KEYEXIST
     * OperationStatus.KEYEXIST} if the key already appears in the database, 
     * else {@link com.sleepycat.je.OperationStatus#SUCCESS 
     * OperationStatus.SUCCESS}
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseException if any other failure occurs.
     */
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

    /**
     * Stores the key/data pair into the database if it does not already appear
     * in the database.
     *
     * <p>This method may only be called if the underlying database has been
     * configured to support sorted duplicates.</p>
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param key the key {@link com.sleepycat.je.DatabaseEntry DatabaseEntry}
     * operated on.
     *
     * @param data the data {@link com.sleepycat.je.DatabaseEntry
     * DatabaseEntry} stored.
     *
     * @return true if the key/data pair already appears in the database, this
     * method will return {@link com.sleepycat.je.OperationStatus#KEYEXIST
     * OperationStatus.KEYEXIST}.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseException if a failure occurs.
     */
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
                    (envHandle, txn, isTransactional(),
                     databaseImpl.isReplicated()); // autoTxnIsReplicated

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
     * Creates a specialized join cursor for use in performing equality or
     * natural joins on secondary indices.
     *
     * <p>Each cursor in the <code>cursors</code> array must have been
     * initialized to refer to the key on which the underlying database should
     * be joined.  Typically, this initialization is done by calling {@link
     * Cursor#getSearchKey Cursor.getSearchKey}.</p>
     *
     * <p>Once the cursors have been passed to this method, they should not be
     * accessed or modified until the newly created join cursor has been
     * closed, or else inconsistent results may be returned.  However, the
     * position of the cursors will not be changed by this method or by the
     * methods of the join cursor.</p>
     *
     * @param cursors an array of cursors associated with this primary
     * database.
     *
     * @param config The join attributes.  If null, default attributes are
     * used.
     *
     * @return a specialized cursor that returns the results of the equality
     * join operation.
     *
     * @throws DatabaseException if a failure occurs. @see JoinCursor
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
     * Preloads the cache.  This method should only be called when there are no
     * operations being performed on the database in other threads.  Executing
     * preload during concurrent updates may result in some or all of the tree
     * being loaded into the JE cache.  Executing preload during any other
     * types of operations may result in JE exceeding its allocated cache
     * size. preload() effectively locks the entire database and therefore will
     * lock out the checkpointer, cleaner, and compressor, as well as not allow
     * eviction to occur.
     *
     * @deprecated As of JE 2.0.83, replaced by {@link
     * Database#preload(PreloadConfig)}.</p>
     *
     * @param maxBytes The maximum number of bytes to load.  If maxBytes is 0,
     * je.evictor.maxMemory is used.
     *
     * @throws DatabaseException if a failure occurs.
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

    /**
     * Preloads the cache.  This method should only be called when there are no
     * operations being performed on the database in other threads.  Executing
     * preload during concurrent updates may result in some or all of the tree
     * being loaded into the JE cache.  Executing preload during any other
     * types of operations may result in JE exceeding its allocated cache
     * size. preload() effectively locks the entire database and therefore will
     * lock out the checkpointer, cleaner, and compressor, as well as not allow
     * eviction to occur.
     *
     * @deprecated As of JE 2.0.101, replaced by {@link
     * Database#preload(PreloadConfig)}.</p>
     *
     * @param maxBytes The maximum number of bytes to load.  If maxBytes is 0,
     * je.evictor.maxMemory is used.
     *
     * @param maxMillisecs The maximum time in milliseconds to use when
     * preloading.  Preloading stops once this limit has been reached.  If
     * maxMillisecs is 0, preloading can go on indefinitely or until maxBytes
     * (if non-0) is reached.
     *
     * @throws DatabaseException if a failure occurs.
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

    /**
     * Preloads the cache.  This method should only be called when there are no
     * operations being performed on the database in other threads.  Executing
     * preload during concurrent updates may result in some or all of the tree
     * being loaded into the JE cache.  Executing preload during any other
     * types of operations may result in JE exceeding its allocated cache
     * size. preload() effectively locks the entire database and therefore will
     * lock out the checkpointer, cleaner, and compressor, as well as not allow
     * eviction to occur.
     *
     * @param config The PreloadConfig object that specifies the parameters
     * of the preload.
     *
     * @return A PreloadStats object with various statistics about the
     * preload() operation.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public PreloadStats preload(PreloadConfig config)
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.preload");
        databaseImpl.checkIsDeleted("preload");

        return databaseImpl.preload(config);
    }

    /**
     * Counts the key/data pairs in the database. This operation is faster than
     * obtaining a count from a cursor based scan of the database, and will not
     * perturb the current contents of the cache. However, the count is not
     * guaranteed to be accurate if there are concurrent updates.
     *
     * <p>A count of the key/data pairs in the database is returned without
     * adding to the cache.  The count may not be accurate in the face of
     * concurrent update operations in the database.</p>
     *
     * @return The count of key/data pairs in the database.
     */
    public long count()
        throws DatabaseException {

        checkEnv();
        checkRequiredDbState(OPEN, "Can't call Database.count");
        databaseImpl.checkIsDeleted("count");

        return databaseImpl.count();
    }

    /**
     * Returns database statistics.
     *
     * <p>If this method has not been configured to avoid expensive operations
     * (using the {@link com.sleepycat.je.StatsConfig#setFast
     * StatsConfig.setFast} method), it will access some of or all the pages in
     * the database, incurring a severe performance penalty as well as possibly
     * flushing the underlying cache.</p>
     *
     * <p>In the presence of multiple threads or processes accessing an active
     * database, the information returned by this method may be
     * out-of-date.</p>
     *
     * @param config The statistics returned; if null, default statistics are
     * returned.
     *
     * @return Database statistics.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Verifies the integrity of the database.
     *
     * <p>Verification is an expensive operation that should normally only be
     * used for troubleshooting and debugging.</p>
     *
     * @param config Configures the verify operation; if null, the default
     * operation is performed.
     *
     * @return Database statistics.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
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

    /**
     * Returns the database name.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The database name.
     */
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

    /**
     * Returns this Database object's configuration.
     *
     * <p>This may differ from the configuration used to open this object if
     * the database existed previously.</p>
     *
     * @return This Database object's configuration.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public DatabaseConfig getConfig()
        throws DatabaseException {

        try {
            DatabaseConfig showConfig = configuration.cloneConfig();

            /*
             * Set the comparators from the database impl, they might have
             * changed from another handle.
             */
            Comparator<byte[]> btComp = null;
            Comparator<byte[]> dupComp = null;
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

    /**
     * Returns the {@link com.sleepycat.je.Environment Environment} handle for
     * the database environment underlying the {@link
     * com.sleepycat.je.Database Database}.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The {@link com.sleepycat.je.Environment Environment} handle
     * for the database environment underlying the {@link
     * com.sleepycat.je.Database Database}.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public Environment getEnvironment()
        throws DatabaseException {

        return envHandle;
    }

    /**
     * Returns a list of all {@link com.sleepycat.je.SecondaryDatabase
     * SecondaryDatabase} objects associated with a primary database.
     *
     * <p>If no secondaries are associated or this is itself a secondary
     * database, an empty list is returned.</p>
     *
     * @return A list of all {@link com.sleepycat.je.SecondaryDatabase
     * SecondaryDatabase} objects associated with a primary database.
     */
    public List<SecondaryDatabase> getSecondaryDatabases()
        throws DatabaseException {

        try {
            List<SecondaryDatabase> list = new ArrayList<SecondaryDatabase>();
            if (hasTriggers()) {
                acquireTriggerListReadLock();
                try {
                    for (int i = 0; i < triggerList.size(); i += 1) {
                        DatabaseTrigger t = triggerList.get(i);
                        if (t instanceof SecondaryTrigger) {
                            list.add(((SecondaryTrigger) t).getDb());
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
     * Returns true if the Database was opened read/write.
     *
     * @return true if the Database was opened read/write.
     */
    boolean isWritable() {
        return isWritable;
    }

    /**
     * Returns the databaseImpl object instance.
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
     * Invalidates the handle, called by txn.abort by way of DbInternal.
     *
     * Note that this method (unlike close) does not call handleClosed, which
     * performs sync and removal of DW DBs.  A DW DB cannot be transactional. 
     */
    synchronized void invalidate() {
        state = INVALID;
        envHandle.removeReferringHandle(this);
        if (databaseImpl != null) {
            databaseImpl.removeReferringHandle(this);
            envHandle.getEnvironmentImpl().
                getDbTree().releaseDb(databaseImpl);

            /*
             * Database.close may be called after an abort.  By setting the
             * databaseImpl field to null we ensure that close won't call
             * releaseDb or endOperation. [#13415]
             */
            databaseImpl = null;
        }
    }

    /**
     * Checks that write operations aren't used on a readonly Database.
     */
    private void checkWritable(String operation)
        throws DatabaseException {

        if (!isWritable) {
            throw new UnsupportedOperationException
                ("Database is Read Only: " + operation);
        }
    }

    /**
     * Sends trace messages to the java.util.logger. Don't rely on the logger
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
     * Sends trace messages to the java.util.logger. Don't rely on the logger
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
            triggerList = new ArrayList<DatabaseTrigger>();
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
            triggerList = new ArrayList<DatabaseTrigger>();
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
                DatabaseTrigger trigger = triggerList.get(i);
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
                DatabaseTrigger trigger = triggerList.get(i);

                /* Notify trigger. */
                trigger.databaseUpdated
                    (this, locker, priKey, oldData, newData);
            }
        } finally {
            releaseTriggerListReadLock();
        }
    }
}
