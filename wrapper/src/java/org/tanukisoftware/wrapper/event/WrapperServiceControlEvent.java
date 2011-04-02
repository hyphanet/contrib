package org.tanukisoftware.wrapper.event;

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
 * WrapperServiceControlEvents are used to notify the listener whenever a
 *  Service Control Event is received by the service.   These events will
 *  only be fired on Windows platforms when the Wrapper is running as a
 *  service.
 *
 * <dl>
 *   <dt>WrapperManager.SERVICE_CONTROL_CODE_STOP (1)</dt>
 *   <dd>The service was requested to stop.</dd>
 *   <dt>WrapperManager.SERVICE_CONTROL_CODE_PAUSE (2)</dt>
 *   <dd>The system requested that the service be paused.</dd>
 *   <dt>WrapperManager.SERVICE_CONTROL_CODE_CONTINUE (3)</dt>
 *   <dd>The system requested that the paused service be resumed.</dd>
 *   <dt>WrapperManager.SERVICE_CONTROL_CODE_INTERROGATE (4)</dt>
 *   <dd>The service manager queried the service to make sure it is still alive.</dd>
 *   <dt>WrapperManager.SERVICE_CONTROL_CODE_SHUTDOWN (5)</dt>
 *   <dd>The system is shutting down.</dd>
 *   <dt>User code (128-255)</dt>
 *   <dd>User defined code.</dd>
 * </dl>
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperServiceControlEvent
    extends WrapperServiceEvent
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = -8642470717850552167L;
    
    /** The event code of the Service Control Code. */ 
    private int m_serviceControlCode;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperServiceControlEvent.
     *
     * @param serviceControlCode Service Control Code.
     */
    public WrapperServiceControlEvent( int serviceControlCode )
    {
        m_serviceControlCode = serviceControlCode;
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the event code of the Service Control Code.
     *
     * @return The event code of the Service Control Code.
     */
    public int getServiceControlCode()
    {
        return m_serviceControlCode;
    }
    
    /**
     * Returns a string representation of the event.
     *
     * @return A string representation of the event.
     */
    public String toString()
    {
        return "WrapperServiceControlEvent[serviceControlCode=" + getServiceControlCode() + "]";
    }
}
