import freenet.FieldSet;
import freenet.client.FECDecoder;
import freenet.client.metadata.SplitFile;
import freenet.support.Bucket;
import freenet.support.BucketSink;
import freenet.support.BucketFactory;
import freenet.support.StripedBucketArray;

import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.DefaultFECCodeFactory;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;

import java.io.IOException;

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
public class NativeOnionFECDecoder implements FECDecoder {

    private SplitFile sf = null;
    private BucketSink sink = null;
    private BucketFactory bucketFactory = null;
    private Hashtable buckets = new Hashtable();
    private FECCode code = null;

    private int n = -1;
    private int k = -1;
    private int baseN = -1;
    private int baseK = 1;
    private int endN = -1;
    private int endK = 1;

    private int len = -1;
    private int blockSize = -1;
    private int segments = -1;
    private int stripeWidth = -1;
    private int currentSegment = -1;

    // Important: Keep in synch. with OnionFECEncoder.
    private final static int SEGMENTLENGTH = 128 * 1024 * 1024;
    private final static String DECODER_NAME = "OnionDecoder_0";

    private boolean readSplitFileParams(SplitFile sf) {

        String name = sf.getDecoderName();
        if (name == null) {
            // REDFLAG: later, backwards compatibility
            //          with original param-less cli generated
            //          SplitFiles?
            return false;
        }

        if (!name.equals(DECODER_NAME)) {
            // We don't know how to decode this format.
            return false;
        }

        FieldSet params = sf.getDecoderParams();
        if (params == null) {
            // Something's wrong.
            return false;
        }
        
        len = sf.getSize();
        blockSize = sf.getBlockSize();

        try {
            baseN = Integer.parseInt(params.get("baseN"), 16);
            baseK = Integer.parseInt(params.get("baseK"), 16);
            endN = Integer.parseInt(params.get("endN"), 16);
            endK = Integer.parseInt(params.get("endK"), 16);
            segments = Integer.parseInt(params.get("segments"), 16);
            stripeWidth = Integer.parseInt(params.get("stripeWidth"));
        }
        catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }

    public boolean init(SplitFile sf, BucketSink sink,
                        BucketFactory factory) {
        release();

        if (!readSplitFileParams(sf)) {
            // We don't know how to decode this 
            // SplitFile.
            return false;
        }

        this.sf = sf; // REDFLAG: Nesc?
        this.sink = sink;
        this.bucketFactory = factory;

        n = baseN;
        k = baseK;
        currentSegment = 0;

        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
        code = f.createFECCode(k, n);
        return code != null;
    }

    public void release() {
        releaseBuckets();
        code = null;
    }

    public int getBlockSize() { return blockSize; }
    
    public int getK() { return k; }
    public int getN() { return n; }

    public int getTotalN() {
        if (segments == 1) {
            return baseN;
        }
        else {
            return (segments - 1) * baseN + endN;
        }
    }

    public int getTotalK() {
        if (segments == 1) {
            return baseK;
        }
        else {
            return (segments - 1) * baseK + endK;
        }
    }

    public int getSegmentCount() { return segments; }

    public int getCurrentSegment() { return currentSegment; }

    public boolean setSegment(int segment) {
        if ((segment < 0) || (segment >= segments)) {
            return false;
        }

        if ((segments < 2) || segment == currentSegment) {
            return true; // nop
        } 

        if ((currentSegment < segments - 1) &&
            (segment < segments - 1)) {
            return true;
        }
        
        // We only switch when going from an end
        // to a non-end segment or vice versa.

        // This is horrible. Use a different code
        // for the final segment.
        int newN = baseN;
        int newK = baseK;
        if (segment == segments - 1) {
            newK = endK;
            newN = endN;
        }

        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
        FECCode newCode = f.createFECCode(newK, newN);
        
        if (newCode != null) {
            code = newCode;
            n = newN;
            k = newK;
            currentSegment = segment;
            return true;
        }

        return false;
    }

    // Caller must not continue to stuff buckets into 
    // the decoder while decode() is running.

    // Caller is reponsible for keeping track of which 
    // segment this Bucket belongs to.

    // REDFLAG: hmmm make sure clients halt on IOException
    // Owns buckets and is responsible for deleting them.
    public void putBucket(Bucket bucket, int number) throws IOException {
        buckets.put(new Integer(number), bucket);
        // Not sure this is a good idea.
        // Tell the sink about data as soon as we have it.
        // The sink *must not* free the bucket before decode().
        if (number < k) {
            sink.putBucket(bucket, number);
            System.err.println("Wrote block before FEC decode: " + number);
        }
    }

    // Blocking call.  Can take a very long time.
    public boolean decode() throws IOException {
        if (!isDecodable()) {
            return false;
        }

        if (buckets.size() < k) {
            return false;
        }
        
        Bucket[] orderedBuckets = new Bucket[n];
        for (Enumeration e = buckets.keys() ; e.hasMoreElements() ;) {
            Integer number = (Integer)e.nextElement();
	    Bucket bucket = (Bucket)buckets.get(number);
            orderedBuckets[number.intValue()] = bucket;
	}

        FECUtils.NonNulls nn = FECUtils.findNonNullBuckets(orderedBuckets, k);
        
        // Only decode if we are missing data blocks.
        if (nn.missingIndices.length > 0) {
            Bucket[] decoded = null;
            try {
                // Allocate Buckets to hold the decoded blocks.
                decoded = FECUtils.makeBuckets(bucketFactory, nn.missingIndices.length, blockSize, true);

                if (stripeWidth == -1) {
                    // Do simple decode.
                    System.err.println("before bs: " + blockSize + " " + decoded[0].size());
                    FECUtils.decode(code, nn.buckets, nn.indices, k, nn.missingIndices, decoded);
                    System.err.println("after bs: " + blockSize + " " + decoded[0].size());

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
                
                // All other data blocks should have already
                // been sent.
                int index = 0;
                for (int i = 0; i < k ; i++) {
                    if (orderedBuckets[i] == null) {
                        sink.putBucket(decoded[index], i);
                        System.err.println("OnionFECDecoder.decode -- sent: " + i);
                        
                        decoded[index] = null;
                        index++;
                    }
                    // The sink owns the bucket now.
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

        return true;
    }

    // Returns true as soon as enough blocks
    // are available to decode.
    public boolean isDecodable() {
        return buckets.size() >= k;
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
}












