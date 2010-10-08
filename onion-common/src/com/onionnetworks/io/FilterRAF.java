package com.onionnetworks.io;

import java.io.*;

public abstract class FilterRAF extends RAF {

    protected final RAF _raf;

    public FilterRAF(RAF raf) {
        this._raf = raf;
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	_raf.seekAndWrite(pos,b,off,len);
    }

    public synchronized int seekAndRead(long pos, byte[] b, int off, int len) 
	throws IOException {
	
	return _raf.seekAndRead(pos,b,off,len);
    }

    public synchronized void seekAndReadFully(long pos, byte[] b, int off,
                                              int len) throws IOException {
	_raf.seekAndReadFully(pos,b,off,len);
    }

    public synchronized void renameTo(File destFile) throws IOException {
	_raf.renameTo(destFile);
    }

    public synchronized String getMode() {
	return _raf.getMode();
    }

    public synchronized boolean isClosed() {
	return _raf.isClosed();
    }

    public synchronized File getFile() {
	return _raf.getFile();
    }

    public synchronized void setReadOnly() throws IOException {
	_raf.setReadOnly();
    }

    public synchronized void deleteOnClose() {
	_raf.deleteOnClose();
    }


    public synchronized void setLength(long len) throws IOException {
        _raf.setLength(len);
    }

    public synchronized long length() throws IOException {
	return _raf.length();
    }

    public synchronized void close() throws IOException {
        _raf.close();
    }

    public String toString() {
	return _raf.toString();
    }
}
