/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: JoinCursor.java,v 1.20 2008/01/07 14:28:46 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;

import com.sleepycat.je.dbi.GetMode;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.utilint.DatabaseUtil;

/**
 * A specialized join cursor for use in performing equality or natural joins on
 * secondary indices.
 *
 * <p>A join cursor is returned when calling {@link Database#join
 * Database.join}.</p>
 *
 * <p>To open a join cursor using two secondary cursors:</p>
 *
 * <pre>
 *     Transaction txn = ...
 *     Database primaryDb = ...
 *     SecondaryDatabase secondaryDb1 = ...
 *     SecondaryDatabase secondaryDb2 = ...
 *     <p>
 *     SecondaryCursor cursor1 = null;
 *     SecondaryCursor cursor2 = null;
 *     JoinCursor joinCursor = null;
 *     try {
 *         DatabaseEntry key = new DatabaseEntry();
 *         DatabaseEntry data = new DatabaseEntry();
 *         <p>
 *         cursor1 = secondaryDb1.openSecondaryCursor(txn, null);
 *         cursor2 = secondaryDb2.openSecondaryCursor(txn, null);
 *         <p>
 *         key.setData(...); // initialize key for secondary index 1
 *         OperationStatus status1 =
 *         cursor1.getSearchKey(key, data, LockMode.DEFAULT);
 *         key.setData(...); // initialize key for secondary index 2
 *         OperationStatus status2 =
 *         cursor2.getSearchKey(key, data, LockMode.DEFAULT);
 *         <p>
 *         if (status1 == OperationStatus.SUCCESS &amp;&amp;
 *                 status2 == OperationStatus.SUCCESS) {
 *             <p>
 *             SecondaryCursor[] cursors = {cursor1, cursor2};
 *             joinCursor = primaryDb.join(cursors, null);
 *             <p>
 *             while (true) {
 *                 OperationStatus joinStatus = joinCursor.getNext(key, data,
 *                     LockMode.DEFAULT);
 *                 if (joinStatus == OperationStatus.SUCCESS) {
 *                      // Do something with the key and data.
 *                 } else {
 *                     break;
 *                 }
 *             }
 *         }
 *     } finally {
 *         if (cursor1 != null) {
 *             cursor1.close();
 *         }
 *         if (cursor2 != null) {
 *             cursor2.close();
 *         }
 *         if (joinCursor != null) {
 *             joinCursor.close();
 *         }
 *     }
 * </pre>
 */
public class JoinCursor {

    private JoinConfig config;
    private Database priDb;
    private Cursor priCursor;
    private Cursor[] secCursors;
    private DatabaseEntry[] cursorScratchEntries;
    private DatabaseEntry scratchEntry;

    /**
     * Creates a join cursor without parameter checking.
     */
    JoinCursor(Locker locker,
               Database primaryDb,
               final Cursor[] cursors,
               JoinConfig configParam)
        throws DatabaseException {

        priDb = primaryDb;
        config = (configParam != null) ? configParam.cloneConfig()
                                       : JoinConfig.DEFAULT;
        scratchEntry = new DatabaseEntry();
        cursorScratchEntries = new DatabaseEntry[cursors.length];
        Cursor[] sortedCursors = new Cursor[cursors.length];
        System.arraycopy(cursors, 0, sortedCursors, 0, cursors.length);

        if (!config.getNoSort()) {

            /*
             * Sort ascending by duplicate count.  Collect counts before
             * sorting so that countInternal() is called only once per cursor.
             * Use READ_UNCOMMITTED to avoid blocking writers.
             */
            final int[] counts = new int[cursors.length];
            for (int i = 0; i < cursors.length; i += 1) {
                counts[i] = cursors[i].countInternal
                    (LockMode.READ_UNCOMMITTED);
                assert counts[i] >= 0;
            }
            Arrays.sort(sortedCursors, new Comparator<Cursor>() {
                public int compare(Cursor o1, Cursor o2) {
                    int count1 = -1;
                    int count2 = -1;

                    /*
                     * Scan for objects in cursors not sortedCursors since
                     * sortedCursors is being sorted in place.
                     */
                    for (int i = 0; i < cursors.length &&
                                    (count1 < 0 || count2 < 0); i += 1) {
                        if (cursors[i] == o1) {
                            count1 = counts[i];
                        } else if (cursors[i] == o2) {
                            count2 = counts[i];
                        }
                    }
                    assert count1 >= 0 && count2 >= 0;
                    return (count1 - count2);
                }
            });
        }

        /*
         * Open and dup cursors last.  If an error occurs before the
         * constructor is complete, close them and ignore exceptions during
         * close.
         */
        try {
            priCursor = new Cursor(priDb, locker, null);
            secCursors = new Cursor[cursors.length];
            for (int i = 0; i < cursors.length; i += 1) {
                secCursors[i] = new Cursor(sortedCursors[i], true);
            }
        } catch (DatabaseException e) {
            close(e); /* will throw e */
        }
    }

    /**
     * Closes the cursors that have been opened by this join cursor.
     *
     * <p>The cursors passed to {@link Database#join Database.join} are not
     * closed by this method, and should be closed by the caller.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void close()
        throws DatabaseException {

        if (priCursor == null) {
            throw new DatabaseException("Already closed");
        }
        close(null);
    }

    /**
     * Close all cursors we own, throwing only the first exception that occurs.
     *
     * @param firstException an exception that has already occured, or null.
     */
    private void close(DatabaseException firstException)
        throws DatabaseException {

        if (priCursor != null) {
            try {
                priCursor.close();
            } catch (DatabaseException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
            priCursor = null;
        }
        for (int i = 0; i < secCursors.length; i += 1) {
            if (secCursors[i] != null) {
                try {
                    secCursors[i].close();
                } catch (DatabaseException e) {
                    if (firstException == null) {
                        firstException = e;
                    }
                }
                secCursors[i] = null;
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * For unit testing.
     */
    Cursor[] getSortedCursors() {
        return secCursors;
    }

    /**
     * Returns the primary database handle associated with this cursor.
     *
     * @return the primary database handle associated with this cursor.
     */
    public Database getDatabase() {

        return priDb;
    }

    /**
     * Returns this object's configuration.
     *
     * @return this object's configuration.
     */
    public JoinConfig getConfig() {

        return config.cloneConfig();
    }

    /**
     * Returns the next primary key resulting from the join operation.
     *
     * <p>An entry is returned by the join cursor for each primary key/data
     * pair having all secondary key values that were specified using the array
     * of secondary cursors passed to {@link Database#join Database.join}.</p>
     *
     * @param key the primary key returned as output.  Its byte array does not
     * need to be initialized by the caller.
     *
     * @return {@link com.sleepycat.je.OperationStatus#NOTFOUND
     * OperationStatus.NOTFOUND} if no matching key/data pair is found;
     * otherwise, {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS}.
     *
     * @param lockMode the locking attributes; if null, default attributes
     * are used.
     *
     * @throws NullPointerException if a DatabaseEntry parameter is null or
     * does not contain a required non-null byte array.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public OperationStatus getNext(DatabaseEntry key,
                                   LockMode lockMode)
        throws DatabaseException {

        priCursor.checkEnv();
        DatabaseUtil.checkForNullDbt(key, "key", false);
        priCursor.trace(Level.FINEST, "JoinCursor.getNext(key): ", lockMode);

        return retrieveNext(key, null, lockMode);
    }

    /**
     * Returns the next primary key and data resulting from the join operation.
     *
     * <p>An entry is returned by the join cursor for each primary key/data
     * pair having all secondary key values that were specified using the array
     * of secondary cursors passed to {@link Database#join Database.join}.</p>
     *
     * @param key the primary key returned as output.  Its byte array does not
     * need to be initialized by the caller.
     *
     * @param data the primary data returned as output.  Its byte array does
     * not need to be initialized by the caller.
     *
     * @return {@link com.sleepycat.je.OperationStatus#NOTFOUND
     * OperationStatus.NOTFOUND} if no matching key/data pair is found;
     * otherwise, {@link com.sleepycat.je.OperationStatus#SUCCESS
     * OperationStatus.SUCCESS}.
     *
     * @param lockMode the locking attributes; if null, default attributes
     * are used.
     *
     * @throws NullPointerException if a DatabaseEntry parameter is null or
     * does not contain a required non-null byte array.
     *
     * @throws DeadlockException if the operation was selected to resolve a
     * deadlock.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public OperationStatus getNext(DatabaseEntry key,
                                   DatabaseEntry data,
                                   LockMode lockMode)
        throws DatabaseException {

        priCursor.checkEnv();
        DatabaseUtil.checkForNullDbt(key, "key", false);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        priCursor.trace(Level.FINEST, "JoinCursor.getNext(key,data): ",
                        lockMode);

        return retrieveNext(key, data, lockMode);
    }

    /**
     * Internal version of getNext(), with an optional data param.
     * <p>
     * Since duplicates are always sorted and duplicate-duplicates are not
     * allowed, a natural join can be implemented by simply traversing through
     * the duplicates of the first cursor to find candidate keys, and then
     * looking for each candidate key in the duplicate set of the other
     * cursors, without ever reseting a cursor to the beginning of the
     * duplicate set.
     * <p>
     * This only works when the same duplicate comparison method is used for
     * all cursors.  We don't check for that, we just assume the user won't
     * violate that rule.
     * <p>
     * A future optimization would be to add a SearchMode.BOTH_DUPS operation
     * and use it instead of using SearchMode.BOTH.  This would be the
     * equivalent of the undocumented DB_GET_BOTHC operation used by DB core's
     * join() implementation.
     */
    private OperationStatus retrieveNext(DatabaseEntry keyParam,
                                         DatabaseEntry dataParam,
                                         LockMode lockMode)
        throws DatabaseException {

        outerLoop: while (true) {

            /* Process the first cursor to get a candidate key. */
            Cursor secCursor = secCursors[0];
            DatabaseEntry candidateKey = cursorScratchEntries[0];
            OperationStatus status;
            if (candidateKey == null) {
                /* Get first duplicate at initial cursor position. */
                candidateKey = new DatabaseEntry();
                cursorScratchEntries[0] = candidateKey;
                status = secCursor.getCurrentInternal(scratchEntry,
                                                      candidateKey,
                                                      lockMode);
            } else {
                /* Already initialized, move to the next candidate key. */
                status = secCursor.retrieveNext(scratchEntry, candidateKey,
                                                lockMode,
                                                GetMode.NEXT_DUP);
            }
            if (status != OperationStatus.SUCCESS) {
                /* No more candidate keys. */
                return status;
            }

            /* Process the second and following cursors. */
            for (int i = 1; i < secCursors.length; i += 1) {
                secCursor = secCursors[i];
                DatabaseEntry secKey = cursorScratchEntries[i];
                if (secKey == null) {
                    secKey = new DatabaseEntry();
                    cursorScratchEntries[i] = secKey;
                    status = secCursor.getCurrentInternal(secKey, scratchEntry,
                                                          lockMode);
                    assert status == OperationStatus.SUCCESS;
                }
                scratchEntry.setData(secKey.getData(), secKey.getOffset(),
                                     secKey.getSize());
                status = secCursor.search(scratchEntry, candidateKey, lockMode,
                                          SearchMode.BOTH);
                if (status != OperationStatus.SUCCESS) {
                    /* No match, get another candidate key. */
                    continue outerLoop;
                }
            }

            /* The candidate key was found for all cursors. */
            if (dataParam != null) {
                status = priCursor.search(candidateKey, dataParam,
                                          lockMode, SearchMode.SET);
                if (status != OperationStatus.SUCCESS) {
                    throw new DatabaseException("Secondary corrupt");
                }
            }
            keyParam.setData(candidateKey.getData(), candidateKey.getOffset(),
                             candidateKey.getSize());
            return OperationStatus.SUCCESS;
        }
    }
}
