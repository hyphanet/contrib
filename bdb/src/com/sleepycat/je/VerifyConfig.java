/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: VerifyConfig.java,v 1.11.2.1 2007/02/01 14:49:42 cwl Exp $
 */

package com.sleepycat.je;

import java.io.PrintStream;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class VerifyConfig {
    /*
     * For internal use, to allow null as a valid value for
     * the config parameter.
     */
    public static final VerifyConfig DEFAULT = new VerifyConfig();

    private boolean propagateExceptions = false;
    private boolean aggressive = false;
    private boolean printInfo = false;
    private PrintStream showProgressStream = null;
    private int showProgressInterval = 0;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public VerifyConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setPropagateExceptions(boolean propagate) {
        propagateExceptions = propagate;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getPropagateExceptions() {
        return propagateExceptions;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setAggressive(boolean aggressive) {
        this.aggressive = aggressive;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getAggressive() {
        return aggressive;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setPrintInfo(boolean printInfo) {
        this.printInfo = printInfo;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getPrintInfo() {
        return printInfo;
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

    /**
     * Returns the values for each configuration attribute.
     * @return the values for each configuration attribute.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("propagateExceptions=").append(propagateExceptions);
        return sb.toString();
    }
}
