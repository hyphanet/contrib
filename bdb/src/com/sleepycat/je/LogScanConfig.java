/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogScanConfig.java,v 1.7 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Specify the attributes of a log scan.
 */
public class LogScanConfig {

    private boolean forwards = true;

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public LogScanConfig() {
    }

    /**
     * Configure {@link Environment#scanLog} to scan forwards through the log.
     * <p>
     * @param forwards If true, configure {@link Environment#scanLog} to scan
     * forwards through the log.  The default is true.
     */
    public void setForwards(boolean forwards) {
        this.forwards = forwards;
    }

    /**
     * If true is returned, {@link Environment#scanLog} is configured to scan
     * forwards.
     */
    public boolean getForwards() {
        return forwards;
    }

    /**
     * Returns the values for each configuration attribute.
     *
     * @return the values for each configuration attribute.
     */
    @Override
    public String toString() {
        return "forwards=" + forwards +
            "\n";
    }
}
