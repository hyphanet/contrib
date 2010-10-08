package com.onionnetworks.util;

import java.util.Random;

public class Hex2Bytes {
    static Random rand = new Random();

    public static void main(String[] args) {
        
        for (int i=0;i<100;i++) {
            System.out.println(i+"/100");
	    byte[] b = createByteArray(rand.nextInt(500000) * 2);
            String hex = Util.bytesToHex(b);
            if (! Util.arraysEqual(b, 0, Util.hexToBytes(hex),
                            0, b.length)) {
                throw new RuntimeException("Crap!: " + hex);
            }
        }            
    }

    public static final byte[] createByteArray(int len) {
        byte[] b = new byte[len];
	byte min = Byte.MIN_VALUE;
        for (int i=0;i<b.length;i++) {
            b[i] = (byte)(rand.nextInt(-2 * min) + min);
        }
        return b;
    }
}
                
