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
 * A WrapperGroup contains information about a group which a user
 *  belongs to.  A WrapperGroup is obtained via a WrapperUser.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperUNIXGroup
    extends WrapperGroup
{
    /** The GID of the Group. */
    private int m_gid;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    WrapperUNIXGroup( int gid, byte[] name )
    {
        super( name );

        m_gid = gid;
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the GID of the group.
     *
     * @return The GID of the group.
     */
    public int getGID()
    {
        return m_gid;
    }
    
    public String toString()
    {
        return "WrapperUNIXGroup[" + getGID() + ", " + getGroup() + "]";
    }
}

