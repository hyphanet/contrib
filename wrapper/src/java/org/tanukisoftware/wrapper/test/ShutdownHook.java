package org.tanukisoftware.wrapper.test;

/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 * 
 * 
 * Portions of the Software have been derived from source code
 * developed by Silver Egg Technology under the following license:
 * 
 * Copyright (c) 2001 Silver Egg Technology
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without 
 * restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sub-license, and/or 
 * sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class ShutdownHook {
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        // Locate the add and remove shutdown hook methods using reflection so
        //  that this class can be compiled on 1.2.x versions of java.
        Method addShutdownHookMethod;
        try {
            addShutdownHookMethod =
                Runtime.class.getMethod("addShutdownHook", new Class[] {Thread.class});
        } catch (NoSuchMethodException e) {
            System.out.println("Shutdown hooks not supported by current JVM.");
            return;
        }
        
        System.out.println("This application registers a shutdown hook which");
        System.out.println("should be executed after the JVM has told the Wrapper");
        System.out.println("it is exiting.");
        System.out.println("This is to test the wrapper.jvm_exit.timeout property");
        
        Runtime runtime = Runtime.getRuntime();
        Thread hook = new Thread() {
                public void run() {
                    System.out.println("Starting shutdown hook. Loop for 25 seconds.");
                    System.out.println("Should timeout unless this property is set: wrapper.jvm_exit.timeout=30");

                    long start = System.currentTimeMillis();
                    while(System.currentTimeMillis() - start < 25000)
                    {
                        try
                        {
                            Thread.sleep( 250 );
                        }
                        catch ( InterruptedException e )
                        {
                            // Ignore
                        }
                    }
                    System.out.println("Shutdown hook complete. Should exit now.");
                }
            };
        try {
            addShutdownHookMethod.invoke(runtime, new Object[] {hook});
        } catch (IllegalAccessException e) {
            System.out.println("Unable to register shutdown hook: " + e.getMessage());
        } catch (InvocationTargetException e) {
            System.out.println("Unable to register shutdown hook: " + e.getMessage());
        }
        
        System.out.println("Application complete.  Wrapper should stop, invoking the shutdown hooks.");
        System.out.println();
    }
}

