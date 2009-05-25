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
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperManagerTesting
    implements WrapperManagerTestingMBean
{
    /*---------------------------------------------------------------
     * WrapperManagerTestingMBean Methods
     *-------------------------------------------------------------*/
    /**
     * Causes the WrapperManager to go into a state which makes the JVM appear
     *  to be hung when viewed from the native Wrapper code.  Does not have
     *  any effect when the JVM is not being controlled from the native
     *  Wrapper.
     */
    public void appearHung()
    {
        org.tanukisoftware.wrapper.WrapperManager.appearHung();
    }
    
    /**
     * Cause an access violation within native JNI code.  This currently causes
     *  the access violation by attempting to write to a null pointer.
     */
    public void accessViolationNative()
    {
        // This action normally will not return, so launch it in a background
        //  thread giving JMX a chance to return a response to its client.
        new Thread()
        {
            public void run()
            {
                try
                {
                    Thread.sleep( 1000 );
                }
                catch ( InterruptedException e )
                {
                }
                
                org.tanukisoftware.wrapper.WrapperManager.accessViolationNative();
            }
        }.start();
    }
    
    /**
     * Tells the native wrapper that the JVM wants to shut down and then
     *  promptly halts.  Be careful when using this method as an application
     *  will not be given a chance to shutdown cleanly.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     */
    public void stopImmediate( final int exitCode )
    {
        // This action normally will not return, so launch it in a background
        //  thread giving JMX a chance to return a response to its client.
        new Thread()
        {
            public void run()
            {
                try
                {
                    Thread.sleep( 1000 );
                }
                catch ( InterruptedException e )
                {
                }
                
                org.tanukisoftware.wrapper.WrapperManager.stopImmediate( exitCode );
            }
        }.start();
    }
}
