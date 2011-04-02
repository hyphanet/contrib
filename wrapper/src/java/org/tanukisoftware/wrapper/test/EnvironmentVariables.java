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

import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.tanukisoftware.wrapper.WrapperManager;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class EnvironmentVariables {

    private static Properties _env = null;
    
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        System.out.println("user.language=" + System.getProperty("user.language"));
        System.out.println("user.region=" + System.getProperty("user.region"));
        System.out.println("Locale=" + java.util.Locale.getDefault());        
        System.out.println("Looking for environment variables...");
        
        try {
            getEnvironmentVariables();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        
        boolean passed = false;

        if (_env != null) {
            
            System.out.println();
            passed = check("ENV_VAR_1", "a");
            passed = check("ENV_VAR_2", "b");
            passed = check("ENV_VAR_3", "c");
            passed = check("ENV_VAR_4", "d");
            System.out.println();
        }
        
        if (passed) {
            System.out.println("Environment variables test passed.");
        } else {
            System.out.println("Environment variables test FAILED.");
        }

        System.out.println("Request a JVM restart.");
        WrapperManager.restart();
    }

    private static boolean check(String variable, String expected) {
        
        String actual = _env.getProperty(variable);
        
        System.out.print(variable + " = " + actual + ": ");
        
        if (expected.equals(actual)) {
            System.out.println("OK");
            return true;
        }
        
        System.out.println("FAILED (expected: " + expected + ")");
        return false;
    }

    private static void getEnvironmentVariables() throws IOException {
        
        String os = System.getProperty("os.name").toLowerCase();
        
        System.out.println("Platform is " + os + ".");
        
        Process p = null;
        
        if (os.indexOf("windows 9") > -1) {
            p = Runtime.getRuntime().exec("command.com /c set");
        } else if (os.indexOf("unix") > -1) {
            p = Runtime.getRuntime().exec("/bin/env");
        } else if ((os.indexOf("nt") > -1) || (os.indexOf("windows 2000") > -1)
            || (os.indexOf("windows xp") > -1) || (os.indexOf("windows 2003") > -1) ) {
            p = Runtime.getRuntime().exec("cmd.exe /c set");
        } else if  (os.indexOf("unix") > -1) {
            p = Runtime.getRuntime().exec("/bin/env");
        } else if  ((os.indexOf("linux") > -1) || (os.indexOf("mac os x") > -1)) {
            p = Runtime.getRuntime().exec("/usr/bin/env");
        }
        
        if (p == null) {
            System.out.println(
                "Don't know how to read environment variables on this platform: " + os);
            return;
        }
        
        _env = new Properties();
        
        BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));
        
        String line = null;
        while ((line = br.readLine()) != null) {
            
            int idx = line.indexOf('=');
            
            if (idx > -1) {
                String key   = line.substring(0, idx);
                String value = line.substring(idx + 1);
                
                _env.setProperty(key, value);
            }
        }
    }
}

