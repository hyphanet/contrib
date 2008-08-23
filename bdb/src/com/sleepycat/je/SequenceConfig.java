/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2008 Oracle.  All rights reserved.
 *
 * $Id: SequenceConfig.java,v 1.11 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Specifies the attributes of a sequence.
 */
public class SequenceConfig {

    /**
     * Default configuration used if null is passed to methods that create a
     * cursor.
     */
    public static final SequenceConfig DEFAULT = new SequenceConfig();

    /* Parameters */
    private int cacheSize = 0;
    private long rangeMin = Long.MIN_VALUE;
    private long rangeMax = Long.MAX_VALUE;
    private long initialValue = 0L;

    /* Flags */
    private boolean allowCreate;
    private boolean decrement;
    private boolean exclusiveCreate;
    private boolean autoCommitNoSync;
    private boolean wrap;

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public SequenceConfig() {
    }

    /**
     * Configures the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method to create the sequence if it does not
     * already exist.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param allowCreate If true, configure the {@link
     * com.sleepycat.je.Database#openSequence Database.openSequence} method to
     * create the sequence if it does not already exist.
     */
    public void setAllowCreate(boolean allowCreate) {
        this.allowCreate = allowCreate;
    }

    /**
     * Returns true if the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method is configured to create the sequence if it
     * does not already exist.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method is configured to create the sequence if it
     * does not already exist.
     */
    public boolean getAllowCreate() {
        return allowCreate;
    }

    /**
     * Set the Configure the number of elements cached by a sequence handle.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param cacheSize The Configure the number of elements cached by a
     * sequence handle.
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    /**
     * Returns the number of elements cached by a sequence handle..
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The number of elements cached by a sequence handle..
     */
    public int getCacheSize() {
        return cacheSize;
    }

    /**
     * Specifies that the sequence should be decremented.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param decrement If true, specify that the sequence should be
     * decremented.
     */
    public void setDecrement(boolean decrement) {
        this.decrement = decrement;
    }

    /**
     * Returns true if the sequence is configured to decrement.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the sequence is configured to decrement.
     */
    public boolean getDecrement() {
         return decrement;
    }

    /**
     * Configures the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method to fail if the database already exists.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param exclusiveCreate If true, configure the {@link
     * com.sleepycat.je.Database#openSequence Database.openSequence} method to
     * fail if the database already exists.
     */
    public void setExclusiveCreate(boolean exclusiveCreate) {
        this.exclusiveCreate = exclusiveCreate;
    }

    /**
     * Returns true if the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method is configured to fail if the database
     * already exists.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the {@link com.sleepycat.je.Database#openSequence
     * Database.openSequence} method is configured to fail if the database
     * already exists.
     */
    public boolean getExclusiveCreate() {
        return exclusiveCreate;
    }

    /**
     * Sets the initial value for a sequence.
     *
     * <p>This call is only effective when the sequence is being created.</p>
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param initialValue The Set the initial value for a sequence.
     */
    public void setInitialValue(long initialValue) {
        this.initialValue = initialValue;
    }

    /**
     * Returns the initial value for a sequence..
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The initial value for a sequence..
     */
    public long getInitialValue() {
        return initialValue;
    }

    /**
     * Configures auto-commit operations on the sequence to not flush the
     * transaction log.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param autoCommitNoSync If true, configure auto-commit operations on
     * the sequence to not flush the transaction log.
     */
    public void setAutoCommitNoSync(boolean autoCommitNoSync) {
        this.autoCommitNoSync = autoCommitNoSync;
    }

    /**
     * Returns true if the auto-commit operations on the sequence are configure
     * to not flush the transaction log..
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the auto-commit operations on the sequence are configure
     * to not flush the transaction log..
     */
    public boolean getAutoCommitNoSync() {
        return autoCommitNoSync;
    }

    /**
     * Configures a sequence range.  This call is only effective when the
     * sequence is being created.
     *
     * @param min The minimum value for the sequence.
     *
     * @param max The maximum value for the sequence.
     */
    public void setRange(long min, long max) {
        this.rangeMin = min;
        this.rangeMax = max;
    }

    /**
     * Returns the minimum value for the sequence.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The minimum value for the sequence.
     */
    public long getRangeMin() {
        return rangeMin;
    }

    /**
     * Returns the maximum value for the sequence.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The maximum value for the sequence.
     */
    public long getRangeMax() {
        return rangeMax;
    }

    /**
     * Specifies that the sequence should wrap around when it is incremented
     * (decremented) past the specified maximum (minimum) value.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @param wrap If true, specify that the sequence should wrap around when
     * it is incremented (decremented) past the specified maximum (minimum)
     * value.
     */
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    /**
     * Returns true if the sequence will wrap around when it is incremented
     * (decremented) past the specified maximum (minimum) value.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the sequence will wrap around when it is incremented
     * (decremented) past the specified maximum (minimum) value.
     */
    public boolean getWrap() {
        return wrap;
    }

    /**
     * Returns the values for each configuration attribute.
     *
     * @return the values for each configuration attribute.
     */
    @Override
    public String toString() {
        return "allowCreate=" + allowCreate +
            "\ncacheSize=" + cacheSize +
            "\ndecrement=" + decrement +
            "\nexclusiveCreate=" + exclusiveCreate +
            "\ninitialValue=" + initialValue +
            "\nautoCommitNoSync=" + autoCommitNoSync +
            "\nrangeMin=" + rangeMin +
            "\nrangeMax=" + rangeMax +
            "\nwrap=" + wrap +
            "\n";
    }
}
