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

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class LongRunningBackgroundThreads implements Runnable {
    private volatile int _threadCount;
    
    /*---------------------------------------------------------------
     * Runnable Method
     *-------------------------------------------------------------*/
    public void run() {
        ++_threadCount;
        int loops = 0;
        
        while(loops < 10) {
            loops++;
            System.out.println(Thread.currentThread().getName() + " loop #" + loops);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
        System.out.println(Thread.currentThread().getName() + " stopping.");
        if(--_threadCount <= 0){
            System.out.println("The JVM and then the wrapper should exit now.");
        }
    }
    
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        System.out.println("Long-running Background Threads Running...");
        
        LongRunningBackgroundThreads app = new LongRunningBackgroundThreads();
        for (int i = 0; i < 2; i++) {
            Thread thread = new Thread(app, "App-Thread-" + i);
            thread.start();
        }
        
        System.out.println("Running as a service: " + WrapperManager.isLaunchedAsService());
        System.out.println("Controlled by wrapper: " + WrapperManager.isControlledByNativeWrapper());
        
        System.out.println("Long-running Background Threads Main Done...");
    }
}
