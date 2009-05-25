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

import org.tanukisoftware.wrapper.WrapperManager;
import org.tanukisoftware.wrapper.WrapperListener;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class TestAction
    extends AbstractActionApp
    implements WrapperListener
{
    private ActionRunner m_actionRunner;
    
    /**************************************************************************
     * Constructors
     *************************************************************************/
    private TestAction() {
    }

    /**************************************************************************
     * WrapperListener Methods
     *************************************************************************/
    public Integer start(String[] args) {
        Thread actionThread;

        System.out.println("start()");
        
        if (args.length <= 0)
            printHelp("Missing action parameter.");

        prepareSystemOutErr();
        
        // * * Start the action thread
        m_actionRunner = new ActionRunner(args[0]);
        actionThread = new Thread(m_actionRunner);
        actionThread.start();

        return null;
    }
    
    public int stop(int exitCode) {
        System.out.println("stop(" + exitCode + ")");
        
        if (isNestedExit())
        {
            System.out.println("calling System.exit(" + exitCode + ") within stop.");
            System.exit(exitCode);
        }
        
        return exitCode;
    }
    
    public void controlEvent(int event) {
        System.out.println("controlEvent(" + event + ")");
        if (event == WrapperManager.WRAPPER_CTRL_C_EVENT) {
            if ( !ignoreControlEvents() ) {
                //WrapperManager.stop(0);
                
                // May be called before the runner is started.
                if (m_actionRunner != null) {
                    m_actionRunner.endThread();
                }
            }
        }
    }

    /**************************************************************************
     * Inner Classes
     *************************************************************************/
    private class ActionRunner implements Runnable {
        private String m_action;
        private boolean m_alive;
        
        public ActionRunner(String action) {
            m_action = action;
            m_alive = true;
        }
    
        public void run() {
            // Wait for 5 seconds so that the startup will complete.
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {}
            
            if (!TestAction.this.doAction(m_action)) {
                printHelp("\"" + m_action + "\" is an unknown action.");
                WrapperManager.stop(0);
                return;
            }
    
            while (m_alive) {
                // Idle some
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    
        public void endThread( ) {
            m_alive = false;
        }
    }
    
    /**
     * Prints the usage text.
     *
     * @param error_msg Error message to write with usage text
     */
    private static void printHelp(String error_msg) {
        System.err.println( "USAGE" );
        System.err.println( "" );
        System.err.println( "TestAction <action>" );
        System.err.println( "" );
        System.err.println( "[ACTIONS]" );
        System.err.println( "  Actions which should cause the Wrapper to exit cleanly:" );
        System.err.println( "   stop0                    : Calls WrapperManager.stop(0)" );
        System.err.println( "   exit0                    : Calls System.exit(0)" );
        System.err.println( "   stopimmediate0           : Calls WrapperManager.stopImmediate(0)" );
        System.err.println( "   stopandreturn0           : Calls WrapperManager.stopAndReturn(0)" );
        System.err.println( "  Actions which should cause the Wrapper to exit in an error state:" );
        System.err.println( "   stop1                    : Calls WrapperManager.stop(1)" );
        System.err.println( "   exit1                    : Calls System.exit(1)" );
        System.err.println( "   nestedexit1              : Calls System.exit(1) within WrapperListener.stop(1) callback" );
        System.err.println( "   stopimmediate1           : Calls WrapperManager.stopImmediate(1)" );
        System.err.println( "  Actions which should cause the Wrapper to restart the JVM:" );
        System.err.println( "   access_violation         : Calls WrapperManager.accessViolation" );
        System.err.println( "   access_violation_native  : Calls WrapperManager.accessViolationNative()" );
        System.err.println( "   appear_hung              : Calls WrapperManager.appearHung()" );
        System.err.println( "   halt0                    : Calls Runtime.getRuntime().halt(0)" );
        System.err.println( "   halt1                    : Calls Runtime.getRuntime().halt(1)" );
        System.err.println( "   restart                  : Calls WrapperManager.restart()" );
        System.err.println( "   restartandreturn         : Calls WrapperManager.restartAndReturn()" );
        System.err.println( "  Additional Tests:" );
        System.err.println( "   ignore_events            : Makes this application ignore control events." );
        System.err.println( "   dump                     : Calls WrapperManager.requestThreadDump()" );
        System.err.println( "   deadlock_out             : Deadlocks the JVM's System.out and err streams." );
        System.err.println( "   users                    : Start polling the current and interactive users." );
        System.err.println( "   groups                   : Start polling the current and interactive users with groups." );
        System.err.println( "   console                  : Prompt for actions in the console." );
        System.err.println( "   idle                     : Do nothing just run in idle mode." );
        System.err.println( "   properties               : Dump all System Properties to the console." );
        System.err.println( "   configuration            : Dump all Wrapper Configuration Properties to the console." );
        System.err.println( "" );
        System.err.println( "[EXAMPLE]" );
        System.err.println( "   TestAction access_violation_native " );
        System.err.println( "" );
        System.err.println( "ERROR: " + error_msg );
        System.err.println( "" );

        System.exit( 1 );
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
        WrapperManager.start(new TestAction(), args);
    }
}

