/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: Environment.java,v 1.217.2.1 2008/07/24 07:25:30 tao Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.DatabaseUtil;
import com.sleepycat.je.utilint.Tracer;

/**
 * A database environment.  Environments include support for some or all of
 * caching, locking, logging and transactions.
 *
 * <p>To open an existing environment with default attributes the application
 * may use a default environment configuration object or null:</p>
 *
 * <blockquote><pre>
 *   // Open an environment handle with default attributes.
 *   Environment env = new Environment(home, new EnvironmentConfig());
 * </pre></blockquote>
 *
 * <p>or</p>
 *
 * <blockquote><pre>
 *     Environment env = new Environment(home, null);
 * </pre></blockquote>
 *
 * <p>Note that many Environment objects may access a single environment.</p>
 *
 * <p>To create an environment or customize attributes, the application should
 * customize the configuration class. For example:</p>
 *
 * <blockquote><pre>
 *     EnvironmentConfig envConfig = new EnvironmentConfig();
 *     envConfig.setTransactional(true);
 *     envConfig.setAllowCreate(true);
 *     envConfig.setCacheSize(1000000);
 *     <p>
 *     Environment newlyCreatedEnv = new Environment(home, envConfig);
 * </pre></blockquote>
 *
 * <p>Note that environment configuration parameters can also be set through
 * the &lt;environment home&gt;/je.properties file. This file takes precedence
 * over any programmatically specified configuration parameters so that
 * configuration changes can be made without recompiling. Environment
 * configuration follows this order of precedence:</p>
 *
 * <ol>
 * <li>Configuration parameters specified in
 * &lt;environment home&gt;/je.properties take first precedence.
 * <li> Configuration parameters set in the EnvironmentConfig object used at
 * Environment construction are next.
 * <li>Any configuration parameters not set by the application are set to
 * system defaults, described along with the parameter name String constants
 * in the EnvironmentConfig class.
 * </ol>
 *
 * <p>An <em>environment handle</em> is an Environment instance.  More than one
 * Environment instance may be created for the same physical directory, which
 * is the same as saying that more than one Environment handle may be open at
 * one time for a given environment.</p>
 *
 * The Environment handle should not be closed while any other handle remains
 * open that is using it as a reference (for example, {@link
 * com.sleepycat.je.Database Database} or {@link com.sleepycat.je.Transaction
 * Transaction}.  Once {@link com.sleepycat.je.Environment#close
 * Environment.close} is called, this object may not be accessed again,
 * regardless of whether or not it throws an exception.
 */
public class Environment {

    /**
     * @hidden
     *  envImpl is a reference to the shared underlying environment.
     */
    protected EnvironmentImpl envImpl;
    private TransactionConfig defaultTxnConfig;
    private EnvironmentMutableConfig handleConfig;

    private Set<Database> referringDbs;
    private Set<Transaction> referringDbTxns;

    private boolean valid;

    /**
     * @hidden
     * The name of the cleaner daemon thread.  This constant is passed to an
     * ExceptionEvent's threadName argument when an exception is thrown in the
     * cleaner daemon thread.
     */
    public static final String CLEANER_NAME = "Cleaner";

    /**
     * @hidden
     * The name of the IN Compressor daemon thread.  This constant is passed to
     * an ExceptionEvent's threadName argument when an exception is thrown in
     * the IN Compressor daemon thread.
     */
    public static final String INCOMP_NAME = "INCompressor";

    /**
     * @hidden
     * The name of the Checkpointer daemon thread.  This constant is passed to
     * an ExceptionEvent's threadName argument when an exception is thrown in
     * the Checkpointer daemon thread.
     */
    public static final String CHECKPOINTER_NAME = "Checkpointer";

    /**
    * Creates a database environment handle.
    *
    * @param envHome The database environment's home directory.
    *
    * @param configuration The database environment attributes.  If null,
    * default attributes are used.
    *
    * @throws IllegalArgumentException if an invalid parameter was specified.
    *
    * @throws DatabaseException if a failure occurs.
    *
    * @throws EnvironmentLockedException when an environment cannot be opened
    * for write access because another process has the same environment open
    * for write access.
     */
    public Environment(File envHome, EnvironmentConfig configuration)
        throws DatabaseException, EnvironmentLockedException {

        this(envHome, configuration, true /*openIfNeeded*/,
             false /*replicationIntended*/);
    }

    /**
     * Replication support. Environments are created before the replicator, but
     * we must check at recovery time whether the environment will be used for
     * replication, so we can error check the persistent replication bit.
     */
    Environment(File envHome,
                EnvironmentConfig configuration,
                boolean replicationIntended)
        throws DatabaseException {

        this(envHome, configuration, true /*openIfNeeded*/,
             replicationIntended);
    }

    /**
     * Gets an Environment for an existing EnvironmentImpl. Used by utilities
     * such as the JMX MBean which don't want to open the environment or be
     * reference counted. The calling application must take care not to retain
     */
    Environment(File envHome)
        throws DatabaseException {

        this(envHome, null /*configuration*/, false /*openIfNeeded*/,
             false /*replicationIntended*/);
    }

    /**
     * Internal common constructor.
     */
    private Environment(File envHome,
                        EnvironmentConfig configuration,
                        boolean openIfNeeded,
                        boolean replicationIntended)
        throws DatabaseException {

        /* If openIfNeeded is false, then configuration must be null. */
        assert openIfNeeded || configuration == null;

        envImpl = null;
        referringDbs = Collections.synchronizedSet(new HashSet<Database>());
        referringDbTxns =
            Collections.synchronizedSet(new HashSet<Transaction>());
        valid = false;

        DatabaseUtil.checkForNullParam(envHome, "envHome");

        /* If the user specified a null object, use the default */
        EnvironmentConfig baseConfig = (configuration == null) ?
            EnvironmentConfig.DEFAULT : configuration;

        /* Make a copy, apply je.properties, and init the handle config. */
        EnvironmentConfig useConfig = baseConfig.cloneConfig();
        applyFileConfig(envHome, useConfig);
        copyToHandleConfig(useConfig, useConfig);

        /* Open a new or existing environment in the shared pool. */
        envImpl = DbEnvPool.getInstance().getEnvironment
            (envHome, useConfig,
             configuration != null /*checkImmutableParams*/,
             openIfNeeded, replicationIntended);

        valid = true;
    }

    /**
     * Applies the configurations specified in the je.properties file to
     * override any programatically set configurations.
     */
    private void applyFileConfig(File envHome,
                                 EnvironmentMutableConfig useConfig)
        throws IllegalArgumentException {

        /* Apply the je.properties file. */
        if (useConfig.getLoadPropertyFile()) {
            DbConfigManager.applyFileConfig(envHome,
                                            DbInternal.getProps(useConfig),
                                            false,       // forReplication
                                            useConfig.getClass().getName());
        }
    }

    /**
     * The Environment.close method closes the Berkeley DB environment.
     *
     * <p>When the last environment handle is closed, allocated resources are
     * freed, and daemon threads are stopped, even if they are performing work.
     * For example, if the cleaner is still cleaning the log, it will be
     * stopped at the next reasonable opportunity and perform no more cleaning
     * operations.</p>
     *
     * <p>The Environment handle should not be closed while any other handle
     * that refers to it is not yet closed; for example, database environment
     * handles must not be closed while database handles remain open, or
     * transactions in the environment have not yet committed or aborted.
     * Specifically, this includes {@link com.sleepycat.je.Database Database},
     * {@link com.sleepycat.je.Cursor Cursor} and {@link
     * com.sleepycat.je.Transaction Transaction} handles.</p>
     *
     * <p>In multithreaded applications, only a single thread should call
     * Environment.close. Other callers will see a DatabaseException
     * complaining that the handle is already closed.</p>
     *
     * <p>After Environment.close has been called, regardless of its return,
     * the Berkeley DB environment handle may not be accessed again.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public synchronized void close()
        throws DatabaseException {

        checkHandleIsValid();
        try {
            checkEnv();
        } catch (RunRecoveryException e) {

            /*
             * We're trying to close on an environment that has seen a fatal
             * exception. Try to do the minimum, such as closing file
             * descriptors, to support re-opening the environment in the same
             * JVM.
             */
            if (envImpl != null) {
                envImpl.closeAfterRunRecovery();
            }
            return;
        }

        StringBuffer errors = new StringBuffer();
        try {
            if (referringDbs != null) {
                int nDbs = referringDbs.size();
                if (nDbs != 0) {
                    errors.append("There ");
                    if (nDbs == 1) {
                        errors.append
                            ("is 1 open Database in the Environment.\n");
                    } else {
                        errors.append("are ");
                        errors.append(nDbs);
                        errors.append
                            (" open Databases in the Environment.\n");
                    }
                    errors.append("Closing the following databases:\n");

                    /*
                     * Copy the referringDbs Set because db.close() below
                     * modifies this Set, potentially causing a
                     * ConcurrentModificationException.
                     */
                    Iterator<Database> iter =
                        new HashSet<Database>(referringDbs).iterator();
                    while (iter.hasNext()) {
                        String dbName = "";
                        try {
                            Database db = iter.next();

                            /*
                             * Save the db name before we attempt the close,
                             * it's unavailable after the close.
                             */
                            dbName = db.getDebugName();
                            errors.append(dbName).append(" ");
                            db.close();
                        } catch (RunRecoveryException e) {
                            throw e;
                        } catch (DatabaseException E) {
                            errors.append("\nWhile closing Database ");
                            errors.append(dbName);
                            errors.append(" encountered exception: ");
                            errors.append(E).append("\n");
                        } catch (Exception E) {
                            errors = new StringBuffer();
                            throw new DatabaseException(E);
                        }
                    }
                }
            }

            if (referringDbTxns != null) {
                int nTxns = referringDbTxns.size();
                if (nTxns != 0) {
                    Iterator<Transaction> iter = referringDbTxns.iterator();
                    errors.append("There ");
                    if (nTxns == 1) {
                        errors.append("is 1 existing transaction opened");
                        errors.append(" against the Environment.\n");
                    } else {
                        errors.append("are ");
                        errors.append(nTxns);
                        errors.append(" existing transactions opened against");
                        errors.append(" the Environment.\n");
                    }
                    errors.append("Aborting open transactions ...\n");

                    while (iter.hasNext()) {
                        Transaction txn = iter.next();
                        try {
                            errors.append("aborting " + txn);
                            txn.abort();
                        } catch (RunRecoveryException e) {
                            throw e;
                        } catch (DatabaseException DBE) {
                            errors.append("\nWhile aborting transaction ");
                            errors.append(txn.getId());
                            errors.append(" encountered exception: ");
                            errors.append(DBE).append("\n");
                        }
                    }
                }
            }

            try {
                envImpl.close();
            } catch (RunRecoveryException e) {
                throw e;
            } catch (DatabaseException DBE) {
                errors.append
                    ("\nWhile closing Environment encountered exception: ");
                errors.append(DBE).append("\n");
            }
        } finally {
            envImpl = null;
            valid = false;
            if (errors.length() > 0) {
                throw new DatabaseException(errors.toString());
            }
        }
    }

    /**
     * Opens, and optionally creates, a <code>Database</code>.
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param databaseName The name of the database.
     *
     * @param dbConfig The database attributes.  If null, default attributes
     * are used.
     *
     * @return Database handle.
     *
     * @throws DatabaseNotFoundException if the database file does not exist.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public synchronized Database openDatabase(Transaction txn,
                                              String databaseName,
                                              DatabaseConfig dbConfig)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        /*
         * Currently all user-created databases are replicated in a
         * replicated environment.
         */
        try {
            if (dbConfig == null) {
                dbConfig = DatabaseConfig.DEFAULT;
            }

            Database db = new Database(this);
            setupDatabase(txn, db, databaseName, dbConfig,
                          false,                  // needWritableLockerForInit,
                          false,                   // allowReservedName,
                          envImpl.isReplicated()); // autoTxnIsReplicated
            return db;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        }
    }

    /**
     * Creates a database for internal JE use. Used in situations when the user
     * needs a Database handle; some internal uses go directly to the
     * DatabaseImpl. DbConfig should not be null.
     *  - permits use of reserved names.
     *  - the Locker used is non-transactional or an auto commit txn
     *  - the database is not replicated.
     */
    synchronized Database openLocalInternalDatabase(String databaseName,
                                                    DatabaseConfig dbConfig)
        throws DatabaseException {

        /* Should only be used for non-replicated cases. */
        assert !DbInternal.getDbConfigReplicated(dbConfig):
            databaseName + " shouldn't be replicated";

        try {
            Database db = new Database(this);
            setupDatabase(null, // txn
                          db, databaseName, dbConfig,
                          false,  // needWritableLockerForInit,
                          true,   // allowReservedName
                          false); // autoTxnIsReplicated
            return db;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        }
    }

    /**
     * Opens and optionally creates a <code>SecondaryDatabase</code>.
     *
     * <p>Note that the associations between primary and secondary databases
     * are not stored persistently.  Whenever a primary database is opened for
     * write access by the application, the appropriate associated secondary
     * databases should also be opened by the application.  This is necessary
     * to ensure data integrity when changes are made to the primary
     * database.</p>
     *
     * @param txn For a transactional database, an explicit transaction may be
     * specified, or null may be specified to use auto-commit.  For a
     * non-transactional database, null must be specified.
     *
     * @param databaseName The name of the database.
     *
     * @param primaryDatabase the primary database with which the secondary
     * database will be associated.  The primary database must not be
     * configured for duplicates.
     *
     * @param dbConfig The secondary database attributes.  If null, default
     * attributes are used.
     *
     * @return Database handle.
     *
     * @throws DatabaseNotFoundException if the database file does not exist.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public synchronized
        SecondaryDatabase openSecondaryDatabase(Transaction txn,
                                                String databaseName,
                                                Database primaryDatabase,
                                                SecondaryConfig dbConfig)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        try {
            if (dbConfig == null) {
                dbConfig = SecondaryConfig.DEFAULT;
            }
            SecondaryDatabase db =
                new SecondaryDatabase(this, dbConfig, primaryDatabase);

            /*
             * If we're populating the secondary, we should own with
             * a writable Locker.
             */
            boolean needWritableLockerForInit = dbConfig.getAllowPopulate();
            setupDatabase(txn, db, databaseName, dbConfig,
                          needWritableLockerForInit,
                          false,                   // allowReservedName
                          envImpl.isReplicated()); // autoTxnIsReplicated
            return db;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        }
    }

    /**
     * @param txn may be null
     * @param newDb is the Database handle which houses this database
     * @param allowReserveName true if this database may use one of the
     * names reserved for JE internal databases.
     * @param autoTxnIsReplicated true if this setupDatabase is going to set
     * up a replicated database.
     */
    private void setupDatabase(Transaction txn,
                               Database newDb,
                               String databaseName,
                               DatabaseConfig dbConfig,
                               boolean needWritableLockerForInit,
                               boolean allowReservedName,
                               boolean autoTxnIsReplicated)
        throws DatabaseException {

        checkEnv();
        DatabaseUtil.checkForNullParam(databaseName, "databaseName");

        Tracer.trace(Level.FINEST, envImpl, "Environment.open: " +
                     " name=" + databaseName +
                     " dbConfig=" + dbConfig);

        /*
         * Check that the open configuration is valid and doesn't conflict with
         * the envImpl configuration.
         */
        validateDbConfig(dbConfig, databaseName);
        validateDbConfigAgainstEnv(dbConfig, databaseName);

        /* Perform eviction before each operation that allocates memory. */
        envImpl.getEvictor().doCriticalEviction(false); // backgroundIO

        Locker locker = null;
        DatabaseImpl database = null;
        boolean operationOk = false;
        boolean dbIsClosing = false;
        try {

            /*
             * Does this database exist? Get a transaction to use. If the
             * database exists already, we really only need a readable locker.
             * If the database must be created, we need a writable one.
             * Unfortunately, we have to get the readable one first before we
             * know whether we have to create.  However, if we need to write
             * during initialization (to populate a secondary for example),
             * then just create a writable locker now.
             */
            boolean isWritableLocker;
            if (needWritableLockerForInit) {
                locker = LockerFactory.getWritableLocker
                    (this,
                     txn,
                     dbConfig.getTransactional(),
                     true,  // retainNonTxnLocks
                     autoTxnIsReplicated,
                     null);
                isWritableLocker = true;
            } else {
                locker = LockerFactory.getReadableLocker
                    (this, txn,
                     dbConfig.getTransactional(),
                     true,   // retainNonTxnLocks
                     false); // readCommittedIsolation
                isWritableLocker = !dbConfig.getTransactional() ||
                    locker.isTransactional();
            }

            database = envImpl.getDbTree().getDb(locker, databaseName, newDb);
            boolean databaseExists =
                (database != null) && !database.isDeleted();

            if (databaseExists) {
                if (dbConfig.getAllowCreate() &&
                    dbConfig.getExclusiveCreate()) {
                    /* We intended to create this, but it already exists. */
                    dbIsClosing = true;
                    throw new DatabaseException
                        ("Database " + databaseName + " already exists");
                }

                newDb.initExisting(this, locker, database, dbConfig);
            } else {
                /* Release deleted DB. [#13415] */
                envImpl.getDbTree().releaseDb(database);
                database = null;

                if (!allowReservedName &&
                    DbTree.isReservedDbName(databaseName)) {
                    throw new IllegalArgumentException
                        (databaseName + " is a reserved database name.");
                }

                /* No database. Create if we're allowed to. */
                if (dbConfig.getAllowCreate()) {

                    /*
                     * We're going to have to do some writing. Switch to a
                     * writable locker if we don't already have one.  Note
                     * that the existing locker does not hold the handle lock
                     * we need, since the database was not found; therefore it
                     * is OK to call operationEnd on the existing locker.
                     */
                    if (!isWritableLocker) {
                        locker.operationEnd(OperationStatus.SUCCESS);
                        locker = LockerFactory.getWritableLocker
                            (this,
                             txn,
                             dbConfig.getTransactional(),
                             true,  // retainNonTxnLocks
                             autoTxnIsReplicated,
                             null);
                        isWritableLocker  = true;
                    }

                    newDb.initNew(this, locker, databaseName, dbConfig);
                } else {
                    /* We aren't allowed to create this database. */
                    throw new DatabaseNotFoundException("Database " +
                                                        databaseName +
                                                        " not found.");
                }
            }

            operationOk = true;
            addReferringHandle(newDb);
        } finally {

            /*
             * Tell the transaction that this operation is over. Some types of
             * transactions (BasicLocker and auto Txn) will actually finish. The
             * transaction can decide if it is finishing and if it needs to
             * transfer the db handle lock it owns to someone else.
             */
            if (locker != null) {
                locker.setHandleLockOwner(operationOk, newDb, dbIsClosing);
                locker.operationEnd(operationOk);
            }

            /*
             * Normally releaseDb will be called when the DB is closed, or by
             * abort if a transaction is used, or by setHandleLockOwner if a
             * non-transactional locker is used.  But when the open operation
             * fails and the Database.databaseImpl field was not initialized,
             * we must call releaseDb here. [#13415]
             */
            if ((!operationOk || dbIsClosing) &&
                newDb.getDatabaseImpl() == null) {
                envImpl.getDbTree().releaseDb(database);
            }
        }
    }

    private void validateDbConfig(DatabaseConfig dbConfig, String databaseName)
        throws IllegalArgumentException {

        if ((dbConfig.getDeferredWrite() && dbConfig.getTemporary()) ||
            (dbConfig.getDeferredWrite() && dbConfig.getTransactional()) ||
            (dbConfig.getTemporary() && dbConfig.getTransactional())) {
            throw new IllegalArgumentException
                ("Attempted to open Database " + databaseName +
                 " and two ore more of the following exclusive properties" +
                 " are true: deferredWrite, temporary, transactional");
        }

        /*
         * R/W database handles on a replicated database must be transactional,
         * for now. In the future we may support non-transactional database
         * handles.
         */
        if (envImpl.isReplicated() &&
            dbConfig.getReplicated() &&
            !dbConfig.getReadOnly()) {
            if (!dbConfig.getTransactional()) {
                throw new IllegalArgumentException
                    ("Read/Write Database instances for replicated " +
                     "database " + databaseName + " must be transactional.");
            }
        }
    }

    private void validateDbConfigAgainstEnv(DatabaseConfig dbConfig,
                                            String databaseName)
        throws IllegalArgumentException {

        /* Check operation's transactional status against the Environment */
        if (dbConfig.getTransactional() &&
            !(envImpl.isTransactional())) {
            throw new IllegalArgumentException
                ("Attempted to open Database " + databaseName +
                 " transactionally, but parent Environment is" +
                 " not transactional");
        }

        /* Check read/write status */
        if (envImpl.isReadOnly() && (!dbConfig.getReadOnly())) {
            throw new IllegalArgumentException
                ("Attempted to open Database " + databaseName +
                 " as writable but parent Environment is read only ");
        }
    }

    /**
     * Removes a database.
     *
     * <p>Applications should never remove databases with open {@link
     * com.sleepycat.je.Database Database} handles.</p>
     *
     * @param txn For a transactional environment, an explicit transaction
     * may be specified or null may be specified to use auto-commit.  For a
     * non-transactional environment, null must be specified.
     *
     * @param databaseName The database to be removed.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseNotFoundException if the database file does not exist.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void removeDatabase(Transaction txn,
                               String databaseName)
        throws DatabaseException {

        checkHandleIsValid();

        removeDatabaseInternal(txn,
                               databaseName,
                               envImpl.isReplicated()); // autoTxnIsReplicated
    }

    void removeDatabaseInternal(Transaction txn,
                                String databaseName,
                                boolean autoTxnIsReplicated)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        DatabaseUtil.checkForNullParam(databaseName, "databaseName");

        Locker locker = null;
        boolean operationOk = false;
        try {

            /*
             * Note: use env level isTransactional as proxy for the db
             * isTransactional.
             */
            locker = LockerFactory.getWritableLocker
                (this,
                 txn,
                 envImpl.isTransactional(),
                 true,  // retainNonTxnLocks,
                 autoTxnIsReplicated,
                 null);
            envImpl.getDbTree().dbRemove(locker,
                                         databaseName,
                                         null /*checkId*/);
            operationOk = true;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        } finally {
            if (locker != null) {
                locker.operationEnd(operationOk);
            }
        }
    }

    /**
     * Renames a database.
     *
     * <p>Applications should never rename databases with open {@link
     * com.sleepycat.je.Database Database} handles.</p>
     *
     * @param txn For a transactional environment, an explicit transaction
     * may be specified or null may be specified to use auto-commit.  For a
     * non-transactional environment, null must be specified.
     *
     * @param databaseName The new name of the database.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseNotFoundException if the database file does not exist.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void renameDatabase(Transaction txn,
                               String databaseName,
                               String newName)
        throws DatabaseException {

        DatabaseUtil.checkForNullParam(databaseName, "databaseName");
        DatabaseUtil.checkForNullParam(newName, "newName");

        checkHandleIsValid();
        checkEnv();

        Locker locker = null;
        boolean operationOk = false;
        try {

            /*
             * Note: use env level isTransactional as proxy for the db
             * isTransactional.
             */
            locker = LockerFactory.getWritableLocker
                (this, txn,
                 envImpl.isTransactional(),
                 true /*retainNonTxnLocks*/,
                 envImpl.isReplicated(),  // autoTxnIsReplicated
                 null);
            envImpl.getDbTree().dbRename(locker, databaseName, newName);
            operationOk = true;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        } finally {
            if (locker != null) {
                locker.operationEnd(operationOk);
            }
        }
    }

    /**
     * Empties the database, discarding all records it contains.
     *
     * <p>When called on a database configured with secondary indices, the
     * application is responsible for also truncating all associated secondary
     * indices.</p>
     *
     * <p>Applications should never truncate databases with open {@link
     * com.sleepycat.je.Database Database} handles.</p>
     *
     * @param txn For a transactional environment, an explicit transaction may
     * be specified or null may be specified to use auto-commit.  For a
     * non-transactional environment, null must be specified.
     *
     * @param databaseName The database to be truncated.
     *
     * @param returnCount If true, count and return the number of records
     * discarded.
     *
     * @return The number of records discarded, or -1 if returnCount is false.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws DatabaseNotFoundException if the database file does not exist.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public long truncateDatabase(Transaction txn,
                                 String databaseName,
                                 boolean returnCount)
        throws DatabaseException {

        checkHandleIsValid();

        return truncateDatabaseInternal
            (txn, databaseName, returnCount,
             envImpl.isReplicated()); // autoTxnIsReplicated
    }

    long truncateDatabaseInternal(Transaction txn,
                                  String databaseName,
                                  boolean returnCount,
                                  boolean autoTxnIsReplicated)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        DatabaseUtil.checkForNullParam(databaseName, "databaseName");

        Locker locker = null;
        boolean operationOk = false;
        long count = 0;
        try {

            /*
             * Note: use env level isTransactional as proxy for the db
             * isTransactional.
             */
            locker = LockerFactory.getWritableLocker
                (this, txn,
                 envImpl.isTransactional(),
                 true /*retainNonTxnLocks*/,
                 autoTxnIsReplicated,
                 null);

            count = envImpl.getDbTree().truncate(locker,
                                                 databaseName,
                                                 returnCount);

            operationOk = true;
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        } finally {
            if (locker != null) {
                locker.operationEnd(operationOk);
            }
        }
        return count;
    }

    /**
     * For unit testing.  Returns the current memory usage in bytes for all
     * btrees in the envImpl.
     */
    long getMemoryUsage()
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();

        return envImpl.getMemoryBudget().getCacheMemoryUsage();
    }

    /**
     * Returns the database environment's home directory.
     *
     * @return The database environment's home directory.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public File getHome()
        throws DatabaseException {

        checkHandleIsValid();

        return envImpl.getEnvironmentHome();
    }

    /*
     * Transaction management
     */

    /**
     * Returns the default txn config for this environment handle.
     */
    TransactionConfig getDefaultTxnConfig() {
        return defaultTxnConfig;
    }

    /**
     * Copies the handle properties out of the config properties, and
     * initializes the default transaction config.
     */
    private void copyToHandleConfig(EnvironmentMutableConfig useConfig,
                                    EnvironmentConfig initStaticConfig)
        throws DatabaseException {

        /*
         * Create the new objects, initialize them, then change the instance
         * fields.  This avoids synchronization issues.
         */
        EnvironmentMutableConfig newHandleConfig =
            new EnvironmentMutableConfig();
        useConfig.copyHandlePropsTo(newHandleConfig);
        this.handleConfig = newHandleConfig;

        TransactionConfig newTxnConfig =
            TransactionConfig.DEFAULT.cloneConfig();
        newTxnConfig.setNoSync(handleConfig.getTxnNoSync());
        newTxnConfig.setWriteNoSync(handleConfig.getTxnWriteNoSync());
        newTxnConfig.setDurability(handleConfig.getDurability());
        newTxnConfig.setConsistencyPolicy
            (handleConfig.getConsistencyPolicy());
        if (initStaticConfig != null) {
            newTxnConfig.setSerializableIsolation
                (initStaticConfig.getTxnSerializableIsolation());
            newTxnConfig.setReadCommitted
                (initStaticConfig.getTxnReadCommitted());
        } else {
            newTxnConfig.setSerializableIsolation
                (defaultTxnConfig.getSerializableIsolation());
            newTxnConfig.setReadCommitted
                (defaultTxnConfig.getReadCommitted());
        }
        this.defaultTxnConfig = newTxnConfig;
    }

    /**
     * Creates a new transaction in the database environment.
     *
     * <p>Transaction handles are free-threaded; transactions handles may be
     * used concurrently by multiple threads.</p>
     *
     * <p>Cursors may not span transactions; that is, each cursor must be
     * opened and closed within a single transaction. The parent parameter is a
     * placeholder for nested transactions, and must currently be null.</p>
     *
     * @param txnConfig The transaction attributes.  If null, default
     * attributes are used.
     *
     * @return The newly created transaction's handle.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public Transaction beginTransaction(Transaction parent,
                                        TransactionConfig txnConfig)
        throws DatabaseException {

        try {
            return beginTransactionInternal(parent, txnConfig);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    private Transaction beginTransactionInternal(Transaction parent,
                                                 TransactionConfig txnConfig)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();

        if (!envImpl.isTransactional()) {
            throw new UnsupportedOperationException
                ("Transactions can not be used in a non-transactional " +
                 "environment");
        }

        checkTxnConfig(txnConfig);

        /*
         * Apply txn config defaults.  We don't need to clone unless we have to
         * apply the env default, since we don't hold onto a txn config
         * reference.
         */
        TransactionConfig useConfig = null;
        if (txnConfig == null) {
            useConfig = defaultTxnConfig;
        } else {
            if (defaultTxnConfig.getNoSync() ||
                defaultTxnConfig.getWriteNoSync()) {

                /*
                 * The environment sync settings have been set, check if any
                 * were set in the user's txn config. If none were set in the
                 * user's config, apply the environment defaults
                 */
                if (!txnConfig.getNoSync() &&
                    !txnConfig.getSync() &&
                    !txnConfig.getWriteNoSync()) {
                    useConfig = txnConfig.cloneConfig();
                    if (defaultTxnConfig.getWriteNoSync()) {
                        useConfig.setWriteNoSync(true);
                    } else {
                        useConfig.setNoSync(true);
                    }
                }
            }

            if ((defaultTxnConfig.getDurability() != null) &&
                 (txnConfig.getDurability() == null)) {
                /*
                 * Inherit transaction durability from the environment in the
                 * absence of an explicit transaction config durability.
                 */
                if (useConfig == null) {
                    useConfig = txnConfig.cloneConfig();
                }
                useConfig.setDurability(defaultTxnConfig.getDurability());
            }

            /* Apply isolation level default. */
            if (!txnConfig.getSerializableIsolation() &&
                !txnConfig.getReadCommitted() &&
                !txnConfig.getReadUncommitted()) {
                if (defaultTxnConfig.getSerializableIsolation()) {
                    if (useConfig == null) {
                        useConfig = txnConfig.cloneConfig();
                    }
                    useConfig.setSerializableIsolation(true);
                } else if (defaultTxnConfig.getReadCommitted()) {
                    if (useConfig == null) {
                        useConfig = txnConfig.cloneConfig();
                    }
                    useConfig.setReadCommitted(true);
                }
            }

            /* No environment level defaults applied. */
            if (useConfig == null) {
                useConfig = txnConfig;
            }
        }
        Txn internalTxn = envImpl.txnBegin(parent, useConfig);

        /*
         * Currently all user transactions in a replicated environment are
         * replicated.
         */
        internalTxn.setRepContext(envImpl.isReplicated() ?
                                  ReplicationContext.MASTER :
                                  ReplicationContext.NO_REPLICATE);

        Transaction txn = new Transaction(this, internalTxn);
        addReferringHandle(txn);
        return txn;
    }

    /**
     * Checks the txnConfig object to ensure that its correctly configured and
     * is compatible with the configuration of the Environment.
     *
     * @param txnConfig the configuration being checked.
     *
     * @throws IllegalArgumentException if any of the checks fail.
     */
    private void checkTxnConfig(TransactionConfig txnConfig)
        throws IllegalArgumentException {
        if (txnConfig == null) {
            return ;
        }
        if ((txnConfig.getSerializableIsolation() &&
             txnConfig.getReadUncommitted()) ||
            (txnConfig.getSerializableIsolation() &&
             txnConfig.getReadCommitted()) ||
            (txnConfig.getReadUncommitted() &&
             txnConfig.getReadCommitted())) {
            throw new IllegalArgumentException
                ("Only one may be specified: SerializableIsolation, " +
                "ReadCommitted or ReadUncommitted");
        }
        if ((txnConfig.getDurability() != null) &&
            ((defaultTxnConfig.getSync() ||
              defaultTxnConfig.getNoSync() ||
              defaultTxnConfig.getWriteNoSync()))) {
           throw new IllegalArgumentException
               ("Mixed use of deprecated durability API for the " +
                "Environment with the new durability API for " +
                "TransactionConfig.setDurability()");
        }
        if ((defaultTxnConfig.getDurability() != null) &&
            ((txnConfig.getSync() ||
              txnConfig.getNoSync() ||
              txnConfig.getWriteNoSync()))) {
            throw new IllegalArgumentException
                   ("Mixed use of new durability API for the " +
                    "Environment with the deprecated durability API for " +
                    "TransactionConfig.");
        }
    }

    /**
     * Synchronously checkpoint the database environment.
     *
     * <p>This is an optional action for the application since this activity
     * is, by default, handled by a database environment owned background
     * thread.</p>
     *
     * @param ckptConfig The checkpoint attributes.  If null, default
     * attributes are used.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void checkpoint(CheckpointConfig ckptConfig)
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            CheckpointConfig useConfig =
                (ckptConfig == null) ? CheckpointConfig.DEFAULT : ckptConfig;

            envImpl.invokeCheckpoint(useConfig,
                                     false, // flushAll
                                     "api");
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Synchronously flushes database environment databases to stable storage.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void sync()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            CheckpointConfig config = new CheckpointConfig();
            config.setForce(true);
            envImpl.invokeCheckpoint(config,
                                     true,  // flushAll
                                     "sync");
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Synchronously invokes database environment log cleaning.  This method is
     * called periodically by the cleaner daemon thread.
     *
     * <p>Zero or more log files will be cleaned as necessary to bring the disk
     * space utilization of the environment above the configured minimum
     * utilization threshold.  The threshold is determined by the
     * <code>je.cleaner.minUtilization</code> configuration setting.</p>
     *
     * <p>Note that <code>cleanLog</code> does not perform the complete task of
     * cleaning a log file.  Eviction and checkpointing migrate records that
     * are marked by the cleaner, and a full checkpoint is necessary following
     * cleaning before cleaned files will be deleted (or renamed).  Checkpoints
     * normally occur periodically and when the environment is closed.</p>
     *
     * <p>This is an optional action for the application since this activity
     * is, by default, handled by a database environment owned background
     * thread.</p>
     *
     * <p>There are two intended use cases for the <code>cleanLog</code>
     * method.  The first case is where the application wishes to disable the
     * built-in cleaner thread.  To replace the functionality of the cleaner
     * thread, the application should call <code>cleanLog</code>
     * periodically.</p>
     *
     * <p>In the second use case, "batch cleaning", the application disables
     * the cleaner thread for maximum performance during active periods, and
     * calls <code>cleanLog</code> during periods when the application is
     * quiescent or less active than usual.  If the cleaner has a large number
     * of files to clean, <code>cleanLog</code> may stop without reaching the
     * target utilization; to ensure that the target utilization is reached,
     * <code>cleanLog</code> should be called in a loop until it returns
     * zero. And to complete the work of cleaning, a checkpoint is necessary.
     * An example of performing batch cleaning follows.</p>
     *
     * <pre>
     *       Environment env;
     *       boolean anyCleaned = false;
     *       while (env.cleanLog() &gt; 0) {
     *           anyCleaned = true;
     *       }
     *       if (anyCleaned) {
     *           CheckpointConfig force = new CheckpointConfig();
     *           force.setForce(true);
     *           env.checkpoint(force);
     *       }
     * </pre>
     *
     * @return The number of log files that were cleaned, and that will be
     * deleted (or renamed) when a qualifying checkpoint occurs.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public int cleanLog()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            return envImpl.invokeCleaner();
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Synchronously invokes the mechanism for keeping memory usage within the
     * cache size boundaries.
     *
     * <p>This is an optional action for the application since this activity
     * is, by default, handled by a database environment owned background
     * thread.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void evictMemory()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            envImpl.invokeEvictor();
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Synchronously invokes the compressor mechanism which compacts in memory
     * data structures after delete operations.
     *
     * <p>This is an optional action for the application since this activity
     * is, by default, handled by a database environment owned background
     * thread.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void compress()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            envImpl.invokeCompressor();
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns this object's configuration.
     *
     * @return This object's configuration.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public EnvironmentConfig getConfig()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            EnvironmentConfig config = envImpl.cloneConfig();
            handleConfig.copyHandlePropsTo(config);
            config.fillInEnvironmentGeneratedProps(envImpl);
            return config;
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Sets database environment attributes.
     *
     * <p>Attributes only apply to a specific Environment object and are not
     * necessarily shared by other Environment objects accessing this
     * database environment.</p>
     *
     * @param mutableConfig The database environment attributes.  If null,
     * default attributes are used.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public synchronized void setMutableConfig(EnvironmentMutableConfig
                                              mutableConfig)
	throws DatabaseException {

        /*
         * This method is synchronized so that we atomically call both
         * EnvironmentImpl.setMutableConfig and copyToHandleConfig. This ensures
         * that the handle and the EnvironmentImpl properties match.
         */
        try {
            checkHandleIsValid();
            DatabaseUtil.checkForNullParam(mutableConfig, "mutableConfig");

            /*
             * Change the mutable properties specified in the given
             * configuratation.
             */
            envImpl.setMutableConfig(mutableConfig);

            /* Reset the handle config properties. */
            copyToHandleConfig(mutableConfig, null);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns database environment attributes.
     *
     * @return Environment attributes.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public EnvironmentMutableConfig getMutableConfig()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            EnvironmentMutableConfig config =
                envImpl.cloneMutableConfig();
            handleConfig.copyHandlePropsTo(config);
            config.fillInEnvironmentGeneratedProps(envImpl);
            return config;
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Not public yet, since there's nothing to upgrade.
     */
    void upgrade()
        throws DatabaseException {

        /* Do nothing.  Nothing to upgrade yet. */
    }

    /**
     * Returns the general database environment statistics.
     *
     * @param config The general statistics attributes.  If null, default
     * attributes are used.
     *
     * @return The general database environment statistics.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public EnvironmentStats getStats(StatsConfig config)
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        try {
            StatsConfig useConfig =
                (config == null) ? StatsConfig.DEFAULT : config;

            if (envImpl != null) {
                return envImpl.loadStats(useConfig);
            } else {
                return new EnvironmentStats();
            }
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns the database environment's locking statistics.
     *
     * @param config The locking statistics attributes.  If null, default
     * attributes are used.
     *
     * @return The database environment's locking statistics.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public LockStats getLockStats(StatsConfig config)
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            StatsConfig useConfig =
                (config == null) ? StatsConfig.DEFAULT : config;

            return envImpl.lockStat(useConfig);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns the database environment's transactional statistics.
     *
     * @param config The transactional statistics attributes.  If null,
     * default attributes are used.
     *
     * @return The database environment's transactional statistics.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public TransactionStats getTransactionStats(StatsConfig config)
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            StatsConfig useConfig =
                (config == null) ? StatsConfig.DEFAULT : config;
            return envImpl.txnStat(useConfig);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns a List of database names for the database environment.
     *
     * <p>Each element in the list is a String.</p>
     *
     * @return A List of database names for the database environment.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public List<String> getDatabaseNames()
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            return envImpl.getDbTree().getDbNames();
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Scans raw log entries in the JE log between two given points, passing
     * all records for a given set of databases to the scanRecord method of the
     * given LogScanner object.
     *
     * <p>EnvironmentStats.getEndOfLog should be used to get the end-of-log at
     * a particular point in time.  Values returned by that method can be
     * passed for the startPostion and endPosition parameters.</p>
     *
     * <p><em>WARNING:</em> This interface is meant for low level processing of
     * log records, not for application level queries. See LogScanner for
     * further restrictions!</p>
     *
     * @param startPosition the log position at which to start scanning. If no
     * such log position exists, the first existing position greater or less
     * (if forward is true or false) is used.
     *
     * @param endPosition the log position at which to stop scanning. If no
     * such log position exists, the first existing position less or greater
     * (if forward is true or false) is used.
     *
     * @param config the parameters for this scanLog invocation.
     *
     * @param scanner is an object of a class that implements the LogScanner
     * interface, to process scanned records.
     *
     * @return true if the scan was completed, or false if the scan was
     * canceled because LogScanner.scanRecord returned false.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public boolean scanLog(long startPosition,
                           long endPosition,
                           LogScanConfig config,
                           LogScanner scanner)
	throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();

            if (startPosition < 0 ||
                endPosition < 0) {
                throw new IllegalArgumentException
                    ("The start or end position argument is negative.");
            }

            if (config.getForwards()) {
                if (startPosition >= endPosition) {
                    throw new IllegalArgumentException
                        ("The startPosition (" + startPosition +
                        ") is not before the endPosition (" +
                        endPosition + ") on a forward scan.");
                }
            } else {
                if (startPosition < endPosition) {
                    throw new IllegalArgumentException
                        ("The startPosition (" +
                         startPosition +
                         ") is not after the endPosition (" +
                         endPosition + ") on a backward scan.");
                }
            }

            return envImpl.scanLog(startPosition, endPosition,
				   config, scanner);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns if the database environment is consistent and correct.
     *
     * <p>Verification is an expensive operation that should normally only be
     * used for troubleshooting and debugging.</p>
     *
     * @param config The verification attributes.  If null, default
     * attributes are used.
     *
     * @param out The stream to which verification debugging information is
     * written.
     *
     * @return true if the database environment is consistent and correct.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public boolean verify(VerifyConfig config, PrintStream out)
        throws DatabaseException {

        try {
            checkHandleIsValid();
            checkEnv();
            VerifyConfig useConfig =
                (config == null) ? VerifyConfig.DEFAULT : config;
            return envImpl.verify(useConfig, out);
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Returns the transaction associated with this thread if implied
     * transactions are being used.  Implied transactions are used in an XA or
     * JCA "Local Transaction" environment.  In an XA environment the
     * XAEnvironment.start() entrypoint causes a transaction to be created and
     * become associated with the calling thread.  Subsequent API calls
     * implicitly use that transaction.  XAEnvironment.end() causes the
     * transaction to be disassociated with the thread.  In a JCA Local
     * Transaction environment, the call to JEConnectionFactory.getConnection()
     * causes a new transaction to be created and associated with the calling
     * thread.
     */
    public Transaction getThreadTransaction()
        throws DatabaseException {

        checkHandleIsValid();
        checkEnv();
        try {
            return envImpl.getTxnManager().getTxnForThread();
        } catch (Error E) {
            if (envImpl != null) {
                envImpl.invalidate(E);
            }
            throw E;
        }
    }

    /**
     * Sets the transaction associated with this thread if implied transactions
     * are being used.  Implied transactions are used in an XA or JCA "Local
     * Transaction" environment.  In an XA environment the
     * XAEnvironment.start() entrypoint causes a transaction to be created and
     * become associated with the calling thread.  Subsequent API calls
     * implicitly use that transaction.  XAEnvironment.end() causes the
     * transaction to be disassociated with the thread.  In a JCA Local
     * Transaction environment, the call to JEConnectionFactory.getConnection()
     * causes a new transaction to be created and associated with the calling
     * thread.
     */
    public void setThreadTransaction(Transaction txn) {

        try {
            checkHandleIsValid();
            checkEnv();
        } catch (DatabaseException databaseException) {
            /* API compatibility hack. See SR 15861 for details. */
            throw new IllegalStateException(databaseException.getMessage());
        }
        try {
            envImpl.getTxnManager().setTxnForThread(txn);
        } catch (Error E) {
            envImpl.invalidate(E);
            throw E;
        }
    }

    /*
     * Non public api -- helpers
     */

    /*
     * Let the Environment remember what's opened against it.
     */
    void addReferringHandle(Database db) {
        referringDbs.add(db);
    }

    /**
     * Lets the Environment remember what's opened against it.
     */
    void addReferringHandle(Transaction txn) {
        referringDbTxns.add(txn);
    }

    /**
     * The referring db has been closed.
     */
    void removeReferringHandle(Database db) {
        referringDbs.remove(db);
    }

    /**
     * The referring Transaction has been closed.
     */
    void removeReferringHandle(Transaction txn) {
        referringDbTxns.remove(txn);
    }

    /**
     * For internal use only.
     * @hidden
     * @throws DatabaseException if the environment is not open.
     */
    public void checkHandleIsValid()
        throws DatabaseException {

        if (!valid) {
            throw new DatabaseException
                ("Attempt to use non-open Environment object().");
        }
    }

    /*
     * Debugging aids.
     */

    /**
     * Internal entrypoint.
     */
    EnvironmentImpl getEnvironmentImpl() {
        return envImpl;
    }

    /**
     * For internal use only.
     * @hidden
     * Throws if the envImpl is invalid.
     */
    protected void checkEnv()
        throws DatabaseException, RunRecoveryException {

        if (envImpl == null) {
            return;
        }
        envImpl.checkIfInvalid();
        envImpl.checkNotClosed();
    }
}

