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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class Memory implements Runnable
{
    private Writer m_writer;
    private Thread m_runner;
    
    /*---------------------------------------------------------------
     * Runnable Method
     *-------------------------------------------------------------*/
    public void run()
    {
        if ( m_runner == null )
        {
            // This is the runner
            m_runner = Thread.currentThread();
        }
        else
        {
            System.out.println("Stopping..." );
            // This is the shutdown hook.  Sloppy code, but simple :-)
            m_runner = null;
            return;
        }
        
        long startTime = System.currentTimeMillis();
        long lastTest = startTime;
        try
        {
            m_writer.write( "--> Starting Memory Log\n" );
            m_writer.flush();
    
            while( m_runner != null )
            {
                long now = System.currentTimeMillis();
                System.out.println( "Running for " + ( now - startTime ) + "ms..." );
                
                if ( now - lastTest > 15000 )
                {
                    Runtime rt = Runtime.getRuntime();
                    System.gc();
                    long totalMemory = rt.totalMemory();
                    long freeMemory = rt.freeMemory();
                    long usedMemory = totalMemory - freeMemory;
                    
                    m_writer.write( "total memory=" + pad( totalMemory, 10 )
                        + ", used=" + pad( usedMemory, 10 )
                        + ", free=" + pad( freeMemory, 10 ) + "\n" );
                    m_writer.flush();
                    
                    lastTest = now;
                }
                
                try
                {
                    Thread.sleep( 250 );
                }
                catch ( InterruptedException e )
                {
                }
            }
            
            m_writer.write( "<-- Stopping Memory Log\n" );
            m_writer.flush();
            m_writer.close();
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    private static final String PADDING = "                ";
    private String pad( long n, int len )
    {
        String s = Long.toString( n );
        int sLen = s.length();
        if ( sLen < len )
        {
            s = s + PADDING.substring( 0, len - sLen );
        }
        return s;
    }
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args)
    {
        System.out.println("Memory Tester Running...");
        
        // Locate the add and remove shutdown hook methods using reflection so
        //  that this class can be compiled on 1.2.x versions of java.
        Method addShutdownHookMethod;
        try {
            addShutdownHookMethod =
                Runtime.class.getMethod("addShutdownHook", new Class[] {Thread.class});
        } catch (NoSuchMethodException e) {
            System.out.println("Shutdown hooks not supported by current JVM.");
            addShutdownHookMethod = null;
        }
        
        Memory app = new Memory();
        
        // Create a Writer for the memory output
        try
        {
            app.m_writer = new FileWriter( "memory.log" );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            return;
        }
        
        // Register a shutdown hook using reflection.
        if (addShutdownHookMethod != null) {
            Runtime runtime = Runtime.getRuntime();
            Thread hook = new Thread( app, "shutdown-hook" );
            try {
                addShutdownHookMethod.invoke(runtime, new Object[] {hook});
            } catch (IllegalAccessException e) {
                System.out.println("Unable to register shutdown hook: " + e.getMessage());
            } catch (InvocationTargetException e) {
                System.out.println("Unable to register shutdown hook: " + e.getMessage());
            }
        }
        
        // Start the runner
        new Thread( app, "runner" ).start();
    }
}
