/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: DaemonRunner.java,v 1.5 2006/10/30 21:14:28 bostic Exp $
 */

package com.sleepycat.je.utilint;


/**
 * An object capable of running (run/pause/shutdown/etc) a daemon thread.
 * See DaemonThread for details.
 */
public interface DaemonRunner {
    void runOrPause(boolean run);
    void requestShutdown();
    void shutdown();
    int getNWakeupRequests();
}
