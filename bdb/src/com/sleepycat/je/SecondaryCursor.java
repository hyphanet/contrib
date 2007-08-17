/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SecondaryCursor.java,v 1.35.2.2 2007/06/13 21:22:17 mark Exp $
 */

package com.sleepycat.je;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import com.sleepycat.je.dbi.GetMode;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.utilint.DatabaseUtil;

/**
 * Javadoc for this public class is generated via
 * the doc templates in the doc_src directory.
 */
public class SecondaryCursor extends Cursor {

    private SecondaryDatabase secondaryDb;
    private Database primaryDb;

    /**
     * Cursor constructor. Not public. To get a cursor, the user should
     * call SecondaryDatabase.cursor();
     */
    SecondaryCursor(SecondaryDatabase dbHandle,
                    Transaction txn,
                    CursorConfig cursorConfig) 
        throws DatabaseException {

        super(dbHandle, txn, cursorConfig);
        secondaryDb = dbHandle;
        primaryDb = dbHandle.getPrimaryDatabase();
    }

    /**
     * Copy constructor.
     */
    private SecondaryCursor(SecondaryCursor cursor, boolean samePosition)
        throws DatabaseException {

        super(cursor, samePosition);
        secondaryDb = cursor.secondaryDb;
        primaryDb = cursor.primaryDb;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Database getPrimaryDatabase() {
	return primaryDb;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Cursor dup(boolean samePosition)
        throws DatabaseException {

        checkState(true);
        return new SecondaryCursor(this, samePosition);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public SecondaryCursor dupSecondary(boolean samePosition)
        throws DatabaseException {

        return (SecondaryCursor) dup(samePosition);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus delete()
        throws DatabaseException {

        checkState(true);
        checkUpdatesAllowed("delete");
        trace(Level.FINEST, "SecondaryCursor.delete: ", null);

        /* Read the primary key (the data of a secondary). */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry pKey = new DatabaseEntry();
        OperationStatus status = getCurrentInternal(key, pKey,
                                                    LockMode.RMW);

        /* Delete the primary and all secondaries (including this one). */
        if (status == OperationStatus.SUCCESS) {
            status =
		primaryDb.deleteInternal(cursorImpl.getLocker(), pKey, null);
            if (status != OperationStatus.SUCCESS) {
                SecondaryDatabase secDb = (SecondaryDatabase) getDatabase();
                throw secDb.secondaryCorruptException();
            }
        }
        return status;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus put(DatabaseEntry key, DatabaseEntry data) 
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus putNoOverwrite(DatabaseEntry key,
                                          DatabaseEntry data) 
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus putNoDupData(DatabaseEntry key, DatabaseEntry data) 
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus putCurrent(DatabaseEntry data)
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getCurrent(DatabaseEntry key,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        return getCurrent(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getCurrent(DatabaseEntry key,
                                      DatabaseEntry pKey,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        checkState(true);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getCurrent: ", lockMode);

        return getCurrentInternal(key, pKey, data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getFirst(DatabaseEntry key,
                                    DatabaseEntry data,
                                    LockMode lockMode) 
        throws DatabaseException {

        return getFirst(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getFirst(DatabaseEntry key,
                                    DatabaseEntry pKey,
                                    DatabaseEntry data,
                                    LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getFirst: ", lockMode);

        return position(key, pKey, data, lockMode, true);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getLast(DatabaseEntry key,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        return getLast(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getLast(DatabaseEntry key,
                                   DatabaseEntry pKey,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getLast: ", lockMode);

        return position(key, pKey, data, lockMode, false);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNext(DatabaseEntry key,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        return getNext(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNext(DatabaseEntry key,
                                   DatabaseEntry pKey,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getNext: ", lockMode);

        if (cursorImpl.isNotInitialized()) {
            return position(key, pKey, data, lockMode, true);
        } else {
            return retrieveNext(key, pKey, data, lockMode, GetMode.NEXT);
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNextDup(DatabaseEntry key,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        return getNextDup(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNextDup(DatabaseEntry key,
                                      DatabaseEntry pKey,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        checkState(true);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getNextDup: ", lockMode);

        return retrieveNext(key, pKey, data, lockMode, GetMode.NEXT_DUP);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNextNoDup(DatabaseEntry key,
                                        DatabaseEntry data,
                                        LockMode lockMode) 
        throws DatabaseException {

        return getNextNoDup(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getNextNoDup(DatabaseEntry key,
                                        DatabaseEntry pKey,
                                        DatabaseEntry data,
                                        LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getNextNoDup: ", null, null,
              lockMode);

        if (cursorImpl.isNotInitialized()) {
            return position(key, pKey, data, lockMode, true);
        } else {
            return retrieveNext(key, pKey, data, lockMode,
                                GetMode.NEXT_NODUP);
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrev(DatabaseEntry key,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        return getPrev(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrev(DatabaseEntry key,
                                   DatabaseEntry pKey,
                                   DatabaseEntry data,
                                   LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getPrev: ", lockMode);

        if (cursorImpl.isNotInitialized()) {
            return position(key, pKey, data, lockMode, false);
        } else {
            return retrieveNext(key, pKey, data, lockMode, GetMode.PREV);
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrevDup(DatabaseEntry key,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        return getPrevDup(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrevDup(DatabaseEntry key,
                                      DatabaseEntry pKey,
                                      DatabaseEntry data,
                                      LockMode lockMode) 
        throws DatabaseException {

        checkState(true);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getPrevDup: ", lockMode);

        return retrieveNext(key, pKey, data, lockMode, GetMode.PREV_DUP);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrevNoDup(DatabaseEntry key,
                                        DatabaseEntry data,
                                        LockMode lockMode) 
        throws DatabaseException {

        return getPrevNoDup(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getPrevNoDup(DatabaseEntry key,
                                        DatabaseEntry pKey,
                                        DatabaseEntry data,
                                        LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        checkArgsNoValRequired(key, pKey, data);
        trace(Level.FINEST, "SecondaryCursor.getPrevNoDup: ", lockMode);

        if (cursorImpl.isNotInitialized()) {
            return position(key, pKey, data, lockMode, false);
        } else {
            return retrieveNext(key, pKey, data, lockMode,
                                GetMode.PREV_NODUP);
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchKey(DatabaseEntry key,
                                        DatabaseEntry data,
                                        LockMode lockMode)
        throws DatabaseException {

        return getSearchKey(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchKey(DatabaseEntry key,
                                        DatabaseEntry pKey,
                                        DatabaseEntry data,
                                        LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(pKey, "pKey", false);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        trace(Level.FINEST, "SecondaryCursor.getSearchKey: ", key, null,
              lockMode);

        return search(key, pKey, data, lockMode, SearchMode.SET);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchKeyRange(DatabaseEntry key,
                                             DatabaseEntry data,
                                             LockMode lockMode) 
        throws DatabaseException {

        return getSearchKeyRange(key, new DatabaseEntry(), data, lockMode);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchKeyRange(DatabaseEntry key,
                                             DatabaseEntry pKey,
                                             DatabaseEntry data,
                                             LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(pKey, "pKey", false);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        trace(Level.FINEST, "SecondaryCursor.getSearchKeyRange: ", key, data,
              lockMode);

        return search(key, pKey, data, lockMode, SearchMode.SET_RANGE);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchBoth(DatabaseEntry key,
                                         DatabaseEntry data,
                                         LockMode lockMode) 
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchBoth(DatabaseEntry key,
                                         DatabaseEntry pKey,
                                         DatabaseEntry data,
                                         LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(pKey, "pKey", true);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        trace(Level.FINEST, "SecondaryCursor.getSearchBoth: ", key, data,
              lockMode);

        return search(key, pKey, data, lockMode, SearchMode.BOTH);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchBothRange(DatabaseEntry key,
                                              DatabaseEntry data,
                                              LockMode lockMode) 
        throws DatabaseException {

        throw SecondaryDatabase.notAllowedException();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public OperationStatus getSearchBothRange(DatabaseEntry key,
                                              DatabaseEntry pKey,
                                              DatabaseEntry data,
                                              LockMode lockMode) 
        throws DatabaseException {

        checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(pKey, "pKey", true);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        trace(Level.FINEST, "SecondaryCursor.getSearchBothRange: ", key, data,
              lockMode);

        return search(key, pKey, data, lockMode, SearchMode.BOTH_RANGE);
    }

    /**
     * Returns the current key and data.
     */
    private OperationStatus getCurrentInternal(DatabaseEntry key,
                                               DatabaseEntry pKey,
                                               DatabaseEntry data,
                                               LockMode lockMode)
        throws DatabaseException {

        OperationStatus status = getCurrentInternal(key, pKey, lockMode);
        if (status == OperationStatus.SUCCESS) {

            /*
             * May return KEYEMPTY if read-uncommitted and the primary was
             * deleted.
             */
            status = readPrimaryAfterGet(key, pKey, data, lockMode);
        }
        return status;
    }

    /**
     * Calls search() and retrieves primary data.
     */
    OperationStatus search(DatabaseEntry key,
                           DatabaseEntry pKey,
                           DatabaseEntry data,
                           LockMode lockMode,
                           SearchMode searchMode)
        throws DatabaseException {

        /*
         * Perform retries to account for deletions during a read-uncommitted.
         */
        while (true) {
            OperationStatus status = search(key, pKey, lockMode, searchMode);
            if (status != OperationStatus.SUCCESS) {
                return status;
            }
            status = readPrimaryAfterGet(key, pKey, data, lockMode);
            if (status == OperationStatus.SUCCESS) {
                return status;
            }
        }
    }

    /**
     * Calls position() and retrieves primary data.
     */
    OperationStatus position(DatabaseEntry key,
                             DatabaseEntry pKey,
                             DatabaseEntry data,
                             LockMode lockMode,
                             boolean first)
        throws DatabaseException {

        /*
         * Perform retries to account for deletions during a read-uncommitted.
         */
        while (true) {
            OperationStatus status = position(key, pKey, lockMode, first);
            if (status != OperationStatus.SUCCESS) {
                return status;
            }
            status = readPrimaryAfterGet(key, pKey, data, lockMode);
            if (status == OperationStatus.SUCCESS) {
                return status;
            }
        }
    }

    /**
     * Calls retrieveNext() and retrieves primary data.
     */
    OperationStatus retrieveNext(DatabaseEntry key,
                                 DatabaseEntry pKey,
                                 DatabaseEntry data,
                                 LockMode lockMode,
                                 GetMode getMode)
        throws DatabaseException {

        /*
         * Perform retries to account for deletions during a read-uncommitted.
         */
        while (true) {
            OperationStatus status = retrieveNext(key, pKey, lockMode,
                                                  getMode);
            if (status != OperationStatus.SUCCESS) {
                return status;
            }
            status = readPrimaryAfterGet(key, pKey, data, lockMode);
            if (status == OperationStatus.SUCCESS) {
                return status;
            }
        }
    }

    /**
     * Reads the primary data for a primary key that was read via a secondary.
     * When SUCCESS is returned by this method, the caller should return
     * SUCCESS.  When KEYEMPTY is returned, the caller should treat this as a
     * deleted record and either retry the operation (in the case of position,
     * search, and retrieveNext) or return KEYEMPTY (in the case of
     * getCurrent).  KEYEMPTY is only returned when read-uncommitted is used.
     *
     * @return SUCCESS if the primary was read succesfully, or KEYEMPTY if
     * using read-uncommitted and the primary has been deleted, or KEYEMPTY if
     * using read-uncommitted and the primary has been updated and no longer
     * contains the secondary key.
     *
     * @throws DatabaseException to indicate a corrupt secondary reference if
     * the primary record is not found and read-uncommitted is not used (or
     * read-uncommitted is used, but we cannot verify that a valid deletion has
     * occured).
     */
    private OperationStatus readPrimaryAfterGet(DatabaseEntry key,
                                                DatabaseEntry pKey,
                                                DatabaseEntry data,
                                                LockMode lockMode)
        throws DatabaseException {

        /*
         * There is no need to read the primary if no data and no locking
         * (read-uncommitted) are requested by the caller.  However, if partial
         * data is requested along with read-uncommitted, then we must read all
         * data in order to call the key creator below. [#14966]
         */
        DatabaseEntry copyToPartialEntry = null;
        boolean readUncommitted = isReadUncommittedMode(lockMode);
        if (readUncommitted && data.getPartial()) {
            if (data.getPartialLength() == 0) {
                /* No need to read the primary. */
                data.setData(LogUtils.ZERO_LENGTH_BYTE_ARRAY);
                return OperationStatus.SUCCESS;
            } else {
                /* Read all data and then copy the requested partial data. */
                copyToPartialEntry = data;
                data = new DatabaseEntry();
            }
        }

        Locker locker = cursorImpl.getLocker();
        Cursor cursor = null;
        try {

            /*
             * Use Cursor constructor with DatabaseImpl parameter so that the
             * non-transactional locker is used in the primary cursor. [#15573]
             */
	    cursor = new Cursor(primaryDb.getDatabaseImpl(), locker, null);
            OperationStatus status =
                cursor.search(pKey, data, lockMode, SearchMode.SET);
            if (status != OperationStatus.SUCCESS) {

                /*
                 * If using read-uncommitted and the primary is not found,
                 * check to see if the secondary key also has been deleted.  If
                 * so, the primary was deleted in between reading the secondary
                 * and the primary.  It is not corrupt, so we return KEYEMPTY.
                 */
                if (readUncommitted) {
                    status = getCurrentInternal(key, pKey, lockMode);
                    if (status == OperationStatus.KEYEMPTY) {
                        return status;
                    }
                }

                /* Secondary reference is corrupt. */
                SecondaryDatabase secDb = (SecondaryDatabase) getDatabase();
                throw secDb.secondaryCorruptException();
            }

            /*
             * If using read-uncommitted and the primary was found, check to
             * see if primary was updated so that it no longer contains the
             * secondary key.  If it has been, return KEYEMPTY.
             */
            if (readUncommitted) {
                SecondaryConfig config =
                    secondaryDb.getPrivateSecondaryConfig();

                /*
                 * If the secondary key is immutable, or the key creators are
                 * null (the database is read only), then we can skip this
                 * check.
                 */
                if (config.getImmutableSecondaryKey()) {
                    /* Do nothing. */
                } else if (config.getKeyCreator() != null) {

                    /*
                     * Check that the key we're using is equal to the key
                     * returned by the key creator.
                     */
                    DatabaseEntry secKey = new DatabaseEntry();
                    if (!config.getKeyCreator().createSecondaryKey
                            (secondaryDb, pKey, data, secKey) ||
                        !secKey.equals(key)) {
                        return OperationStatus.KEYEMPTY;
                    }
                } else if (config.getMultiKeyCreator() != null) {

                    /*
                     * Check that the key we're using is in the set returned by
                     * the key creator.
                     */
                    Set results = new HashSet();
                    config.getMultiKeyCreator().createSecondaryKeys
                        (secondaryDb, pKey, data, results);
                    if (!results.contains(key)) {
                        return OperationStatus.KEYEMPTY;
                    }
                }
            }
            
            /*
             * When a partial entry was requested but we read all the data,
             * copy the requested partial data to the caller's entry. [#14966]
             */
            if (copyToPartialEntry != null) {
                CursorImpl.setDbt(copyToPartialEntry, data.getData());
            }
            return OperationStatus.SUCCESS;
        } finally {

            /*
             * Do not release non-transactional locks when closing the primary
             * cursor.  They are held until all locks for this operation are
             * released by the secondary cursor.  [#15573]
             */
            if (cursor != null) {
                cursor.close(false /*releaseNonTxnLocks*/);
            }
        }
    }

    /**
     * Note that this flavor of checkArgs doesn't require that the
     * dbt data is set.
     */
    private void checkArgsNoValRequired(DatabaseEntry key,
                                        DatabaseEntry pKey,
                                        DatabaseEntry data) {
        DatabaseUtil.checkForNullDbt(key, "key", false);
        DatabaseUtil.checkForNullDbt(pKey, "pKey", false);
        DatabaseUtil.checkForNullDbt(data, "data", false);
    }
}
