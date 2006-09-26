package com.onionnetworks.fec.io;

import com.onionnetworks.util.Util;

/**
 * This class contains all of the functions for performing packet, block, and
 * stripe calculations given a set of FEC parameters.  Most important are the 
 * boundary conditions which are difficult to keep straight.
 *
 * FECParameters objects are immutable, therefore they may be safely used
 * without synchronization.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 * @author Ry4an Brase (ry4an@ry4an.org)
 */
public class FECParameters {

    protected final int n,k,packetSize,numBlocks,firstGapStripe;
    protected final long fileSize;
    
    /**
     * Creates a new FECParameters instance.
     *
     * @param k The number of vanilla packets per block.
     * @param n The number of packets that the k vanilla packets can be 
     * expanded to.
     * @param packetSize The size of each packet.
     * @param fileSize The size of the original, unencoded file.
     */
    public FECParameters (int k, int n, int packetSize, long fileSize) {
        if (k <= 0 || n < k || packetSize <= 0 || fileSize <= 0) {
            throw new IllegalArgumentException
                ("Argument is < 0 or n < k :"+"k="+k+",n="+n+
                 ",packetSize="+packetSize+",fileSize="+fileSize);
        }

        this.k = k;
        this.n = n;
        this.packetSize = packetSize;
        this.fileSize = fileSize;

        // Round up after division.
        numBlocks = Util.divideCeil(fileSize,packetSize*k);

        firstGapStripe = Util.divideCeil(getUnexpandedBlockSize(numBlocks-1),
                                         packetSize);
    }
     
    /**
     * @return The size of the original, unencoded file.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * @return k, The number of vanilla packets per block.
     */
    public int getK() {
        return k;
    }

    /**
     * @return n, The number of packets that the k vanilla packets can be 
     * expanded to.
     */
    public int getN() {
        return n;
    }

    /**
     * @return The maximum (and default) size that most packets will be.
     */
    public int getPacketSize() {
        return packetSize;
    }

    /**
     * @return The maximum size (in bytes) that a stripe will be.
     */
    public int getMaxStripeSize() {
        return packetSize*numBlocks;
    }

    /**
     * @return The default number of vanilla bytes that a block will contain.
     */
    public int getUnexpandedBlockSize() {
        return k*packetSize;
    }

    /**
     * @return The maximum number of bytes that a fully encoded block can 
     * contain.
     */
    public int getExpandedBlockSize() {
        return n*packetSize;
    }

    /**
     * @return The number of blocks that this file will be partioned into.
     */
    public int getBlockCount() {
        return numBlocks;
    }

    /** 
     * @return The maximum number of stripes (N) that be created from this
     * file.
     */
    public int getNumStripes() {
        return n;
    }

    /**
     * @return The number of packets required to send across the original 
     * file.  Also the minimum number of packets required to recreate the
     * original file.
     */
    public int getUnexpandedPacketCount() {
	return Util.divideCeil(fileSize,packetSize);
    }

    /**
     * @param blockNum The blockNum for which to count packets.
     *
     * @return The number of packets requried to send across the original
     * block.
     */
    public int getUnexpandedPacketCount(int blockNum) {
	return Util.divideCeil(getUnexpandedBlockSize(blockNum),packetSize);
    }
    
    /**
     * @param whichStripe The stripe for which we are counting packets.
     *
     * @return The number of packets in <code>whichStripe</code>.  Most of the
     * time this will be equal to <code>numBlocks</code>, but if this stripe
     * lands on a gap in the last block then it will contain <code>numBlocks-1
     * </code>
     */
    public int getStripePacketCount(int whichStripe) {
        if (whichStripe >= firstGapStripe && whichStripe < k) {
            return numBlocks-1;
        } else {
            return numBlocks;
        }
    }
    
    /**
     * @param whichBlock The block which the packet is in.
     * @param whichStripe The stripe which the packet is in, or in other words
     * the index of the packet within <code>whichBlock</code>
     *
     * @return The size of the packet.  Normally the packet size will be
     * the same as <code>getPacketSize()</code>.  But if the packet is in
     * the last block then it may be smaller.  If the packet is in the last
     * block and is in a gap between the end of the file and K, then it's size
     * will be 0.  Also if the packet is the last packet in the file (the 
     * packet right before the gap), then the packetSize > 0 && < maxPacketSize
     */
    public int getPacketSize(int whichBlock, int whichStripe) {
        if (whichBlock == numBlocks-1) {
            if (whichStripe >= firstGapStripe && whichStripe < k) {
                return 0;
            }
            if (whichStripe == firstGapStripe-1) {
                return ((int)fileSize) % packetSize;
            }
        } 
        return packetSize;
    }

    /**
     * @param whichStripe The stripe for which we are finding the size of.
     *
     * @return The size of the stripe (in bytes).  Normally the stripe size
     * will simply be <code>packetSize*numBlocks</code> But if the stripe falls
     * on a gap it will be less.
     */
    public long getStripeSize(int whichStripe) {
        if (getStripePacketCount(whichStripe) == numBlocks-1) {
            return packetSize*(numBlocks-1);
        } else if (whichStripe == firstGapStripe-1) {
            if (fileSize % packetSize != 0) {
                return (packetSize*(numBlocks-1)) + (fileSize % packetSize);
            }
        } 
            
        return packetSize*numBlocks;
    }

    /**
     * @param whichBlock The block for which we are finding the size of.
     * 
     * @return The size of the unexpanded block.  Normally this will simply
     * be <code>k*packetSize</code>.  But if this is the last block then
     * it may be less.
     */
    public int getUnexpandedBlockSize(int whichBlock) {
        if (whichBlock == numBlocks-1) {
            if (numBlocks == 1) {
                return (int) fileSize;
            } else {
                return ((int) fileSize) - (k*(numBlocks-1)*packetSize);
            }
        }
        return k*packetSize;
    }

    /**
     * Padding packets are empty packets that should never been read or
     * written and shouldn't be sent across the network (they are all '0's)
     *
     * @param blockNum The blockNum of the packet to check
     * @param stripeNum The stripeNum of the packet to check
     *
     * @return true if this packet is a padding packet.
     */
    public boolean isPaddingPacket(int blockNum, int stripeNum) {
        return blockNum == numBlocks-1 && 
            stripeNum >= getUnexpandedPacketCount(blockNum) &&
            stripeNum < k;
    }

    public int hashCode() {
        return k * n * (int) fileSize * packetSize;
    }

    public boolean equals(Object obj) {
        if (obj instanceof FECParameters) {
            FECParameters f2 = (FECParameters) obj;
            if (f2.k == k && f2.n == n && f2.fileSize == fileSize && 
                f2.packetSize == packetSize) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return "FECParameters(k="+k+",n="+n+",packetSize="+packetSize+
            ",fileSize="+fileSize+")";
    }
}
