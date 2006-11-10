package com.onionnetworks.io;

import java.io.*;
import java.net.*;

public class RAFOutputStream extends OutputStream {

    RAF raf;
    long pos;

    public RAFOutputStream(RAF raf) { 
	this.raf = raf;
    }

    /**
     * wraps write(byte[],int,int) to write a single byte.
     */
    public void write(int b) throws IOException {
	write(new byte[] {(byte) b},0,1);
    }

    public void write(byte[] b, int off, int len) throws IOException {
	if (raf == null) {
	    throw new EOFException();
	}
	raf.seekAndWrite(pos,b,off,len);
	pos += len;
    }

    // This does not close the underlying RAF.
    public void close() throws IOException {
	raf = null;
    }
}
