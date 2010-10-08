package com.onionnetworks.io;

import com.onionnetworks.util.*;
import java.io.*;
import java.text.ParseException;

public class Journal extends AsyncPersistentProps {

    public static final String FILE_PROP = "file";
    public static final String BYTES_PROP = "bytes";

    File f;
    RangeSet written;

    public Journal(File f) throws IOException {
	super(f);

	// Read in the byte ranges.
	String bytes = getProperty(BYTES_PROP);
	if (bytes != null) {
	    // try and read existing journal.
	    try {
		written = RangeSet.parse(bytes);
	    } catch (ParseException e) {
		throw new IOException("Corrupt journal.");
	    }
	} else {
	    // new journal.
	    this.written = new RangeSet();
	}

	// Read in the target file name.
	String file = getProperty(FILE_PROP);
	if (file != null) {
	    this.f = new File(file);
	}
    }

    public void setTargetFile(File f) {
	this.f = f;
	setProperty(FILE_PROP, f.getAbsolutePath());
    }

    public File getTargetFile() {
	return f;
    }

    public void addByteRange(Range r) {
	written.add(r);
	setProperty(BYTES_PROP, written.toString());
    }

    public RangeSet getByteRanges() {
	return written;
    }
}

    
