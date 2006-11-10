package com.onionnetworks.io;

import com.onionnetworks.util.Buffer;
import java.io.*;
import java.util.ArrayList;
import java.security.*;

/**
 * @author Justin F. Chapweske
 */
public class BlockDigestInputStream extends FilterInputStream {

    protected MessageDigest md;
    protected int blockSize, byteCount;
    ArrayList digestList = new ArrayList();
    Buffer[] digests = null;

    public BlockDigestInputStream(InputStream is, String algorithm, 
                                  int blockSize) 
        throws NoSuchAlgorithmException {
        
        super(is);
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be > 0");
        }
        this.md = MessageDigest.getInstance(algorithm);
        this.blockSize = blockSize;
    }

    public int read() throws IOException {
        byte[] b = new byte[1];
        if (read(b,0,1) == -1) {
            return -1;
        }
        return b[0] & 0xFF;
    }

    public long skip(long n) throws IOException {
        byte[] b = new byte[n < 1024 ? (int)n : 1024];
        long l = n;
        int c;
        while (l > 0) {
            if ((c = read(b, 0, l < 1024 ? (int)l : 1024)) == -1) {
                break;
            }
            l -= c;
        }
        return n - l;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int left = blockSize-byteCount;
        int c;
        // truc the read if they want more than is left for this block.
        if ((c = in.read(b,off,len < left ? len : left)) == -1) {
            return -1;
        }
        md.update(b,off,c);        
        byteCount += c;
        // this block is full
        if(byteCount == blockSize) {
            digestList.add(new Buffer(md.digest()));        
            byteCount = 0;
        }
        return c;
    }

    public void finish() {
        if (byteCount != 0) {
            digestList.add(new Buffer(md.digest()));
        }
        digests = (Buffer[]) digestList.toArray(new Buffer[0]);
        digestList = null;
    }

    public void close() throws IOException {
        if (digestList != null) {
            finish();
        }
        in.close();
    }

    public Buffer[] getBlockDigests() {
        if (digests == null) {
            throw new IllegalStateException("Must call finish or close first");
        }
        return digests;
    }
}
