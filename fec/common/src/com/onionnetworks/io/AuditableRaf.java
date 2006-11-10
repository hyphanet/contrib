package com.onionnetworks.io;

import java.io.*;

public abstract class AuditableRaf extends FilterRAF {

    protected String defaultUri;
    
    public AuditableRaf(RAF raf) {
	this(raf,null);
    }

    public AuditableRaf(RAF raf, String defaultUri) {
	super(raf);
	this.defaultUri = defaultUri;
    }

    public synchronized String getDefaultUri() {
	return defaultUri;
    }

    public synchronized void setDefaultUri(String uri) {
	this.defaultUri = uri;
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	if (defaultUri == null) {
	    throw new IllegalStateException("defaultUri is null");
	}
	seekAndWrite(defaultUri,pos,b,off,len);
    }

    public abstract void seekAndWrite(String uri, long pos, byte[] b, int off,
				      int len) throws IOException;
}
