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

import java.io.FileWriter;
import java.io.IOException;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class OutputLoader {
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        System.out.println("Start outputting lots of data.");
        
        long start = System.currentTimeMillis();
        int count = 0;
        while ((System.currentTimeMillis()) < start + 20000) {
            System.out.println("Testing line Out #" + (++count));
            System.err.println("Testing line Err #" + (++count));
        }
        
        System.out.println("Printed " + count + " lines of output in 20 seconds");
        
        // Write the output to a file as well, so we can see the results
        //  when output is disabled.
        try {
            FileWriter fw = new FileWriter("OutputLoader.log", true);
            fw.write("Printed " + count + " lines of output in 20 seconds\n");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

