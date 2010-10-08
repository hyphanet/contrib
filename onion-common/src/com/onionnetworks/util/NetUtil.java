// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.util;

import java.net.URL;
import java.net.InetAddress;
import java.io.IOException;

public class NetUtil {

    /**
     * Takes a URL whose host portion is a name and returns a list of URLs with
     * all the different IP addreses for theat host name found by
     * InetAddress.getAllByName
     *
     * @param url the url for which to find other locations
     * @return urls w/ all the IPs that DNS maps to the given URL's hostname
     * @author Ry4an Brase (ry4an@onionnetworks.com)
     */
    public static final URL[] getIpUrlsByName(URL url) throws IOException {

        //String query = url.getQuery();   // These three are redundant
        //String path = url.getPath();
        //String authority = url.getAuthority();
        String userInfo = url.getUserInfo();
        String protocol = url.getProtocol();
        String host = url.getHost();
        String file = url.getFile();
        String ref = url.getRef();
        int port = url.getPort();

        //System.out.println("Query = '" + query + "'");
        //System.out.println("Path = '" + path + "'");
        //System.out.println("Authority = '" + authority + "'");
        //System.out.println("UserInfo = '" + userInfo + "'");
        //System.out.println("Protocol = '" + protocol + "'");
        //System.out.println("Host = '" + host + "'");
        //System.out.println("File = '" + file + "'");
        //System.out.println("Ref = '" + ref + "'");
        //System.out.println("Port = " + port);

        if (host == null || "".equals(host)) { // avoids UnknownHostException
            return new URL[] { url };
        }

        InetAddress[] addrs = InetAddress.getAllByName(host);
        URL[] retval = new URL[addrs.length];
        for (int i=0; i < addrs.length; i++) {
            retval[i] = new URL(
                protocol,
                ((userInfo == null)
                    ? addrs[i].getHostAddress()
                    : (userInfo + "@" + addrs[i].getHostAddress())),
                port, // -1 is okay
                ((ref == null)
                    ? file
                    : (file + "#" + ref)));
        }

        return retval;

    }

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            args = new String[] {

                // Everything
                "http://user:pass@cnn.com:14234/dir/foobar?param&adsf=2312#anc",

                // no ref
                "http://user:pass@cnn.com:14234/dir/foobar?param&adsf=2312",

                // user w/ no pass
                "http://user@cnn.com:14234/dir/foobar?param&adsf=2312#anc",

                // no user or pass
                "http://cnn.com:14234/dir/foobar?param&adsf=2312#anc",

                // no host
                "http:/dir/foobar?param&adsf=2312#anc",

                // bare bones
                "http://cnn.com/",

                // IP for the host makes this useless but harmless
                "http://64.236.24.4:14234/dir/foobar",
            };
        }
        
        for (int j = 0; j < args.length; j++) {
            System.out.println("----------[ Matches for: " + args[j]);
            URL[] urls = getIpUrlsByName(new URL(args[j]));

            for (int i=0; i < urls.length; i++) {
                System.out.println(urls[i].toExternalForm());
            }
        }
    }
}
