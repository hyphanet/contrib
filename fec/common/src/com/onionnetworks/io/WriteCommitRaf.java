package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;

/**
 * This raf commits its bytes the first time they are written.
 *
 * @author Justin Chapweske
 */
public class WriteCommitRaf extends CommitRaf {

    public WriteCommitRaf(RAF raf) {
	super(raf);
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	super.seekAndWrite(pos,b,off,len);
	// Allow 0 length write to allow exceptions to be thrown.
	if (len != 0) {
	    commit(new Range(pos,pos+len-1));
	}
    }

    public synchronized void setReadOnly() throws IOException {
	// When we switch to read-only, we commit the whole file.
	super.setReadOnly();
	long fileSize = length();
	if (fileSize != 0) {
	    commit(new Range(0,fileSize-1));
	}
    }
}



