/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TestHookExecute.java,v 1.7 2008/01/07 14:28:57 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * Execute a test hook if set. This wrapper is used so that test hook execution
 * can be packaged into a single statement that can be done within an assert
 * statement.
 */
public class TestHookExecute {

    public static boolean doHookSetupIfSet(TestHook testHook) {
        if (testHook != null) {
            testHook.hookSetup();
        }
        return true;
    }

    public static boolean doHookIfSet(TestHook testHook) {
        if (testHook != null) {
            testHook.doHook();
        }
        return true;
    }
}
