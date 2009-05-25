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
 * WrapperServiceExceptions are thrown when the Wrapper is unable to obtain
 *  information on a requested service.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperServiceException
    extends Exception
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 5163822791166376887L;
    
    /**
     * The error code.
     */
    private final int m_errorCode;

    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperServiceException.
     *
     * @param errorCode ErrorCode which was encountered.
     * @param message Message describing the exception.
     */
    WrapperServiceException( int errorCode, byte[] message )
    {
        super( new String( message ) );
        m_errorCode = errorCode;
    }

    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the error code.
     *
     * @return The error code.
     */
    public int getErrorCode()
    {
        return m_errorCode;
    }
    
    /**
     * Return string representation of the Exception.
     *
     * @return String representation of the Exception.
     */
    public String toString()
    {
        return this.getClass().getName() + " " + getMessage() + " Error Code: " + getErrorCode(); 
    }
}

