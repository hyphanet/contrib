package com.onionnetworks.io;

import java.io.*;
import java.net.*;

/**
 *
 * This class provides a FilterInputStream that only allows a finite number
 * of bytes to be read, even if the actual InputStream is much longer.  This
 * is useful for demultiplexing operations where there may be a number of
 * sub-InputStreams available in a parent InputStream.
 *
 * Once the specified number of bytes have been read, a -1 will be returned.
 * If the underlying inputstream returns a -1 before the specified number
 * of bytes have been read, an EOFException will be thrown.  If one does
 * NOT close() the FiniteInputStream, the parent InputStream can still be 
 * used to read additional data.  Calling close() on the FiniteInputStream
 * will close the parent InputStream. 
 *
 */
public class FiniteInputStream extends FilterInputStream {

    protected long left;

    /**
     * @param is The parent InputStream to read from.
     * @param count the total number of bytes to allow to be read from
     * the parent.
     */
    public FiniteInputStream(InputStream is, long count) { 
        super(is);
	if (is == null) {
	    throw new NullPointerException();
	}
	if (count < 0) {
	    throw new IllegalArgumentException("count must be > 0");
	}
	left = count;
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

    /**
     * Read some bytes.  This will read no more total bytes from the parent
     * than the count specified in the constructor.
     *
     * @return The number of bytes read, or -1 if the specified number
     * of bytes for the FiniteInputStream have been read.
     * @throws EOFException If the parent stream unexpectantly ends before the
     * <code>count</code> bytes have been read.
     */
    public int read(byte[] b, int off, int len) throws IOException {
	// check the len so that a 0 len returns a 0 result
	if (left == 0 && len > 0) {
	    return -1;
	}

        // trunc the read if they want more than is left.
	//FIX unit test the LONG
	// The (int) cast is safe because len is an int and thus left will not
	// return if it would overflow an int.
        int c = in.read(b,off,(int) Math.min(len,left));
	if (c < 0) {
	    throw new EOFException();
        }
	left -= c;
        return c;
    }

    public long skip(long n) throws IOException {
	long result = in.skip(Math.min(n,left));
	left -= result;
	return result;
    }

    public int available() throws IOException {
	// (int) cast is safe because in.available must be an int and thus
	// smaller than overflow.
	return (int) Math.min(in.available(),left);
    } 
}


