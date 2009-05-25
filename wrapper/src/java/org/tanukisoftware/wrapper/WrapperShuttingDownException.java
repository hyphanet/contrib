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
 * WrapperShuttingDownExceptions are thrown when certain Wrapper functions
 *  are accessed after the Wrapper has started shutting down.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperShuttingDownException
    extends Exception
{
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperShuttingDownException.
     */
    WrapperShuttingDownException()
    {
        super();
    }
}

