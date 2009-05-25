package org.tanukisoftware.wrapper;

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
 * A WrapperWin32Service contains information about an individual service
 *  registered with the current system.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperWin32Service
{
    public static final int SERVICE_STATE_STOPPED          = 0x00000001;
    public static final int SERVICE_STATE_START_PENDING    = 0x00000002;
    public static final int SERVICE_STATE_STOP_PENDING     = 0x00000003;
    public static final int SERVICE_STATE_RUNNING          = 0x00000004;
    public static final int SERVICE_STATE_CONTINUE_PENDING = 0x00000005;
    public static final int SERVICE_STATE_PAUSE_PENDING    = 0x00000006;
    public static final int SERVICE_STATE_PAUSED           = 0x00000007;
    
    /** The name of the service. */
    private String m_name;
    
    /** The display name of the service. */
    private String m_displayName;
    
    /** The last known state of the service. */
    private int m_serviceState;
    
    /** The exit of the service. */
    private int m_exitCode;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    WrapperWin32Service( byte[] name, byte[] displayName, int serviceState, int exitCode )
    {
        // Decode the parameters using the default system encoding.
        m_name = new String( name );
        m_displayName = new String( displayName );
        
        m_serviceState = serviceState;
        m_exitCode = exitCode;
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the name of the service.
     *
     * @return The name of the service.
     */
    public String getName()
    {
        return m_name;
    }
    
    /**
     * Returns the display name of the service.
     *
     * @return The display name of the service.
     */
    public String getDisplayName()
    {
        return m_displayName;
    }
    
    /**
     * Returns the last known state name of the service.
     *
     * @return The last known state name of the service.
     */
    public String getServiceStateName()
    {
        int serviceState = getServiceState();
        switch( serviceState )
        {
        case SERVICE_STATE_STOPPED:
            return "STOPPED";
            
        case SERVICE_STATE_START_PENDING:
            return "START_PENDING";
            
        case SERVICE_STATE_STOP_PENDING:
            return "STOP_PENDING";
            
        case SERVICE_STATE_RUNNING:
            return "RUNNING";
            
        case SERVICE_STATE_CONTINUE_PENDING:
            return "CONTINUE_PENDING";
            
        case SERVICE_STATE_PAUSE_PENDING:
            return "PAUSE_PENDING";
            
        case SERVICE_STATE_PAUSED:
            return "PAUSED";
            
        default:
            return "UNKNOWN(" + serviceState + ")";
        }
    }
    
    /**
     * Returns the last known state of the service.
     *
     * @return The last known state of the service.
     */
    public int getServiceState()
    {
        return m_serviceState;
    }
    
    /**
     * Returns the exit of the service, or 0 if it is still running.
     *
     * @return The exit of the service.
     */
    public int getExitCode()
    {
        return m_exitCode;
    }
    
    /**
     * Returns a string representation of the user.
     *
     * @return A string representation of the user.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append( "WrapperWin32Service[name=\"" );
        sb.append( getName() );
        sb.append( "\", displayName=\"" );
        sb.append( getDisplayName() );
        
        sb.append( "\", state=" );
        sb.append( getServiceStateName() );
        sb.append( ", exitCode=" );
        sb.append( getExitCode() );
        sb.append( "]" );
        return sb.toString();
    }
}

