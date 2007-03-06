/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: TestHookExecute.java,v 1.4 2006/10/30 21:14:29 bostic Exp $
 */

package com.sleepycat.je.utilint;

/**
 */
public class TestHookExecute {
    public static boolean doHookIfSet(TestHook testHook) {
        if (testHook != null) {
            testHook.doHook();
        }
        return true;
    }
}
