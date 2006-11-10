package com.onionnetworks.util;

import java.security.*;
import java.util.*;
import java.io.*;

import junit.framework.*;

public class BlockDigestInputStreamTest extends TestCase {

    public static final String ALGORITHM = "SHA";

    public static final Random rand = new Random();
    
    public BlockDigestInputStreamTest(String name) {
	super(name);
    }

    public void testBlockCount() throws Exception {
        for (int i=0;i<100;i++) {
            int len = rand.nextInt(10000)+1;
            int blockSize = rand.nextInt(10000)+1;
            BlockDigestInputStream bdis = new BlockDigestInputStream
                (getRandomInputStream(len),ALGORITHM,blockSize);
            byte[] b = new byte[len];
            new DataInputStream(bdis).readFully(b);
            bdis.close();
            assertEquals("Same block count len="+len+",bs="+blockSize,
                         Util.divideCeil(len,blockSize),
                         bdis.getBlockDigests().length);

        }
    }

    public void testDigest() throws Exception {
        for (int i=0;i<100;i++) {
            int len = rand.nextInt(10000)+1;
            int blockSize = len;
            BlockDigestInputStream bdis = new BlockDigestInputStream
                (getRandomInputStream(len),ALGORITHM,blockSize);
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            DigestInputStream dis = new DigestInputStream(bdis,md);
            byte[] b = new byte[len];
            new DataInputStream(dis).readFully(b);
            dis.close();
            Buffer buf = bdis.getBlockDigests()[0];
            assert("Equal Hashes",Util.arraysEqual(buf.b,buf.off,
                                                   md.digest(),0,buf.len));
        }
    }

    public static final InputStream getRandomInputStream(int len) {
        byte[] b = new byte[len];
        rand.nextBytes(b);
        return new ByteArrayInputStream(b);
    }
}
                
