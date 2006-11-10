package com.onionnetworks.util;

import java.util.*;
import java.io.*;

import junit.framework.*;

public class AsyncPersistentPropsTest extends TestCase {

    private Random rand = new Random();
    
    public AsyncPersistentPropsTest(String name) {
	super(name);
    }

    public void testReadWrite() {
        for (int a=0;a<100;a++) {
            try {
                File f = File.createTempFile("swarmtest","tmp");
                f.deleteOnExit();
                AsyncPersistentProps app = new AsyncPersistentProps(f);
                Properties props = new Properties();
                for (int i=0;i<100;i++) {
                    String key = new Integer(rand.nextInt(1000)).toString();
                    String value = new Integer(rand.nextInt(1000)).toString();
                    app.setProperty(key,value);
                    props.setProperty(key,value);
                    if ((i % 10) == 0) { // decimate
                        app.remove(key);
                        props.remove(key);
                    }
                }
                app.close();
                app = new AsyncPersistentProps(f);
                assertEquals(props,app.getProperties());

                // cleanup
                app.close();
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
    }

    public void testException() {
	File f = new File("a/b/c/d/f/g/h.tmp");
	if (f.getParentFile().exists()) {
	    fail(f+" shouldn't exist for this test");
	}
	AsyncPersistentProps app = null;
	try {
	    app = new AsyncPersistentProps(f);
	} catch (IOException e) {
	    fail(""+e);
	}
	
	try {
	    app.setProperty("foo","bar");
	    app.flush();
	    app.close();
	    fail("Exception should have been thrown");
	} catch (IOException e) {}
    }
}
                
