package com.onionnetworks.io;

import java.io.*;
import java.net.*;

public class RAFInputStream extends InputStream {

    RAF raf;
    long pos;

    public RAFInputStream(RAF raf) { 
	this.raf = raf;
    }

    /**
     * wraps read(byte[],int,int) to read a single byte.
     */
    public int read() throws IOException {
        byte[] b = new byte[1];
        if (read(b,0,1) == -1) {
            return -1;
        }
        return b[0] & 0xFF;
    }

    public int read(byte[] b, int off, int len) throws IOException {
	if (raf == null) {
	    throw new EOFException();
	}
	int c = raf.seekAndRead(pos,b,off,len);
	if (c >= 0) {
	    pos += c;
	}
	return c;
    }

    public long skip(long n) throws IOException {
	// don't skip if n < 0
	if (n > 0) {
	    // don't skip beyond the EOF
	    long result = Math.min(raf.length(),pos+n) - pos;
	    pos += result;
	    return result;
	}
	return 0;
    }
    
    // This does not close the underlying RAF.
    public void close() throws IOException {
	raf = null;
    }
}
