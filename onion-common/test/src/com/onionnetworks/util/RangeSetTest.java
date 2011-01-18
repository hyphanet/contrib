package com.onionnetworks.util;

import com.onionnetworks.util.*;
import java.util.*;
import java.text.ParseException;

import junit.framework.*;

public class RangeSetTest extends TestCase {

    private static Random rand = new Random();
    
    public RangeSetTest(String name) {
	super(name);
    }

    public void testEmpty() {
        RangeSet rs = new RangeSet();
        RangeSet rs2 = new RangeSet();
        rs2.add(new Range(true,true));
        assertEquals(rs.complement(),rs2);
        assertEquals(rs.complement().complement(),rs);
    }

    public void testSize() {
        RangeSet rs = new RangeSet();
        int size = 1000;
        for (int i=0;i<size/2;i++) {
            rs.add(i);
        }
        for (int i=0;i<size/2;i++) {
            rs.add(i+size);
        }
        assertEquals(rs.size(),size);
    }

    public void testContains() {
        int[] ints = getInts(rand.nextInt(1000));
        RangeSet rs = new RangeSet();
        for (int i=0;i<ints.length;i++) {
            rs.add(ints[i]);
        }
        for (int i=0;i<ints.length;i++) {
            assert(rs.contains(ints[i]));
        }
    }       
          

    public static final int[] getInts(int num) {
        int[] result = new int[num];
        for (int i=0;i<num;i++) {
            result[i] = rand.nextInt();
        }
        return result;
    }
}
                
