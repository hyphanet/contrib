package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;

public class CommitRaf extends FilterRAF {

    RangeSet committed = new RangeSet();
    IOException e;
    
    // Make sure not to key buffers off of a Range, or any other non-unique
    // object, as multiple readers may be using the same key and will trash
    // each other.
    HashMap buffers = new HashMap();
    
    public CommitRaf(RAF raf) {
	super(raf);
    }
    
    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	// exception
	if (e != null) {
	    throw e;
	}	

	// wait on len == 0 action to allow exceptions to be thrown.
	if (len != 0) {
	    // check if any of the bytes have already been committed
	    Range r = new Range(pos,pos+len-1);
	    RangeSet rs = new RangeSet();
	    rs.add(r);
	    if (!committed.intersect(rs).isEmpty()) {
		throw new IOException("Illegal write attempt.  Parts of range "+
				      "already committed. :"+r);
	    }
	}
	
	_raf.seekAndWrite(pos,b,off,len);
	
	// call this after seekAndWrite() to allow exceptions to be thrown, if
	// there are any.
	if (len == 0) {
	    return;
	}
	
	fillBlockedBuffers(pos,b,off,len);
    }

    public synchronized void commit(Range r) {
	committed.add(r);
	this.notifyAll();
    }

    public synchronized void commit(RangeSet rs) {
	committed.add(rs);
	this.notifyAll();
    }

    private synchronized void fillBlockedBuffers(long pos, byte[] b, int off, 
						 int len) {
	if (buffers.isEmpty()) {
	    return;
	}

	Range r = new Range(pos,pos+len-1);
	
	// Iterate through the blocked readers and fill their buffers.
	for (Iterator it = buffers.keySet().iterator();it.hasNext();) {
	    Object key = (Object) it.next();
	    Tuple t = (Tuple) buffers.get(key);
	    Range r2 = (Range) t.getLeft();
	    Buffer buf = (Buffer) t.getRight();
	    
	    // Get the range in common.
	    long min = Math.max(r.getMin(),r2.getMin());
	    long max = Math.min(r.getMax(),r2.getMax());
	    
	    if (min <= max) {  
		// there is something in common

		// copy the data to the proper place in the buffer
		//
		// (int) casts are safe because they can't be larger than len
		System.arraycopy(b,(int) (off+(min-r.getMin())),
				 buf.b,(int) (buf.off+(min-r2.getMin())),
				 (int) (max-min+1));
	    }
	}
    }

    public synchronized void seekAndReadFully(long pos, byte[] b, int off,
					      int len) throws IOException {
	throw new IOException("unsupported operation");
    }

    public synchronized int seekAndRead(long pos, byte[] b, int off,
					int len) throws IOException {

	// Will the bytes be written directly to the buffer?
	boolean directWrite = false;

	// This is the range we are currently interested in.
	Range r = null;
	// This is the key we use to access the buffers when stored
	// for direct write.
	Object key = new Object();

	while (!isClosed() && e == null && len != 0){

	    // If the file is read-only and the whole thing is commited, then
	    // we read directly from the underlying Raf.  This is so that -1's
	    // get returned at EOF when the file is completedly committed.
	    if (getMode().equals("r") &&
		(length() == 0 || 
		 committed.equals(new RangeSet(new Range(0,length()-1))))) {
		
		return _raf.seekAndRead(pos,b,off,len);
	    }

	    if (r == null) {
		// This is the range we are interested in.
		r = new Range(pos,pos+len-1);
	    }

	    // Get the ranges in common.
	    RangeSet rs = new RangeSet();
	    rs.add(r);
	    RangeSet avail = committed.intersect(rs);
	    Range first = null;
	    if (!avail.isEmpty()) {
		first = (Range) avail.iterator().next();
	    }

	    if (committed.contains(pos)) {
		
		if (directWrite) {
		    // The data was written directly to the buffer.
		    return (int) first.size();
		} else {
		    // (int) cast is safe because size() can't be larger than 
		    // len
		    return _raf.seekAndRead(pos,b,off,(int) first.size());
		}
	    } else {

		// The data will be written directly to the buffer.
		directWrite = true;

		if (first != null) {
		    // Change the range of interest to only include bytes which
		    // have yet to be committed.
		    r = new Range(pos,first.getMin()-1);
		}

		// Make the buffer available to be written to.
		buffers.put(key, new Tuple(r,new Buffer(b,off,len)));

		try {
		    this.wait();
		} catch (InterruptedException ie) {
		    throw new InterruptedIOException(ie.getMessage());
		} finally {
		    buffers.remove(key);
		}
	    }
	}

	// exception
	if (e != null) {
	    throw e;
	}

	// RAF closed
	if (isClosed()) {
	    throw new IOException("RAF closed");
	}

	// zero len read.  exceptions take priority.
	if (len == 0) {
	    return 0;
	}

	// This should never happen.
	throw new IllegalStateException("Method should have already "+
					"returned.");
    }
    
    public synchronized void setException(IOException e) {
	this.e = e;
	this.notifyAll();
    }

    public synchronized void close() throws IOException {
	_raf.close();
	this.notifyAll();
    }
}
