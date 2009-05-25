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

import org.tanukisoftware.wrapper.WrapperManager;
import org.tanukisoftware.wrapper.WrapperListener;

/**
 * This test is to make sure the Wrapper works correctly when stop or restart is
 *  called before the start method has completed.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class StopWhileStarting implements WrapperListener {
    /**************************************************************************
     * Constructors
     *************************************************************************/
    private StopWhileStarting() {
    }
    
    /**************************************************************************
     * WrapperListener Methods
     *************************************************************************/
    public Integer start(String[] args) {
        System.out.println("start()");
        
        switch ( WrapperManager.getJVMId() ) {
        case 1:
            System.out.println( "start() request restart" );
            WrapperManager.restart();
            break;
            
        case 2:
            System.out.println( "start() request halt(0)" );
            Runtime.getRuntime().halt( 0 );
            break;
            
        case 3:
            System.out.println( "start() request System.exit(99).  Will restart due to on_exit configuration" );
            System.exit( 99 );
            break;
            
        default:
            System.out.println("start() request stop(0)");
            WrapperManager.stop( 0 );
            break;
        }
        
        System.out.println("start() END - Should not get here.");
        
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
        WrapperManager.start(new StopWhileStarting(), args);
    }
}

