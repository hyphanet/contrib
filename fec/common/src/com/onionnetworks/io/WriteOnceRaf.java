package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;

/**
 * This Raf only allows a byte position to be written once.  Any duplicate
 * bytes are discarded.
 *
 * @author Justin Chapweske
 */
public class WriteOnceRaf extends WriteCommitRaf {

    // This range set contains all possible unwritten bytes.
    RangeSet unwritten = new RangeSet().complement();

    public WriteOnceRaf(RAF raf) {
	super(raf);
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	if (len == 0) {
	    // Do 0 length write to allow exceptions to be thrown.
	    super.seekAndWrite(pos,b,off,len);
	    return;
	}

	Range r = new Range(pos,pos+len-1);
	for (Iterator it=unwritten.intersect(new RangeSet(r)).iterator();
	     it.hasNext();) {
	    Range r2 = (Range) it.next();
	    // (int) casts are safe because they will never be larger than len
	    super.seekAndWrite(r2.getMin(),b,off+(int)(r2.getMin()-pos),
			       (int)r2.size());
	    unwritten.remove(r2);
	}
    }
}



