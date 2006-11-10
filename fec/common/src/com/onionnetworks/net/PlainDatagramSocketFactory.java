// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.net;

import java.net.*;
import java.io.*;

public class PlainDatagramSocketFactory extends DatagramSocketFactory {

    public DatagramSocket createDatagramSocket() throws IOException {
        return new DatagramSocket();
    }

    public DatagramSocket createDatagramSocket(int port) throws IOException {
        return new DatagramSocket(port);
    }

    public DatagramSocket createDatagramSocket(int port, InetAddress iaddr) 
        throws IOException {
        
        return new DatagramSocket(port, iaddr);
    }
}
