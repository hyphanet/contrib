package com.onionnetworks.fec;

//import java.security.AccessController;
//import sun.security.action.*;
import com.onionnetworks.util.*;

/**
 * This class is the frontend for the JNI wrapper for the C implementation of
 * the FEC Codes.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class Native8Code extends FECCode {
    
    // One must be very very careful not to let code escape, it stores the
    // memory address of a fec_parms struct and if modified could give an
    // attacker the ability to point to anything in memory.
    private int code;
    
    static {
        String path = NativeDeployer.getLibraryPath
            (Native8Code.class.getClassLoader(),"fec8");
        if (path != null) {
            System.load(path);
        } else {
            System.out.println("Unable to find native library for fec8");
        }
    }
    
    public Native8Code(int k, int n) {
        super(k,n);
        code = nativeNewFEC(k,n);
    }

    protected void encode(byte[][] src, int[] srcOff, byte[][] repair, 
                          int[] repairOff, int[] index, int packetLength) {
        
        nativeEncode(code,src,srcOff,index,repair,repairOff,k,packetLength);
    }

    protected void decode(byte[][] pkts, int[] pktsOff,
                          int[] index, int packetLength, boolean inOrder) {
        // We need to shuffle at this point so that the Java byte[][] stays
        // in sync with what happens in native land.
        if (!inOrder) {
            shuffle(pkts,pktsOff,index,k);
        }
        nativeDecode(code,pkts,pktsOff,index,k,packetLength);
    }

    protected native void nativeEncode
        (int code, byte[][] src, int[] srcOff, int[] index, byte[][] repair, 
         int[] repairOff, int k, int packetLength);

    protected native void nativeDecode(int code, byte[][] pkts, int[] pktsOff,
                                       int[] index, int k, int packetLength);

    protected synchronized native int nativeNewFEC(int k, int n);

    protected synchronized native void nativeFreeFEC(int code);

    protected void finalize() throws Throwable {
        nativeFreeFEC(code);
    }

    public String toString() {
        return new String("Native8Code[k="+k+",n="+n+"]");
    }
}
