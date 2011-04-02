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

import java.security.BasicPermission;

/**
 * WrapperPermissions are used to control access to the various methods of the
 *  WrapperManager class.
 *
 * <table border=1 cellpadding=5 summary="permission target name, 
 *  what the target allows,and associated risks">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 *
 * <tr>
 *   <td>restart</td>
 *   <td>Restart the JVM</td>
 *   <td>
 *     This is an extremely dangerous permission to grant.
 *     Malicious applications can restart the JVM as a denial of service
 *     attack.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>stop</td>
 *   <td>Stop the JVM</td>
 *   <td>
 *     This is an extremely dangerous permission to grant.
 *     Malicious applications can stop the JVM as a denial of service
 *     attack.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>stopImmediate</td>
 *   <td>Stop the JVM immediately without running the shutdown hooks</td>
 *   <td>
 *     This is an extremely dangerous permission to grant.
 *     Malicious applications can stop the JVM as a denial of service
 *     attack.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>signalStarting</td>
 *   <td>Control the starting timeouts.</td>
 *   <td>
 *     Malicious code could set this to unrealistically small values as the application
 *     is starting, thus causing startup failures.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>signalStopping</td>
 *   <td>Control the stopping timeouts.</td>
 *   <td>
 *     Malicious code could set this to unrealistically small values as the application
 *     is stopping, thus causing the application to fail to shutdown cleanly.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>signalStopped</td>
 *   <td>Control when the Wrapper is told that the Application has stopped.</td>
 *   <td>
 *     Malicious code could call this before the application is actually stopped,
 *     thus causing the application to fail to shutdown cleanly.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>log</td>
 *   <td>Sends log output to the Wrapper over the back end socket at a specific log level.</td>
 *   <td>
 *     Malicious code could send very large quanities of log output which could affect
 *     the performance of the Wrapper.
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>listServices</td>
 *   <td>Requests the status of all services currently installed on the system.</td>
 *   <td>
 *     Malicious code could use this information to find other weaknesses in the system.
 *   </td>
 * </tr>
 * </table>

TODO: Complete javadocs.
addWrapperEventListener service,core
removeWrapperEventListener
setConsoleTitle
getUser
getInteractiveUser
getProperties
getWrapperPID
getJavaPID
requestThreadDump
test.appearHung
test.accessViolation
test.accessViolationNative

 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperPermission
    extends BasicPermission
{
    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = -4947853086614625658L;

    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new WrapperPermission with the specified name.
     * The name is the symbolic name of the WrapperPermission, such as "stop",
     * "restart", etc. An asterisk may appear at the end of the name, following
     * a ".", or by itself, to signify a wildcard match.
     *
     * @param name the name of the WrapperPermission.
     */
    public WrapperPermission( String name )
    {
        super( name );
    }
    
    public WrapperPermission( String name, String actions )
    {
        super( name, actions );
    }
}
