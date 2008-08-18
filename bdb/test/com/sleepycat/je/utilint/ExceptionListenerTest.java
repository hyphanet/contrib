/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ExceptionListenerTest.java,v 1.11 2008/01/07 14:29:15 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;

public class ExceptionListenerTest extends TestCase {

    private File envHome;

    private volatile boolean exceptionThrownCalled = false;

    private DaemonThread dt = null;

    public ExceptionListenerTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testExceptionListener()
	throws Exception {

	EnvironmentConfig envConfig = new EnvironmentConfig();
	envConfig.setExceptionListener(new MyExceptionListener());
	envConfig.setAllowCreate(true);
	Environment env = new Environment(envHome, envConfig);
	EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);

        assertSame(envConfig.getExceptionListener(),
                   envImpl.getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getCheckpointer().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getINCompressor().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getEvictor().getExceptionListener());

	dt = new MyDaemonThread(0, Environment.CLEANER_NAME, envImpl);
        dt.setExceptionListener(envImpl.getExceptionListener());
	dt.stifleExceptionChatter = true;
	dt.runOrPause(true);
        long startTime = System.currentTimeMillis();
	while (!dt.isShutdownRequested() &&
               System.currentTimeMillis() - startTime < 10 * 1000) {
	    Thread.yield();
	}
	assertTrue("ExceptionListener apparently not called",
		   exceptionThrownCalled);

	/* Change the exception listener. */
	envConfig = env.getConfig();
	exceptionThrownCalled = false;
	envConfig.setExceptionListener(new MyExceptionListener());
	env.setMutableConfig(envConfig);

        assertSame(envConfig.getExceptionListener(),
                   envImpl.getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getCheckpointer().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getINCompressor().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getEvictor().getExceptionListener());

	dt = new MyDaemonThread(0, Environment.CLEANER_NAME, envImpl);
        dt.setExceptionListener(envImpl.getExceptionListener());
	dt.stifleExceptionChatter = true;
	dt.runOrPause(true);
        startTime = System.currentTimeMillis();
	while (!dt.isShutdownRequested() &&
               System.currentTimeMillis() - startTime < 10 * 1000) {
	    Thread.yield();
	}
	assertTrue("ExceptionListener apparently not called",
		   exceptionThrownCalled);
    }

    private class MyDaemonThread extends DaemonThread {
	MyDaemonThread(long waitTime, String name, EnvironmentImpl envImpl) {
	    super(waitTime, name, envImpl);
	}

	protected void onWakeup()
	    throws DatabaseException {

	    throw new RuntimeException("test exception listener");
	}
    }

    private class MyExceptionListener implements ExceptionListener {
	public void exceptionThrown(ExceptionEvent event) {
            assertEquals("daemonName should be CLEANER_NAME",
			 Environment.CLEANER_NAME,
                         event.getThreadName());
	    dt.requestShutdown();
	    exceptionThrownCalled = true;
	}
    }
}
