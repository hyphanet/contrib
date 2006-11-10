// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.util;

import java.util.*;
import java.lang.reflect.*;

public class Util {

    private static final int MAX_ZERO_COPY = 16384;
    private static byte[] zeroBytes;
    private static char[] zeroChars;
    private static char[] hexDigit = new char[] 
        {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
    
    // Must be global for shuffle
    public static final Random rand = new Random(); 

    public static final byte[] getBytes(int i) {
        byte[] b = new byte[4];
        b[0] = (byte) ((i >>> 24) & 0xFF);
        b[1] = (byte) ((i >>> 16) & 0xFF);
        b[2] = (byte) ((i >>> 8) & 0xFF);
        b[3] = (byte) ((i >>> 0) & 0xFF);
        return b;
    }

    public static final int getInt(byte[] b) {
        return (((b[0]&0xFF) << 24) | ((b[1]&0xFF) << 16)
	    | ((b[2]&0xFF) << 8) | (b[3]&0xFF));
    }

    /**
     * Fills in a range of an array with 0's.
     *
     * This method is meant to be a clone of the functionality of the C
     * bzero function.  
     *
     * @param b The byte array to be 0'd
     * @param off The offset within b to begin 0'ing
     * @param len The number of bytes to be 0'd
     */
    public static final void bzero(byte[] b, int off, int len) {
        if (zeroBytes == null) {
            zeroBytes = new byte[64];
        }
        if (len < zeroBytes.length) {
            System.arraycopy(zeroBytes,0,b,off,len);
            return;
        } else {
            System.arraycopy(zeroBytes,0,b,off,zeroBytes.length);
        }            

        int zeroLength = zeroBytes.length;
        do {
            int delta = len-zeroLength;
            int copyLength = zeroLength > delta ? delta : zeroLength;
            if (copyLength > MAX_ZERO_COPY) {
                copyLength = MAX_ZERO_COPY;
            }
            // We copy from close to the current position so we aren't
            // thrashing mem for really large buffers.
            System.arraycopy(b,off+zeroLength-copyLength,b,off+zeroLength,
                             copyLength);
            zeroLength+=copyLength;
        } while(zeroLength < len);
    }

    /**
     * Fills in a range of an array with 0's.
     *
     * This method is meant to be a clone of the functionality of the C
     * bzero function.  
     *
     * @param b The char array to be 0'd
     * @param off The offset within b to begin 0'ing
     * @param len The number of chars to be 0'd
     */
    public static final void bzero(char[] b, int off, int len) {
        if (zeroChars == null) {
            zeroChars = new char[64];
        }
        if (len < zeroChars.length) {
            System.arraycopy(zeroChars,0,b,off,len);
            return;
        } else {
            System.arraycopy(zeroChars,0,b,off,zeroChars.length);
        }            

        int zeroLength = zeroChars.length;
        do {
            int delta = len-zeroLength;
            int copyLength = zeroLength > delta ? delta : zeroLength;
            if (copyLength > MAX_ZERO_COPY) {
                copyLength = MAX_ZERO_COPY;
            }
            // We copy from close to the current position so we aren't
            // thrashing mem for really large buffers.
            System.arraycopy(b,off+zeroLength-copyLength,b,off+zeroLength,
                             copyLength);
            zeroLength+=copyLength;
        } while(zeroLength < len);
    }

    public static final String getSpaces(int num) {
	StringBuffer sb = new StringBuffer();
	for (int i=0;i<num;i++) {
	    sb.append(" ");
	}
	return sb.toString();
    }

    public static boolean arraysEqual(int[] arr1, int start1, 
                                      int[] arr2, int start2, int len) {
	if (arr1 == arr2 && start1 == start2) {
	    return true;
	}
        for (int i=len-1;i>=0;i--) {
            if (arr1[start1+i] != arr2[start2+i]) {
                return false;
            }
        }
        return true;
    }    

    public static boolean arraysEqual(long[] arr1, int start1, 
                                      long[] arr2, int start2, int len) {
	if (arr1 == arr2 && start1 == start2) {
	    return true;
	}
        for (int i=len-1;i>=0;i--) {
            if (arr1[start1+i] != arr2[start2+i]) {
                return false;
            }
        }
        return true;
    }    

    public static boolean arraysEqual(char[] arr1, int start1, 
                                      char[] arr2, int start2, int len) {
	if (arr1 == arr2 && start1 == start2) {
	    return true;
	}
        for (int i=len-1;i>=0;i--) {
            if (arr1[start1+i] != arr2[start2+i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean arraysEqual(byte[] arr1, int start1, 
                                      byte[] arr2, int start2, int len) {
	if (arr1 == arr2 && start1 == start2) {
	    return true;
	}
        for (int i=len-1;i>=0;i--) {
            if (arr1[start1+i] != arr2[start2+i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fisher-Yates shuffle.
     */
    public static final void shuffle(int[] list) {
        for (int i = list.length-1; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            if (i == j) {
                continue;
            }
            int tmp = list[i];
            list[i] = list[j];
            list[j] = tmp;
        }
    }

    public static final void shuffle(boolean[] list) {
        for (int i = list.length-1; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            if (i == j) {
                continue;
            }
            boolean tmp = list[i];
            list[i] = list[j];
            list[j] = tmp;
        }
    }

    public static final void shuffle(Object[] list) {
        for (int i = list.length-1; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            if (i == j) {
                continue;
            }
            Object tmp = list[i];
            list[i] = list[j];
            list[j] = tmp;
        }
    }


    public static final byte[] getBytes(char[] chars) {
	byte[] retval = new byte[chars.length*2];
        arraycopy(chars,0,retval,0,retval.length);
        return retval;
    }

    public static final char[] getChars(byte[] bytes) {
        int len = bytes.length;
	if (len % 2 != 0) {
	    throw new IllegalArgumentException("Input array.length non-even.");
	}
        char[] retval = new char[len/2];
        arraycopy(bytes,0,retval,0,len);
        return retval;
    }

    public static final void arraycopy(char[] chars, int charOff, 
                                       byte[] bytes, int byteOff, 
                                       int numBytes) {
	int indexCounter = byteOff;
        int loopMax = numBytes/2+charOff;
	for (int i=charOff; i<loopMax; i++) {
	    bytes[indexCounter++] = (byte)((chars[i] & 0xFF00) >> 8);
	    bytes[indexCounter++] = (byte)(chars[i] & 0xFF);
	}
        // copy the straggler, if any.
        if (numBytes % 2 != 0) {
            bytes[indexCounter] = (byte)((chars[loopMax] & 0xFF00) >> 8);
        }
    }

    /** Dumps a byte array to an UNIX style hex dump.  This isn't terribly
     * efficient so you should probably try to limit it to debug code.
     * @param byte[] the byte to dump
     */
    public static String getHexDump(byte[] b) {
	int pos = 0; 
	final int INDEX_WIDTH = 7;
	final String ZEROS = "0000000"; // must be at least INDEX_WIDTH length
	StringBuffer sb = new StringBuffer();
	while (pos < b.length) {
	    if ((pos % 16) == 0) {
		if (pos > 0) {
		    sb.append("\n");
		}
		String index = Integer.toOctalString(pos);
		sb.append(ZEROS.substring(0,INDEX_WIDTH-index.length()));
		sb.append(index).append(" ");
	    } else if ((pos % 4) == 0) {
		sb.append(" ");
	    }
	    String val = Integer.toHexString(b[pos] & 0xFF);
	    if (val.length() == 1) {
		sb.append("0");
	    }
	    sb.append(val);
	    pos++;
	}
	return sb.toString();
    }

    public static final void arraycopy(byte[] bytes, int byteOff, 
                                       char[] chars, int charOff, 
                                       int numBytes) {
	int indexCounter = byteOff;
        int loopMax = numBytes/2+charOff;
	for (int i=charOff; i<loopMax; i++) {
	    chars[i] = (char)(((bytes[indexCounter++]&0xFF)<<8) 
                              | (bytes[indexCounter++]&0xFF));
	}
        // copy the straggler, if any.
        if (numBytes % 2 != 0) {
	    chars[loopMax] = (char)((bytes[indexCounter]&0xFF)<<8);
        }
    }

    /**
     * Divides and rounds up on remainder
     */
    public static int divideCeil(int num, int denom) {
        return num/denom + ((num%denom==0)?0:1);
    }
    
    public static int divideCeil(long num, long denom) {
        return (int) (num/denom + ((num%denom==0)?0:1));
    }

    /**
     * @param a The number take take the log base 2 of.
     * @return the log base 2 of the supplied number.
     */
    public static double log2(double a) {
	return Math.log(a)/Math.log(2);
    }
    
    public static String bytesToHex(Buffer b) {
        return bytesToHex(b.b,b.off,b.len);
    }

    public static String bytesToHex(byte[] in) {
        return bytesToHex(in,0,in.length);
    }

    /** Turn a byte array into a lowercase hex string
     */
    public static String bytesToHex(byte[] in, int off, int len) {
        char[] out = new char[in.length * 2];
	for (int i = 0; i < len; i++) {
            out[i*2] = hexDigit[(0xF0 & in[i+off]) >> 4]; // high nybble
	    out[i*2+1] = hexDigit[0xF  & in[i+off]]; // low nybble
	}
	return new String(out);
    }

    /** Create a byte array from a hex string
     */
    public static byte[] hexToBytes(String in) {
        int len = in.length();
    	if (len % 2 != 0) {
	    throw new IllegalArgumentException("Even length string expected.");
	}
	byte[] out = new byte[len/2];
	try {
	    for (int i = 0; i < out.length; i++) {
		out[i] = (byte)(Integer.parseInt(in.substring(i*2, i*2+2), 16));
	    }
	} catch (NumberFormatException doh) {
	    doh.printStackTrace();
	    throw new IllegalArgumentException("ParseError");
	}
	return out;
    }

    /** Check if an IP address is probably inside a NAT.  Data culled from
     * RFC 790.
     * @param byte[] the 4 byte long IP address to check
     * @return true if the address is probably inside a NAT
     */
    public static boolean isProbablyNat(byte[] addr) {
        if (addr.length != 4) {
            throw new IllegalArgumentException("Address must be 4 bytes long");
        }
	int a = 0xFF & addr[0];
	int b = 0xFF & addr[1];
	int c = 0xFF & addr[2];
	int d = 0xFF & addr[3];
	return ((a == 10)
		|| (a==192 && b==168)
		|| (a==192 && b==0 && c==1)
		|| (a==223 && b==255 && c==255));
    }

    /**
     * Class.getMethod requires exact parameters for the types.  This
     * method is more fuzzy and just finds the first one that works.  This
     * class will also prefer public classes/methods.
     */
    public static final Method getPublicMethod(Class clazz, String name, 
                                               Class[] types) 
        throws NoSuchMethodException {

        Class c = clazz;
        while (c != null) {
            if (Modifier.isPublic(c.getModifiers())) {
                Method m = getMethod(c.getMethods(),name,types);
                if (m != null) {
                    return m;
                }
            }
            
            // check the interfaces.
            Class[] interfs = clazz.getInterfaces();
            for (int a=0;a<interfs.length;a++) {
                if (!Modifier.isPublic(interfs[a].getModifiers())) {
                    continue;
                }
                Method m = getMethod(interfs[a].getMethods(),name,types);
                if (m != null) {
                    return m;
                }
            }
            // climb up the superclass chain.
            c = c.getSuperclass();
        }
        throw new NoSuchMethodException();
    }  
    

    /**
     * @return a public method that matches the signature, null if none match.
     */
    public static final Method getMethod(Method[] methods, String name,
                                         Class[] types) {

        for (int i=0;i<methods.length;i++) {
            if (Modifier.isPublic(methods[i].getModifiers()) &&
                name.equals(methods[i].getName()) && 
                types.length == methods[i].getParameterTypes().length) {
                
		if (types.length == 0) {
		    return methods[i];
		}

                for (int j=0;j<types.length;j++) {
                    if (!methods[i].getParameterTypes()[j].
                        isAssignableFrom(types[j])) {
                        break;
                    } else if (j == types.length-1) {
                        return methods[i];
                    }
                }
            }
        }
        return null;
    }

    /**
     * Creates an IntIterator from an Iterator containing Integer objects.
     */
    public static final IntIterator createIntIterator(final Iterator it) {
        return new IntIterator() {
                public boolean hasNextInt() {
                    return it.hasNext();
                }

                public int nextInt() {
                    return ((Integer) it.next()).intValue();
                }

                public void removeInt() {
                    it.remove();
                }
            };
    }
}
