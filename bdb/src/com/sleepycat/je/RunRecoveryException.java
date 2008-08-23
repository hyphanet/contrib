/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: RunRecoveryException.java,v 1.25 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Thrown when the JE environment has encountered an exception or a 
 * resource shortfall and cannot continue on safely. The Environment will
 * no longer permit any operations and the application must be reinstantiated 
 * the Environment.
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
                                String message,
				Throwable t) {
        super(message, t);
        invalidate(env);
    }

    private void invalidate(EnvironmentImpl env) {
	if (env != null) {
	    env.invalidate(this);
	}
    }

    /**
     * @hidden
     * Remember that this was already thrown. That way, if we re-throw it
     * because another handle uses the environment after the fatal throw, the
     * message is more clear.
     */
    public void setAlreadyThrown(boolean alreadyThrown) {
        this.alreadyThrown = alreadyThrown;
    }

    @Override
    public String toString() {
        if (alreadyThrown) {
            return "Environment invalid because of previous exception: " +
		super.toString();
        } else {
            return super.toString();
        }
    }
}
