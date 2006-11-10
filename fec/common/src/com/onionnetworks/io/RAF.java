package com.onionnetworks.io;

//import org.apache.log4j.Category;
import java.io.*;

// Implement Filtering.
public class RAF {

    //  static Category cat = Category.getInstance(RAF.class.getName());

    protected File f;
    protected String mode;
    protected RandomAccessFile raf;
    private boolean closed;
    protected boolean deleteOnClose;

    public RAF(File f, String mode) throws IOException {
        this.f = f;
        this.mode = mode;
        this.raf = new RandomAccessFile(f,mode);
    }

    /**
     * This is only to be used by subclasses requiring more flexible
     * constructors.
     */
    protected RAF() {}

    public File getFile() {
	return f;
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
        raf.seek(pos);
        raf.write(b,off,len);
    }

    public synchronized int seekAndRead(long pos, byte[] b, int off, int len) 
	throws IOException {
	raf.seek(pos);
	return raf.read(b,off,len);
    }

    public synchronized void seekAndReadFully(long pos, byte[] b, int off,
                                              int len) throws IOException {
        raf.seek(pos);
        raf.readFully(b,off,len);
    }

    /**
     * This version of renameTo() attempts to mimic the behavior of the unix 
     * 'mv' command rather than File.renameTo.  This means that the destFile
     * will automatically be deleted if it exists and if we are unable to
     * mv the file directly because they are on different file systems then 
     * we will manually copy and delete the original.  
     *
     * If there is an exception during the copy then we will attempt to 
     * delete the new copy and revert back to the old copy.  There is a very
     * slight chance that after reverting, the original copy may be unusable.
     * This would probably only happen with a file system that is
     * experiencing IO problems, in which case you are going to have problems
     * anyway.
     *
     * There is also a very rare chance that during a failed copy, we will
     * be unable to delete a partially written destination file and it will
     * remain on disk.  This could happen on a directory with "drop box"
     * semantics where you can only create and write, and not delete.
     */
    public synchronized void renameTo(File destFile) throws IOException {
        if (closed) {
            throw new IOException("File closed.");
        }
        raf.close();
        // Move to final location.
        try {
            // If they are the same file than do nothing.  It is obviously 
            // important for this to be checked before we delete the destFile.
            if (f.getCanonicalFile().equals(destFile.getCanonicalFile())) {
                return;
            }

            // Delete the destFile if it exists.
            if (destFile.exists()) {
                //cat.debug("renameTo(): destFile exists, deleting...");
                if (!destFile.delete()) {
                    throw new IOException("Unable to delete destination :"+
                                          destFile);
                }
            }
            
            if (!f.renameTo(destFile)) {
                //cat.debug("renameTo(): File.renameTo failed, copying...");
                try {
                    byte[] b = new byte[8192];
                    InputStream is = new FileInputStream(f);
                    OutputStream os = new FileOutputStream(destFile);
                    int c;
                    while ((c = is.read(b,0,b.length)) != -1) {
                        os.write(b,0,c);
                    }
                    is.close();
                    os.close();

		    // If there was a prob with the copy then it should 
		    // have bombed before now.
		    if (!f.delete()) {
			// Even though this is a problem with the source, 
			// we still delete the destination and keep the source
			// in the catch {} clause because we want this method
			// to fall back to the source under any failure
			// condition.
			throw new IOException("Unable to delete source "+
					      "post-move");
		    }
                } catch (IOException e) {
                    if (destFile.exists() && !destFile.delete()) {
                        throw new IOException("Unable to delete destination "+
                                              "after failed move : "+destFile+
                                              " : "+e.getMessage());
                    }       
                    throw new IOException("Unable to move "+f+" to "+
                                          destFile+" : "+e.getMessage());
                }

                f = destFile;
            } else {
                f = destFile;
            }
        } finally {
            // If exception is thrown, re-open the old one.
            raf = new RandomAccessFile(f,mode);
        }
    }

    public synchronized String getMode() {
        return mode;
    }

    public synchronized boolean isClosed() {
	return closed;
    }

    public synchronized void setReadOnly() throws IOException {
        if (closed) {
            throw new IOException("File closed.");
        }
        this.mode = "r";
        raf.close();
        raf = new RandomAccessFile(f,mode);
    }

    public synchronized void deleteOnClose() {
	if (closed) {
	    throw new IllegalStateException("File already closed");
	}
	deleteOnClose = true;
    }


    public synchronized void setLength(long len) throws IOException {
        raf.setLength(len);
    }

    public synchronized long length() throws IOException {
	return raf.length();
    }

    public synchronized void close() throws IOException {
        closed = true;
        raf.close();
	if (deleteOnClose) {
	    if (!f.delete()) {
		throw new IOException("Unable to delete file on close");
	    }
	}
    }

    /**
     * Cleans up this objects resources by calling close().  This will
     * also cause the file to be deleted if deleteOnClose() was called.
     *
     * @see close()
     */
    protected void finalize() throws IOException {
	if (!closed) {
	    close();
	}
    }

    public String toString() {
	return "RAF[file="+f.getAbsolutePath()+",mode="+mode+"]";
    }
}





