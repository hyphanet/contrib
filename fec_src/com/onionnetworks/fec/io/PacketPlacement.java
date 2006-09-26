package com.onionnetworks.fec.io;

import java.util.*;
import org.apache.log4j.Category;

/**
 * This class allocates and tracks how packets are written to disk.  It
 * is fully synchronized to safely support multi-threaded access.  Its
 * data structures and operations are fairly well optimized.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class PacketPlacement {

    public static final short DECODED_BLOCK = -1;

    static Category cat = Category.getInstance(PacketPlacement.class.
					       getName());
    
    FECParameters params;

    int decodedBlockCount, totalPacketCount, extEntryCount;

    // Tracks the number of packets in each block, if the block is decoded then
    // it is set to DECODED_BLOCK.
    short[] packetCount;

    // List of all packets for each block in order written.
    ArrayList[] entries;

    // Maps block/stripe to packetIndexes.
    HashMap[] revEntries;

    int k, blockCount;


    /**
     * Creates a new PacketPlacement object.
     *
     * @param params The FECParameters for this download.
     */
    public PacketPlacement(FECParameters params) {
        
	this.params = params;
        
	k = params.getK();
        blockCount = params.getBlockCount();
        
        packetCount = new short[blockCount];
        entries = new ArrayList[blockCount];
        revEntries = new HashMap[blockCount];
    }

    /**
     * @return the number of blocks that have been decoded and written 
     * back to disk.
     */
    public synchronized int getDecodedBlockCount() {
        return decodedBlockCount;
    }

    /**
     * Signify that this block has been decoded.
     *
     * @param blockNum The blockNum of the decoded block.
     */
    public synchronized void setBlockDecoded(int blockNum) {
        if (packetCount[blockNum] == DECODED_BLOCK) {
            throw new IllegalStateException
                ("This block is already decoded:"+blockNum);
        }
        // This block has been decoded.
        packetCount[blockNum] = DECODED_BLOCK;
        decodedBlockCount++;
        
        // Update the forward and reverse entries
        entries[blockNum] = null;
        revEntries[blockNum] = null;
    }

    /**
     * @param The blockNum of the block to check.
     * @return true if the block has been decoded.
     */
    public synchronized boolean isBlockDecoded(int blockNum) {
        return packetCount[blockNum] == DECODED_BLOCK;
    }

    /**
     * @param blockNum the blockNum for which to count packets.
     *
     * @return The number of packets in the block.
     * PacketPlacement.DECODED_BLOCK (-1) if the block is decoded.
     */ 
    public synchronized int getPacketCount(int blockNum) {
        return packetCount[blockNum];
    }

    /**
     * @return The total number of packets that have been written to disk. 
     */ 
    public synchronized int getWrittenCount() {
        return totalPacketCount;
    }

    /**
     * Perform a reverse lookup on the index to find the index of a specific
     * packet.
     * 
     * @param blockNum The blockNum of the packet.
     * @param stripeNum The stripeNum of the packet.
     *
     * @return the packetIndex if the packet is on disk, else -1
     */
    public synchronized int getPacketIndex(int blockNum, int stripeNum) {
        // If its decoded then the vanilla packets are on disk.
        if (isBlockDecoded(blockNum)) {
            return (stripeNum >= 0 && stripeNum < k) ? 
                blockNum*k+stripeNum : -1;
        }

        HashMap h = revEntries[blockNum];

        // its not decoded, so a null h means that there are no packets.
        if (h != null) {
            Integer pi = (Integer) h.get(new Integer(stripeNum));
            if (pi != null) {
                return pi.intValue();
            }  
        }
        return -1;
    }

    /**
     * Add a new entry to an available slot.  BlockFulls will be thrown
     * in preference to DuplicatePackets.
     *
     * @param blockNum The blockNum of the entry.
     * @param stripeNum the stripeNum of the entry.
     *
     * @return the packetIndex which the data will be written to.
     *
     * @throws DuplicatePacketException When there is an attempt to write a 
     *   packet a second time.
     * @throws BlockFullException When the desired block is already full.
     */
    public synchronized int addPacketEntry(int blockNum, int stripeNum) {

        // block already decoded
        if (isBlockDecoded(blockNum)) {
            throw new IllegalStateException
                ("block already decoded, blockNum="+blockNum+",stripeNum="+
                 stripeNum);
        } 

        // duplicate packet.
        if (getPacketIndex(blockNum,stripeNum) != -1) {
            throw new IllegalArgumentException
                ("Duplicate entry for blockNum="+blockNum+",stripeNum="+
                 stripeNum+",packetIndex="+getPacketIndex(blockNum,stripeNum));
        }
        
        short blockPacketCount = (short) getPacketCount(blockNum);
        int packetIndex = -1;

	ArrayList blockEntries = entries[blockNum];
  
	// first packet for this block.
	if (blockEntries == null) { 
	    blockEntries = new ArrayList();
	    entries[blockNum] = blockEntries;
	}
	
	// Add the entry.
	blockEntries.add(new Integer(stripeNum));

        // check to see if this'll be an extended entry.
        if (blockPacketCount < k) {
	    // regular entry in the block's area on disk.
            packetIndex = blockNum*k+blockPacketCount; 
        } else {
            // extended entry at the end of the file.
            packetIndex = blockCount*k+extEntryCount;
            extEntryCount++;
	}

        HashMap revBlockEntries = revEntries[blockNum];
        if (revBlockEntries == null) {
            revBlockEntries = new HashMap();
            revEntries[blockNum] = revBlockEntries;
        }
        revEntries[blockNum].put(new Integer(stripeNum),
                                 new Integer(packetIndex));

        packetCount[blockNum]++;
        totalPacketCount++;        
        
        return packetIndex;
    }
    
    /*
     * Returns the list of stripes available for a specific blockNum.  These
     * stripes will be in the order that they were written on disk.
     * 
     * @param blockNum The blockNum for which you want stripes.
     * 
     * @return an int[] containing the stripes found for that blockNum.
     */
    public synchronized int[] getStripeNums(int blockNum) {
	return getStripeNums(blockNum,entries[blockNum].size());
    }
   
    /*
     * Returns the list of stripes available for a specific blockNum.  These
     * stripes will be in the order that they were written on disk.
     * 
     * @param blockNum The blockNum for which you want stripes.
     * @param count The number of stripes to return. 
     * 
     * @return an int[] containing the stripes found for that blockNum.
     */
    public synchronized int[] getStripeNums(int blockNum, int count) {
	int[] result = new int[count];
	for (int i=0;i<result.length;i++) {
	    result[i] = ((Integer) entries[blockNum].get(i)).intValue();
	}
	return result;
    }
}
