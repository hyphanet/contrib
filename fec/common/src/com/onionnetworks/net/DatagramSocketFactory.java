// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.net;

import java.net.*;
import java.io.IOException;

public abstract class DatagramSocketFactory {

    protected DatagramSocketFactory() {}

    public abstract DatagramSocket createDatagramSocket() throws IOException;
    public abstract DatagramSocket createDatagramSocket(int port) 
        throws IOException;
    public abstract DatagramSocket createDatagramSocket(int port, 
                                                        InetAddress iaddr) 
        throws IOException;
    

    public static DatagramSocketFactory getDefault() {
        return new PlainDatagramSocketFactory();
    }
}
