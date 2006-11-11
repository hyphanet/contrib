/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: TestHook.java,v 1.6 2006/09/12 19:17:00 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.IOException;

/**
 * TestHook is used induce testing behavior that can't be provoked externally.
 * For example, unit tests may use hooks to throw IOExceptions, or to cause
 * waiting behavior.
 *
 * To use this, a unit test should extend TestHook with a class that overrides
 * the desired method. The desired code will have a method that allows the unit
 * test to specify a hook, and will execute the hook if it is non-null.
 */
public interface TestHook {

    public void doIOHook()
	throws IOException;

    public void doHook();

    public Object getHookValue();
}
