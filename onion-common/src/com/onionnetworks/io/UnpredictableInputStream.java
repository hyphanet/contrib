package com.onionnetworks.io;

import java.io.*;
import java.util.Random;

/**
 * This InputStream is designed to simulate real-world network IO conditions
 * where less bytes may be returned or skipped than specified.  This is
 * designed for testing for edge-conditions or incorrect assumptions about IO
 * semantics.
 *
 * @author Justin F. Chapweske
 */
public class UnpredictableInputStream extends FilterInputStream {

    Random rand = new Random();

    public UnpredictableInputStream(InputStream is) {
        super(is);
    }

    public long skip(long n) throws IOException {
	// We use nextInt rather than nextLong to ensure equal distribution
	// across the possible bytes so that we're consistantly skipping
	// less than n bytes.
	// (int) cast is safe due to min(int,long)
	if (rand.nextInt(5) == 0) {
	    // Return 0 length skips quite often for testing implementations.
	    return super.skip(0);
	}
	System.out.println("skip!");
	return super.skip(rand.nextInt((int)Math.min(Integer.MAX_VALUE,n+1)));
    }

    public int read(byte[] b) throws IOException {
	// This method must block until data is available, thus we will
	// keep reading on a 0 byte result.
	if (b.length == 0) {
	    return 0;
	}
	int c;
	while ((c = read(b,0,b.length)) == 0) {}
	return c;
    }
	
    public int read(byte[] b, int off, int len) throws IOException {
	// Even though FilterInputStream's JavaDoc claims that this method
	// must block until data is available, the current FilterInputStream
	// implementation doesn't even enforce that.
	if (rand.nextInt(5) == 0) {
	    // Return 0 length reads quite often for testing implementations.
	    return super.read(b,off,0);
	}
	return super.read(b,off,rand.nextInt(len+1));
    }
}
