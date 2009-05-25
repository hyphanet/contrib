package org.tanukisoftware.wrapper;

/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 */

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.ServerSocket;
import java.util.Hashtable;

import org.tanukisoftware.wrapper.WrapperManager;

/**
 * If an application instantiates an instance of this class, the JVM will
 *  listen on the specified port for connections.  When a connection is
 *  detected, the first byte of input will be read from the socket and
 *  then the connection will be immediately closed.  An action will then
 *  be performed based on the byte read from the stream.
 * <p>
 * The easiest way to invoke an action manually is to telnet to the specified
 *  port and then type the single command key.
 *  <code>telnet localhost 9999</code>, for example.
 * <p>
 * Valid commands include:
 * <ul>
 *   <li><b>S</b> : Shutdown cleanly.</li>
 *   <li><b>H</b> : Immediate forced shutdown.</li>
 *   <li><b>R</b> : Restart</li>
 *   <li><b>D</b> : Perform a Thread Dump</li>
 *   <li><b>U</b> : Unexpected shutdown. (Simulate a crash for testing)</li>
 *   <li><b>V</b> : Cause an access violation. (For testing)</li>
 *   <li><b>G</b> : Make the JVM appear to be hung. (For testing)</li>
 * </ul>
 * Additional user defined actions can be defined by calling the
 *  {@link #registerAction( byte command, Runnable action )} method.
 *  The Wrapper project reserves the right to define any upper case
 *  commands in the future.  To avoid future conflicts, please use lower
 *  case for user defined commands.
 * <p>
 * This application will work even in most deadlock situations because the
 *  thread is in issolation from the rest of the application.  If the JVM
 *  is truely hung, this class will fail to accept connections but the
 *  Wrapper itself will detect the hang and restart the JVM externally.
 * <p>
 * The following code can be used in your application to start up the
 *  WrapperActionServer with all default actions enabled:
 * <pre>
 *  int port = 9999;
 *  WrapperActionServer server = new WrapperActionServer( port );
 *  server.enableShutdownAction( true );
 *  server.enableHaltExpectedAction( true );
 *  server.enableRestartAction( true );
 *  server.enableThreadDumpAction( true );
 *  server.enableHaltUnexpectedAction( true );
 *  server.enableAccessViolationAction( true );
 *  server.enableAppearHungAction( true );
 *  server.start();
 * </pre>
 * Then remember to stop the server when your application shuts down:
 * <pre>
 *  server.stop();
 * </pre>
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public class WrapperActionServer
    implements Runnable
{
    /** Command to invoke a shutdown action. */
    public final static byte COMMAND_SHUTDOWN         = (byte)'S';
    /** Command to invoke an expected halt action. */
    public final static byte COMMAND_HALT_EXPECTED    = (byte)'H';
    /** Command to invoke a restart action. */
    public final static byte COMMAND_RESTART          = (byte)'R';
    /** Command to invoke a thread dump action. */
    public final static byte COMMAND_DUMP             = (byte)'D';
    /** Command to invoke an unexpected halt action. */
    public final static byte COMMAND_HALT_UNEXPECTED  = (byte)'U';
    /** Command to invoke an access violation. */
    public final static byte COMMAND_ACCESS_VIOLATION = (byte)'V';
    /** Command to invoke an appear hung action. */
    public final static byte COMMAND_APPEAR_HUNG      = (byte)'G';

    /** The address to bind the port server to.  Null for any address. */
    private InetAddress m_bindAddr;
    
    /** The port to listen on for connections. */
    private int m_port;
    
    /** Reference to the worker thread. */
    private Thread m_runner;
    
    /** Flag set when the m_runner thread has been asked to stop. */
    private boolean m_runnerStop = false;
    
    /** Reference to the ServerSocket. */
    private ServerSocket m_serverSocket;
    
    /** Table of all the registered actions. */
    private Hashtable m_actions = new Hashtable();
    
    /** Log channel */
    private static WrapperPrintStream m_out;
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /**
     * Creates and starts WrapperActionServer instance bound to the
     *  specified port and address.
     *
     * @param port Port on which to listen for connections.
     * @param bindAddress Address to bind to.
     */
    public WrapperActionServer( int port, InetAddress bindAddress )
    {
        m_port = port;
        m_bindAddr = bindAddress;
        
        m_out = new WrapperPrintStream( System.out, "WrapperActionServer: " );
    }
    
    /**
     * Creates and starts WrapperActionServer instance bound to the
     *  specified port.  The socket will bind to all addresses and
     *  should be concidered a security risk.
     *
     * @param port Port on which to listen for connections.
     */
    public WrapperActionServer( int port )
    {
        this( port, null );
    }
    
    /*---------------------------------------------------------------
     * Runnable Methods
     *-------------------------------------------------------------*/
    /**
     * Thread which will listen for connections on the socket.
     */
    public void run()
    {
        if ( Thread.currentThread() != m_runner )
        {
            throw new IllegalStateException( "Private method." );
        }
        
        try
        {
            while ( !m_runnerStop )
            {
                try
                {
                    int command;
                    Socket socket = m_serverSocket.accept();
                    try
                    {
                        // Set a short timeout of 15 seconds,
                        //  so connections will be promptly closed if left idle.
                        socket.setSoTimeout( 15000 );
                        
                        // Read a single byte.
                        command = socket.getInputStream().read();
                    }
                    finally
                    {
                        socket.close();
                    }
                    
                    if ( command >= 0 )
                    {
                        Runnable action;
                        synchronized( m_actions )
                        {
                            action = (Runnable)m_actions.get( new Integer( command ) );
                        }
                        
                        if ( action != null )
                        {
                            try
                            {
                                action.run();
                            }
                            catch ( Throwable t )
                            {
                                m_out.println( "Error processing action." );
                                t.printStackTrace( m_out );
                            }
                        }
                    }
                }
                catch ( Throwable t )
                {
                    // Check for throwable type this way rather than with seperate catches
                    //  to work around a problem where InterruptedException can be thrown
                    //  when the compiler gives an error saying that it can't.
                    if ( m_runnerStop
                        && ( ( t instanceof InterruptedException )
                        || ( t instanceof SocketException )
                        || ( t instanceof InterruptedIOException ) ) )
                    {
                        // This is expected, the service is being stopped.
                    }
                    else
                    {
                        m_out.println( "Unexpeced error." );
                        t.printStackTrace( m_out );
                        
                        // Avoid tight thrashing
                        try
                        {
                            Thread.sleep( 5000 );
                        }
                        catch ( InterruptedException e )
                        {
                            // Ignore
                        }
                    }
                }
            }
        }
        finally
        {
            synchronized( this )
            {
                m_runner = null;
                
                // Wake up the stop method if it is waiting for the runner to stop.
                this.notify();
            }
        }
    }
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Starts the runner thread.
     *
     * @throws IOException If the server socket is unable to bind to the
     *                     specified port or there are any other problems
     *                     opening a socket.
     */
    public void start()
        throws IOException
    {
        // Create the server socket.
        m_serverSocket = new ServerSocket( m_port, 5, m_bindAddr );
        
        m_runner = new Thread( this, "WrapperActionServer_runner" );
        m_runner.setDaemon( true );
        m_runner.start();
    }
    
    /**
     * Stops the runner thread, blocking until it has stopped.
     */
    public void stop()
        throws Exception
    {
        Thread runner = m_runner;
        m_runnerStop = true;
        runner.interrupt();

        // Close the server socket so it stops blocking for new connections.
        ServerSocket serverSocket = m_serverSocket;
        if ( serverSocket != null )
        {
            try
            {
                serverSocket.close();
            }
            catch ( IOException e )
            {
                // Ignore.
            }
        }
        
        synchronized( this )
        {
            while( m_runner != null )
            {
                try
                {
                    // Wait to be notified that the thread has exited.
                    this.wait();
                }
                catch ( InterruptedException e )
                {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Registers an action with the action server.  The server will not accept
     *  any new connections until an action has returned, so keep that in mind
     *  when writing them.  Also be aware than any uncaught exceptions will be
     *  dumped to the console if uncaught by the action.  To avoid this, wrap
     *  the code in a <code>try { ... } catch (Throwable t) { ... }</code>
     *  block.
     *
     * @param command Command to be registered.  Will override any exiting
     *                action already registered with the same command.
     * @param action Action to be registered.
     */
    public void registerAction( byte command, Runnable action )
    {
        synchronized( m_actions )
        {
            m_actions.put( new Integer( command ), action );
        }
    }
    
    /**
     * Unregisters an action with the given command.  If no action exists with
     *  the specified command, the method will quietly ignore the call.
     */
    public void unregisterAction( byte command )
    {
        synchronized( m_actions )
        {
            m_actions.remove( new Integer( command ) );
        }
    }
    
    /**
     * Enable or disable the shutdown command.  Disabled by default.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableShutdownAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_SHUTDOWN, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.stopAndReturn( 0 );
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_SHUTDOWN );
        }
    }
    
    /**
     * Enable or disable the expected halt command.  Disabled by default.
     *  This will shutdown the JVM, but will do so immediately without going
     *  through the clean shutdown process.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableHaltExpectedAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_HALT_EXPECTED, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.stopImmediate( 0 );
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_HALT_EXPECTED );
        }
    }
    
    /**
     * Enable or disable the restart command.  Disabled by default.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableRestartAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_RESTART, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.restartAndReturn();
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_RESTART );
        }
    }
    
    /**
     * Enable or disable the thread dump command.  Disabled by default.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableThreadDumpAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_DUMP, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.requestThreadDump();
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_DUMP );
        }
    }
    
    /**
     * Enable or disable the unexpected halt command.  Disabled by default.
     *  If this command is executed, the Wrapper will think the JVM crashed
     *  and restart it.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableHaltUnexpectedAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_HALT_UNEXPECTED, new Runnable()
                {
                    public void run()
                    {
                        // Execute runtime.halt(0) using reflection so this class will
                        //  compile on 1.2.x versions of Java.
                        Method haltMethod;
                        try
                        {
                            haltMethod =
                                Runtime.class.getMethod( "halt", new Class[] { Integer.TYPE } );
                        }
                        catch ( NoSuchMethodException e )
                        {
                            m_out.println( "halt not supported by current JVM." );
                            haltMethod = null;
                        }
                        
                        if ( haltMethod != null )
                        {
                            Runtime runtime = Runtime.getRuntime();
                            try
                            {
                                haltMethod.invoke( runtime, new Object[] { new Integer( 0 ) } );
                            }
                            catch ( IllegalAccessException e )
                            {
                                m_out.println(
                                    "Unable to call runitme.halt: " + e.getMessage() );
                            }
                            catch ( InvocationTargetException e )
                            {
                                m_out.println(
                                    "Unable to call runitme.halt: " + e.getMessage() );
                            }
                        }
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_HALT_UNEXPECTED );
        }
    }
    
    /**
     * Enable or disable the access violation command.  Disabled by default.
     *  This command is useful for testing how an application handles the worst
     *  case situation where the JVM suddenly crashed.  When this happens, the
     *  the JVM will simply die and there will be absolutely no chance for any
     *  shutdown or cleanup work to be done by the JVM.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableAccessViolationAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_ACCESS_VIOLATION, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.accessViolationNative();
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_ACCESS_VIOLATION );
        }
    }
    
    /**
     * Enable or disable the appear hung command.  Disabled by default.
     *  This command is useful for testing how an application handles the
     *  situation where the JVM stops responding to the Wrapper's ping
     *  requests.   This can happen if the JVM hangs or some piece of code
     *  deadlocks.  When this happens, the Wrapper will give up after the
     *  ping timeout has expired and kill the JVM process.  The JVM will
     *  not have a chance to clean up and shudown gracefully.
     *
     * @param enable True to enable to action, false to disable it.
     */
    public void enableAppearHungAction( boolean enable )
    {
        if ( enable )
        {
            registerAction( COMMAND_APPEAR_HUNG, new Runnable()
                {
                    public void run()
                    {
                        WrapperManager.appearHung();
                    }
                } );
        }
        else
        {
            unregisterAction( COMMAND_APPEAR_HUNG );
        }
    }
}

