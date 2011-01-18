package com.onionnetworks.io;

import java.io.*;
import com.onionnetworks.util.*;

public class LazyRenameRAF extends FilterRAF {

    File destFile;

    public LazyRenameRAF(RAF raf) throws IOException {
	super(raf);
	
	if (getMode().equals("r")) {
	    throw new IllegalStateException("LazyRenameRAFs are only useful "+
					    "in read/write mode.");
	}
    }

    // setting the destination will not happen until setReadOnly() is called.
    // It is ok if this throws an IOException because the RAF
    // will revert to its previous state, no harm done.
    // document that it will create a temp file in the same directory.
    /**
     * If the current location and the newFile are different, it is guarenteed
     * that the file will be moved to some new location, even if it isn't the
     * final destination.  This is to allow the safe setting of deleteOnExit
     * for locations that are intended to be temporary.
     */
    public synchronized void renameTo(File newFile) throws IOException {
	//FIX figure out the proper semantics for this temporary same-directory
	// file.
	//
	// we set the destination before doing anything else, so that if
	// moving to the new temp location fails, we still have the destination
	// set.
	this.destFile = newFile;

	if (getMode().equals("r")) {
	    _raf.renameTo(destFile);
	} else {
	    // create a temp file in the same directory as destFile, if 
	    // destFile is null, then try to create a temp file in the
	    // user temp directory, then fall back to the system temp dir.
	    File newTemp = FileUtil.createTempFile(destFile);

	    _raf.renameTo(newTemp);   
	}
    }

    // This should at least by in read-only mode when it bombs, should
    // FIX parent.setReadOnly to revert as well.
    public synchronized void setReadOnly() throws IOException {
	_raf.setReadOnly();
	if (destFile != null) {
	    _raf.renameTo(destFile);
	}
    }
}












