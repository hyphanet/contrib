/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DbChecksumException.java,v 1.23 2008/05/13 01:44:52 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Invalid serialized items seen.
 */
public class DbChecksumException extends RunRecoveryException {

    private String extraInfo;

    public DbChecksumException(EnvironmentImpl env, String message) {
	super(env, message);
    }

    public DbChecksumException(EnvironmentImpl env,
                               String message,
                               Throwable t) {
	super(env, message, t);
    }

    /**
     * Support the addition of extra error information. Use this approach
     * rather than wrapping exceptions because RunRecoveryException hierarchy
     * does some intricate things with setting the environment as invalid.
     */
    public void addErrorMessage(String newExtraInfo) {

        if (extraInfo == null) {
            extraInfo = newExtraInfo;
        } else {
            extraInfo = extraInfo + newExtraInfo;
        }
    }

    public String toString() {
        if (extraInfo == null) {
            return super.toString();
        } else {
            return super.toString() + extraInfo;
        }
    }
}

