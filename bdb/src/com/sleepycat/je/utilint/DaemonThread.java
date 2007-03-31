/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DaemonThread.java,v 1.55.2.1 2007/02/01 14:49:54 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;

/**
 * A daemon thread.
 */
public abstract class DaemonThread implements DaemonRunner, Runnable {
    private static final int JOIN_MILLIS = 10;
    private long waitTime;
    private Object synchronizer = new Object();
    private Thread thread;
    private EnvironmentImpl env;
    protected String name;
    protected Set workQueue;
    protected Latch workQueueLatch;
    protected int nWakeupRequests;
    protected boolean stifleExceptionChatter = false;
    
    /* Fields shared between threads must be 'volatile'. */
    private volatile boolean shutdownRequest = false;
    private volatile boolean paused = false;

    /* This is not volatile because it is only an approximation. */
    private boolean running = false;

    public DaemonThread(long waitTime, String name, EnvironmentImpl env) {
        this.waitTime = waitTime;
        this.name = name;
	this.env = env;
        workQueue = new HashSet();
        workQueueLatch = LatchSupport.makeLatch(name + " work queue", env);
    }

    /**
     * For testing.
     */
    public Thread getThread() {
        return thread;
    }

    /**
     * If run is true, starts the thread if not started or unpauses it
     * if already started; if run is false, pauses the thread if
     * started or does nothing if not started.
     */
    public void runOrPause(boolean run) {
        if (run) {
            paused = false;
            if (thread != null) {
                wakeup();
            } else {
                thread = new Thread(this, name);
                thread.setDaemon(true);
                thread.start();
            }
        } else {
            paused = true;
        }
    }

    public void requestShutdown() {
	shutdownRequest = true;
    }

    /**
     * Requests shutdown and calls join() to wait for the thread to stop.
     */
    public void shutdown() {
        if (thread != null) {
            shutdownRequest = true;
            while (thread.isAlive()) {
                synchronized (synchronizer) {
                    synchronizer.notifyAll();
                }
                try {
                    thread.join(JOIN_MILLIS);
                } catch (InterruptedException e) {

		    /* 
		     * Klockwork - ok
		     * Don't say anything about exceptions here.
		     */
		}
            }
            thread = null;
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("<DaemonThread name=\"").append(name).append("\"/>");
        return sb.toString();
    }

    public void addToQueue(Object o)
        throws DatabaseException {

        workQueueLatch.acquire();
        workQueue.add(o);
        wakeup();
        workQueueLatch.release();
    }

    public int getQueueSize()
        throws DatabaseException {

        workQueueLatch.acquire();
        int count = workQueue.size();
        workQueueLatch.release();
        return count;
    }

    /*
     * Add an entry to the queue.  Call this if the workQueueLatch is
     * already held.
     */
    public void addToQueueAlreadyLatched(Collection c)
        throws DatabaseException {

        workQueue.addAll(c);
    }

    public void wakeup() {
        if (!paused) {
            synchronized (synchronizer) {
                synchronizer.notifyAll();
            }
        }
    }

    public void run() {
        while (true) {
            /* Check for shutdown request. */
            if (shutdownRequest) {
                break;
            }
            try {
                workQueueLatch.acquire();
                boolean nothingToDo = workQueue.size() == 0;
                workQueueLatch.release();
                if (nothingToDo) {
                    synchronized (synchronizer) {
                        if (waitTime == 0) {
                            synchronizer.wait();
                        } else {
                            synchronizer.wait(waitTime);
                        }
                    }
                }

                /* Check for shutdown request. */
                if (shutdownRequest) {
                    break;
                }

                /* If paused, wait until notified. */
                if (paused) {
                    synchronized (synchronizer) {
			/* FindBugs whines unnecessarily here. */
                        synchronizer.wait();
                    }
                    continue;
                }

		int numTries = 0;
		int maxRetries = nDeadlockRetries();

		do {
		    try {
                        nWakeupRequests++;
                        running = true;
			onWakeup();
			break;
		    } catch (DeadlockException e) {
		    } finally {
                        running = false;
                    }
		    numTries++;

		    /* Check for shutdown request. */
		    if (shutdownRequest) {
			break;
		    }

		} while (numTries <= maxRetries);

                /* Check for shutdown request. */
                if (shutdownRequest) {
                    break;
                }
            } catch (InterruptedException IE) {
		ExceptionListener exceptionListener =
		    env.getExceptionListener();
		if (exceptionListener != null) {
		    exceptionListener.exceptionThrown
			(DbInternal.makeExceptionEvent(IE, name));
		}

		if (!stifleExceptionChatter) {
		    System.err.println
			("Shutting down " + this + " due to exception: " + IE);
		}
                shutdownRequest = true;
            } catch (Exception E) {
		ExceptionListener exceptionListener =
		    env.getExceptionListener();
		if (exceptionListener != null) {
		    exceptionListener.exceptionThrown
			(DbInternal.makeExceptionEvent(E, name));
		}

		if (!stifleExceptionChatter) {
		    System.err.println(this + " caught exception: " + E);
		    E.printStackTrace(System.err);
		}
		if (env.mayNotWrite()) {
		    if (!stifleExceptionChatter) {
			System.err.println("Exiting");
		    }
		    shutdownRequest = true;
		} else {
		    if (!stifleExceptionChatter) {
			System.err.println("Continuing");
		    }
		}
            }
        }
    }

    /**
     * Returns the number of retries to perform when Deadlock Exceptions
     * occur.
     */
    protected int nDeadlockRetries()
        throws DatabaseException {

        return 0;
    }

    /**
     * onWakeup is synchronized to ensure that multiple invocations of the
     * DaemonThread aren't made.  isRunnable must be called from within
     * onWakeup to avoid having the following sequence:
     * Thread A: isRunnable() => true,
     * Thread B: isRunnable() => true,
     * Thread A: onWakeup() starts
     * Thread B: waits for monitor on thread to call onWakeup()
     * Thread A: onWakeup() completes rendering isRunnable() predicate false
     * Thread B: onWakeup() starts, but isRunnable predicate is now false
     */
    abstract protected void onWakeup()
        throws DatabaseException;

    /**
     * Returns whether shutdown has been requested.  This method should be
     * used to to terminate daemon loops.
     */
    protected boolean isShutdownRequested() {
        return shutdownRequest;
    }

    /**
     * Returns whether the daemon is currently paused/disabled.  This method
     * should be used to to terminate daemon loops.
     */
    protected boolean isPaused() {
        return paused;
    }

    /** 
     * Returns whether the onWakeup method is currently executing.  This is
     * only an approximation and is used to avoid unnecessary wakeups.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * For unit testing.
     */
    public int getNWakeupRequests() {
        return nWakeupRequests;
    }
}
