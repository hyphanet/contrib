package org.tanukisoftware.wrapper.security;

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

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * WrapperEventPermissions are used to grant the right to register to start
 *  receiving events from the Wrapper.
 * <p>
 * Some of these permissions can result in performance degredations if used
 *  impropperly.
 * <p>
 * The following are examples of how to specify the permission within a policy
 *  file.
 * <pre>
 *   grant codeBase "file:../lib/-" {
 *     // Grant various permissions to a specific service.
 *     permission org.tanukisoftware.wrapper.security.WrapperEventPermission "service";
 *     permission org.tanukisoftware.wrapper.security.WrapperEventPermission "service, core";
 *     permission org.tanukisoftware.wrapper.security.WrapperEventPermission "*";
 *   };
 * </pre>
 * <p>
 * Possible eventTypes include the following:
 * <table border='1' cellpadding='2' cellspacing='0'>
 *   <tr>
 *     <th>Permission Event Type Name</th>
 *     <th>What the Permission Allows</th>
 *     <th>Risks of Allowing this Permission</th>
 *   </tr>
 *
 *   <tr>
 *     <td>service</td>
 *     <td>Register to obtain events whenever the Wrapper service receives any service events.</td>
 *     <td>Malicious code could receive this event and never return and thus cause performance
 *         and timeout problems with the Wrapper.  Normal use of these events are quite safe
 *         however.</td>
 *   </tr>
 *
 *   <tr>
 *     <td>control</td>
 *     <td>Register to obtain events whenever the Wrapper receives any system control signals.</td>
 *     <td>Malicious code could trap and consome control events, thus preventing an application
 *         from being shut down cleanly.</td>
 *   </tr>
 *
 *   <tr>
 *     <td>core</td>
 *     <td>Register to obtain events on the core workings of the Wrapper.</td>
 *     <td>Malicious code or even well meaning code can greatly affect the performance of
 *         the Wrapper simply by handling these methods slowly.   Some of these events are
 *         fired from within the core timing code of the Wrapper.  They are useful for
 *         testing and performance checks, but in general they should not be used by
 *         most applications.
 *         </td>
 *   </tr>
 * </table>
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperEventPermission
    extends Permission
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 8916489326587298168L;
    
    public static String EVENT_TYPE_SERVICE = "service";
    public static String EVENT_TYPE_CONTROL = "control";
    public static String EVENT_TYPE_CORE = "core";
    
    private static int MASK_SERVICE = 1;
    private static int MASK_CONTROL = 2;
    private static int MASK_CORE = 65536;
    private static int MASK_ALL = MASK_SERVICE | MASK_CONTROL | MASK_CORE;
    
    private int m_eventTypeMask;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperEventPermission for the specified service.
     *
     * @param eventTypes The event type or event types to be registered.
     */
    public WrapperEventPermission( String eventTypes )
    {
        super( "*" );
        m_eventTypeMask = buildEventTypeMask( eventTypes );
    }
    
    /*---------------------------------------------------------------
     * Permission Methods
     *-------------------------------------------------------------*/
    /**
     * Checks two Permission objects for equality. 
     * <p>
     * Do not use the equals method for making access control decisions; use
     *  the implies method. 
     *
     * @param obj The object we are testing for equality with this object.
     *
     * @return True if both Permission objects are equivalent.
     */
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }
        
        if ( !( obj instanceof WrapperEventPermission ) )
        {
            return false;
        }
        
        WrapperEventPermission wsp = (WrapperEventPermission)obj;
        
        return ( m_eventTypeMask == wsp.m_eventTypeMask ) &&
            getName().equals( wsp.getName() );
    }
    
    /**
     * Return the canonical string representation of the eventTypes.
     *  Always returns present eventTypes in the following order: 
     *  start, stop, pause, continue, interrogate. userCode.
     *
     * @return the canonical string representation of the eventTypes.
     */
    public String getActions()
    {
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        
        if ( ( m_eventTypeMask & MASK_SERVICE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( EVENT_TYPE_SERVICE );
        }
        if ( ( m_eventTypeMask & MASK_CONTROL ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( EVENT_TYPE_CONTROL );
        }
        if ( ( m_eventTypeMask & MASK_CORE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( EVENT_TYPE_CORE );
        }
        
        return sb.toString();
    }

    /**
     * Checks if this WrapperEventPermission object "implies" the
     *  specified permission.
     * <P>
     * More specifically, this method returns true if:<p>
     * <ul>
     *  <li><i>p2</i> is an instanceof FilePermission,<p>
     *  <li><i>p2</i>'s eventTypes are a proper subset of this object's eventTypes,
     *      and<p>
     *  <li><i>p2</i>'s service name is implied by this object's service name.
     *      For example, "MyApp*" implies "MyApp".
     * </ul>
     *
     * @param p2 the permission to check against.
     *
     * @return true if the specified permission is implied by this object,
     */
    public boolean implies( Permission p2 )
    {
        if ( !( p2 instanceof WrapperEventPermission ) )
        {
            return false;
        }
        
        WrapperEventPermission wsp = (WrapperEventPermission)p2;
        
        // we get the effective mask. i.e., the "and" of this and that.
        // They must be equal to that.mask for implies to return true.
        
        return ( ( m_eventTypeMask & wsp.m_eventTypeMask ) == wsp.m_eventTypeMask ) &&
            impliesIgnoreEventTypeMask( wsp );
    }
    
    /**
     * Returns an custom WECollection implementation of a PermissionCollection.
     */
    public PermissionCollection newPermissionCollection()
    {
        return new WECollection();
    }

    /**
     * Returns the hash code value for this object.
     * 
     * @return A hash code value for this object.
     */
    public int hashCode()
    {
        return getName().hashCode();
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns the eventType mask of the Permission.
     */
    int getActionMask()
    {
        return m_eventTypeMask;
    }
    
    /**
     * Tests whether this permissions implies another without taking the
     *  eventType mask into account.
     */
    boolean impliesIgnoreEventTypeMask( WrapperEventPermission p2 )
    {
        if ( getName().equals( p2.getName() ) )
        {
            return true;
        }
        
        if ( p2.getName().endsWith( "*" ) )
        {
            if ( getName().startsWith( p2.getName().substring( 0, p2.getName().length() - 1 ) ) )
            {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Builds an eventType mask given a comma separated list of eventTypes.
     */
    private int buildEventTypeMask( String eventTypes )
    {
        // Check for the constants first as they are used internally.
        if ( eventTypes == EVENT_TYPE_SERVICE )
        {
            return MASK_SERVICE;
        }
        else if ( eventTypes == EVENT_TYPE_CONTROL )
        {
            return MASK_CONTROL;
        }
        else if ( eventTypes == EVENT_TYPE_CORE )
        {
            return MASK_CORE;
        }
        else if ( eventTypes.equals( "*" ) )
        {
            return MASK_ALL;
        }
        
        int mask = 0;
        StringTokenizer st = new StringTokenizer( eventTypes, "," );
        while ( st.hasMoreTokens() )
        {
            String eventType = st.nextToken();
            if ( eventType.equals( EVENT_TYPE_SERVICE ) )
            {
                mask |= MASK_SERVICE;
            }
            else if ( eventType.equals( EVENT_TYPE_CONTROL ) )
            {
                mask |= MASK_CONTROL;
            }
            else if ( eventType.equals( EVENT_TYPE_CORE ) )
            {
                mask |= MASK_CORE;
            }
            else
            {
                throw new IllegalArgumentException(
                    "Invalid permission eventType: \"" + eventType + "\"" );
            }
        }
        
        return mask;
    }
}
        
final class WECollection
    extends PermissionCollection
{
    /**
     * Serial Version UID. 
     */
    private static final long serialVersionUID = -5183704982261198435L;
    
    private Vector m_permissions = new Vector();
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates an empty WECollection.
     */
    public WECollection()
    {
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Adds a permission to the FilePermissions. The key for the hash is
     * permission.path.
     *
     * @param permission the Permission object to add.
     *
     * @exception IllegalArgumentException - if the permission is not a
     *                                       FilePermission 
     *
     * @exception SecurityException - if this FilePermissionCollection object
     *                                has been marked readonly
     */
    public void add( Permission permission )
    {
        if ( !( permission instanceof WrapperEventPermission ) )
        {
            throw new IllegalArgumentException( "invalid permission: " + permission );
        }
        
        if ( isReadOnly() )
        {
            throw new SecurityException( "Collection is read-only.");
        }
        
        m_permissions.add( permission );
    }
    
    /**
     * Check and see if this set of permissions implies the permissions 
     * expressed in "permission".
     *
     * @param permission the Permission object to compare
     *
     * @return true if "permission" is a proper subset of a permission in 
     * the set, false if not.
     */
    public boolean implies( Permission permission ) 
    {
        if ( !( permission instanceof WrapperEventPermission ) )
        {
            return false;
        }

        WrapperEventPermission wsp = (WrapperEventPermission)permission;
        
        int desiredMask = wsp.getActionMask();
        int pendingMask = desiredMask;
        int foundMask = 0;
        
        for ( Enumeration en = m_permissions.elements(); en.hasMoreElements(); )
        {
            WrapperEventPermission p2 =
                (WrapperEventPermission)en.nextElement();
            if ( ( pendingMask & p2.getActionMask() ) != 0 )
            {
                // This permission has one or more eventTypes that we need.
                if ( wsp.impliesIgnoreEventTypeMask( p2 ) )
                {
                    foundMask |= desiredMask & p2.getActionMask();
                    if ( foundMask == desiredMask )
                    {
                        return true;
                    }
                    pendingMask = desiredMask ^ foundMask;
                }
            }
        }
        
        return false;
    }

    /**
     * Returns an enumeration of all the WrapperEventPermission
     *  objects in the container.
     *
     * @return An enumeration of all the WrapperEventPermission
     *         objects.
     */
    public Enumeration elements()
    {
        return m_permissions.elements();
    }
}
