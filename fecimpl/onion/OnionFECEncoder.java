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
public class OnionFECEncoder extends OnionFECBase implements FECEncoder {

    // Here's my naming convention
    // Encoder/Decoder pairs must return the same name.
    // <name>_<rev>_<redundancy numerator>_<redundancy denominator>
    protected final static String DEFAULT_NAME = "OnionFEC_a_1_2";
    protected final static int DEFAULT_NUM = 1;
    protected final static int DEFAULT_DENOM = 2;

    public OnionFECEncoder() {
        // 50% redundancy
        this(DEFAULT_NAME, DEFAULT_NUM, DEFAULT_DENOM);
    }

    // Used by subclasses to define new encoders with different
    // parameters.
    protected OnionFECEncoder(String name, 
                              int redundancyNum,
                              int redundancyDenom) {
        super(name, redundancyNum, redundancyDenom);
    }

    // REDFLAG: document constraint somewhere? better assert
    // BucketFactory must produce either FileBuckets or
    // RandomAccessFileBuckets.
    //
    // true on success, false if the decoder can't be
    // created.


    // Blocking call.  Can take a very long time.
    // Polite implementations should be interruptable and
    // should throw an InterruptedIO exception when interrupted.
    // Caller is responsible for freeing buckets.
    public Bucket[] encode(int segmentNumber, Bucket[] blocks, int[] requested) 
        throws IOException {

        if (code == null) {
            throw new IllegalStateException("Not initialized");
        }

        setSegment(segmentNumber);

        final int l = n - k;

        // If requested is null, return all check blocks
        int i = 0;
        if (requested == null) {
            requested = new int[l];
            for (i = 0; i < l; i++) {
                requested[i] = k + i;
            }
        }
        
        // Paranoid checks.
        if (requested.length == 0 || requested.length > l) {
            throw new IllegalArgumentException("You asked for ridiculous check block indices.");
        }
        for (i = 0; i < requested.length; i++) {
            if ((requested[i] < k) ||  (requested[i] >= n)) {
                throw new IllegalArgumentException("You asked for ridiculous check block indices.");
            }
        }

        Bucket[] ret = null;
        // Allocate Buckets to hold the check blocks.
        Bucket[] checkBlocks = FECUtils.makeBuckets(bucketFactory, requested.length, blockSize, true);
        try {
            if (stripeWidth == -1) { 
                // No striping.
                FECUtils.encode(code, n, k, blocks, checkBlocks, requested);
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
                    for (i = 0; i < stripeCount; i++) {
                        System.err.println("Encoding stripe: " + i +
                                           " [" + stripeWidth + "]");
                        dataArray.setRange(i * stripeWidth, stripeWidth);
                        checkArray.setRange(i * stripeWidth, stripeWidth);
                        FECUtils.encode(code, n, k, dataStripe, checkStripe, requested);
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

    ////////////////////////////////////////////////////////////
    // REDFLAG: Remove after updating fproxy
    //
    public Bucket[] encode(int segmentNum, Bucket[] blocks) throws IOException {
        return null;
    }
    
    public SplitFile makeMetadata(String[] dataURIs, String[] checkURIs) {
        // REDFLAG: remove
        return null; 
    }
}




