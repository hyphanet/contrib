/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2007 Oracle.  All rights reserved.
 *
 * $Id: Sequence.java,v 1.8.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;

/**
 * Javadoc for this public class is generated via
 * the doc templates in the doc_src directory.
 */
public class Sequence {

    private static final byte FLAG_INCR = ((byte) 0x1);
    private static final byte FLAG_WRAP = ((byte) 0x2);
    private static final byte FLAG_OVER = ((byte) 0x4);

    /* Allocation size for the record data. */
    private static final int MAX_DATA_SIZE = 50;

    /* Version of the format for fields stored in the sequence record. */
    private static final byte CURRENT_VERSION = 0;

    /* A sequence is a unique record in a database. */
    private Database db;
    private DatabaseEntry key;

    /* Persistent fields. */
    private boolean wrapAllowed;
    private boolean increment;
    private boolean overflow;
    private long rangeMin;
    private long rangeMax;
    private long storedValue;

    /* Handle-specific fields. */
    private int cacheSize;
    private long cacheValue;
    private long cacheLast;
    private int nGets;
    private int nCachedGets;
    private TransactionConfig autoCommitConfig;
    private Logger logger;

    /*
     * The cache holds the range of values [cacheValue, cacheLast], which is
     * the same as [cacheValue, storedValue) at the time the record is written.
     * At store time, cacheLast is set to one before (after) storedValue.
     *
     * storedValue may be used by other Sequence handles with separate caches.
     * storedValue is always the next value to be returned by any handle that
     * runs out of cached values.
     */

    /**
     * Opens a sequence handle, adding the sequence record if appropriate.
     */
    Sequence(Database db,
             Transaction txn,
             DatabaseEntry key,
             SequenceConfig config)
        throws DatabaseException {

        if (db.getDatabaseImpl().getSortedDuplicates()) {
            throw new IllegalArgumentException
                ("Sequences not supported in databases configured for " +
                 "duplicates");
        }

        SequenceConfig useConfig = (config != null) ?
            config : SequenceConfig.DEFAULT;

        if (useConfig.getRangeMin() >= useConfig.getRangeMax()) {
            throw new IllegalArgumentException
                ("Minimum sequence value must be less than the maximum");
        }

        if (useConfig.getInitialValue() > useConfig.getRangeMax() ||
            useConfig.getInitialValue() < useConfig.getRangeMin()) {
            throw new IllegalArgumentException
                ("Initial sequence value is out of range");
        }

        if (useConfig.getRangeMin() >
            useConfig.getRangeMax() - useConfig.getCacheSize()) {
            throw new IllegalArgumentException
                ("The cache size is larger than the sequence range");
        }

        if (useConfig.getAutoCommitNoSync()) {
            autoCommitConfig = new TransactionConfig();
            autoCommitConfig.setNoSync(true);
        } else {
            /* Use the environment's default transaction config. */
            autoCommitConfig = null;
        }

        this.db = db;
        this.key = copyEntry(key);
        logger = db.getEnvironment().getEnvironmentImpl().getLogger();

        /* Perform an auto-commit transaction to create the sequence. */
        Locker locker = null;
        Cursor cursor = null;
        OperationStatus status = OperationStatus.NOTFOUND;
        try {
            locker = LockerFactory.getWritableLocker
                (db.getEnvironment(), txn, db.isTransactional(),
                 false, autoCommitConfig);

            cursor = new Cursor(db, locker, null);

            if (useConfig.getAllowCreate()) {

                /* Get the persistent fields from the config. */
                rangeMin = useConfig.getRangeMin();
                rangeMax = useConfig.getRangeMax();
                increment = !useConfig.getDecrement();
                wrapAllowed = useConfig.getWrap();
                storedValue = useConfig.getInitialValue();

                /*
                 * To avoid dependence on SerializableIsolation, try
                 * putNoOverwrite first.  If it fails, then try to get an
                 * existing record.
                 */
                status = cursor.putNoOverwrite(key, makeData());

                if (status == OperationStatus.KEYEXIST) {
                    if (useConfig.getExclusiveCreate()) {
                        throw new DatabaseException
                            ("ExclusiveCreate=true and the sequence record " +
                             "already exists.");
                    }
                    if (!readData(cursor, null)) {
                        throw new DatabaseException
                            ("Sequence record removed during openSequence.");
                    }
                    status = OperationStatus.SUCCESS;
                }
            } else {

                /* Get an existing record. */
                if (!readData(cursor, null)) {
                    throw new DatabaseException
                        ("AllowCreate=false and the sequence record " +
                         "does not exist.");
                }
                status = OperationStatus.SUCCESS;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (locker != null) {
                locker.operationEnd(status);
            }
        }

        /*
         * cacheLast is initialized such that the cache will be considered
         * empty the first time get() is called.
         */
        cacheSize = useConfig.getCacheSize();
        cacheValue = storedValue;
        cacheLast = increment ? (storedValue - 1) : (storedValue + 1);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void close()
        throws DatabaseException {

        /* Defined only for DB compatibility and possible future use. */
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     *
     * <p>This method is synchronized to protect updating of the cached value,
     * since multiple threads may share a single handle.  Multiple handles
     * for the same database/key may be used to increase concurrency.</p>
     */
    public synchronized long get(Transaction txn, int delta)
        throws DatabaseException {

        /* Check parameters, being careful of overflow. */
        if (delta <= 0) {
            throw new IllegalArgumentException
                ("Sequence delta must be greater than zero");
        }
        if (rangeMin > rangeMax - delta) {
            throw new IllegalArgumentException
                ("Sequence delta is larger than the range");
        }

        /* Status variables for tracing. */
        boolean cached = true;
        boolean wrapped = false;

        /*
         * Determine whether we have exceeded the cache.  The cache size is
         * always <= Integer.MAX_VALUE, so we don't have to worry about
         * overflow here as long as we subtract the two long values first.
         */
        if ((increment && delta > ((cacheLast - cacheValue) + 1)) ||
            (!increment && delta > ((cacheValue - cacheLast) + 1))) {

            cached = false;

            /*
             * We need to allocate delta or cacheSize values, whichever is
             * larger, by incrementing or decrementing the stored value by
             * adjust.
             */
            int adjust = (delta > cacheSize) ? delta : cacheSize;

            /* Perform an auto-commit transaction to update the sequence. */
            Locker locker = null;
            Cursor cursor = null;
            OperationStatus status = OperationStatus.NOTFOUND;
            try {
                locker = LockerFactory.getWritableLocker
                    (db.getEnvironment(), txn, db.isTransactional(),
                     false, autoCommitConfig);

                cursor = new Cursor(db, locker, null);

                /* Get the existing record. */
                readDataRequired(cursor, LockMode.RMW);

                /* If we would have wrapped when not allowed, overflow. */
                if (overflow) {
                    throw new DatabaseException
                        ("Sequence overflow " + storedValue);
                }

                /*
                 * Handle wrapping.  The range size can be larger than a long
                 * can hold, so to avoid arithmetic overflow we use BigInteger
                 * arithmetic.  Since we are going to write, the BigInteger
                 * overhead is acceptable.
                 */
                BigInteger availBig;
                if (increment) {
                    /* Available amount: rangeMax - storedValue */
                    availBig = BigInteger.valueOf(rangeMax).
                        subtract(BigInteger.valueOf(storedValue));
                } else {
                    /* Available amount: storedValue - rangeMin */
                    availBig = BigInteger.valueOf(storedValue).
                        subtract(BigInteger.valueOf(rangeMin));
                }

                if (availBig.compareTo(BigInteger.valueOf(adjust)) < 0) {
                    /* If availBig < adjust then availBig fits in an int. */
                    int availInt = (int) availBig.longValue();
                    if (availInt < delta) {
                        if (wrapAllowed) {
                            /* Wrap to the opposite range end point. */
                            storedValue = increment ? rangeMin : rangeMax;
                            wrapped = true;
                        } else {
                            /* Signal an overflow next time. */
                            overflow = true;
                            adjust = 0;
                        }
                    } else {

                        /*
                         * If the delta fits in the cache available, don't wrap
                         * just to allocate the full cacheSize; instead,
                         * allocate as much as is available.
                         */
                        adjust = availInt;
                    }
                }

                /* Negate the adjustment for decrementing. */
                if (!increment) {
                    adjust = -adjust;
                }

                /* Set the stored value one past the cached amount. */
                storedValue += adjust;

                /* Write the new stored value. */
                cursor.put(key, makeData());
                status = OperationStatus.SUCCESS;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (locker != null) {
                    locker.operationEnd(status);
                }
            }

            /* The cache now contains the range: [cacheValue, storedValue) */
            cacheValue = storedValue - adjust;
            cacheLast = storedValue + (increment ? (-1) : 1);
        }

        /* Return the current value and increment/decrement it by delta. */
        long retVal = cacheValue;
        if (increment) {
            cacheValue += delta;
        } else {
            cacheValue -= delta;
        }

        /* Increment stats. */
        nGets += 1;
        if (cached) {
            nCachedGets += 1;
        }

        /* Trace this method at the FINEST level. */
        if (logger.isLoggable(Level.FINEST)) {
            logger.log
                (Level.FINEST,
                 "Sequence.get" +
                 " value=" + retVal +
                 " cached=" + cached +
                 " wrapped=" + wrapped);
        }

        return retVal;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Database getDatabase()
        throws DatabaseException {

        return db;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public DatabaseEntry getKey()
        throws DatabaseException {

        return copyEntry(key);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public SequenceStats getStats(StatsConfig config)
        throws DatabaseException {

        if (config == null) {
            config = StatsConfig.DEFAULT;
        }

        if (!config.getFast()) {

            /*
             * storedValue may have been updated by another handle since it
             * was last read by this handle.  Fetch the last written value.
             * READ_UNCOMMITTED must be used to avoid lock conflicts.
             */
            Cursor cursor = db.openCursor(null, null);
            try {
                readDataRequired(cursor, LockMode.READ_UNCOMMITTED);
            } finally {
                cursor.close();
            }
        }

        SequenceStats stats = new SequenceStats
            (nGets,
             nCachedGets,
             storedValue,
             cacheValue,
             cacheLast,
             rangeMin,
             rangeMax,
             cacheSize);

        if (config.getClear()) {
            nGets = 0;
            nCachedGets = 0;
        }

        return stats;
    }

    /**
     * Reads persistent fields from the sequence record.
     * Throws an exception if the key is not present in the database.
     */
    private void readDataRequired(Cursor cursor, LockMode lockMode)
        throws DatabaseException {

        if (!readData(cursor, lockMode)) {
            throw new DatabaseException
                ("The sequence record has been deleted while it is open.");
        }
    }

    /**
     * Reads persistent fields from the sequence record.
     * Returns false if the key is not present in the database.
     */
    private boolean readData(Cursor cursor, LockMode lockMode)
        throws DatabaseException {

        /* Fetch the sequence record. */
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = cursor.getSearchKey(key, data, lockMode);
        if (status != OperationStatus.SUCCESS) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data.getData());

        /* Get the persistent fields from the record data. */
        byte ignoreVersionForNow = buf.get();
        byte flags = buf.get();
        rangeMin = LogUtils.readLong(buf);
        rangeMax = LogUtils.readLong(buf);
        storedValue = LogUtils.readLong(buf);

        increment = (flags & FLAG_INCR) != 0;
        wrapAllowed = (flags & FLAG_WRAP) != 0;
        overflow = (flags & FLAG_OVER) != 0;

        return true;
    }

    /**
     * Makes a storable database entry from the persistent fields.
     */
    private DatabaseEntry makeData() {

        byte[] data = new byte[MAX_DATA_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(data);

        byte flags = 0;
        if (increment) {
            flags |= FLAG_INCR;
        }
        if (wrapAllowed) {
            flags |= FLAG_WRAP;
        }
        if (overflow) {
            flags |= FLAG_OVER;
        }

        buf.put(CURRENT_VERSION);
        buf.put(flags);
        LogUtils.writeLong(buf, rangeMin);
        LogUtils.writeLong(buf, rangeMax);
        LogUtils.writeLong(buf, storedValue);

        return new DatabaseEntry(data, 0, buf.position());
    }

    /**
     * Returns a deep copy of the given database entry.
     */
    private DatabaseEntry copyEntry(DatabaseEntry entry) {

	int len = entry.getSize();
        byte[] data;
	if (len == 0) {
	    data = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
	} else {
	    data = new byte[len];
	    System.arraycopy
		(entry.getData(), entry.getOffset(), data, 0, data.length);
	}

        return new DatabaseEntry(data);
    }
}
