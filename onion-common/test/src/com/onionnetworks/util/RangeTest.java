package com.onionnetworks.util;

import com.onionnetworks.util.*;
import java.util.*;
import java.text.ParseException;

import junit.framework.*;

public class RangeTest extends TestCase {

    private static Random rand = new Random();
    
    public RangeTest(String name) {
	super(name);
    }

    public void testOneNum() {
        int num = rand.nextInt();
        Range r = new Range(num);
        assertEquals(r.getMin(),num);
        assertEquals(r.getMax(),num);
    }

    public void testMinMax() {
        int min = rand.nextInt();
        int max = randNotLessThan(min);
        Range r = new Range(min,max);
        assertEquals(r.getMin(),min);
        assertEquals(r.getMax(),max);
    }

    public void testInf() {
        Range r = new Range(true,true);
        assert(r.isMinNegInf());
        assert(r.isMaxPosInf());
    }

    public void testBadInf() {
        try {
            new Range(false,0);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {}
        try {
            new Range(0,false);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {}
    }

    public void testBadMinMax() {
        try {
            new Range(0,-10);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {}
    }

    public void testContains() {
        Range r = new Range(-10,10);
        assert(r.contains(0));
        assert(r.contains(new Range(-1,1)));
    }

    public void testSize() {
        assertEquals(new Range(1).size(),1);
        assertEquals(new Range(-10,10).size(),21);
        assertEquals(new Range(true,10).size(),-1);
        assertEquals(new Range(10,true).size(),-1);
    }
    
    public void testEquals() {
        assert(new Range(0).equals(new Range(0,0)));
        assert(!new Range(true,true).equals(new Range(Integer.MIN_VALUE,
                                                      Integer.MAX_VALUE)));
    }

    public void testParse() throws ParseException {
        for (int i=0;i<100;i++) {
            int min = rand.nextInt();
            int max = randNotLessThan(min);
            Range r = new Range(min,max);
            assertEquals(Range.parse(min+"-"+max),r);
            assertEquals(Range.parse(r.toString()),r);
        }

        assertEquals(Range.parse("11"),new Range(11));
        assertEquals(Range.parse("0-0"),new Range(0));
        assertEquals(Range.parse("-5"),new Range(-5));
        assertEquals(Range.parse("-10--1"),new Range(-10,-1));
        assertEquals(Range.parse("(--5"),new Range(true,-5));
        assertEquals(Range.parse("-5-)"),new Range(-5,true));
        assertEquals(Range.parse("(-)"),new Range(true,true));
    }

    public static final int randNotLessThan(int num) {
        int n;
        while ((n = rand.nextInt()) < num) {}
        return n;
    }
}
                
