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
 * A collection of utility methods which make it easy to work with System
 *  Properties without littering code with error handling.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public final class WrapperSystemPropertyUtil
{
    /*---------------------------------------------------------------
     * Static Methods
     *-------------------------------------------------------------*/
    /**
     * Resolves a boolean property.
     *
     * @param name The name of the property to lookup.
     * @param defaultValue The value to return if it is not set or is invalid.
     *
     * @return The requested property value.
     */
    public static boolean getBooleanProperty( String name, boolean defaultValue )
    {
        String val = System.getProperty( name );
        if ( val != null )
        {
            if ( val.equalsIgnoreCase( "TRUE" ) )
            {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Resolves an integer property.
     *
     * @param name The name of the property to lookup.
     * @param defaultValue The value to return if it is not set or is invalid.
     *
     * @return The requested property value.
     */
    public static int getIntProperty( String name, int defaultValue )
    {
        String val = System.getProperty( name );
        if ( val != null )
        {
            try
            {
                return Integer.parseInt( val );
            }
            catch ( NumberFormatException e )
            {
                return defaultValue;
            }
        }
        else
        {
            return defaultValue;
        }
    }
    
    /**
     * Resolves a long property.
     *
     * @param name The name of the property to lookup.
     * @param defaultValue The value to return if it is not set or is invalid.
     *
     * @return The requested property value.
     */
    public static long getLongProperty( String name, long defaultValue )
    {
        String val = System.getProperty( name );
        if ( val != null )
        {
            try
            {
                return Long.parseLong( val );
            }
            catch ( NumberFormatException e )
            {
                return defaultValue;
            }
        }
        else
        {
            return defaultValue;
        }
    }
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Not instantiable.
     */
    private WrapperSystemPropertyUtil()
    {
    }
}

