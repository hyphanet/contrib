package org.tanukisoftware.wrapper.jmx;

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

/**
 * This MBean interface provides access to a number of actions which can be
 *  useful for testing how well an application responds to JVM crashes.  It
 *  has been broken out frtom the WrapperManagerMBean interface so system
 *  administrators can easily disable the testing functions.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public interface WrapperManagerTestingMBean
{
    /**
     * Causes the WrapperManager to go into a state which makes the JVM appear
     *  to be hung when viewed from the native Wrapper code.  Does not have
     *  any effect when the JVM is not being controlled from the native
     *  Wrapper.
     */
    void appearHung();
    
    /**
     * Cause an access violation within native JNI code.  This currently causes
     *  the access violation by attempting to write to a null pointer.
     */
    void accessViolationNative();
    
    /**
     * Tells the native wrapper that the JVM wants to shut down and then
     *  promptly halts.  Be careful when using this method as an application
     *  will not be given a chance to shutdown cleanly.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     */
    void stopImmediate( int exitCode );
}
