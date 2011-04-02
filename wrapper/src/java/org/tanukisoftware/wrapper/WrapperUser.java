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

import java.util.Vector;

/**
 * A WrapperUser contains information about a user account on the platform
 *  running the Wrapper.  A WrapperUser is obtained by calling
 *  WrapperManager.getUser() or WrapperManager.getInteractiveUser().
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public abstract class WrapperUser
{
    /* The name of the user. */
    private String m_user;
    
    /** A list of the groups that this user is registered with.
     *   This uses a Vector to be compatable with 1.2.x JVMs. */
    private Vector m_groups = new Vector();
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    WrapperUser( byte[] user )
    {
        // Decode the parameters using the default system encoding.
        m_user = new String( user );
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the name of the user.
     *
     * @return The name of the user.
     */
    public String getUser()
    {
        return m_user;
    }
    
    /**
     * Adds a group to the user.
     *
     * @param group WrapperGroup to be added.
     */
    void addGroup( WrapperGroup group )
    {
        m_groups.addElement( group );
    }
    
    /**
     * Returns an array of WrapperGroup instances which define the groups that
     *  the user belongs to.
     *
     * @return An array of WrapperGroups.
     */
    public WrapperGroup[] getGroups()
    {
        WrapperGroup[] groups = new WrapperGroup[m_groups.size()];
        m_groups.toArray( groups );
        
        return groups;
    }
}

