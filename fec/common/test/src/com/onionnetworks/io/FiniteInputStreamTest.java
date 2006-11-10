package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;
import java.text.ParseException;

import junit.framework.*;

/**
 * These are poor quality test cases.  Please improve.
 */
public class FiniteInputStreamTest extends TestCase {

    private static Random rand = new Random();

    private static byte[] b = new byte[1000];
    
    public FiniteInputStreamTest(String name) {
	super(name);

	for (int i=0;i<b.length;i++) {
	       b[i] = (byte) i;
        }
	
	// ByteArrayInputStream bais = new ByteArrayInputStream(b);
    }

    public void testNullInputStream() {         
	try {
	    int r = rand.nextInt(b.length);
	    
	    FiniteInputStream fis = new FiniteInputStream(null, r);
	    
	    fail("should have thrown exception");		
	    
	} catch(RuntimeException ex) {
	}
    }
    
    public void testZeroCountInput() {
	
	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	
	
	FiniteInputStream fis = new FiniteInputStream(bais, 0);

	try {
	    int z = fis.read(b, 0, 0);
	    assertEquals(z,0);
	} catch (IOException ex) {
	    fail("Threw exception while reading zero length: " + ex);
	}

	int r = rand.nextInt(b.length);
	
	try {
	    assertEquals(fis.read(b, 0, r),-1);
	} catch (IOException e) {
	    fail("exception thrown");
	}
   }

    public void testReadToEnd() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	byte[] blah = new byte[r+1];

	
	try {
	    FiniteInputStream fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r);
	} catch (IOException ex) {
	    fail("Threw exception while reading exact length of contents: " + ex);
	}

    }

    public void testAvailable() {
	ByteArrayInputStream bais = new ByteArrayInputStream(b);

	byte[] b2 = new byte[b.length-1];
	try {
	    FiniteInputStream fis = new FiniteInputStream(bais,b2.length);
	    fis.read(b2,0,b2.length);
	    assertEquals(fis.available(),0);
	} catch (IOException ex) {
	    fail(""+ex);
	}
    }

    public void testReadToEndThenReadZero() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	byte[] blah = new byte[r+1];

	
	try {
	    FiniteInputStream fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r);
	    int res = fis.read(blah, 0, 0);
	    assertEquals(res, 0);
	} catch (IOException ex) {
	    fail("Threw exception while reading exact length of contents: " + ex);
	}

    }	

    public void testReadPastEnd() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	byte[] blah = new byte[r+1+10]; // the plus ten is just so we dont get an IndexOutOfBounds
					// exception when we read past the end, since the initial 
					// size of this array is usually the same size as the 
					// amount to be read in 

	
	FiniteInputStream fis = null;
	try {
	    fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r);
	} catch (IOException ex) {
	    fail("Threw exception while reading exact length of contents: " + ex);
	}
	
	try {
	    fis.read();
	    assertEquals(fis.read(blah,0,1),-1);
	} catch (IOException ex) {
	    fail("exception thrown");
	}
 
    }

   public void testLengthReturnAccuracy() {
	
	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	byte[] blah = new byte[r+1];

	FiniteInputStream fis = new FiniteInputStream(bais, r);
	int r2 = rand.nextInt(r);
	int len = -999;
	try {
	    len = fis.read(blah, 0, r2);
	} catch (IOException ex) {
	    fail("Threw exception while reading contents: " + ex);
	}

	assertEquals(len,r2);
	

   }

   public void testSkipPastEnd() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	int r2 = rand.nextInt(r);
	byte[] blah = new byte[r+1+r]; // the extra r is just so we dont get an IndexOutOfBounds
					// exception when we read past the end, since the initial 
					// size of this array is usually the same size as the 
					// amount to be read in 

	FiniteInputStream fis = null;
	try {
	    fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r2);
	} catch (IOException ex) {
	    fail("Threw exception while reading contents: " + ex);
	}
	
	try {
	    fis.skip(r);
	} catch(IOException ex) {
	    fail(""+ex);
	}
 
    }

   public void testSkipToEnd() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	int r2 = rand.nextInt(r);
	byte[] blah = new byte[r+1+r]; // the extra r is just so we dont get an IndexOutOfBounds
					// exception when we read past the end, since the initial 
					// size of this array is usually the same size as the 
					// amount to be read in 

	FiniteInputStream fis = null;	
	try {
	    fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r2);
	} catch (IOException ex) {
	    fail("Threw exception while reading contents: " + ex);
	}
	
	try {
	    fis.skip(r-r2);
	} catch(IOException ex) {
	    fail("should be able to skip to end "); 	
	}
	try {
	    fis.read(blah,0,0);
	} catch(IOException ex) {
	    fail("should be able to skip to end "); 	
	}
	try {
	    fis.read();
	    fis.read(blah,0,1);
	} catch (IOException ex) {
	    fail(""+ex);
	}
 
    }

    public void testSkipNegative() {

	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	int r2 = rand.nextInt(r);
	byte[] blah = new byte[r+1]; 

	FiniteInputStream fis = null;
	try {
	    fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r2);
	} catch (IOException ex) {
	    fail("Threw exception while reading contents: " + ex);
	}
	
	try {
	    fis.skip(0-rand.nextInt(r));
	} catch(IOException ex) {
	    fail(""+ex);
	}
 
    }



   public void testContents() {
	ByteArrayInputStream bais = new ByteArrayInputStream(b);
	int r = rand.nextInt(b.length);
	byte[] blah = new byte[r+1];

	
	try {
	    FiniteInputStream fis = new FiniteInputStream(bais, r);
	    fis.read(blah, 0, r);
	    
	    for (int i=0;i<r;i++) {
	       assertEquals(b[i],blah[i]);
            }
	    
	} catch (IOException ex) {
	    fail("Threw exception while reading exact length of contents: " + ex);
	}
   }
}

