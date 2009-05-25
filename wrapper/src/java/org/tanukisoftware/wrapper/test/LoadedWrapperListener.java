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
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 * This test was created to test timeout problems under heavily loaded
 *  conditions.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class LoadedWrapperListener
    implements WrapperListener, Runnable
{
    private String[] m_startMainArgs;
    private boolean m_mainComplete;
    private Integer m_mainExitCode;
    private boolean m_waitTimedOut;
    
    /*---------------------------------------------------------------
     * Constructor
     *-------------------------------------------------------------*/
    private LoadedWrapperListener()
    {
    }
    
    /*---------------------------------------------------------------
     * WrapperListener Methods
     *-------------------------------------------------------------*/
    /**
     * The start method is called when the WrapperManager is signaled by the 
     *	native wrapper code that it can start its application.  This
     *	method call is expected to return, so a new thread should be launched
     *	if necessary.
     *
     * @param args List of arguments used to initialize the application.
     *
     * @return Any error code if the application should exit on completion
     *         of the start method.  If there were no problems then this
     *         method should return null.
     */
    public Integer start( String[] args )
    {
        if ( WrapperManager.isDebugEnabled() )
        {
            System.out.println( "LoadedWrapperListener: start(args)" );
        }

        Thread mainThread = new Thread( this, "LoadedWrapperListenerMain" );
        synchronized ( this )
        {
            m_startMainArgs = args;
            mainThread.start();
            // Wait for five seconds to give the application a chance to have failed.
            try
            {
                this.wait( 5000 );
            }
            catch ( InterruptedException e ) { }
            m_waitTimedOut = true;

            if ( WrapperManager.isDebugEnabled() )
            {
                System.out.println( "LoadedWrapperListener: start(args) end.  Main Completed=" +
                    m_mainComplete + ", exitCode=" + m_mainExitCode );
            }
            return m_mainExitCode;
        }
    }
    
    /**
     * Called when the application is shutting down.  The Wrapper assumes that
     *  this method will return fairly quickly.  If the shutdown code code
     *  could potentially take a long time, then WrapperManager.signalStopping()
     *  should be called to extend the timeout period.  If for some reason,
     *  the stop method can not return, then it must call
     *  WrapperManager.stopped() to avoid warning messages from the Wrapper.
     *
     * @param exitCode The suggested exit code that will be returned to the OS
     *                 when the JVM exits.
     *
     * @return The exit code to actually return to the OS.  In most cases, this
     *         should just be the value of exitCode, however the user code has
     *         the option of changing the exit code if there are any problems
     *         during shutdown.
     */
    public int stop( int exitCode )
    {
        if ( WrapperManager.isDebugEnabled() )
        {
            System.out.println( "LoadedWrapperListener: stop(" + exitCode + ")" );
        }
        
        return exitCode;
    }
    
    /**
     * Called whenever the native wrapper code traps a system control signal
     *  against the Java process.  It is up to the callback to take any actions
     *  necessary.  Possible values are: WrapperManager.WRAPPER_CTRL_C_EVENT, 
     *    WRAPPER_CTRL_CLOSE_EVENT, WRAPPER_CTRL_LOGOFF_EVENT, or 
     *    WRAPPER_CTRL_SHUTDOWN_EVENT
     *
     * @param event The system control signal.
     */
    public void controlEvent( int event )
    {
        if ( WrapperManager.isControlledByNativeWrapper() )
        {
            if ( WrapperManager.isDebugEnabled() )
            {
                System.out.println( "LoadedWrapperListener: controlEvent(" + event + ") Ignored" );
            }
            // Ignore the event as the native wrapper will handle it.
        }
        else
        {
            if ( WrapperManager.isDebugEnabled() )
            {
                System.out.println( "LoadedWrapperListener: controlEvent(" + event + ") Stopping" );
            }

            // Not being run under a wrapper, so this isn't an NT service and should always exit.
            //  Handle the event here.
            WrapperManager.stop( 0 );
            // Will not get here.
        }
    }
    
    /*---------------------------------------------------------------
     * Runnable Methods
     *-------------------------------------------------------------*/
    /**
     * Runner thread which actually launches the application.
     */
    public void run()
    {
        Throwable t = null;
        try
        {
            if ( WrapperManager.isDebugEnabled() )
            {
                System.out.println( "LoadedWrapperListener: invoking start main method" );
            }
            appMain( m_startMainArgs );
            if ( WrapperManager.isDebugEnabled() )
            {
                System.out.println( "LoadedWrapperListener: start main method completed" );
            }

            synchronized ( this )
            {
                // Let the start() method know that the main method returned, in case it is
                //  still waiting.
                m_mainComplete = true;
                
                this.notifyAll();
            }

            return;
        }
        catch (Throwable e)
        {
            t = e;
        }

        // If we get here, then an error was thrown.  If this happened quickly
        // enough, the start method should be allowed to shut things down.
        System.out.println( "Encountered an error running start main: " + t );
        t.printStackTrace();

        synchronized( this )
        {
            if ( m_waitTimedOut )
            {
                // Shut down here.
                WrapperManager.stop( 1 );
                return; // Will not get here.
            }
            else
            {
                // Let start method handle shutdown.
                m_mainComplete = true;
                m_mainExitCode = new Integer( 1 );
                this.notifyAll();
                return;
            }
        }
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Main method of the actual application.
     */
    private void appMain( String[] args )
    {
        System.out.println( "App Main Starting." );
        System.out.println();
        
        // Loop and display 500 long lines of text to place to dump a lot of
        //  output before the CPU starts being loaded down.  This will strain
        //  the Wrapper just as the CPU suddenly hpegs at 100%.
        for ( int i = 0; i < 500; i++ )
        {
            System.out.println( new Date() + "  Pre " + i + " of output. "
                + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" );
        }
        
        // Start up a thread to thrash the hard disk.
        Thread diskThrasher = new Thread( "LoadedWrapperListener_DiskThrasher" )
        {
            public void run()
            {
                performDiskThrashing();
            }
        };
        diskThrasher.start();
        
        // Start up a thread to thrash memory.
        Thread memoryThrasher = new Thread( "LoadedWrapperListener_MemoryThrasher" )
        {
            public void run()
            {
                performMemoryThrashing();
            }
        };
        memoryThrasher.start();
        
        // Start up some threads to eat all available CPU
        for ( int i = 0; i < 4; i++ )
        {
            Thread cpuThrasher = new Thread( "LoadedWrapperListener_CPUThrasher_" + i )
            {
                public void run()
                {
                    performCPUThrashing();
                }
            };
            cpuThrasher.start();
        }
        
        // Loop and display 5000 long lines of text to place a heavy load on the
        //  JVM output processing code of the Wrapper while the above threads are
        //  eating all available CPU.
        for ( int i = 0; i < 5000; i++ )
        {
            System.out.println( new Date() + "  Row " + i + " of output. "
                + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" );
        }
        System.out.println();
        System.out.println( "App Main Complete." );
    }
    
    private void performDiskThrashing()
    {
        while( !m_mainComplete )
        {
            File file = new File( "loadedwrapperlistener.dat" );
            try
            {
                PrintWriter w = new PrintWriter( new FileWriter( file ) );
                try
                {
                    for ( int i = 0; i < 100; i++ )
                    {
                        w.println( new Date() + "  Row " + i + " of output. "
                            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" );
                    }
                }
                finally
                {
                    w.close();
                }
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
            file.delete();
        }
    }
    
    private void performMemoryThrashing()
    {
        while( !m_mainComplete )
        {
            // 200MB block of memory
            byte[][] garbage = new byte[200][];
            for ( int i = 0; i < garbage.length; i++ )
            {
                garbage[i] = new byte[1024 * 1024];
            }
            garbage = null;
            
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            System.out.println( "Total Memory=" + totalMemory + ", "
                + "Used Memory=" + ( totalMemory - freeMemory ) );
        }
    }
    
    private void performCPUThrashing()
    {
        while( !m_mainComplete )
        {
            // Do nothing, we just want a tight loop.
        }
    }
    
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        // Test an initial line feed as a regression test.  Must be
        //  the first output from the JVM
        System.out.println();

        System.out.println( "LoadedWrapperListener.main" );
        
        WrapperManager.start( new LoadedWrapperListener(), args );
    }
}

