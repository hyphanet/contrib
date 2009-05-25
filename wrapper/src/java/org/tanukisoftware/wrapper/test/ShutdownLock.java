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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.tanukisoftware.wrapper.WrapperManager;
import org.tanukisoftware.wrapper.WrapperShuttingDownException;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class ShutdownLock {
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        Thread daemon = new Thread() {
            public void run() {
                System.out.println( "Daemon thread started." );
                
                System.out.println( "Requesting a shutdown lock." );
                try
                {
                    WrapperManager.requestShutdownLock();
                }
                catch ( WrapperShuttingDownException e )
                {
                    System.out.println( e );
                }
            
                System.out.println( "Waiting for 20 seconds." );
                try
                {
                    Thread.sleep( 20000 );
                }
                catch ( InterruptedException e )
                {
                }
            
                System.out.println( "Freeing up shutdown lock." );
                WrapperManager.releaseShutdownLock();
                
                System.out.println( "Daemon thread completed." );
            }
        };
        daemon.setDaemon( true );

        System.out.println( "Starting daemon thread." );
        daemon.start();

        try
        {
            Thread.sleep( 5000 );
        }
        catch ( InterruptedException e )
        {
        }

        System.out.println("Application complete.  Wrapper should stop when shutdown lock is released.");
        System.out.println();
    }
}

