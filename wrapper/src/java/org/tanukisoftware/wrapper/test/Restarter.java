package org.tanukisoftware.wrapper.test;

/*
 * Copyright (c) 1999, 2008 Tanuki Software, Inc.
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

import org.tanukisoftware.wrapper.WrapperManager;
import org.tanukisoftware.wrapper.WrapperListener;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class Restarter implements WrapperListener {
    /**************************************************************************
     * Constructors
     *************************************************************************/
    private Restarter() {
    }
    
    /**************************************************************************
     * WrapperListener Methods
     *************************************************************************/
    public Integer start(String[] args) {
        System.out.println("start()");
        
        // Start up a thread whose job it is to request a restart in 5 seconds.
        Thread restarter = new Thread("restarter") {
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {}
                
                // Start up a thread whose only job is to dump output to the console.
                Thread outputter = new Thread("outputter") {
                    public void run() {
                        int counter = 0;
                        while(true) {
                            /*
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {}
                            */
                            Thread.yield();
                            
                            System.out.println("        outputer line #" + (++counter));
                            System.out.println("           1) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           2) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           3) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           4) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           5) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           6) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           7) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           8) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           9) A long line of test data to cause lots of data to be sent to the console.");
                            System.out.println("           10)A long line of test data to cause lots of data to be sent to the console.");
                            System.out.flush();
                        }
                    }
                };
                //outputter.start();
                
                System.out.println("Requesting restart...");
                WrapperManager.restart();
            }
        };
        restarter.start();
        
        return null;
    }
    
    public int stop(int exitCode) {
        System.out.println("stop(" + exitCode + ")");
        
        return exitCode;
    }
    
    public void controlEvent(int event) {
        System.out.println("controlEvent(" + event + ")");
        if (event == WrapperManager.WRAPPER_CTRL_C_EVENT) {
            WrapperManager.stop(0);
        }
    }
    
    /**************************************************************************
     * Main Method
     *************************************************************************/
    public static void main(String[] args) {
        System.out.println("Initializing...");
        
        // Start the application.  If the JVM was launched from the native
        //  Wrapper then the application will wait for the native Wrapper to
        //  call the application's start method.  Otherwise the start method
        //  will be called immediately.
        WrapperManager.start(new Restarter(), args);
    }
}

