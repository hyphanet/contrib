package com.onionnetworks.util;

import java.util.BitSet;
import java.util.StringTokenizer;
import java.net.URL;
import java.io.*;

public class FileUtil {
    
    // safe characters for file name sanitization
    static BitSet safeChars = new BitSet(256);

    static {
	// a-z
	for (int i='a';i<='z';i++) {
	    safeChars.set(i);
	}
	// A-Z
	for (int i='A';i<='Z';i++) {
	    safeChars.set(i);
	}
	// 0-9
	for (int i='0';i<='9';i++) {
	    safeChars.set(i);
	}
	safeChars.set('-');
	safeChars.set('_');
	safeChars.set(' ');
	safeChars.set('.');
    }

    public static final String sanitizeFileName(String name) {
	StringBuffer result = new StringBuffer();
	for (int i=0;i<name.length();i++) {
	    char c = name.charAt(i);
	    // squish multiple '.'s into a single one.
	    if (c == '.' && i < name.length()-1 && name.charAt(i+1) == '.') {
		continue;
	    }
	    if (safeChars.get(c)) {
		result.append(c);
	    }
	}
	return result.toString();
    }

    public static final String pickSafeFileName(URL url) {
	String name = sanitizeFileName(new File(url.getFile()).getName());
	if (name == null || name.equals("")) {
	    name = "index.html";
	}
	return name;
    }

    /**
     * Creates a file in the .onion directory after scrubbing every directory
     * and filename for unsafe chracters.
     * @param rel the relative path to clean and put in .onion
     * @return File object that's name safe
     * @throws IllegalArgumentException if path isn't relative or if any path 
     *  element (including the filename) is "" after sanitization.
     */
    public static File safeOnionFile(String rel) {
        if ((new File(rel)).isAbsolute()) {
            throw new IllegalArgumentException(rel + " isn't relative");
        }
        StringBuffer safe = new StringBuffer();
        for (StringTokenizer st = new StringTokenizer(rel, File.separator);
                st.hasMoreTokens(); ) {
            String tok = sanitizeFileName(st.nextToken());
            if (tok == null || "".equals(tok)) {
                throw new IllegalArgumentException("collapsed elemnt");
            }
            if (safe == null) {
                safe = new StringBuffer(tok);
            } else {
                safe.append(File.separator).append(tok);
            }
        }

	File result = new File(getOnionDir(), safe.toString()); 

	try {
	    ensureExists(result);
	} catch (IOException e) {
	    throw new IllegalStateException
		("Failed to ensure that file exists: "+e.getMessage());
	}

	return result;
    }
    
    public static void ensureExists(File f) throws IOException {
	if (!f.getParentFile().exists()) {
	    if (!f.getParentFile().mkdirs()) {
		throw new IOException("Couldn't create parent dirs: "+f);
	    }
	}
	
	if (!f.exists()) {
	    f.createNewFile();
	}
    }

    /**
     * Get the .onion directory off of the user's home directory.
     * Created if it doesn't exist
     * @return File to onion networks directory, null on failure to create
     */
    public static File getOnionDir() {
	String s = System.getProperty("user.home");
	// If they don't have a home directory, then null ("java.io.tmpdir")
	// will be used.
	if (s == null) {
	    //System.out.println("user.home was null");
	    return null;
	}
	File f = new File(s, ".onion"); // FIX hardcoded name.
	//System.out.println(f);
	// check if exists
	if (!f.exists()) {
	    //System.out.println("doesn't exist");
	    // make dirs
	    if (!f.mkdirs()) {
		//System.out.println("mkdirs failed");
		// fall back to system temp dir.
		return null;
	    }
	}
	return f;
    }

    /**
     * Create a temp directory off of the onion directory, we do this
     * instead of the system temp directory because the user probably has more
     * disk space in their home directory.
     */
    public static final File getUserTempDir() {
	
	File f = new File(getOnionDir(), "tmp"); // FIX hardcoded name
	//System.out.println(f);
	// check if exists
	if (!f.exists()) {
	    //System.out.println("doesn't exist");
	    // make dirs
	    if (!f.mkdirs()) {
		//System.out.println("mkdirs failed");
		// fall back to system temp dir.
		return null;
	    }
	}
	return f;
    }

    /**
     * Create a temporary file in the same directory as f and with a similar
     * name.  If f is null, then a randomly named file will be created in
     * either the user temp directory or the system temp directory.
     */
    public static final File createTempFile(File f) throws IOException {
	//FIX unit test for relative file
	//FIX the file names are hardcoded
	File parent;
	String name;
	if (f == null) {
	    // null parent causes it to use the system temp dir.
	    parent = getUserTempDir();
	    name = "onion";
	} else {
	    parent = f.getAbsoluteFile().getParentFile();
	    name = f.getName();
	    // createTempFile requires a suffix length of at least 3
	    if (name.length() < 3) {
		name += "onion";
	    }
	}
	
	try {
	    return File.createTempFile(name,null,parent);
	} catch (IOException e) {
	    if (f != null) {
		// try the user's temp dir
		return createTempFile(null);
	    } else if (parent != null) {
		// try the system temp dir
		return File.createTempFile(name,null,null);
	    } else {
		throw new IOException("Unable to create temp file");
	    }
	}
    }

    public static void skipFully(InputStream is, long count) 
	throws IOException {
	
	byte[] b = null;

	long left = count;
	while (left > 0) {
	    long skipped = is.skip(left);
	    if (skipped == 0) {
		// We couldn't skip any bytes, lets try reading some.
		if (b == null) {
		    b = new byte[1024];
		}
		// (int) cast is safe due to min(int,long)
		int c = is.read(b,0,(int)Math.min(b.length,left));
		if (c == -1) {
		    throw new EOFException();
		} else {
		    skipped = c;
		}
	    }
	    left -= skipped;
	}
    }
}
