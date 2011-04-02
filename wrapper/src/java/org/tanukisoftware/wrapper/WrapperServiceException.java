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

    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperServiceException.
     *
     * @param message Message describing the exception.
     */
    WrapperServiceException( byte[] message )
    {
        super( new String( message ) );
    }
}

