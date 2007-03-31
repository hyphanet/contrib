/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JUnitMethodThread.java,v 1.5.2.1 2007/02/01 14:50:12 cwl Exp $
 */

package com.sleepycat.je.junit;

import java.lang.reflect.Method;

import junit.framework.TestCase;
    
/**
 * A JUnitThread whose testBody calls a given TestCase method.
 */
public class JUnitMethodThread extends JUnitThread {
    
    private TestCase testCase;
    private Method method;
    private Object param;

    public JUnitMethodThread(String threadName, String methodName,
                             TestCase testCase) 
        throws NoSuchMethodException {

        this(threadName, methodName, testCase, null);
    }

    public JUnitMethodThread(String threadName, String methodName,
                             TestCase testCase, Object param) 
        throws NoSuchMethodException {

        super(threadName);
        this.testCase = testCase;
        this.param = param;
        method = testCase.getClass().getMethod(methodName, new Class[0]);
    }

    public void testBody() 
        throws Exception {

        if (param != null) {
            method.invoke(testCase, new Object[] { param });
        } else {
            method.invoke(testCase, new Object[0]);
        }
    }
}
