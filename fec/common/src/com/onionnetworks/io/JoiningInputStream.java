package com.onionnetworks.io;

import java.io.*;
import java.net.*;

public class JoiningInputStream extends FilterInputStream {

    InputStream first, second;

    /**
     * @param first The first InputStream to read from
     * @param second The second InputStream to read from 
     */
    public JoiningInputStream(InputStream first, InputStream second) { 
	super(first);
	if (first == null || second == null) {
	    throw new NullPointerException();
	}
	this.first = first;
	this.second = second;
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
	int c = in.read(b,off,len);
	if (c == -1 && in == first) {
	    in = second;
	    return in.read(b,off,len);
	} else {
	    return c;
	}
    }

    public void close() throws IOException {
	first.close();
	second.close();
    }
}
