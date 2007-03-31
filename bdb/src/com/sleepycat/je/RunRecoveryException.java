/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RunRecoveryException.java,v 1.18.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class RunRecoveryException extends DatabaseException {

    private boolean alreadyThrown = false;

    RunRecoveryException() {
	super();
    }

    public RunRecoveryException(EnvironmentImpl env) {
        super();
        invalidate(env);
    }

    public RunRecoveryException(EnvironmentImpl env, Throwable t) {
        super(t);
        invalidate(env);
    }

    public RunRecoveryException(EnvironmentImpl env, String message) {
        super(message);
        invalidate(env);
    }

    public RunRecoveryException(EnvironmentImpl env,
                                String message, Throwable t) {
        super(message, t);
        invalidate(env);
    }

    private void invalidate(EnvironmentImpl env) {
	if (env != null) {
	    env.invalidate(this);
	}
    }

    /* 
     * Remember that this was already thrown. That way, if we re-throw it
     * because another handle uses the environment after the fatal throw,
     * the message is more clear.
     */
    public void setAlreadyThrown() {
        alreadyThrown = true;
    }

    public String toString() {
        if (alreadyThrown) {
            return "Environment invalid because of previous exception: " +
		super.toString();
        } else {
            return super.toString();
        }
    }
}
