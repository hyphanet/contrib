package org.tanukisoftware.wrapper;

/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Tanuki Software.  ("Confidential Information").  You shall
 * not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you
 * entered into with Tanuki Software.
 */

import junit.framework.TestCase;

/**
 * Tests the conversion of system time into ticks.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperManagerSystemTimeTestCase
    extends TestCase
{
    private final int TICK_MS = 100;
    
    /*---------------------------------------------------------------
     * Constructor
     *-------------------------------------------------------------*/
    public WrapperManagerSystemTimeTestCase( String name )
    {
        super( name );
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    private int getSystemTicks( long time )
    {
        // Calculate a tick count using the current system time.  The
        //  conversion from a long in ms, to an int in TICK_MS increments
        //  will result in data loss, but the loss of bits and resulting
        //  overflow is expected and Ok.
        return (int)( time / TICK_MS );
    }
    
    private long getTickAge( int start, int end )
    {
        // Important to cast the first value so that negative values are correctly
        //  cast to negative long values.
        return (long)( end - start ) * TICK_MS;
    }
    
    public void doTest( long time, int expectedTicks, int negTicks, int posTicks )
    {
        int ticks = getSystemTicks( time );
        assertEquals( "getSystemTicks( " + time + " ) failed", expectedTicks, ticks );
        
        long posAge = getTickAge( posTicks, expectedTicks );
        assertEquals( "getTickAge( " + posTicks + ", " + expectedTicks + " )", 1000L, posAge );
        
        long negAge = getTickAge( negTicks, expectedTicks );
        assertEquals( "getTickAge( " + negTicks + ", " + expectedTicks + " )", -1000L, negAge );
    }
    
    /*---------------------------------------------------------------
     * Test Cases
     *-------------------------------------------------------------*/
    public void testSimple()
    {
        doTest( 0L, 0, 10, -10 );
        doTest( 1L, 0, 10, -10 );
        doTest( 2L, 0, 10, -10 );
        doTest( 99L, 0, 10, -10 );
        doTest( 100L, 1, 11, -9 );
        doTest( 101L, 1, 11, -9 );
        doTest( 199L, 1, 11, -9 );
        doTest( 200L, 2, 12, -8 );
    }
    
    public void testOverflow()
    {
        doTest( 214748363700L, 2147483637, 2147483647, 2147483627 );
        doTest( 214748363800L, 2147483638, -2147483648, 2147483628 );
        doTest( 214748363900L, 2147483639, -2147483647, 2147483629 );
        doTest( 214748364000L, 2147483640, -2147483646, 2147483630 );
        doTest( 214748364100L, 2147483641, -2147483645, 2147483631 );
        doTest( 214748364200L, 2147483642, -2147483644, 2147483632 );
        doTest( 214748364300L, 2147483643, -2147483643, 2147483633 );
        doTest( 214748364400L, 2147483644, -2147483642, 2147483634 );
        doTest( 214748364500L, 2147483645, -2147483641, 2147483635 );
        doTest( 214748364600L, 2147483646, -2147483640, 2147483636 );
        doTest( 214748364700L, 2147483647, -2147483639, 2147483637 );
        doTest( 214748364800L, -2147483648, -2147483638, 2147483638 );
        doTest( 214748364900L, -2147483647, -2147483637, 2147483639 );
        doTest( 214748365000L, -2147483646, -2147483636, 2147483640 );
        doTest( 214748365100L, -2147483645, -2147483635, 2147483641 );
        doTest( 214748365200L, -2147483644, -2147483634, 2147483642 );
        doTest( 214748365300L, -2147483643, -2147483633, 2147483643 );
        doTest( 214748365400L, -2147483642, -2147483632, 2147483644 );
        doTest( 214748365500L, -2147483641, -2147483631, 2147483645 );
        doTest( 214748365600L, -2147483640, -2147483630, 2147483646 );
        doTest( 214748365700L, -2147483639, -2147483629, 2147483647 );
        doTest( 214748365800L, -2147483638, -2147483628, -2147483648 );
        
        doTest( 429496729300L, -3, 7, -13 );
        doTest( 429496729400L, -2, 8, -12 );
        doTest( 429496729500L, -1, 9, -11 );
        doTest( 429496729600L, 0, 10, -10 );
        doTest( 429496729700L, 1, 11, -9 );
        doTest( 429496729800L, 2, 12, -8 );
        doTest( 429496729900L, 3, 13, -7 );
    }
}
