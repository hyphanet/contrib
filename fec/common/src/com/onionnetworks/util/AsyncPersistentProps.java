package com.onionnetworks.util;

import java.io.*;
import java.util.*;

/**
 * @author Justin F. Chapweske
 */
public class AsyncPersistentProps implements Runnable {

    private File f;
    private Properties p;
    private IOException ioe;
    private boolean closed;
    private boolean changed, writing;

    /**
     * Reads in the properties from the file if it exists.  If the file
     * does not exist then the file and an empty Properties will be created
     */
    public AsyncPersistentProps(File f) throws IOException {
	    this.f = f;
	    p = new Properties();
	    if (f.exists()) {
		    p.load(new FileInputStream(f));
	    }
	    final Thread thread = new Thread(this,"Props Writer :"+f.getName());
	    thread.setDaemon(true);
	    thread.start();
    }

    public Properties getProperties() {
        return p;
    }

    public File getFile() {
        return f;
    }

    public synchronized Object setProperty(String key, String value) {
	checkState();

        Object result = p.setProperty(key,value);
        changed = true;
        this.notifyAll();
        return result;
    }

    public synchronized Object remove(Object key) {
	checkState();

        Object result = p.remove(key);
        if (result != null) {
            changed = true;
            this.notifyAll();
        }
        return result;
    }

    public synchronized void clear() {
	checkState();

        p.clear();
        changed = true;
        this.notifyAll();
    }

    public synchronized String getProperty(String key) {
        return p.getProperty(key);
    }

    public synchronized void flush() throws IOException {
        while (!closed && (changed || writing)) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.getMessage());
            }
        }

        if (ioe != null) {
	  /* this code avoids throw {} finally {} for the sake of GCJ 3.0 */
	  IOException ex = ioe;
	  ioe = null;
	  throw ex;
        }
    }

    public synchronized void close() throws IOException {
        flush(); // This will toss the exception if set.
        closed = true;
        this.notifyAll();
    }

    private synchronized void fail(IOException e) {
	closed = true;
	ioe = e;
	this.notifyAll();
    }

    private void checkState() {
	if (ioe != null) {
	    throw new IllegalStateException(ioe.getMessage());
	} else if (closed) {
            throw new IllegalStateException("Sorry, we're closed");
	}
    }
	    
    public void run() {
        while (true) {
            try {
                byte[] b = null;
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                    // If its not changed, then wait.
                    if (!changed) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
			    fail(new InterruptedIOException(e.getMessage()));
			}
                        if (!changed) {
                            continue;
                        } 
                    }
                    //snapshot the Properties into a byte[]
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    p.store(baos,null);
                    b = baos.toByteArray();
                    changed = false;
                    writing = true;
                }

                //Write the snapshot to disk.
                if (f.exists()) {
                    f.delete();
                }
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(b);
                fos.flush();
                fos.close();

                // Notify that we're done writing.
                synchronized (this) {
                    writing = false;
                    this.notifyAll();
                }
            } catch (IOException e) {
		fail(e);
            }
        }
    }
}
