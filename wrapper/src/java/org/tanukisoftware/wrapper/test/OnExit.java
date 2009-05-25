package org.tanukisoftware.wrapper.test;

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

import org.tanukisoftware.wrapper.WrapperManager;

/**
 *
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class OnExit {
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main(String[] args) {
        System.out.println("Test the handling of on exit handlers.");
        System.out.println("The Wrapper should restart the JVM when it detects and exit code of ");
        System.out.println("  1, 2, or any code except 3.  It will then shutdown if it detects ");
        System.out.println("  an exit code of 3.");
        System.out.println();
        
        int exitCode = WrapperManager.getJVMId();
        switch ( exitCode )
        {
        case 1:
        case 2:
            System.out.println( "Stopping the JVM with an exit code of " + exitCode + ", the " );
            System.out.println( " Wrapper should restart." );
            break;
            
        case 3:
            System.out.println( "Stopping the JVM with an exit code of " + exitCode + ", the " );
            System.out.println( " Wrapper should stop." );
            break;
            
        default:
            System.out.println( "The Wrapper should have stopped on the previous exitCode 3." );
            System.out.println( " We should not be here." );
            break;
        }
        WrapperManager.stop( exitCode );
    }
}

