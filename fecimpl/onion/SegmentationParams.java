/**
 * This class encapsulates the logic used to
 * select parameters for encoding/decoding
 * and file segmentation.
 **/

class SegmentationParams {

    public final int baseN;
    public final int baseK;
    public final int endN;
    public final int endK;
    public final int baseBlockSize;
    public final int endBlockSize;
    public final int baseStripeWidth;
    public final int endStripeWidth;
    public final int baseSegmentSize;
    public final int endSegmentSize;
    public final int segments;

    SegmentationParams(int baseN_,
                       int baseK_,
                       int endN_,
                       int endK_,
                       int baseBlockSize_,
                       int endBlockSize_,
                       int baseStripeWidth_,
                       int endStripeWidth_,
                       int baseSegmentSize_,
                       int endSegmentSize_,
                       int segments_) {

        this.baseN = baseN_;
        this.baseK = baseK_;
        this.endN = endN_;
        this.endK = endK_;
        this.baseBlockSize = baseBlockSize_;
        this.endBlockSize = endBlockSize_;
        this.baseStripeWidth = baseStripeWidth_;
        this.endStripeWidth = endStripeWidth_;
        this.baseSegmentSize = baseSegmentSize_;
        this.endSegmentSize = endSegmentSize_;
        this.segments = segments_;
    }

    SegmentationParams(long len, int redundancyNum, int redundancyDenom) {
        if ((len < 1) || (redundancyNum < 1) || (redundancyDenom < 1)) {
            throw new IllegalArgumentException("len=" + len + " redundancyNum=" + redundancyNum +
                                               " redundancyDenom=" + redundancyDenom
                                               );
        }

        // We won't try to encode more than
        // 128MB at a time.  Past that, we
        // have to segment.
        int segCount = (int)(len / C_SEGLEN);
        
        if ((len % C_SEGLEN) != 0) {
            segCount++;
        }

        segments = segCount;

        // Calculate values for base segment.
        long segLen = len;
        if (len > C_SEGLEN) {
            segLen = C_SEGLEN;
        }
        if (segLen < C_MIN_SEGLEN) {
            // Hmmm... C_MIN_SEGLEN is
            // bounded by the minimum block size.
            // We effectively add empty data blocks
            // in order to make the stats work.
            segLen = C_MIN_SEGLEN;
        }
        
        baseSegmentSize = (int)segLen;
        baseBlockSize = selectBlockSize(baseSegmentSize);
        baseK = calculateK(baseSegmentSize, baseBlockSize);
        baseN = calculateN(baseK, redundancyNum, redundancyDenom);
        baseStripeWidth = selectStripeWidth(baseSegmentSize,
                                            baseBlockSize);
       
        if (segments > 1) {
            // Calculate values for the final segment
            segLen = len % baseSegmentSize;
            if (segLen == 0) {
                endSegmentSize = baseSegmentSize; 
                endBlockSize = baseBlockSize;
                endK = baseK;
                endN = baseN;
                endStripeWidth = baseStripeWidth;
            }
            else {
                if (segLen > C_SEGLEN) {
                throw new RuntimeException("assertion failure: segLen <= " + C_SEGLEN);
                }
                if (segLen < C_MIN_SEGLEN) {
                    // Hmmm... C_MIN_SEGLEN is
                    // bounded by the minimum block size.
                    // We effectively add empty data blocks
                    // in order to make the stats work.
                    segLen = C_MIN_SEGLEN;
                }
                
                endSegmentSize = (int)segLen;
                endBlockSize = selectBlockSize(endSegmentSize);
                endK = calculateK(endSegmentSize, endBlockSize);
                endN = calculateN(endK, redundancyNum, redundancyDenom);
                endStripeWidth = selectStripeWidth(endSegmentSize,
                                                   endBlockSize);
            }
        }
        else {
            endSegmentSize = baseSegmentSize;
            endBlockSize = baseBlockSize;
            endK = baseK;
            endN = baseN;
            endStripeWidth = baseStripeWidth;
        }
        //dump(); // REDFLAG: remove
    }

    // REDFLAG: remove debugging function
    public final void dump() {
        System.err.println("----------------------------------------");
        System.err.println("baseN: " + baseN);
        System.err.println("baseK: " + baseK);
        System.err.println("endN: " + endN);
        System.err.println("endK: " + endK);
        System.err.println("baseBlockSize: " + baseBlockSize);
        System.err.println("endBlockSize: " + endBlockSize);
        System.err.println("baseStripeWidth: " + baseStripeWidth);
        System.err.println("endStripeWidth: " + endStripeWidth);
        System.err.println("baseSegmentSize: " + baseSegmentSize);
        System.err.println("endSegmentSize: " + endSegmentSize);
        System.err.println("segments: " + segments);
        System.err.println("----------------------------------------");
    }


    ////////////////////////////////////////////////////////////
    // Helper functions
    ////////////////////////////////////////////////////////////
    private final static int selectBlockSize(long segLen) {
        int blockSize;
        if (segLen < C_1M) {
            // hmmmm... unprincipled. 
            blockSize = C_128K;
        }
        else if (segLen < C_32M) {
            // 256K for up to 32MB
            blockSize = C_256K;
        }
        else if (segLen < C_64M) {
            // 512K for up to 64MB
            blockSize = C_512K;
        }
        else  {
            // 1MB for up to 128MB
            blockSize = C_1M;
        }
        return blockSize;
    }

    private final static int calculateK(long segLen, int blockSize) {
        int k = 0;
        if (segLen < C_SEGLEN) {
            k = (int)(segLen / blockSize);
            if ((segLen % blockSize) != 0) {
                k++;
            }
        }
        else {
            k =(int) ( C_SEGLEN / blockSize );
        }
        return k;
    }

    private final static int calculateN(int k, int redundancyNum, int redundancyDenom) {
        //System.err.println("k: " + k + " num: " + redundancyNum + " denom: " + redundancyDenom);

        if (k > 128) {
            // Stick with 8-bit coders.
            throw new IllegalArgumentException("k > 128!");
        }

        if (((double)redundancyNum) / redundancyDenom > 0.5) {
            // Could go a little higher.  But you don't want to do that
            // because the FEC encode/decode times go up dramatically.
            //
            // *Must* keep n under 256 so that we are using the 8-bit 
            // coders.
            throw new IllegalArgumentException("Maximum allowed redundancy is 50%.");
        }

        // REDFLAG: hmmm rounds down.
        int n = k + ((k * redundancyNum) / redundancyDenom);

        if (n == k) {
            n++; // hmmmm....
        }

        return n;
    }

    private final static int selectStripeWidth(int segLen, int blockSize) {
        int stripeWidth = -1;
        if (segLen > C_16M) {
            // Set stripe width so that we never
            // need to load more that 16Mb of
            // data blocks into memory.
            
            // REDFLAG: double check
            int stripeCount = 1;
            while (segLen / stripeCount > C_16M) {
                stripeCount = stripeCount << 1;
            }
            
            stripeWidth = blockSize / stripeCount;
            
            // REFLAG: remove
            if (blockSize % stripeCount != 0) {
                throw new RuntimeException("assertion failure: blockSize % stripeCount == 0");
            }
        }
        return stripeWidth;
    }
    private final static int C_128K = 128 * 1024; 
    private final static int C_256K = 256 * 1024; 
    private final static int C_512K = 512 * 1024; 
    private final static int C_1M = 1024 * 1024; 
    private final static int C_16M = 16 * 1024 * 1024;
    private final static int C_32M = 32 * 1024 * 1024;
    private final static int C_64M = 64 * 1024 * 1024; 
    private final static int C_128M = 128 * 1024 * 1024; 


    // REDFLAG: hack for testing segmentation
    //private final static long C_SEGLEN = 2 * C_1M;
    private final static long C_SEGLEN = C_128M;

    // REDFLAG: More smaller chunks?

    // This means that you will always
    // get at least .75M of data blocks no matter
    // how small the file is you are trying to encode.
    private final static int C_MIN_SEGLEN = 6 * C_128K;
}





