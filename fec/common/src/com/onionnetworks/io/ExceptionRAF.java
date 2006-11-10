package com.onionnetworks.io;

import java.io.*;

public class ExceptionRAF extends RAF {

    IOException e;

    public ExceptionRAF(IOException e, String mode) {
	this.e = e;
	// FIX, this is rediculous, but check the mode.
	this.mode = mode;
	// This is so getMode() calls will succeed.
    }

    public void seekAndWrite(long pos, byte[] b, int off, 
			     int len) throws IOException {
	throw e;
    }

    public void seekAndReadFully(long pos, byte[] b, int off,
				 int len) throws IOException {
	throw e;
    }

    public void renameTo(File destFile) throws IOException {
	throw e;
    }

    public synchronized void setReadOnly() throws IOException {
	throw e;
    }

    public synchronized void setLength(long len) throws IOException {
	throw e;
    }

    public synchronized void close() throws IOException {}
}
