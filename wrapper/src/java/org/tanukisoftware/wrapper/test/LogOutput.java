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
public class LogOutput {
    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}
    }
    
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        System.out.println("Test the various log levels...");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG,  "Debug output");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO,   "Info output");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_STATUS, "Status output");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_WARN,   "Warn output");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_ERROR,  "Error output");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_FATAL,  "Fatal output");
        
        // Let things catch up as the timing of WrapperManager.log output and System.out
        //  output can not be guaranteed.
        sleep();
        
        System.out.println("Put the logger through its paces...");
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO,  "Special C characters in %s %d % %%");
        sleep();
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO,   "");
        sleep();
        
        String sa = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 100; i++) {
            sb.append(sa);
        }
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO, sb.toString());
        sleep();

        sb = new StringBuffer();
        for (int i = 0; i < 100; i++) {
            sb.append(sa);
            sb.append("\n");
        }
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO, sb.toString());
        sleep();
        
        for (int i = 0; i < 100; i++) {
            WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO, sa);
        }
    }
}

