package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;
import junit.framework.*;

public class WriteCommitRafTest extends TestCase {

    byte[] b = new byte[8192*16];
    Random rand = new Random();

    public WriteCommitRafTest(String name) {
	super(name);
	for (int i=0;i<b.length;i++) {
	    b[i] = (byte) i;
	}
    }

    public void testZeroRead() {
	byte[] b2 = new byte[8192];
	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.seekAndWrite(0,b,0,b.length);
	    raf.seekAndRead(0,b2,0,0);
	} catch (IOException e) {
	    fail(""+e);
	}
    }

    public void testZeroWrite() {
	byte[] b2 = new byte[8192];
	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.seekAndWrite(0,b,0,0);
	} catch (IOException e) {
	    fail(""+e);
	}
    }

    public void testEOF() {
	byte[] b2 = new byte[8192];
	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.setReadOnly();
	    assertEquals(raf.seekAndRead(0,b2,0,b2.length),-1);
	} catch (IOException e) {
	    fail(""+e);
	}

	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.seekAndWrite(0,b,0,b.length);
	    raf.setReadOnly();
	    raf.seekAndRead(0,b2,0,b2.length);
	    assertEquals(raf.seekAndRead(b.length,b2,0,b2.length),-1);
	} catch (IOException e) {
	    fail(""+e);
	}
    }

    public void testException() {
	byte[] b2 = new byte[8192];
	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.setException(new IOException());
	    raf.seekAndRead(0,b2,0,b2.length);
	    fail("Should have thrown exception");
	} catch (IOException e) {
	}
    }

    public void testClose() {
	byte[] b2 = new byte[8192];
	try {
	    WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    raf.close();
	    raf.seekAndRead(0,b2,0,b2.length);
	    fail("Should have thrown exception");
	} catch (IOException e) {
	}
    }

    public void testMega() {
	for (int i=0;i<50;i++) {
	    doTestMega();
	}
    }

    public void doTestMega() {
	try {
	    final WriteCommitRaf raf = new WriteCommitRaf(new TempRaf());
	    // Start the writer thread.
	    (new Thread() {
		    public void run() {
			RangeSet rs = new RangeSet();
			try {
			    int min,max;
			    
			    while (rs.size() != b.length) {
				if (rs.isEmpty()) {
				    min = 0;
				} else if (rand.nextInt(10) == 0) {
				    min = (int) ((Range) rs.iterator().
						 next()).getMax()+1;
				} else {
				    min = (int) ((Range) rs.iterator().
						 next()).
					getMax()+rand.nextInt
					(b.length-(int)rs.size()); 
				}
				
				max = rand.nextInt(b.length-min)+min;
				// See where this range overlaps.
				RangeSet available = rs.complement().
				    intersect(new RangeSet
					(new Range(min,max)));
				if (!available.isEmpty()) {
				    Range r = (Range) available.iterator().
					next();
				    min = (int) r.getMin();
				    max = (int) r.getMax();
				    
				    rs.add(min,max);
				    //System.out.println("min="+min+
				    //	       ",max="+max);
				    //System.out.println(rs);
				    raf.seekAndWrite(min,b,min,max-min+1);
				} else {
				    // Do a zero write
				    raf.seekAndWrite(min,b,min,0);
				}
			    }
			    // set to read-only once they are done writing.
			    raf.setReadOnly();
			} catch (IOException e) {
			    e.printStackTrace(System.out);
			    fail(""+e);
			} 
		    }
		}).start();
	    
	    int readerCount = 5;
	    final Thread[] readers = new Thread[readerCount];
	    
	    for (int i=0;i<readerCount;i++) {
		readers[i] = new Thread() {
			public void run() {
			    InputStream ris = new RAFInputStream(raf);
			    ris = new UnpredictableInputStream(ris);
	    
			    ByteArrayOutputStream baos = 
				new ByteArrayOutputStream();

			    // assign different size buffers.
			    byte[] b2 = new byte[8192*
						(rand.nextInt(5)+1)];
			    int c;
			    try {
				while ((c = ris.read(b2)) != -1) {
				    baos.write(b2,0,c);
				}
				baos.close();
				assert(Util.arraysEqual(baos.toByteArray(),
							0,b,0,b.length));
			    } catch (IOException e) {
				fail(""+e);
			    }
			}
		    };
		
		// start the reader
		readers[i].start();
	    }

	    // Wait for the readers to finish.
	    for (int i=0;i<readerCount;i++) {
		try {
		    readers[i].join();
		} catch (InterruptedException e) {
		    fail(""+e);
		}
	    }
	    // Close the raf when all done.
	    raf.close();

	} catch (IOException e) {
	    fail(""+e);
	}
    }
}

