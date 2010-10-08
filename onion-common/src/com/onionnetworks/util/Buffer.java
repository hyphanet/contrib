// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.util;

/**
 * A class (struct) for holding the offset and length of a byte[] buffer
 *
 *@author Justin F. Chapweske
 */
public class Buffer {

    public byte[] b;
    public int off;
    public int len;

    public Buffer(int len) {
        this(new byte[len]);
    }

    public Buffer(byte[] b) {
        this(b,0,b.length);
    }

    public Buffer(byte[] b, int off, int len) {
        if (len < 0 || off < 0 || off+len > b.length) {
            throw new ArrayIndexOutOfBoundsException("b.length="+b.length+
                                                     ",off="+off+",len="+len);
        }

        this.b = b;
        this.off = off;
        this.len = len;
    }

    /**
     *  Calling this defeats the purpose of reusing byte arrays, so only
     *  call this when you have to get a byte[].
     */
    public byte[] getBytes() {
	byte[] retval = new byte[len];
	System.arraycopy(b, off, retval, 0, len);
	return retval;
    }
    
    /**
     * If two buffers are the same length and contain the same data, then this
     * method will return true, otherwise false.
     */
    public boolean equals(Object o) {
        if (o instanceof Buffer) {
            Buffer buf = (Buffer) o;
            if (buf.len != len) {
                return false;
            }
            for (int i=0;i<len;i++) {
                if (buf.b[buf.off+i] != b[off+i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Uncomment this if you're not a coward.
     * /
    public int hashCode() {
        int retval = 0;
        for (int i=0;i<len;i++) {
            retval |= ((int)(b[off+i])) << (8 * (i & 4));
        }
        return retval;
    }
    */

    public String toString() {
      StringBuffer rep = new StringBuffer("Buffer{length: "+len+"; offset: "+off+"; ");
      for (int i = off; i<len; i++) {
	rep.append(""+i+": "+b[i]);
	if (i != len-1) rep.append(", ");
      }
      rep.append("}");
      return rep.toString();
    }

}
