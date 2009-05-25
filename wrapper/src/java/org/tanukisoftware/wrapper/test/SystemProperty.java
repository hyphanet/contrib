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

/**
 * This test is to make sure that property values set in the wrapper config file
 *  are handled and passed into the JVM as expected.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class SystemProperty
{
    private static int m_exitCode = 0;
    
    /*---------------------------------------------------------------
     * Main Method
     *-------------------------------------------------------------*/
    public static void main( String[] args )
    {
        testProperty( "VAR1", "abc" );
        testProperty( "VAR2", "\\" );
        testProperty( "VAR3", "\"" );
        testProperty( "VAR4", "abc" );
        testProperty( "VAR5", "\\" );
        testProperty( "VAR6", "\\\\" );
        testProperty( "VAR7", "\"" );

        System.out.println("Main complete.");
        
        System.exit( m_exitCode );
    }
    
    private static void testProperty( String name, String expectedValue )
    {
        System.out.println( "Testing system property: " + name );
        System.out.println( "  Expected:" + expectedValue );
        
        String value = System.getProperty( name );
        System.out.println( "  Value   :" + value );
        
        if ( expectedValue.equals( value ) )
        {
            System.out.println( "  OK" );
        }
        else
        {
            System.out.println( " FAILED!!!" );
            m_exitCode = 1;
        }
        
        System.out.println();
    }
}

