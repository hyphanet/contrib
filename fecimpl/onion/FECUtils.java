// REDFLAG: rename to OnionFECUtils
// REDFLAG: Purge the unused code out of this file.
// REDFLAG: investigate file io , extra zeroing????


/*
  This code is distributed under the GNU Public Licence (GPL) version 2.  See
  http://www.gnu.org/ for further details of the GPL.
  
*/

import java.io.*;
import java.util.Properties;
import com.onionnetworks.fec.*;
import com.onionnetworks.util.*;

import freenet.support.Bucket;
import freenet.support.BucketFactory;

/**
 * Wrap the Onion Networks FEC routines so that they can operate on Buckets.
 * <p>
 * NOTE: These routines load all data into *memory*.
 * <p>
 * @author giannij
 **/
public class FECUtils {

    // Calling this prevents an ugly warning stack
    // trace from the DefaultFECCodeFactory when the 
    // native libs are not installed.
    public final static void disableNativeCode() {
        Properties systemProps = System.getProperties();
        systemProps.setProperty("com.onionnetworks.fec.keys","pure8,pure16");
        System.setProperties(systemProps);
        System.err.println("Native code off. Using Java FEC implementaion.");
    }

    // package scope on purpose.
    // Caller must ensure that n, k match the code.
    // requested can be null
    // REQUIRES: 
    final static void encode(FECCode code, int n, int k,
                             Bucket[] blocks, Bucket[] checkBlocks, int[] requested) 
        throws IOException {

        final int BLOCK_SIZE = (int)blocks[0].size();
        
        final int l = n - k;

        int[] index = null;
        
        if (requested != null) {
            // Only encode requested check blocks
            index = requested;
        }
        else {
            // Encode all check blocks
            index = new int[l];
            int i = 0;
            for (i = 0; i < l; i++) {
                index[i] = k + i;
            }
        }

        if (requested.length != index.length) {
            throw new IOException("Illegal arguments: requested.length != checkBlocks.length"); 
        }

        Buffer[] src = readBuffers(blocks);
        Buffer[] repair = allocateBuffers(index.length, BLOCK_SIZE);


        long start = System.currentTimeMillis();
        code.encode(src, repair, index);
        System.err.println("Made " + index.length + " " + BLOCK_SIZE + " byte check blocks in "
                           + (System.currentTimeMillis() - start ) + "ms.");
        
        int i = 0;
        for (i = 0; i < repair.length; i++) {
            // We can just throw here because we don't own
            // any of the buckets.
            dumpBlock(repair[i], checkBlocks[i]);
        }
    }

    // block size is taken from the size of the first non-null block in
    // blocks.
    // blocks can have null entries.  Must have at least k non-null entries.
    public final static Bucket[] decode(Bucket[] blocks, int k, BucketFactory bucketFactory)
        throws IOException {

        DefaultFECCodeFactory factory = new DefaultFECCodeFactory();

        final int n = blocks.length;

        FECCode code = factory.createFECCode(k, n);

        NonNulls nn = findNonNullBuckets(blocks, k);

        Bucket[] ret = null;
        Bucket[] decoded = null;
        try {
            // Make storage for decoded blocks.
            decoded = makeBuckets(bucketFactory, nn.missingIndices.length, 
                                  (int)nn.buckets[0].size(), false);
            
            // Decode.
            decode(code, nn.buckets, nn.indices, k, nn.missingIndices, decoded);
            
            // Sort out return value.
            int index = 0;
            ret = new Bucket[k];
            for (int i = 0; i < k; i++) {
                    if (blocks[i] != null) {
                        ret[i] = blocks[i];
                    }
                    else {
                        ret[i] = decoded[index];
                        index++;
                    }
            }
            decoded = null; 
        }
        finally {
            freeBuckets(bucketFactory, decoded);
        }

        return ret;
    } 

    static class NonNulls {
        Bucket[] buckets = null;
        int[] indices = null;
        int[] missingIndices = null;
    }

    final static NonNulls findNonNullBuckets(Bucket[] blocks, int k) {
        int missingDataBlocks = 0;
        int count = 0;
        int i = 0;
        for (i=0; i < blocks.length; i++) {
            if (blocks[i] != null) {
               count++;
            }
            else if (i < k) {
                missingDataBlocks++;
            }
        }

        NonNulls ret = new NonNulls();
        ret.buckets = new Bucket[count];
        ret.indices = new int[count];
        ret.missingIndices = new int[missingDataBlocks];

        int index = 0;
        int missingIndex = 0;
        for (i=0; i < blocks.length; i++) {
            if (blocks[i] != null) {
                ret.buckets[index] = blocks[i];
                ret.indices[index] = i;
                index++;
            }
            else if (i < k) {
                ret.missingIndices[missingIndex] = i;
                missingIndex++;
            }
        }
        return ret;
    }

    // blocks can't have any null entries
    final static void decode(FECCode code, Bucket[] blocks, int[] indices,
                             int k, int[] decodeIndices, Bucket[] decoded)
        throws IOException {
    
        if (blocks.length < k) {
            throw new IllegalArgumentException("Not enough packets to decode.");
        }

        Buffer[] packets = readBuffers(blocks);

        long start = System.currentTimeMillis();
        
        // REDFLAG: REMOVE
        int j = 0;
        String list = "";
        for(j = 0; j < indices.length; j++) {
            list += " " + indices[j];
        }

        System.err.println("Decoding from packets: " + list.trim());

        // Make a copy because FECCode.decode() reorders
        // the elements of its arguments.
        int[] copyOfIndices = new int[indices.length];
        System.arraycopy(indices, 0, copyOfIndices, 0, indices.length);

        code.decode(packets, copyOfIndices);

        list = "";
        for(j = 0; j < indices.length; j++) {
            list += " " + indices[j];
        }

        //System.err.println("Decoded into packets: " + list.trim());

        System.err.println("FEC decode took " + (System.currentTimeMillis() - start ) + "ms.");
        
        for (int i = 0; i < decodeIndices.length; i++) {
            // Nothing to clean up on exception
            // because we don't own any Buckets.
            dumpBlock(packets[decodeIndices[i]], decoded[i]);
        }
    }

    // Makes Buckets *and* zeros the contents to make sure
    // that the Bucket really is blockSize bytes long
    // in the underlying storage (i.e. file.).  This is
    // important for striping. 
    public final static Bucket[] makeBuckets(BucketFactory factory, int n, int blockSize, boolean zero)
        throws IOException {
        Bucket[] ret = null;
        Bucket[] buckets = new Bucket[n];
        byte[] buf = null;
        int i = 0;

        if (zero) {
            buf = new byte[4096];
            for (i = 0; i < buf.length; i++) {
                buf[i] = 0; // required?
            }
        }

        try {
            for (i=0; i < n; i++) {
                buckets[i] = factory.makeBucket(blockSize);
                if (zero) {
                    // Fill buckets with zeros
                    OutputStream out = null;
                    int byteCount = blockSize;
                    try {
                        out = buckets[i].getOutputStream();
                        while (byteCount > 0) {
                            int nBytes = buf.length;
                            if (nBytes > byteCount) {
                                nBytes = byteCount;
                            }
                            out.write(buf, 0, nBytes);
                            byteCount -= nBytes;
                        }
                    }
                    finally {
                        if (out != null) {
                            try { out.close(); } catch (Exception e) {}
                        }
                    }
                }
            }
            ret = buckets;
            buckets = null;
        }
        finally {
            freeBuckets(factory, buckets);
        }
        
        return ret;
    }

    public static void freeBuckets(BucketFactory bucketFactory, Bucket[] buckets) {
        if ((buckets == null) || (bucketFactory == null)) {
            return;
        }
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] != null) {
                try {
                    bucketFactory.freeBucket(buckets[i]);
                }
                catch (Exception e) {
                }
                buckets[i] = null;
            }
        }
    }

    ////////////////////////////////////////////////////////////
    // Helper functions
    ////////////////////////////////////////////////////////////

    // Write a bucket into the buffer buf at offset bufOffset.
    private final static void read(Bucket b, byte[] buf, int bufOffset) throws IOException {
        int length = (int)b.size();
        int offset = 0;

        InputStream in = null;
        try {
            in = b.getInputStream();
            while (length > 0) {
                int nRead = in.read(buf, offset + bufOffset, length);
                if (nRead < 0) {
                    throw new IOException("unexpected !");
                }
                length -= nRead;
                offset += nRead;
            }
        }
        finally {
            if (in != null) {
                try {in.close();} catch (IOException ioe) {}
            }
        }
    }

    // reads Buffers from buckets
    private final static Buffer[] readBuffers(Bucket[] buckets) throws IOException {
        Buffer[] ret = new Buffer[buckets.length];

        for (int i = 0; i < buckets.length; i++) {
            byte buf[] = new byte[(int)buckets[i].size()];
            read(buckets[i], buf, 0);
            ret[i] = new Buffer(buf);
        }

        return ret;
    }

    // allocates empty Buffers
    private final static Buffer[] allocateBuffers(int size, int blockSize) {
        Buffer[] ret = new Buffer[size];

        for (int i = 0; i < size; i++) {
            ret[i] = new Buffer(new byte[blockSize]);
        }

        return ret;
    }

    private final static void dumpBlock(Buffer block, Bucket b) throws IOException {
        OutputStream out = null;
        try {
            b.resetWrite();
            out = b.getOutputStream();
            byte[] bytes = block.getBytes();
            int length = bytes.length;
            int offset = 0;
            out.write(bytes, offset, length);
        }
        finally {
            if (out != null) {
                try { out.close(); } catch(IOException ioe) {}
            }
        }
    }

    private final static Bucket[] dumpBlocks(Buffer blocks[], BucketFactory factory, 
                                             int blockSize, int nBlocks) 
        throws IOException {

        int count = blocks.length;
        if (count > nBlocks) {
            count = nBlocks;
        }
        Bucket[] ret = new Bucket[count];

        try {
            for (int i = 0; i < ret.length; i++) {
                ret[i]= factory.makeBucket(blockSize);
                dumpBlock(blocks[i], ret[i]);
            }
        }
        catch (IOException ioe) {
            // Don't leave temp files hanging around.
            for (int i = 0; i < ret.length; i++) {
                if (ret[i] != null) {
                    try { factory.freeBucket(ret[i]); } catch (Exception e) {}
                }
            }
            throw ioe;
        }
        return ret;
    }

    // Read buckets into a contiguous buffer.
    private final static void read(Bucket[] buckets, byte[] buf) throws IOException {
        // paranoid check
        int length = 0;
        int i;
        for (i = 0; i < buckets.length; i++) {
            length += buckets[i].size(); 
        }
        if (length > buf.length) {
            throw new IllegalArgumentException("The buffer is too small.");
        }

        length = 0;
        for (i = 0; i < buckets.length; i++) {
            read(buckets[i], buf, length);
            length += buckets[i].size(); 
        }
    }
}









