package com.onionnetworks.util;

/**
 * This class provides a way to access various structures needed to check
 * the integrity of a file.
 *
 * @author Justin F. Chapweske
 */
public class FileIntegrityImpl implements FileIntegrity {

    private String algo;
    private Buffer fileHash;
    private Buffer[] blockHashes;
    private int blockSize, blockCount;
    private long fileSize;
    

    public FileIntegrityImpl(String algorithm, Buffer fileHash, 
                             Buffer[] blockHashes, long fileSize, 
                             int blockSize) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm is null");
        } else if (fileHash == null) {
            throw new NullPointerException("fileHash is null");
        } else if (blockHashes == null) {
            throw new NullPointerException("blockHashes are null");
        } else if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize < 0");
        } else if (blockSize < 0) {
            throw new IllegalArgumentException("blockSize < 0");
        }
        this.algo = algorithm;
        this.fileHash = fileHash;
        this.blockHashes = blockHashes;
        this.fileSize = fileSize;
        this.blockSize = blockSize;
        this.blockCount = Util.divideCeil(fileSize,blockSize);
        if (blockHashes.length != blockCount) {
            throw new IllegalArgumentException("Incorrect block hash count");
        }
    }

    /**
     * @return the message digest algorithm used to create the the hashes.
     */
    public String getAlgorithm() {
        return algo;
    }

    /**
     * Specifies the size of each block.  This size should be a power of 2
     * and all blocks will be this size, expect for the last block which may
     * be equal to or less than the block size (but not 0).
     *
     * @return the block size.
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * @return the size of the file.
     */
    public long getFileSize() {
        return fileSize;
    }
        
    /**
     * Returns the number of blocks that make up the file.  This value will
     * be equal to ceil(fileSize/blockSize).
     *
     * @return the block count.
     */
    public int getBlockCount() {
        return blockCount;
    }
 

    /**
     * @return the hash of the specified block.
     */
    public Buffer getBlockHash(int blockNum) {
        if (blockNum < 0 || blockNum >= blockCount) {
            throw new IllegalArgumentException("Invalide block #"+blockNum);
        }
        return blockHashes[blockNum];
    }

    /**
     * @return the hash of the entire file.
     */
    public Buffer getFileHash() {
        return fileHash;
    }
}
