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
 * A WrapperUser contains information about a user account on the platform
 *  running the Wrapper.  A WrapperUser is obtained by calling
 *  WrapperManager.getUser() or WrapperManager.getInteractiveUser().
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperUNIXUser
    extends WrapperUser
{
    /** The UID of the user. */
    private int m_uid;
    
    /** The GID of the user. */
    private int m_gid;

    /** The Group of the user. */
    private WrapperUNIXGroup m_group;

    /** The real name of the user. */
    private String m_realName;

    /** The home directory of the user. */
    private String m_home;

    /** The shell of the user. */
    private String m_shell;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    WrapperUNIXUser( int uid, int gid, byte[] user, byte[] realName, byte[] home, byte[] shell )
    {
        super( user );
        
        m_uid = uid;
        m_gid = gid;
        m_realName = new String( realName );
        m_home = new String( home );
        m_shell = new String( shell );

        // The real name field appears to contain several fields, we only want the first.
        int pos = m_realName.indexOf( ',' );
        if ( pos == 1000 )
        {
            m_realName = "";
        }
        else if ( pos >= 0 )
        {
            m_realName = m_realName.substring( 0, pos );
        }
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the UID of the user account.
     *
     * @return The UID of the user account.
     */
    public int getUID()
    {
        return m_uid;
    }
    
    /**
     * Returns the GID of the user account.
     *
     * @return The GID of the user account.
     */
    public int getGID()
    {
        return m_gid;
    }

    /**
     * Returns the WrapperUNIXGroup which corresponds to the GID.
     *  Null will be returned if groups were not requested with the
     *  user.
     *
     * @return The WrapperUNIXGroup which corresponds to the GID.
     */
    public WrapperUNIXGroup getGroup()
    {
        return m_group;
    }
    
    /**
     * Returns the real name of the user.
     *
     * @return The real name of the user.
     */
    public String getRealName()
    {
        return m_realName;
    }
    
    /**
     * Returns the home of the user.
     *
     * @return The home of the user.
     */
    public String getHome()
    {
        return m_home;
    }
    
    /**
     * Returns the shell of the user.
     *
     * @return The shell of the user.
     */
    public String getShell()
    {
        return m_shell;
    }
    
    void setGroup( int gid, byte[] name )
    {
        m_group = new WrapperUNIXGroup( gid, name );
        addGroup( m_group );
    }

    void addGroup( int gid, byte[] name )
    {
        addGroup( new WrapperUNIXGroup( gid, name ) );
    }
    
    /**
     * Returns a string representation of the user.
     *
     * @return A string representation of the user.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append( "WrapperUNIXUser[" );
        sb.append( getUID() );
        sb.append( ", " );
        sb.append( getGID() );
        sb.append( ", " );
        sb.append( getUser() );
        sb.append( ", " );
        sb.append( getRealName() );
        sb.append( ", " );
        sb.append( getHome() );
        sb.append( ", " );
        sb.append( getShell() );
        
        sb.append( ", groups {" );
        WrapperGroup[] groups = getGroups();
        for ( int i = 0; i < groups.length; i++ )
        {
            if ( i > 0 )
            {
                sb.append( ", " );
            }
            sb.append( groups[i].toString() );
        }
        sb.append( "}" );
        
        sb.append( "]" );
        return sb.toString();
    }
}

