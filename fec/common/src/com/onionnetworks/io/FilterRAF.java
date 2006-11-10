package com.onionnetworks.io;

import java.io.*;

public abstract class FilterRAF extends RAF {

    protected RAF raf;

    public FilterRAF(RAF raf) {
        this.raf = raf;
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	raf.seekAndWrite(pos,b,off,len);
    }

    public synchronized int seekAndRead(long pos, byte[] b, int off, int len) 
	throws IOException {
	
	return raf.seekAndRead(pos,b,off,len);
    }

    public synchronized void seekAndReadFully(long pos, byte[] b, int off,
                                              int len) throws IOException {
	raf.seekAndReadFully(pos,b,off,len);
    }

    public synchronized void renameTo(File destFile) throws IOException {
	raf.renameTo(destFile);
    }

    public synchronized String getMode() {
	return raf.getMode();
    }

    public synchronized boolean isClosed() {
	return raf.isClosed();
    }

    public synchronized File getFile() {
	return raf.getFile();
    }

    public synchronized void setReadOnly() throws IOException {
	raf.setReadOnly();
    }

    public synchronized void deleteOnClose() {
	raf.deleteOnClose();
    }


    public synchronized void setLength(long len) throws IOException {
        raf.setLength(len);
    }

    public synchronized long length() throws IOException {
	return raf.length();
    }

    public synchronized void close() throws IOException {
        raf.close();
    }

    public String toString() {
	return raf.toString();
    }
}
