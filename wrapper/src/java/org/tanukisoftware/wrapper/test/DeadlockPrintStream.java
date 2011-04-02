package org.tanukisoftware.wrapper.test;

/*
 * Copyright (c) 1999, 2008 Tanuki Software, Inc.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 */

import java.io.IOException;
import java.io.PrintStream;

/**
 * A print stream which can be put into a state in which all calls to write
 *  to it will result in the calling threads deadlocking in the call.
 * Obviously, this class will not be useful to many as it is for tests.
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class DeadlockPrintStream extends PrintStream {
    /** The Wrapped PrintStream. */
    private PrintStream m_out;
    
    /** True if calling threads should be deadlocked. */
    private boolean m_deadlock;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates a new DeadlockPrintStream wrapped around another PrintStream.
     *
     * @param out The PrintStream which will be wrapped by this new stream.
     */
    public DeadlockPrintStream( PrintStream out )
    {
        super( out );
        
        m_out = out;
    }
    
    /*---------------------------------------------------------------
     * PrintStream Methods
     *-------------------------------------------------------------*/
    public void write( int b )
    {
        deadlock();
        m_out.write( b );
    }
    
    public void write( byte[] b )
        throws IOException
    {
        deadlock();
        m_out.write( b );
    }
    
    public void write( byte[] b, int off, int len )
    {
        deadlock();
        m_out.write( b, off, len );
    }
    
    public void flush()
    {
        deadlock();
        m_out.flush();
    }
    
    public void close()
    {
        deadlock();
        m_out.close();
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * This call will not return as long as the m_deadLock flag is set.
     *  If it is ever cleared with a call to setDeadlock(), stuck threads
     *  will all be released.
     */
    private void deadlock()
    {
        if ( m_deadlock )
        {
            synchronized( this )
            {
                while( m_deadlock )
                {
                    try
                    {
                        this.wait();
                    }
                    catch ( InterruptedException e )
                    {
                        // Ignore
                    }
                }
            }
        }
    }
    
    /**
     * Sets or clears the deadlock flag.  If set, calls to any other method
     *  of this class will result in the calling thread being deadlocked.
     *  They will be released if the flag is cleared with this method.
     *
     * @param deadlock True to set the flag, false to clear it.
     */
    public void setDeadlock( boolean deadlock )
    {
        m_deadlock = deadlock;
        if ( !m_deadlock )
        {
            synchronized( this )
            {
                // Release any threads that are waiting.
                this.notifyAll();
            }
        }
    }
}

