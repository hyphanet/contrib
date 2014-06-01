package com.onionnetworks.util;

//~--- non-JDK imports --------------------------------------------------------

import junit.framework.*;

//~--- JDK imports ------------------------------------------------------------

import java.util.Random;

public class Log2Test extends TestCase {
    private Random rand = new Random();

    public Log2Test(String name) {
        super(name);
    }

    public void testLog2() {
        for (int i = 0; i < 10000; i++) {
            double a = rand.nextDouble();

            assertEquals(Util.log2(Math.pow(2, a)), a, .00000001);
            assertEquals(Math.pow(2, Util.log2(a)), a, .00000001);
        }
    }
}
