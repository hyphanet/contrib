/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: TestHookExecute.java,v 1.3 2006/09/12 19:17:00 cwl Exp $
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
