import freenet.client.FECDecoder;
import freenet.support.Bucket;
import freenet.support.BucketFactory;
import freenet.support.StripedBucketArray;
import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.DefaultFECCodeFactory;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
import java.io.IOException;

// REDFLAG: remove after fproxy cleanup
import freenet.support.BucketSink;
import freenet.client.metadata.SplitFile;

/*
  This code is distributed under the GNU Public Licence (GPL)
  version 2.  See http://www.gnu.org/ for further details of the GPL.
*/

/**
 * A FECDecoder implementation written on top of the
 * Onion Networks Java FEC Library v1.0.2.
 *
 * @author giannij
 **/
public class OnionFECDecoder extends OnionFECBase implements FECDecoder {

    private Hashtable buckets = new Hashtable();

    public OnionFECDecoder() {
        // 50% redundancy
        this(OnionFECEncoder.DEFAULT_NAME,
             OnionFECEncoder.DEFAULT_NUM,
             OnionFECEncoder.DEFAULT_DENOM);
    }

    // Used by subclasses to define new decoders with different
    // parameters.
    protected OnionFECDecoder(String name, 
                              int redundancyNum,
                              int redundancyDenom) {
        super(name, redundancyNum, redundancyDenom);
    }

    public void release() {
        releaseBuckets();
        super.release();
    }

    // Caller must not continue to stuff buckets into 
    // the decoder while decode() is running.

    // Caller is reponsible for keeping track of which 
    // segment this Bucket belongs to.

    // REDFLAG: hmmm make sure clients halt on IOException
    // Owns buckets and is responsible for deleting them.
    public void putBucket(Bucket bucket, int number) throws IOException {
        // REDFLAG: remove debugging code?
        Integer index = new Integer(number);
        if (buckets.get(index) != null) {
            throw new IOException("You already put bucket: " + number);
        }
        buckets.put(index, bucket);
    }

    // REDFLAG: DOC
    // REQUIRES: decodedIndices.length == decodedBlocks.length == missingBlocks()
    public boolean decode(int [] decodedIndices,
                          Bucket[] decodedBlocks) throws IOException {

        if (decodedIndices.length != decodedBlocks.length) {
            return false; // Illegal arguments
        }

        if (decodedIndices.length == 0) {
            System.err.println("OnionFECDecoder.decode -- Refusing to decode 0 segments.");
            return false;
        }

        if (!isDecodable()) {
            return false;
        }

        Bucket[] orderedBuckets = new Bucket[n];
        for (Enumeration e = buckets.keys() ; e.hasMoreElements() ;) {
            Integer number = (Integer)e.nextElement();
	    Bucket bucket = (Bucket)buckets.get(number);
            orderedBuckets[number.intValue()] = bucket;
	}

        FECUtils.NonNulls nn = FECUtils.findNonNullBuckets(orderedBuckets, k);

        if (nn.missingIndices.length != decodedIndices.length) {
            return false; // Illegal arguments
        }

        // Only decode if we are missing data blocks.
        if (nn.missingIndices.length > 0) {
            Bucket[] decoded = null;
            try {
                // Allocate Buckets to hold the decoded blocks.
                decoded = FECUtils.makeBuckets(bucketFactory, nn.missingIndices.length, blockSize, true);

                if (stripeWidth == -1) {
                    // Do simple decode.
                    FECUtils.decode(code, nn.buckets, nn.indices, k, nn.missingIndices, decoded);
                }
                else {
                    // Do striped decode.
                    //
                    // We do this to limit RAM usage.
                    //
                    StripedBucketArray packetArray = new StripedBucketArray();
                    StripedBucketArray decodedArray = new StripedBucketArray();
                    
                    try {
                        // REDFLAG: assert( blockSize % stripeWidth == 0)
                        int stripeCount = blockSize / stripeWidth;
                        Bucket[] packetStripe = packetArray.allocate(nn.buckets);
                        Bucket[] decodedStripe = decodedArray.allocate(decoded);
                        for (int i = 0; i < stripeCount; i++) {
                            packetArray.setRange(i * stripeWidth, stripeWidth);
                            decodedArray.setRange(i * stripeWidth, stripeWidth);
                            FECUtils.decode(code, packetStripe, nn.indices, 
                                            k, nn.missingIndices,decodedStripe);
                        }
                    }
                    finally {
                        packetArray.release();
                        decodedArray.release();
                    }
                }
                
                int index = 0;
                for (int i = 0; i < k ; i++) {
                    if (orderedBuckets[i] == null) {
                        decodedIndices[index] = i;
                        decodedBlocks[index] = decoded[index];
                        decoded[index] = null;
                        index++;
                    }
                    // The caller owns the bucket now.
                    // Make sure that we don't try to free it.
                    buckets.remove(new Integer(i));
                }
                decoded = null;
            }
            finally {
                FECUtils.freeBuckets(bucketFactory, decoded);
                releaseBuckets();
            }
        }
        // else {
        // Hmmm... client asked us to decode even though they have all the blocks.
        // They are dumb, but should we really fail in this case?
        return true;
    }


    public boolean setSegment(int seg) {
        if (currentSegment != seg) {
            // Make sure buckets from previous segment
            // are released.
            releaseBuckets();
            return super.setSegment(seg);
        }
        return true;
    }

    // Returns true as soon as enough blocks
    // are available to decode.
    public boolean isDecodable() {
        return buckets.size() >= k;
    }

    // REDFLAG: document
    public int missingBlocks() {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (buckets.get(new Integer(i)) != null) {
                count++;
            }
        }
        return n - count;
    }

    ////////////////////////////////////////////////////////////
    private void releaseBuckets() {
        // Release all buckets.
	for (Enumeration e = buckets.elements() ; e.hasMoreElements() ;) {
	    Bucket bucket = (Bucket)e.nextElement();
	    try {
		bucketFactory.freeBucket(bucket);
	    }
	    catch(IOException ioe) {
		// NOP
	    }
	}
	buckets.clear();
    }

    ////////////////////////////////////////////////////////////
    // REDFLAG: Remove these functions
    public boolean init(SplitFile sf, BucketSink sink,
                        BucketFactory factory) {
        return false;
    }
        
    public boolean decode() throws IOException {
        return false;
    }
}












