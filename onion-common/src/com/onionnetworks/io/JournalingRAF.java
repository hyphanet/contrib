package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.util.*;

public class JournalingRAF extends FilterRAF {

    Journal journal;

    public JournalingRAF(RAF raf, Journal journal) throws IOException {
	super(raf);
	if (raf.getMode().equals("r")) {
	    throw new IllegalStateException("Can't create a journal for a "+
					    "read-only file.");
	}

	this.journal = journal;
	
	// Track the initial file path.
	journal.setTargetFile(raf.getFile());
    }

    public synchronized void seekAndWrite(long pos, byte[] b, int off, 
                                          int len) throws IOException {
	super.seekAndWrite(pos,b,off,len);
	//FIX flush problem, what if it crashes before data is persisted?

	if (journal != null) {
	    // can be null from deleteJournal
	    // Update the journal..
	    journal.addByteRange(new Range(pos,pos+len-1));
	}
    }

    public synchronized void renameTo(File newFile) throws IOException {
	super.renameTo(newFile);
	// Update the file location.

	// Can be null from deleteJournal()
	if (journal != null) {
	    // Use _raf.getFile() because renameTo() may have failed and was
	    // forced to fall back.
	    journal.setTargetFile(_raf.getFile());

	    // flush here because it is important that the journal stay in
	    // sync on this operation.
	    journal.flush();
	}
    }

    public synchronized void setReadOnly() throws IOException {
	super.setReadOnly();
	// done writing, delete the journal
	deleteJournal();
    }

    public synchronized void close() throws IOException {
	super.close();
	
	// Can be null from deleteJournal
	if (journal != null) {
	    journal.close();
	}
    }
    
    public synchronized void deleteOnClose() {
	super.deleteOnClose();
	deleteJournal();
    }

    private void deleteJournal() {
	File file = journal.getFile();
	try {
	    journal.close();
	} catch (IOException e) {e.printStackTrace();}
	//FIX maybe throw exception on failed delete?
	file.delete();
	journal = null;
    }
}
