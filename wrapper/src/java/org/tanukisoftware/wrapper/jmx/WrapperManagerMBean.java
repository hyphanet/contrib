package org.tanukisoftware.wrapper.jmx;

/*
 * Copyright (c) 1999, 2008 Tanuki Software, Inc.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 */

/**
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public interface WrapperManagerMBean
{
    /**
     * Obtain the current version of Wrapper.
     *
     * @return The version of the Wrapper.
     */
    String getVersion();
    
    /**
     * Obtain the build time of Wrapper.
     *
     * @return The time that the Wrapper was built.
     */
    String getBuildTime();
    
    /**
     * Returns the Id of the current JVM.  JVM Ids increment from 1 each time
     *  the wrapper restarts a new one.
     *
     * @return The Id of the current JVM.
     */
    int getJVMId();
    
    /**
     * Returns true if the current Wrapper edition has support for Professional
     *  Edition features.
     *
     * @return True if professional features are supported.
     */
    boolean isProfessionalEdition();
    
    /**
     * Returns true if the current Wrapper edition has support for Standard
     *  Edition features.
     *
     * @return True if standard features are supported.
     */
    boolean isStandardEdition();

    /**
     * Sets the title of the console in which the Wrapper is running.  This
     *  is currently only supported on Windows platforms.
     * <p>
     * As an alternative, it is also possible to set the console title from
     *  within the wrapper.conf file using the wrapper.console.title property.
     *
     * @param title The new title.  The specified string will be encoded
     *              to a byte array using the default encoding for the
     *              current platform.
     */
    void setConsoleTitle( String title );
    
    /**
     * Returns the PID of the Wrapper process.
     *
     * A PID of 0 will be returned if the JVM was launched standalone.
     *
     * This value can also be obtained using the 'wrapper.pid' system property.
     *
     * @return The PID of the Wrpper process.
     */
    int getWrapperPID();
    
    /**
     * Returns the PID of the Java process.
     *
     * A PID of 0 will be returned if the native library has not been initialized.
     *
     * This value can also be obtained using the 'wrapper.java.pid' system property.
     *
     * @return The PID of the Java process.
     */
    int getJavaPID();
    
    /**
     * Requests that the current JVM process request a thread dump.  This is
     *  the same as pressing CTRL-BREAK (under Windows) or CTRL-\ (under Unix)
     *  in the the console in which Java is running.  This method does nothing
     *  if the native library is not loaded.
     */
    void requestThreadDump();
    
    /**
     * Returns true if the JVM was launched by the Wrapper application.  False
     *  if the JVM was launched manually without the Wrapper controlling it.
     *
     * @return True if the current JVM was launched by the Wrapper.
     */
    boolean isControlledByNativeWrapper();
    
    /**
     * Returns true if the Wrapper was launched as an NT service on Windows or
     *  as a daemon process on UNIX platforms.  False if launched as a console.
     *  This can be useful if you wish to display a user interface when in
     *  Console mode.  On UNIX platforms, this is not as useful because an
     *  X display may not be visible even if launched in a console.
     *
     * @return True if the Wrapper is running as an NT service or daemon
     *         process.
     */
    boolean isLaunchedAsService();
    
    /**
     * Returns true if the wrapper.debug property, or any of the logging
     *  channels are set to DEBUG in the wrapper configuration file.  Useful
     *  for deciding whether or not to output certain information to the
     *  console.
     *
     * @return True if the Wrapper is logging any Debug level output.
     */
    boolean isDebugEnabled();
    
    /**
     * Tells the native wrapper that the JVM wants to restart, then informs
     *	all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * The restart is actually performed in a background thread allowing JMX
     *  a chance to respond to the client.
     */
    void restart();
    
    /**
     * Tells the native wrapper that the JVM wants to shut down, then informs
     *	all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * The stop is actually performed in a background thread allowing JMX
     *  a chance to respond to the client.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     */
    void stop( int exitCode );
    
    /**
     * Returns true if the ShutdownHook for the JVM has already been triggered.
     *  Some code needs to know whether or not the system is shutting down.
     *
     * @return True if the ShutdownHook for the JVM has already been triggered.
     */
    boolean getHasShutdownHookBeenTriggered();
}
