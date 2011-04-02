package org.tanukisoftware.wrapper;

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

/**
 * Applications which need to be controlled directly as a service can implement
 *  the WrapperListener interface and then register themselves with the
 *  WrapperManager on instantiation.  The WrapperManager will then control the
 *  the class as a service for its entire life-cycle.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public interface WrapperListener
{
    /**
     * The start method is called when the WrapperManager is signaled by the 
     *	native wrapper code that it can start its application.  This
     *	method call is expected to return, so a new thread should be launched
     *	if necessary.
     * <p>
     * If this method throws an exception the Wrapper will shutdown the current
     *  JVM in an error state and then relaunch a new JVM.  It is the
     *  responsibility of the user code to catch any exceptions and return an
     *  appropriate exit code if the exception should result in the Wrapper
     *  stopping.
     *
     * @param args List of arguments used to initialize the application.
     *
     * @return Any error code if the application should exit on completion
     *         of the start method.  If there were no problems then this
     *         method should return null.
     */
    Integer start( String[] args );
    
    /**
     * Called when the application is shutting down.  The Wrapper assumes that
     *  this method will return fairly quickly.  If the shutdown code code
     *  could potentially take a long time, then WrapperManager.signalStopping()
     *  should be called to extend the timeout period.  If for some reason,
     *  the stop method can not return, then it must call
     *  WrapperManager.stopped() to avoid warning messages from the Wrapper.
     * <p>
     * WARNING - Directly calling System.exit in this method will result in
     *  a deadlock in cases where this method is called from within a shutdown
     *  hook.  This method will be invoked by a shutdown hook if the JVM
     *  shutdown was originally initiated by a call to System.exit.
     *
     * @param exitCode The suggested exit code that will be returned to the OS
     *                 when the JVM exits.  If WrapperManager.stop was called
     *                 to stop the JVM then this exit code will reflect that
     *                 value.  However, if System.exit or Runtime.halt were
     *                 used then this exitCode will always be 0.  In these
     *                 cases, the Wrapper process will be able to detect the
     *                 actual JVM exit code and handle it correctly.
     *
     * @return The exit code to actually return to the OS.  In most cases, this
     *         should just be the value of exitCode, however the user code has
     *         the option of changing the exit code if there are any problems
     *         during shutdown.
     */
    int stop( int exitCode );
    
    /**
     * Called whenever the native wrapper code traps a system control signal
     *  against the Java process.  It is up to the callback to take any actions
     *  necessary.  Possible values are: WrapperManager.WRAPPER_CTRL_C_EVENT, 
     *    WRAPPER_CTRL_CLOSE_EVENT, WRAPPER_CTRL_LOGOFF_EVENT, 
     *    WRAPPER_CTRL_SHUTDOWN_EVENT, WRAPPER_CTRL_TERM_EVENT, or
     *    WRAPPER_CTRL_HUP_EVENT.
     * <p>
     * The WRAPPER_CTRL_C_EVENT will be called whether or not the JVM is
     *  controlled by the Wrapper.  If controlled by the Wrapper, it is
     *  undetermined as to whether the Wrapper or the JVM will receive this
     *  signal first, but the Wrapper will always initiate a shutdown.  In
     *  most cases, the implementation of this method should call
     *  WrapperManager.stop() to initiate a shutdown from within the JVM.
     *  The WrapperManager will always handle the shutdown correctly whether
     *  shutdown is initiated from the Wrapper, within the JVM or both.
     *  By calling stop here, it will ensure that the application will behave
     *  correctly when run standalone, without the Wrapper.
     * <p>
     * WRAPPER_CTRL_CLOSE_EVENT, WRAPPER_CTRL_LOGOFF_EVENT, and
     *  WRAPPER_CTRL_SHUTDOWN_EVENT events will only be encountered on Windows
     *  systems.  Like the WRAPPER_CTRL_C_EVENT event, it is undetermined as to
     *  whether the Wrapper or JVM will receive the signal first.  All signals
     *  will be triggered by the OS whether the JVM is being run as an NT
     *  service or as a console application.  If the JVM is running as a
     *  console application, the Application must respond to the CLOSE and
     *  LOGOFF events by calling WrapperManager.stop() in a timely manner.
     *  In these cases, Windows will wait for the JVM process to exit before
     *  moving on to signal the next process.  If the JVM process does not exit
     *  within a reasonable amount of time, Windows will pop up a message box
     *  for the user asking if they wish to wait for the process or exit or
     *  forcibly close it.  The JVM must call stop() in response to the
     *  SHUTDOWN method whether running as a console or NT service.  Usually,
     *  the LOGOFF event should be ignored when the Wrapper is running as an
     *  NT service.
     * <p>
     * WRAPPER_CTRL_TERM_EVENT events will only be encountered on UNIX systems.
     * <p>
     * If the wrapper.ignore_signals property is set to TRUE then any
     *  WRAPPER_CTRL_C_EVENT, WRAPPER_CTRL_CLOSE_EVENT,
     *  WRAPPER_CTRL_TERM_EVENT, or WRAPPER_CTRL_HUP_EVENT
     *  events will be blocked prior to this method being called.
     * <p>
     * Unless you know what you are doing, it is suggested that the body of
     *  this method contain the following code, or its functional equivalent.
     * <pre>
     *   public void controlEvent( int event )
     *   {
     *       if ( ( event == WrapperManager.WRAPPER_CTRL_LOGOFF_EVENT )
     *           && ( WrapperManager.isLaunchedAsService() || WrapperManager.isIgnoreConsoleLogouts() ) )
     *       {
     *           // Ignore
     *       }
     *       else
     *       {
     *           WrapperManager.stop( 0 );
     *       }
     *   }
     * </pre>
     *
     * @param event The system control signal.
     */
    void controlEvent( int event );
}

