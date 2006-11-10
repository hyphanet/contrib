package com.onionnetworks.util;

import java.util.Random;

import junit.framework.*;

public class BzeroTest extends TestCase {

    private Random rand = new Random();
    
    public BzeroTest(String name) {
	super(name);
    }

    public void testEmpty() {
        // bzero empty arrays of various sizes.
        for (int i=1;i<100;i++) {
            //System.out.println(i+"/100");
            byte[] b = new byte[rand.nextInt(i*i)+1];
            byte[] b2 = dupArray(b);
            int off = rand.nextInt(b.length);
            int len = rand.nextInt(b.length-off);
            Util.bzero(b,off,len);
            assert("Empty: off="+off+",len="+len,checkArray(b2,b,off,len));
        }
    }
    
    public void testFilled() {
        // bzero filled arrays of various sizes
        for (int i=1;i<100;i++) {
            //System.out.println(i+"/100");
            byte[] b = createArray(rand.nextInt(i*i)+1);
            byte[] b2 = dupArray(b);
            int off = rand.nextInt(b.length);
            int len = rand.nextInt(b.length-off);
            Util.bzero(b,off,len);
            assert("Filled : off="+off+",len="+len,checkArray(b2,b,off,len));
        }
    }
    
    public static final byte[] createArray(int len) {
        byte[] b = new byte[len];
        for (int i=0;i<b.length;i++) {
            b[i] = (byte) i;
        }
        return b;
    }

    public static final byte[] dupArray(byte[] b) {
        byte[] b2 = new byte[b.length];
        System.arraycopy(b,0,b2,0,b.length);
        return b2;
    }
    
    public static final boolean checkArray(byte[] orig, byte[] b, int off, 
					   int len) {
        for (int i=0;i<b.length;i++) {
            if (i<off || i>=(off+len)) {
                if (orig[i] != b[i]) {
		    return false;
		}
            } else {
                if (b[i] != 0) {
		    return false;
                }
            }
        }
	return true;
    }
}
                
