import freenet.FieldSet;
import freenet.client.FECEncoder;
import freenet.client.metadata.SplitFile;
import freenet.support.Bucket;
import freenet.support.BucketFactory;
import freenet.support.StripedBucketArray;


import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.DefaultFECCodeFactory;

import java.io.IOException;


/*
  This code is distributed under the GNU Public Licence (GPL)
  version 2.  See http://www.gnu.org/ for further details of the GPL.
*/

/**
 * A FECEncoder implementation written on top of the
 * Onion Networks Java FEC Library v1.0.2.
 *
 * @author giannij
 **/
public class NativeOnionFECEncoder implements FECEncoder {

    // BucketFactory must produce either FileBuckets or
    // RandomAccessFileBuckets.
    //
    // true on success, false if the decoder can't be
    // created.
    public boolean init(int len, BucketFactory factory) {
        // Select block size
        if (len < C_32M) {
            // 256K for up to 32MB
            blockSize = C_256K;
        }
        else if (len < C_64M) {
            // 512K for up to 64MB
            blockSize = C_512K;
        }
        else  {
            // 1MB for up to 128MB
            blockSize = C_1M;
        }

        // We won't try to encode more than
        // 128MB at a time.  Past that, we
        // have to segment.
        segments = len / C_128M;
        
        if ((len % C_128M) != 0) {
            segments++;
        }

        if (len < C_128M) {
            k = len / blockSize;
            if ((len % blockSize) != 0) {
                k++;
            }
        }
        else {
            k = C_128M / blockSize;
        }

        n = k + k/2;
        
        if (n == k) {
            n++;
        }

        baseN = n;
        baseK = k;

        endN = n;
        endK = k;
        if (len >= C_128M) {
            int finalSegmentLen = len % C_128M;
            endK = finalSegmentLen / blockSize;
            if ((finalSegmentLen % blockSize) != 0) {
                endK++;
            } 
            endN = endK + endK/2;
            if (endK == endN) {
                endN++;
            }
        }

        // REDFLAG: assert n, k < 256
        stripeWidth = -1;
        if (len > C_16M) {
            long activeLength = len;
            if (len > C_128M) {
                len = C_128M;
            }
            
            // Set stripe width so that we never
            // need to load more that 16Mb of
            // data blocks into memory.
            
            // REDFLAG: double check
            int stripeCount = 1;
            while (activeLength/stripeCount > C_16M) {
                stripeCount = stripeCount << 1;
            }
            
            stripeWidth = blockSize / stripeCount;

            // assert(blockSize % stripeCount == 0);
        }

        this.bucketFactory = factory;
        this.len = len;

        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
            
        this.code = f.createFECCode(k, n);

        currentSegment = 0;

        return code != null;
    }

    public void release() {
        code = null;
    }
    
    public int getBlockSize() {
        return blockSize;
    }
    
    public int getN(int segment) {
        if (segment < segments - 1) {
            return baseN;
        }
        return endN;
    }

    public int getK(int segment) {
        if (segment < segments - 1) {
            return baseK;
        }
        return endK;
    }
    
    public int getSegmentCount() {
        return segments;
    }

    public int getSegmentSize() {
        return C_128M;
    }

    private final void setSegment(int segment) {
        if ((segment < 0) || (segment >= segments)) {
            throw new IllegalArgumentException("Bad segment number.");
        }

        if ((segments < 2) || segment == currentSegment) {
            return; // nop
        } 

        if ((currentSegment < segments - 1) &&
            (segment < segments - 1)) {
            return;
        }
        
        // We only switch when going from an end
        // to a non-end segment or vice versa.

        // This is horrible. Use a different code
        // for the final segment.
        int newN = baseN;
        int newK = baseK;
        if (segment == segments - 1) {
            newN = endN;
            newK = endK;
        }
        
        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
        FECCode newCode = f.createFECCode(newK, newN);
        
        if (newCode != null) {
            code = newCode;
            n = newN;
            k = newK;
            currentSegment = segment;
            return;
        }

        // REDFLAG: Test. This is going to be a nightmare to debug.

        throw new RuntimeException("Couldn't set segment.");
    }

    // Blocking call.  Can take a very long time.
    // Polite implementations should be interruptable and
    // should throw an InterruptedIO exception when interrupted.
    // Caller is responsible for freeing buckets.
    public Bucket[] encode(int segmentNum, Bucket[] blocks) throws IOException {
        if (code == null) {
            throw new IllegalStateException("Not initialized");
        }

        setSegment(segmentNum);

        // Order important, setSegment() sets k.
        if (blocks.length != k) {
            throw new IllegalArgumentException("blocks.length != " + k);
        }

        System.err.println("OnionEncoder.encode -- n=" + n);
        System.err.println("OnionEncoder.encode -- k=" + k);
        System.err.println("OnionFECEncoder.encode -- segmentCount=" + segments);
        System.err.println("OnionFECEncoder.encode -- segment=" + currentSegment);
        System.err.println("OnionFECEncoder.encode -- stripeWidth=" + stripeWidth);
        System.err.println("OnionFECEncoder.encode -- blockSize=" + blockSize);

        Bucket[] ret = null;
        // Allocate Buckets to hold the check blocks.
        Bucket[] checkBlocks = FECUtils.makeBuckets(bucketFactory, n - k, blockSize, true);
        try {
            if (stripeWidth == -1) { 
                // No striping.
                FECUtils.encode(code, blocks, checkBlocks);
                ret = checkBlocks;
                checkBlocks = null;
            }
            else {
                // Stripe encoding.
                //
                // We do this to limit RAM usage.
                //
                StripedBucketArray dataArray = new StripedBucketArray();
                StripedBucketArray checkArray = new StripedBucketArray();
                
                try {
                    // REDFLAG: assert( blockSize % stripeWidth == 0)
                    int stripeCount = blockSize / stripeWidth;
                    Bucket[] dataStripe = dataArray.allocate(blocks);
                    Bucket[] checkStripe = checkArray.allocate(checkBlocks);
                    for (int i = 0; i < stripeCount; i++) {
                        System.err.println("Encoding stripe: " + i +
                                           " [" + stripeWidth + "]");
                        dataArray.setRange(i * stripeWidth, stripeWidth);
                        checkArray.setRange(i * stripeWidth, stripeWidth);
                        FECUtils.encode(code, dataStripe, checkStripe);
                    }
                    ret = checkBlocks;
                    checkBlocks = null;
                }
                finally {
                    dataArray.release();
                    checkArray.release();
                }
            }
        }
        finally {
            FECUtils.freeBuckets(bucketFactory, checkBlocks);
        }

        return ret;
    }

    // Writes info required to look up an appropriate
    // decoder into SplitFile.  Throws if n, k are not
    // correct.
    //
    public SplitFile makeMetadata(String[] dataURIs, String[] checkURIs) {
        // Stuff the pars that we need to make an appropriate
        // decoder into the SplitFile.
        FieldSet params = new FieldSet();
        params.put("name", DECODER_NAME);
        //
        // We store the encoder name so that client writers 
        // can write clients that re-insert missing blocks 
        // after FEC decoding. A self healing network...
        //
        params.put("encoder", ENCODER_NAME);
        params.put("segments", Integer.toString(segments));
        params.put("stripeWidth", Integer.toString(stripeWidth));
        params.put("baseK", Integer.toString(baseK));
        params.put("baseN", Integer.toString(baseN));
        params.put("endK", Integer.toString(endK));
        params.put("endN", Integer.toString(endN));

        return new SplitFile(len, blockSize, dataURIs, checkURIs, params);
    }

    ////////////////////////////////////////////////////////////

    private final static String ENCODER_NAME = "OnionEncoder_0";
    private final static String DECODER_NAME = "OnionDecoder_0";

    private final static int C_256K = 256 * 1024; 
    private final static int C_512K = 512 * 1024; 
    private final static int C_1M = 1024 * 1024; 
    private final static int C_16M = 16 * 1024 * 1024;
    private final static int C_32M = 32 * 1024 * 1024;
    private final static int C_64M = 64 * 1024 * 1024; 
    private final static int C_128M = 128 * 1024 * 1024; 

    private BucketFactory bucketFactory = null;
    private int n = -1;
    private int k = -1;
    private int baseN = -1;
    private int baseK = -1;
    private int endN = -1;
    private int endK = -1;
    private int len = -1;
    private int blockSize = -1;
    private int segments = -1;
    private int currentSegment = -1;
    private int stripeWidth = -1;
    private FECCode code = null;
}




