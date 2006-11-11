/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: StatsConfig.java,v 1.11 2006/09/12 19:16:43 cwl Exp $
 */

package com.sleepycat.je;

import java.io.PrintStream;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class StatsConfig {
    /*
     * For internal use, to allow null as a valid value for
     * the config parameter.
     */
    static StatsConfig DEFAULT = new StatsConfig();

    private boolean fast = false;
    private boolean clear = false;
    private PrintStream showProgressStream = null;
    private int showProgressInterval = 0;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public StatsConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setFast(boolean fast) {
        this.fast = fast;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getFast() {
        return fast;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setClear(boolean clear) {
        this.clear = clear;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getClear() {
        return clear;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setShowProgressStream(PrintStream showProgressStream) {
        this.showProgressStream = showProgressStream;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public PrintStream getShowProgressStream() {
        return showProgressStream;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setShowProgressInterval(int showProgressInterval) {
        this.showProgressInterval = showProgressInterval;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getShowProgressInterval() {
        return showProgressInterval;
    }
}
