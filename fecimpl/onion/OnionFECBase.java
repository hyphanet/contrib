import freenet.FieldSet;
import freenet.client.FECDecoder;
import freenet.support.Bucket;
import freenet.support.BucketFactory;

import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.DefaultFECCodeFactory;

/*
  This code is distributed under the GNU Public Licence (GPL)
  version 2.  See http://www.gnu.org/ for further details of the GPL.
*/

/**
 * Implementation base class that contains functionality 
 * common to Onion codec based encoders and decoders.
 **/
class OnionFECBase {
    // coder parameters
    protected String name = null;
    protected int redundancyNumerator = -1;
    protected int redundancyDenominator = -1;
    protected SegmentationParams segParams = null;

    // Per segment values
    protected int n = -1;
    protected int k = -1;
    protected int blockSize = -1;
    protected int stripeWidth = -1;
    protected int currentSegment = -1;

    protected FECCode code = null;

    protected BucketFactory bucketFactory = null;

    protected OnionFECBase(String name, int redundancyNumerator, int redundancyDenominator) {
        this.name = null;
        this.name = name;
        this.redundancyNumerator = redundancyNumerator;
        this.redundancyDenominator = redundancyDenominator;
    }
    
    // Leave a hook for subclasses to provide alternate SegmentationParams
    // implementations.  e.g. for legacy support.
    protected SegmentationParams calculateParams(long fileSize) {
        return new SegmentationParams(fileSize, 
                                      redundancyNumerator,
                                      redundancyDenominator);
    }

    public boolean init(long fileSize,
                        BucketFactory factory) {
        release();

        this.name = name;
        this.segParams = calculateParams(fileSize);

        this.bucketFactory = factory;

        n = segParams.baseN;
        k = segParams.baseK;
        blockSize = segParams.baseBlockSize;
        stripeWidth = segParams.baseStripeWidth;

        currentSegment = 0;

        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
        code = f.createFECCode(k, n);
        return code != null;
    }
    
    // Subclasses should override to completely release
    // any allocated resources
    public void release() {
        // REDFLAG: Debugging hack
        //System.err.println("--- RELEASE FROM ---");
        //(new Exception("Track release calls")).printStackTrace();
        //System.err.println("--------------------");
        code = null; 
    }

    public String getName() { return name; }

    public int getBlockSize(int segment) {
        if ((segParams.segments == 1) || (segment < segParams.segments - 1)) {
            return segParams.baseBlockSize;
        }
        return segParams.endBlockSize;
    }
    public int getBlockSize() { return getBlockSize(currentSegment); }

    public int getCheckBlockSize(int segment) {
        // Checkblock and data block sizes are always the same.
        return getBlockSize(segment);
    }
    public int getCheckBlockSize() { return getCheckBlockSize(currentSegment); }
    public int getK() { return k; }
    public int getN() { return n; }

    // Hmmmm... A little knobby, but ok.
    public int getN(int segment) {
        if ((segParams.segments == 1) || (segment < segParams.segments - 1)) {
            return segParams.baseN;
        }

        return segParams.endN;
    }

    public int getK(int segment) {
        if ((segParams.segments == 1) || (segment < segParams.segments - 1)) {
            return segParams.baseK;
        }

        return segParams.endK;
    }

    public int getTotalN() {
        if (segParams.segments == 1) {
            return segParams.baseN;
        }
        else {
            return (segParams.segments - 1) * segParams.baseN + segParams.endN;
        }
    }

    public int getTotalK() {
        if (segParams.segments == 1) {
            return segParams.baseK;
        }
        else {
            return (segParams.segments - 1) * segParams.baseK + segParams.endK;
        }
    }

    public int getSegmentCount() { return segParams.segments; }
    public int getCurrentSegment() { return currentSegment; }
    
    public int getSegmentSize() { return segParams.baseSegmentSize; }

    public int getSegmentSize(int segment) {
        if ((segParams.segments == 1) || (segment < segParams.segments - 1)) {
            return segParams.baseSegmentSize;
        }

        return segParams.endSegmentSize;
    }

    // REDFLAG: max sure decoder subclasses release buckets
    public boolean setSegment(int segment) {
        if ((segment < 0) || (segment >= segParams.segments)) {
            return false;
        }

        if ((segParams.segments < 2) || segment == currentSegment) {
            return true; // nop
        } 

        
        // We only switch when going from an end
        // to a non-end segment or vice versa.
        if ((currentSegment < segParams.segments - 1) &&
            (segment < segParams.segments - 1)) {
            return true;
        }

        // Use a different code for the final segment.
        int newN = segParams.baseN;
        int newK = segParams.baseK;
        int newBlockSize = segParams.baseBlockSize;
        int newStripeWidth = segParams.baseStripeWidth;

        if (segment == segParams.segments - 1) {
            newK = segParams.endK;
            newN = segParams.endN;
            newBlockSize = segParams.endBlockSize;
            newStripeWidth = segParams.endStripeWidth;
        }

        DefaultFECCodeFactory f = new DefaultFECCodeFactory();
        FECCode newCode = f.createFECCode(newK, newN);
        
        if (newCode != null) {
            code = newCode;
            n = newN;
            k = newK;
            blockSize = newBlockSize;
            stripeWidth = newStripeWidth;

            currentSegment = segment;
            return true;
        }

        return false;
    }
}








