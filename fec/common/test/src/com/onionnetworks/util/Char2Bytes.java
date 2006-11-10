package com.onionnetworks.util;

import java.util.Random;

public class Char2Bytes {
    static Random rand = new Random();

    public static void main(String[] args) {
        
        // GetChars
        for (int i=0;i<100;i++) {
            System.out.println(i+"/100");
	    byte[] b = createByteArray(rand.nextInt(500000) * 2);
	    char[] c = Util.getChars(b);
	    checkArrays(c,b);
        }            
         // getBytes
        for (int i=0;i<100;i++) {
            System.out.println(i+"/100");
	    char[] c = createCharacterArray(rand.nextInt(100000));
	    byte[] b = Util.getBytes(c);
	    checkArrays(c,b);
        }            
    }

    public static final char[] createCharacterArray(int len) {
        char[] b = new char[len];
        for (int i=0;i<b.length;i++) {
            b[i] = (char)(rand.nextInt(Character.MAX_VALUE + 1));
        }
        return b;
    }

    public static final byte[] createByteArray(int len) {
        byte[] b = new byte[len];
	byte min = Byte.MIN_VALUE;
        for (int i=0;i<b.length;i++) {
            b[i] = (byte)(rand.nextInt(-2 * min) + min);
        }
        return b;
    }

    public static final void checkArrays(char[] chars, byte[] bytes) {
	if (chars.length * 2 != bytes.length) {
	    System.err.println(chars.length * 2 + " != " + bytes.length);
	    throw new RuntimeException("Shit! regression!");
	}
        for (int i = 0; i < chars.length; i++) {
	    if ( (byte)((chars[i] & 0xFF00) >> 8) != bytes[2 * i] ) {
		throw new RuntimeException("Shit! regression!");
	    }
	    if ( (byte)(chars[i] & 0xFF) != bytes[2 * i + 1] ) {
		throw new RuntimeException("Shit! regression!");
	    }
        }
    }
}
                
