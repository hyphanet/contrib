package com.onionnetworks.io;

import com.onionnetworks.util.Buffer;

/**
 * This interface provides access to a number of block hashes for a file.  
 * These hashes can be used to check the integrity of a single block and the
 * overall file.
 */
public interface FileIntegrity {

    /**
     * @return the message digest algorithm used to create the the hashes.
     */
    public String getAlgorithm();

    /**
     * Specifies the size of each block.  This size should be a power of 2
     * and all blocks will be this size, expect for the last block which may
     * be equal to or less than the block size (but not 0).
     *
     * @return the block size.
     */
    public int getBlockSize();

    /**
     * @return the size of the file.
     */
    public long getFileSize();

    /**
     * Returns the number of blocks that make up the file.  This value will
     * be equal to ceil(fileSize/blockSize).
     *
     * @return the block count.
     */
    public int getBlockCount();

    /**
     * @return the hash of the specified block.
     */
    public Buffer getBlockHash(int blockNum);

    /**
     * @return the hash of the entire file.
     */
    public Buffer getFileHash();

}
