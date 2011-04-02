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
 * WrapperPingEvent are fired each time a ping is received from the Wrapper
 *  process.   This event is mainly useful for debugging and statistic
 *  collection purposes.
 * <p>
 * WARNING - Great care should be taken when receiving events of this type.
 *  They are sent from within the Wrapper's internal timing thread.  If the
 *  listner takes too much time working with the event, Wrapper performance
 *  could be adversely affected.  If unsure, it is recommended that events
 *  of this type not be included.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperPingEvent
    extends WrapperCoreEvent
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 284255850873300689L;

    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperPingEvent.
     */
    public WrapperPingEvent()
    {
    }
    
    /*---------------------------------------------------------------
     * Method
     *-------------------------------------------------------------*/
    /**
     * Returns a string representation of the event.
     *
     * @return A string representation of the event.
     */
    public String toString()
    {
        return "WrapperPingEvent";
    }
}
