package com.onionnetworks.io;

//~--- non-JDK imports --------------------------------------------------------

import com.onionnetworks.util.*;

//~--- JDK imports ------------------------------------------------------------

import java.io.*;

/**
 * @author Justin Chapweske (justin@chapweske.com)
 */
public class TempRaf extends FilterRAF {
    public static final int NEVER = 0;
    public static final int RENAMED_AND_DONE_WRITING = 1;
    public static final int RENAMED = 2;
    public static final int ALWAYS = 3;
    public static final int DEFAULT_KEEP_POLICY = 0;
    boolean renamed = false;
    int keepPolicy;

    public TempRaf() throws IOException {
        this(DEFAULT_KEEP_POLICY);
    }

    public TempRaf(int keepPolicy) throws IOException {

        // Create a temp file in the user temp dir, or failing that, the
        // system temp dir.
        this(new RAF(FileUtil.createTempFile(null), "rw"), keepPolicy);
    }

    public TempRaf(RAF raf) {
        this(raf, DEFAULT_KEEP_POLICY);
    }

    public TempRaf(RAF raf, int keepPolicy) {
        super(raf);

        if (keepPolicy != ALWAYS) {

            // clean up in case of forcable shutdown.
            raf.getFile().deleteOnExit();
        }

        this.keepPolicy = keepPolicy;
    }

    public synchronized void renameTo(File newFile) throws IOException {
        renamed = true;
        super.renameTo(newFile);
    }

    public synchronized void close() throws IOException {

        // keep as a switch statement for readability.
        switch (keepPolicy) {
        case ALWAYS :
            break;

        case NEVER :
            deleteOnClose();

            break;

        case RENAMED :
            if (!renamed) {
                deleteOnClose();
            }

            break;

        case RENAMED_AND_DONE_WRITING :
            if (!renamed ||!getMode().equals("r")) {
                deleteOnClose();
            }

            break;
        }

        super.close();
    }
}
