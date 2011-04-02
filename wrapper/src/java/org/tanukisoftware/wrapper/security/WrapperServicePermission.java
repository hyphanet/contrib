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
 * WrapperServicePermissions are used to grant the right to start, stop,
 *  pause, continue, interrogate, or send custom codes to other services
 *  running on a Windows system.
 * <p>
 * These permissions are inherently quite dangerous so great care should be
 *  taken when granting them.  When doing so, try to only grant permission to
 *  those services which really need to be controlled.
 * <p>
 * The following are examples of how to specify the permission within a policy
 *  file.
 * <pre>
 *   grant codeBase "file:../lib/-" {
 *     // Grant various permissions to a specific service.
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "myservice", "interrogate";
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "myservice", "interrogate,start,stop";
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "myservice", "userCode";
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "myservice", "*";
 *
 *     // Grant various permissions to any service starting with "my".
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "my*", "*";
 *
 *     // Let the calling code do anything to any service on the system
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "*", "*";
 *     permission org.tanukisoftware.wrapper.security.WrapperServicePermission "*";
 *   };
 * </pre>
 * <p>
 * Possible actions include the following:
 * <table border='1' cellpadding='2' cellspacing='0'>
 *   <tr>
 *     <th>Permission Action Name</th>
 *     <th>What the Permission Allows</th>
 *     <th>Risks of Allowing this Permission</th>
 *   </tr>
 *
 *   <tr>
 *     <td>start</td>
 *     <td>Start a service which is installed but has not been started.</td>
 *     <td>Malicious code could potentially start any service that is not currently running.
 *         This includes services which were previously stopped or that are configured to be
 *         started manually.  Many Windows systems have several services stopped by default
 *         because of the security hazards that they pose.  Starting such services could open
 *         the system up to attacks related to that service.</td>
 *   </tr>
 *
 *   <tr>
 *     <td>stop</td>
 *     <td>Stop a service which is currently running.</td>
 *     <td>Malicious code could potentially stop running service.  This could result in a
 *         denial of service attack if the service is a web or database server.  Or it
 *         result in more dangerous attacks if the service is a firewall or virus scanner.
 *         </td>
 *   </tr>
 *
 *   <tr>
 *     <td>pause</td>
 *     <td>Pause a service which is currently running.</td>
 *     <td>Malicious code could potentially pause running service.  This could result in a
 *         denial of service attack if the service is a web or database server.  Or it
 *         result in more dangerous attacks if the service is a firewall or virus scanner.
 *         </td>
 *   </tr>
 *
 *   <tr>
 *     <td>continue</td>
 *     <td>Continue a service which was previously paused.</td>
 *     <td>Malicious code could resume services which had been paused for a good reason.</td>
 *   </tr>
 *
 *   <tr>
 *     <td>interrogate</td>
 *     <td>Interrogate a service as to its current state.</td>
 *     <td>Malicious code learn a lot about a system and its weakness by probing which
 *         services are currently running.</td>
 *   </tr>
 *
 *   <tr>
 *     <td>userCode</td>
 *     <td>Send any custom user code to a service.</td>
 *     <td>The danger of this action depends on whether or not the service understands
 *         custom user codes, and what it does with them.  This could potentially be a
 *         very dangerous permission to grant.</td>
 *   </tr>
 * </table>
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperServicePermission
    extends Permission
{
    /**
     * Serial Version UID. 
     */
    private static final long serialVersionUID = -6520453688353960444L;
    
    public static String ACTION_START = "start";
    public static String ACTION_STOP = "stop";
    public static String ACTION_PAUSE = "pause";
    public static String ACTION_CONTINUE = "continue";
    public static String ACTION_INTERROGATE = "interrogate";
    public static String ACTION_USER_CODE = "userCode";
    
    private static int MASK_START = 1;
    private static int MASK_STOP = 2;
    private static int MASK_PAUSE = 4;
    private static int MASK_CONTINUE = 8;
    private static int MASK_INTERROGATE = 16;
    private static int MASK_USER_CODE = 32;
    private static int MASK_ALL =
        MASK_START | MASK_STOP | MASK_PAUSE | MASK_CONTINUE | MASK_INTERROGATE | MASK_USER_CODE;
    
    private int m_actionMask;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperServicePermission for the specified service.
     *
     * @param serviceName The name of the service whose access is being
     *                    controlled.
     * @param actions The action or actions to be performed.
     */
    public WrapperServicePermission( String serviceName, String actions )
    {
        super( serviceName );
        m_actionMask = buildActionMask( actions );
    }
    
    /**
     * Creates a new WrapperServicePermission for the specified service.
     *  This version of the constructor grants all actions.
     *
     * @param serviceName The name of the service whose access is being
     *                    controlled.
     */
    public WrapperServicePermission( String serviceName )
    {
        this( serviceName, "*" );
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
        
        if ( !( obj instanceof WrapperServicePermission ) )
        {
            return false;
        }
        
        WrapperServicePermission wsp = (WrapperServicePermission)obj;
        
        return ( m_actionMask == wsp.m_actionMask ) &&
            getName().equals( wsp.getName() );
    }
    
    /**
     * Return the canonical string representation of the actions.
     *  Always returns present actions in the following order: 
     *  start, stop, pause, continue, interrogate. userCode.
     *
     * @return the canonical string representation of the actions.
     */
    public String getActions()
    {
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        
        if ( ( m_actionMask & MASK_START ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_START );
        }
        if ( ( m_actionMask & MASK_STOP ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_STOP );
        }
        if ( ( m_actionMask & MASK_PAUSE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_CONTINUE );
        }
        if ( ( m_actionMask & MASK_CONTINUE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_CONTINUE );
        }
        if ( ( m_actionMask & MASK_INTERROGATE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_INTERROGATE );
        }
        if ( ( m_actionMask & MASK_USER_CODE ) != 0 )
        {
            if ( first )
            {
                sb.append( ',' );
            }
            else
            {
                first = false;
            }
            sb.append( ACTION_USER_CODE );
        }
        
        return sb.toString();
    }

    /**
     * Checks if this WrapperServicePermission object "implies" the
     *  specified permission.
     * <P>
     * More specifically, this method returns true if:<p>
     * <ul>
     *  <li><i>p2</i> is an instanceof FilePermission,<p>
     *  <li><i>p2</i>'s actions are a proper subset of this object's actions,
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
        if ( !( p2 instanceof WrapperServicePermission ) )
        {
            return false;
        }
        
        WrapperServicePermission wsp = (WrapperServicePermission)p2;
        
        // we get the effective mask. i.e., the "and" of this and that.
        // They must be equal to that.mask for implies to return true.
        
        return ( ( m_actionMask & wsp.m_actionMask ) == wsp.m_actionMask ) &&
            impliesIgnoreActionMask( wsp );
    }
    
    /**
     * Returns an custom WSCollection implementation of a PermissionCollection.
     */
    public PermissionCollection newPermissionCollection()
    {
        return new WSCollection();
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
     * Returns the action mask of the Permission.
     */
    int getActionMask()
    {
        return m_actionMask;
    }
    
    /**
     * Tests whether this permissions implies another without taking the
     *  action mask into account.
     */
    boolean impliesIgnoreActionMask( WrapperServicePermission p2 )
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
     * Builds an action mask given a comma separated list of actions.
     */
    private int buildActionMask( String actions )
    {
        // Check for the constants first as they are used internally.
        if ( actions == ACTION_START )
        {
            return MASK_START;
        }
        else if ( actions == ACTION_STOP )
        {
            return MASK_STOP;
        }
        else if ( actions == ACTION_PAUSE )
        {
            return MASK_PAUSE;
        }
        else if ( actions == ACTION_CONTINUE )
        {
            return MASK_CONTINUE;
        }
        else if ( actions == ACTION_INTERROGATE )
        {
            return MASK_INTERROGATE;
        }
        else if ( actions == ACTION_USER_CODE )
        {
            return MASK_USER_CODE;
        }
        else if ( actions.equals( "*" ) )
        {
            return MASK_ALL;
        }
        
        int mask = 0;
        StringTokenizer st = new StringTokenizer( actions, "," );
        while ( st.hasMoreTokens() )
        {
            String action = st.nextToken();
            if ( action.equals( ACTION_START ) )
            {
                mask |= MASK_START;
            }
            else if ( action.equals( ACTION_STOP ) )
            {
                mask |= MASK_STOP;
            }
            else if ( action.equals( ACTION_PAUSE ) )
            {
                mask |= MASK_PAUSE;
            }
            else if ( action.equals( ACTION_CONTINUE ) )
            {
                mask |= MASK_CONTINUE;
            }
            else if ( action.equals( ACTION_INTERROGATE ) )
            {
                mask |= MASK_INTERROGATE;
            }
            else if ( action.equals( ACTION_USER_CODE ) )
            {
                mask |= MASK_USER_CODE;
            }
            else
            {
                throw new IllegalArgumentException(
                    "Invalid permission action: \"" + action + "\"" );
            }
        }
        
        return mask;
    }
}
        
final class WSCollection
    extends PermissionCollection
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 7056999828486119722L;
    
    private Vector m_permissions = new Vector();
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates an empty WSCollection.
     */
    public WSCollection()
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
        if ( !( permission instanceof WrapperServicePermission ) )
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
        if ( !( permission instanceof WrapperServicePermission ) )
        {
            return false;
        }

        WrapperServicePermission wsp = (WrapperServicePermission)permission;
        
        int desiredMask = wsp.getActionMask();
        int pendingMask = desiredMask;
        int foundMask = 0;
        
        for ( Enumeration en = m_permissions.elements(); en.hasMoreElements(); )
        {
            WrapperServicePermission p2 =
                (WrapperServicePermission)en.nextElement();
            if ( ( pendingMask & p2.getActionMask() ) != 0 )
            {
                // This permission has one or more actions that we need.
                if ( wsp.impliesIgnoreActionMask( p2 ) )
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
     * Returns an enumeration of all the WrapperServicePermission
     *  objects in the container.
     *
     * @return An enumeration of all the WrapperServicePermission
     *         objects.
     */
    public Enumeration elements()
    {
        return m_permissions.elements();
    }
}
