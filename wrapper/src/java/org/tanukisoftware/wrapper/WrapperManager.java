package org.tanukisoftware.wrapper;

/*
 * Copyright (c) 1999, 2008 Tanuki Software, Inc.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 * 
 * 
 * Portions of the Software have been derived from source code
 * developed by Silver Egg Technology under the following license:
 * 
 * Copyright (c) 2001 Silver Egg Technology
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without 
 * restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sub-license, and/or 
 * sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 */

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.tanukisoftware.wrapper.event.WrapperControlEvent;
import org.tanukisoftware.wrapper.event.WrapperEvent;
import org.tanukisoftware.wrapper.event.WrapperEventListener;
import org.tanukisoftware.wrapper.event.WrapperPingEvent;
import org.tanukisoftware.wrapper.event.WrapperServiceControlEvent;
import org.tanukisoftware.wrapper.event.WrapperTickEvent;
import org.tanukisoftware.wrapper.resources.ResourceManager;
import org.tanukisoftware.wrapper.security.WrapperEventPermission;
import org.tanukisoftware.wrapper.security.WrapperPermission;
import org.tanukisoftware.wrapper.security.WrapperServicePermission;

/**
 * Handles all communication with the native portion of the Wrapper code.
 *  The native wrapper code will launch Java in a separate process and set
 *  up a server socket which the Java code is expected to open a socket to
 *  on startup.  When the server socket is created, a port will be chosen
 *  depending on what is available to the system.  This port will then be
 *  passed to the Java process as property named "wrapper.port".
 *
 * For security reasons, the native code will only allow connections from
 *  localhost and will expect to receive the key specified in a property
 *  named "wrapper.key".
 *
 * This class is implemented as a singleton class.
 *
 * Generate JNI Headers with the following command in the build/classes
 *  directory:
 *    javah -jni -classpath ./ org.tanukisoftware.wrapper.WrapperManager
 *
 * @author Leif Mortenson <leif@tanukisoftware.com>
 */
public final class WrapperManager
    implements Runnable
{
    private static final String WRAPPER_CONNECTION_THREAD_NAME = "Wrapper-Connection";
    
    private static final int DEFAULT_PORT                = 15003;
    private static final int DEFAULT_SO_TIMEOUT          = 10000;
    private static final int DEFAULT_CPU_TIMEOUT         = 10000;
    
    /** The number of milliseconds in one tick.  Used for internal system
     *   time independent time keeping. */
    private static final int TICK_MS                     = 100;
    private static final int TIMER_FAST_THRESHOLD     = 2 * 24 * 3600 * 1000 / TICK_MS; // 2 days.
    private static final int TIMER_SLOW_THRESHOLD     = 2 * 24 * 3600 * 1000 / TICK_MS; // 2 days.
    
    private static final byte WRAPPER_MSG_START          = (byte)100;
    private static final byte WRAPPER_MSG_STOP           = (byte)101;
    private static final byte WRAPPER_MSG_RESTART        = (byte)102;
    private static final byte WRAPPER_MSG_PING           = (byte)103;
    private static final byte WRAPPER_MSG_STOP_PENDING   = (byte)104;
    private static final byte WRAPPER_MSG_START_PENDING  = (byte)105;
    private static final byte WRAPPER_MSG_STARTED        = (byte)106;
    private static final byte WRAPPER_MSG_STOPPED        = (byte)107;
    private static final byte WRAPPER_MSG_KEY            = (byte)110;
    private static final byte WRAPPER_MSG_BADKEY         = (byte)111;
    private static final byte WRAPPER_MSG_LOW_LOG_LEVEL  = (byte)112;
    private static final byte WRAPPER_MSG_PING_TIMEOUT   = (byte)113;
    private static final byte WRAPPER_MSG_SERVICE_CONTROL_CODE = (byte)114;
    private static final byte WRAPPER_MSG_PROPERTIES     = (byte)115;
    
    /** Log commands are actually 116 + the LOG LEVEL. */
    private static final byte WRAPPER_MSG_LOG            = (byte)116;
    
    /** Received when the user presses CTRL-C in the console on Windows or UNIX platforms. */
    public static final int WRAPPER_CTRL_C_EVENT         = 200;
    
    /** Received when the user clicks on the close button of a Console on Windows. */
    public static final int WRAPPER_CTRL_CLOSE_EVENT     = 201;
    
    /** Received when the user logs off of a Windows system. */
    public static final int WRAPPER_CTRL_LOGOFF_EVENT    = 202;
    
    /** Received when a Windows system is shutting down. */
    public static final int WRAPPER_CTRL_SHUTDOWN_EVENT  = 203;
    
    /** Received when a SIG TERM is received on a UNIX system. */
    public static final int WRAPPER_CTRL_TERM_EVENT      = 204;
    
    /** Received when a SIG HUP is received on a UNIX system. */
    public static final int WRAPPER_CTRL_HUP_EVENT       = 205;
    
    /** Received when a SIG USR1 is received on a UNIX system. */
    public static final int WRAPPER_CTRL_USR1_EVENT      = 206;
    
    /** Received when a SIG USR2 is received on a UNIX system. */
    public static final int WRAPPER_CTRL_USR2_EVENT      = 207;
    
    /** Log message at debug log level. */
    public static final int WRAPPER_LOG_LEVEL_DEBUG      = 1;
    /** Log message at info log level. */
    public static final int WRAPPER_LOG_LEVEL_INFO       = 2;
    /** Log message at status log level. */
    public static final int WRAPPER_LOG_LEVEL_STATUS     = 3;
    /** Log message at warn log level. */
    public static final int WRAPPER_LOG_LEVEL_WARN       = 4;
    /** Log message at error log level. */
    public static final int WRAPPER_LOG_LEVEL_ERROR      = 5;
    /** Log message at fatal log level. */
    public static final int WRAPPER_LOG_LEVEL_FATAL      = 6;
    /** Log message at advice log level. */
    public static final int WRAPPER_LOG_LEVEL_ADVICE     = 7;
    
    /** Service Control code which can be sent to start a service. */
    public static final int SERVICE_CONTROL_CODE_START       = 0x10000;
    
    /** Service Control code which can be sent or received to stop a service. */
    public static final int SERVICE_CONTROL_CODE_STOP        = 1;
    
    /** Service Control code which can be sent to pause a service. */
    public static final int SERVICE_CONTROL_CODE_PAUSE       = 2;
    
    /** Service Control code which can be sent to resume a paused service. */
    public static final int SERVICE_CONTROL_CODE_CONTINUE    = 3;
    
    /** Service Control code which can be sent to or received interrogate the status of a service. */
    public static final int SERVICE_CONTROL_CODE_INTERROGATE = 4;
    
    /** Service Control code which can be received when the system is shutting down. */
    public static final int SERVICE_CONTROL_CODE_SHUTDOWN    = 5;
    
    /** Reference to the original value of System.out. */
    private static PrintStream m_out;
    
    /** Reference to the original value of System.err. */
    private static PrintStream m_err;
    
    /** Info level log channel */
    private static WrapperPrintStream m_outInfo;
    
    /** Error level log channel */
    private static WrapperPrintStream m_outError;
    
    /** Debug level log channel */
    private static WrapperPrintStream m_outDebug;
    
    /** Flag that will be set to true once a SecurityManager has been detected and tested. */
    private static boolean m_securityManagerChecked = false;
    
    private static boolean m_disposed = false;
    private static boolean m_started = false;
    private static WrapperManager m_instance = null;
    private static Thread m_hook = null;
    private static boolean m_hookTriggered = false;
    
    /* Flag which records when the shutdownJVM method has completed. */
    private static boolean m_shutdownJVMComplete = false;
    
    private static String[] m_args;
    private static int m_port    = DEFAULT_PORT;
    private static int m_jvmPort;
    private static int m_jvmPortMin;
    private static int m_jvmPortMax;
    private static String m_key;
    private static int m_soTimeout = DEFAULT_SO_TIMEOUT;
    private static long m_cpuTimeout = DEFAULT_CPU_TIMEOUT;
    
    /** Tick count when the start method completed. */
    private static int m_startedTicks;
    
    /** The lowest configured log level in the Wrapper's configuration.  This 
     *   is set to a high value by default to disable all logging if the
     *   Wrapper does not register its low level or is not present. */
    private static int m_lowLogLevel = WRAPPER_LOG_LEVEL_ADVICE + 1;
    
    /** The maximum amount of time in ms to allow to pass without the JVM
     *   pinging the server before the JVM is terminated to allow a resynch. */
    private static int m_pingTimeout = 30000;
    
    /** Flag, set when the JVM is launched that is used to remember whether
     *   or not system signals are supposed to be ignored. */
    private static boolean m_ignoreSignals = false;
    
    /** Thread which processes all communications with the native code. */
    private static Thread m_commRunner;
    private static boolean m_commRunnerStarted = false;
    private static Thread m_eventRunner;
    private static int m_eventRunnerTicks;
    private static Thread m_startupRunner;
    
    /** True if the system time should be used for internal timeouts. */
    private static boolean m_useSystemTime;
    
    /** The threashold of how many ticks the timer can be fast before a
     *   warning is displayed. */
    private static int m_timerFastThreshold;
    
    /** The threashold of how many ticks the timer can be slow before a
     *   warning is displayed. */
    private static int m_timerSlowThreshold;
    
    /**
     * Bit depth of the currently running JVM.  Will be 32 or 64.
     *  A 64-bit JVM means that the system is also 64-bit, but a 32-bit JVM
     *  can be run either on a 32 or 64-bit system.
     */
    private static int m_jvmBits;
    
    /** An integer which stores the number of ticks since the
     *   JVM was launched.  Using an int rather than a long allows the value
     *   to be used without requiring any synchronization.  This is only
     *   used if the m_useSystemTime flag is false. */
    private static volatile int m_ticks;
    
    private static WrapperListener m_listener;
    
    private static int m_lastPingTicks;
    private static ServerSocket m_serverSocket;
    private static Socket m_socket;
    private static boolean m_shuttingDown = false;
    private static boolean m_appearHung = false;
    
    private static Method m_addShutdownHookMethod = null;
    private static Method m_removeShutdownHookMethod = null;
    
    private static boolean m_ignoreUserLogoffs = false;
    
    private static boolean m_service = false;
    private static boolean m_debug = false;
    private static int m_jvmId = 0;
    private static boolean m_stopping = false;
    private static Thread m_stoppingThread;
    private static int m_exitCode;
    private static boolean m_libraryOK = false;
    private static byte[] m_commandBuffer = new byte[512];
    
    /** The contents of the wrapper configuration. */
    private static WrapperProperties m_properties;
    
    /** List of registered WrapperEventListeners and their registered masks. */
    private static List m_wrapperEventListenerMaskList = new ArrayList();
    
    /** Array of registered WrapperEventListeners and their registered masks.
     *   Should not be referenced directly.  Access by calling
     *   getWrapperEventListenerMasks(). */ 
    private static WrapperEventListenerMask[] m_wrapperEventListenerMasks = null;
    
    /** Flag used to tell whether or not WrapperCoreEvents should be produced. */
    private static boolean m_produceCoreEvents = false;
    
    // message resources: eventually these will be split up
    private static ResourceManager m_res        = ResourceManager.getResourceManager();
    private static ResourceManager m_error      = m_res;
    private static ResourceManager m_warning    = m_res;
    private static ResourceManager m_info       = m_res;
    
    /*---------------------------------------------------------------
     * Class Initializer
     *-------------------------------------------------------------*/
    /**
     * When the WrapperManager class is first loaded, it attempts to load the
     *  configuration file specified using the 'wrapper.config' system property.
     *  When the JVM is launched from the Wrapper native code, the
     *  'wrapper.config' and 'wrapper.key' parameters are specified.
     *  The 'wrapper.key' parameter is a password which is used to verify that
     *  connections are only coming from the native Wrapper which launched the
     *  current JVM.
     */
    static
    {
        // The wraper.jar must be given AllPermissions if a security manager
        //  has been configured.  This is not a problem if one of the standard
        //  Wrapper helper classes is used to launch the JVM.
        // If however a custom WrapperListener is being implemented then this
        //  class will most likely be loaded by code that is neither part of
        //  the system, nor part of the Wrapper code base.  To avoid having
        //  to also give those classes AllPermissions as well, we do all of
        //  initialization in a Privileged block.  This means that the code
        //  only requires that the wrapper.jar has been given the required
        //  permissions.
        AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    privilegedClassInit();
                    return null;
                }
            }
        );
    }
    
    /**
     * The body of the static initializer is moved into a seperate method so
     *  it can be run as a PrivilegedAction.
     */
    private static void privilegedClassInit()
    {
        // Store references to the original System.out and System.err
        //  PrintStreams.  The WrapperManager will always output to the
        //  original streams so its output will always end up in the
        //  wrapper.log file even if the end user code redirects the
        //  output to another log file.
        // This is also important to be protect the Wrapper's functionality
        //  from the case where the user PrintStream enters a deadlock state.
        m_out = System.out;
        m_err = System.err;
        
        // Set up some log channels
        m_outInfo = new WrapperPrintStream( m_out, "WrapperManager: " );
        m_outError = new WrapperPrintStream( m_out, "WrapperManager Error: " );
        m_outDebug = new WrapperPrintStream( m_out, "WrapperManager Debug: " );
        
        // Always create an empty properties object in case we are not running
        //  in the Wrapper or the properties are never sent.
        m_properties = new WrapperProperties();
        m_properties.lock();
        
        // This must be done before attempting to access any System Properties
        //  as that could cause a SecurityException if it is too strict.
        checkSecurityManager();
        
        // Check for the debug flag
        m_debug = WrapperSystemPropertyUtil.getBooleanProperty( "wrapper.debug", false );
        
        if ( m_debug )
        {
            m_outDebug.println( "WrapperManager class initialized by thread: "
                + Thread.currentThread().getName()
                + "  Using classloader: " + WrapperManager.class.getClassLoader() );
        }
        
        // The copyright banner was moved into the wrapper binary.  In order to
        //  aid in the debugging of user integrations, some kind of a known message
        //  needs to be displayed on startup so it is obvious whether or not the
        //  WrapperManager class is being initialized.
        m_outInfo.println( "Initializing..." );
        
        // Check for the jvmID
        m_jvmId = WrapperSystemPropertyUtil.getIntProperty( "wrapper.jvmid", 1 );
        if ( m_debug )
        {
            m_outDebug.println( "JVM #" + m_jvmId );
        }
        
        // Decide whether this is a 32 or 64 bit version of Java.
        m_jvmBits = Integer.getInteger( "sun.arch.data.model", -1 ).intValue();
        if ( m_debug )
        {
            if ( m_jvmBits > 0 )
            {
                m_outDebug.println( "Running a " + m_jvmBits + "-bit JVM." );
            }
            else
            {
                m_outDebug.println( "The bit depth of this JVM could not be determined." );
            }
        }
        
        // Initialize the timerTicks to a very high value.  This means that we will
        // always encounter the first rollover (200 * WRAPPER_MS / 1000) seconds
        // after the Wrapper the starts, which means the rollover will be well
        // tested.
        m_ticks = Integer.MAX_VALUE - 200;
        
        m_useSystemTime = WrapperSystemPropertyUtil.getBooleanProperty(
            "wrapper.use_system_time", false );
        m_timerFastThreshold = WrapperSystemPropertyUtil.getIntProperty(
            "wrapper.timer_fast_threshold", TIMER_FAST_THRESHOLD ) * 1000 / TICK_MS;
        m_timerSlowThreshold = WrapperSystemPropertyUtil.getIntProperty(
            "wrapper.timer_slow_threshold", TIMER_SLOW_THRESHOLD ) * 1000 / TICK_MS;
        
        // Check to see if we should register a shutdown hook
        boolean disableShutdownHook = WrapperSystemPropertyUtil.getBooleanProperty(
            "wrapper.disable_shutdown_hook", false );
        
        // Locate the add and remove shutdown hook methods using reflection so
        //  that this class can be compiled on 1.2.x versions of java.
        try
        {
            m_addShutdownHookMethod =
                Runtime.class.getMethod( "addShutdownHook", new Class[] { Thread.class } );
            m_removeShutdownHookMethod =
                Runtime.class.getMethod( "removeShutdownHook", new Class[] { Thread.class } );
        }
        catch ( NoSuchMethodException e )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Shutdown hooks not supported by current JVM." );
            }
            m_addShutdownHookMethod = null;
            m_removeShutdownHookMethod = null;
            disableShutdownHook = true;
        }
        
        // If the shutdown hook is not disabled, then register it.
        if ( !disableShutdownHook )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Registering shutdown hook" );
            }
            m_hook = new Thread( "Wrapper-Shutdown-Hook" )
            {
                /**
                 * Run the shutdown hook. (Triggered by the JVM when it is about to shutdown)
                 */
                public void run()
                {
                    // Stop the Wrapper cleanly.
                    m_hookTriggered = true;
                    
                    if ( m_debug )
                    {
                        m_outDebug.println( "ShutdownHook started" );
                    }
                    
                    // Let the startup thread die since the shutdown hook is running.
                    m_startupRunner = null;
                    
                    // If we are not already stopping, then do so.
                    WrapperManager.stop( 0 );
                    
                    if ( m_debug )
                    {
                        m_outDebug.println( "ShutdownHook complete" );
                    }
                }
            };
            
            // Actually register the shutdown hook using reflection.
            try
            {
                m_addShutdownHookMethod.invoke( Runtime.getRuntime(), new Object[] { m_hook } );
            }
            catch ( IllegalAccessException e )
            {
                m_outError.println( "Unable to register shutdown hook: " + e );
            }
            catch ( InvocationTargetException e )
            {
                Throwable t = e.getTargetException();
                if ( t == null )
                {
                    t = e;
                }
                
                m_outError.println( "Unable to register shutdown hook: " + t );
            }
        }
        
        // A key is required for the wrapper to work correctly.  If it is not
        //  present, then assume that we are not being controlled by the native
        //  wrapper.
        if ( ( m_key = System.getProperty( "wrapper.key" ) ) == null )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Not using wrapper.  (key not specified)" );
            }
            
            // The wrapper will not be used, so other values will not be used.
            m_port = 0;
            m_jvmPort = 0;
            m_jvmPortMin = 0;
            m_jvmPortMax = 0;
            m_service = false;
            m_cpuTimeout = 31557600000L; // One Year.  Effectively never.
        }
        else
        {
            if ( m_debug )
            {
                m_outDebug.println( "Using wrapper" );
            }
            
            // A port must have been specified.
            String sPort;
            if ( ( sPort = System.getProperty( "wrapper.port" ) ) == null )
            {
                String msg = m_res.format( "MISSING_PORT" );
                m_outError.println( msg );
                throw new ExceptionInInitializerError( msg );
            }
            try
            {
                m_port = Integer.parseInt( sPort );
            }
            catch ( NumberFormatException e )
            {
                String msg = m_res.format( "BAD_PORT", sPort );
                m_outError.println( msg );
                throw new ExceptionInInitializerError( msg );
            }
            
            m_jvmPort =
                WrapperSystemPropertyUtil.getIntProperty( "wrapper.jvm.port", 0 );
            m_jvmPortMin =
                WrapperSystemPropertyUtil.getIntProperty( "wrapper.jvm.port.min", 31000 );
            m_jvmPortMax =
                WrapperSystemPropertyUtil.getIntProperty( "wrapper.jvm.port.max", 31999 );
            
            // Check for the ignore signals flag
            m_ignoreSignals = WrapperSystemPropertyUtil.getBooleanProperty(
                "wrapper.ignore_signals", false );
            
            // If this is being run as a headless server, then a flag would have been set
            m_service = WrapperSystemPropertyUtil.getBooleanProperty( "wrapper.service", false );
            
            // Get the cpuTimeout
            String sCPUTimeout = System.getProperty( "wrapper.cpu.timeout" );
            if ( sCPUTimeout == null )
            {
                m_cpuTimeout = DEFAULT_CPU_TIMEOUT;
            }
            else
            {
                try
                {
                    m_cpuTimeout = Integer.parseInt( sCPUTimeout ) * 1000L;
                }
                catch ( NumberFormatException e )
                {
                    String msg = m_res.format( "BAD_CPU_TIMEOUT", sCPUTimeout );
                    m_outError.println( msg );
                    throw new ExceptionInInitializerError( msg );
                }
            }
        }
        
        // Make sure that the version of the Wrapper is correct.
        verifyWrapperVersion();
        
        // Register the MBeans if configured to do so.
        if ( WrapperSystemPropertyUtil.getBooleanProperty(
            WrapperManager.class.getName() + ".mbean", true ) )
        {
            registerMBean( new org.tanukisoftware.wrapper.jmx.WrapperManager(),
                "org.tanukisoftware.wrapper:type=WrapperManager" );
        }
        if ( WrapperSystemPropertyUtil.getBooleanProperty(
            WrapperManager.class.getName() + ".mbean.testing", false ) )
        {
            registerMBean( new org.tanukisoftware.wrapper.jmx.WrapperManagerTesting(),
                "org.tanukisoftware.wrapper:type=WrapperManagerTesting" );
        }
        
        // Initialize the native code to trap system signals
        initializeNativeLibrary();
        
        if ( m_libraryOK )
        {
            // Make sure that the native library's version is correct.
            verifyNativeLibraryVersion();
            
            // Get the PID of the current JVM from the native library.  Be careful as the method
            //  will not exist if the library is old.
            try
            {
                System.setProperty( "wrapper.java.pid", Integer.toString( nativeGetJavaPID() ) );
            }
            catch ( Throwable e )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Call to nativeGetJavaPID() failed: " + e );
                }
            }
        }
        
        // Start a thread which looks for control events sent to the
        //  process.  The thread is also used to keep track of whether
        //  the VM has been getting CPU to avoid invalid timeouts and
        //  to maintain the number of ticks since the JVM was launched.
        m_eventRunnerTicks = getTicks();
        m_eventRunner = new Thread( "Wrapper-Control-Event-Monitor" )
        {
            public void run()
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Control event monitor thread started." );
                }
                
                try
                {
                    WrapperTickEventImpl tickEvent = new WrapperTickEventImpl();
                    int lastTickOffset = 0;
                    boolean first = true;
                    
                    while ( !m_shuttingDown )
                    {
                        int offsetDiff;
                        if ( !m_useSystemTime )
                        {
                            // Get the tick count based on the system time.
                            int sysTicks = getSystemTicks();
                            
                            // Increment the tick counter by 1. This loop takes just slightly
                            //  more than the length of a "tick" but it is a good enough
                            //  approximation for our purposes.  The accuracy of the tick length
                            //  falls sharply when the system is under heavly load, but this
                            //  has the desired effect as the Wrapper is also much less likely
                            //  to encounter false timeouts due to the heavy load.
                            // The ticks field is volatile and a single integer, so it is not
                            //  necessary to synchronize this.
                            // When the ticks count reaches the upper limit of the int range,
                            //  it is ok to just let it overflow and wrap.
                            m_ticks++;
                            
                            // Calculate the offset between the two tick counts.
                            //  This will always work due to overflow.
                            int tickOffset = sysTicks - m_ticks;
                            
                            // The number we really want is the difference between this tickOffset
                            //  and the previous one.
                            offsetDiff = tickOffset - lastTickOffset;
                            
                            if ( first )
                            {
                                first = false;
                            }
                            else
                            {
                                if ( offsetDiff > m_timerSlowThreshold )
                                {
                                    m_outInfo.println( "The timer fell behind the system clock by "
                                        + ( offsetDiff * TICK_MS ) + "ms." );
                                }
                                else if ( offsetDiff < - m_timerFastThreshold )
                                {
                                    m_outInfo.println( "The system clock fell behind the timer by "
                                        + ( -1 * offsetDiff * TICK_MS ) + "ms." );
                                }
                            }
                            
                            // Store this tick offset for the net time through the loop.
                            lastTickOffset = tickOffset;
                        }
                        else
                        {
                            offsetDiff = 0;
                        }
                        
                        //m_outInfo.println( "  UNIX Time: "
                        //  + Long.toHexString( System.currentTimeMillis() )
                        //  + ", ticks=" + Integer.toHexString( getTicks() ) + ", sysTicks="
                        //  + Integer.toHexString( getSystemTicks() ) );
                            
                        // Attempt to detect whether or not we are being starved of CPU.
                        //  This will only have any effect if the m_useSystemTime flag is
                        //  set.
                        int nowTicks = getTicks();
                        long age = getTickAge( m_eventRunnerTicks, nowTicks );
                        if ( ( m_cpuTimeout > 0 ) && ( age > m_cpuTimeout ) )
                        {
                            m_outInfo.println( "JVM Process has not received any CPU time for "
                                + ( age / 1000 ) + " seconds.  Extending timeouts." );
                            
                            // Make sure that we don't get any ping timeouts in this event
                            m_lastPingTicks = nowTicks;
                        }
                        m_eventRunnerTicks = nowTicks;
                        
                        // If there are any listeners interrested in core events then fire
                        //  off a tick event.
                        if ( m_produceCoreEvents )
                        {
                            tickEvent.m_ticks = nowTicks;
                            tickEvent.m_tickOffset = offsetDiff;
                            fireWrapperEvent( tickEvent );
                        }
                        
                        if ( m_libraryOK )
                        {
                            // Look for control events in the wrapper library.
                            //  There may be more than one.
                            int event = 0;
                            do
                            {
                                event = WrapperManager.nativeGetControlEvent();
                                if ( event != 0 )
                                {
                                    WrapperManager.controlEvent( event );
                                }
                            }
                            while ( event != 0 );
                        }
                        
                        // Wait before checking for another control event.
                        try
                        {
                            Thread.sleep( TICK_MS );
                        }
                        catch ( InterruptedException e )
                        {
                        }
                    }
                }
                finally
                {
                    if ( m_debug )
                    {
                        m_outDebug.println( "Control event monitor thread stopped." );
                    }
                }
            }
        };
        m_eventRunner.setDaemon( true );
        m_eventRunner.start();
        
        // Resolve the system thread count based on the Java Version
        String fullVersion = System.getProperty( "java.fullversion" );
        String vendor = System.getProperty( "java.vm.vendor", "" );
        String os = System.getProperty( "os.name", "" ).toLowerCase();
        if ( fullVersion == null )
        {
            fullVersion = System.getProperty( "java.runtime.version" ) + " "
                + System.getProperty( "java.vm.name" );
        }
        
        if ( m_debug )
        {
            // Display more JVM info right after the call initialization of the library.
            m_outDebug.println( "Java Version   : " + fullVersion );
            m_outDebug.println( "Java VM Vendor : " + vendor );
            m_outDebug.println( "OS Name        : " + System.getProperty( "os.name", "" ) );
            m_outDebug.println( "OS Arch        : " + System.getProperty( "os.arch", "" ) );
            m_outDebug.println();
        }
        
        // This thread will most likely be thread which launches the JVM.
        //  Once this method returns however, the main thread will likely
        //  quit.  There will be a slight delay before the Wrapper binary
        //  has a change to send a command to start the application.
        //  During this lag, the JVM may not have any non-daemon threads
        //  running and would exit.   To keep it from doing so, start a
        //  simple non-daemon thread which will run until the
        //  WrapperListener.start() method returns or the Wrapper's
        //  shutdown thread has started.
        m_startupRunner = new Thread( "Wrapper-Startup-Runner" )
        {
            public void run()
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Startup runner thread started." );
                }
                
                try
                {
                    while ( m_startupRunner != null )
                    {
                        try
                        {
                            Thread.sleep( 100 );
                        }
                        catch ( InterruptedException e )
                        {
                            // Ignore.
                        }
                    }
                }
                finally
                {
                    if ( m_debug )
                    {
                        m_outDebug.println( "Startup runner thread stopped." );
                    }
                }
            }
        };
        // This thread must not be a daemon thread.
        m_startupRunner.setDaemon( false );
        m_startupRunner.start();
        
        // Create the singleton
        m_instance = new WrapperManager();
    }

    /*---------------------------------------------------------------
     * Native Methods
     *-------------------------------------------------------------*/
    private static native void nativeInit( boolean debug );
    private static native String nativeGetLibraryVersion();
    private static native int nativeGetJavaPID();
    private static native boolean nativeIsProfessionalEdition();
    private static native boolean nativeIsStandardEdition();
    private static native int nativeGetControlEvent();
    private static native void nativeRequestThreadDump();
    private static native void accessViolationInner();
    private static native void nativeSetConsoleTitle( byte[] titleBytes );
    private static native WrapperUser nativeGetUser( boolean groups );
    private static native WrapperUser nativeGetInteractiveUser( boolean groups );
    private static native WrapperWin32Service[] nativeListServices();
    private static native WrapperWin32Service nativeSendServiceControlCode( byte[] serviceName, int controlCode );
    
    /*---------------------------------------------------------------
     * Methods
     *-------------------------------------------------------------*/
    /**
     * Returns a tick count calculated from the system clock.
     */
    private static int getSystemTicks()
    {
        // Calculate a tick count using the current system time.  The
        //  conversion from a long in ms, to an int in TICK_MS increments
        //  will result in data loss, but the loss of bits and resulting
        //  overflow is expected and Ok.
        return (int)( System.currentTimeMillis() / TICK_MS );
    }
    
    /**
     * Returns the number of ticks since the JVM was launched.  This
     *  count is not good enough to be used where accuracy is required but
     *  it allows us to implement timeouts in environments where the system
     *  time is modified while the JVM is running.
     * <p>
     * An int is used rather than a long so the counter can be implemented
     *  without requiring any synchronization.  At the tick resolution, the
     *  tick counter will overflow and wrap (every 6.8 years for 100ms ticks).
     *  This behavior is expected.  The getTickAge method should be used
     *  in cases where the difference between two ticks is required.
     *
     * Returns the tick count.
     */
    private static int getTicks()
    {
        if ( m_useSystemTime )
        {
            return getSystemTicks();
        }
        else
        {
            return m_ticks;
        }
    }
    
    /**
     * Returns the number of milliseconds that have elapsed between the
     *  start and end counters.  This method assumes that both tick counts
     *  were obtained by calling getTicks().  This method will correctly
     *  handle cases where the tick counter has overflowed and reset.
     *
     * @param start A base tick count.
     * @param end An end tick count.
     *
     * @return The number of milliseconds that are represented by the
     *         difference between the two specified tick counts.
     */
    private static long getTickAge( int start, int end )
    {
        // Important to cast the first value so that negative values are correctly
        //  cast to negative long values.
        return (long)( end - start ) * TICK_MS;
    }
    
    /**
     * Attempts to load the a native library file.
     *
     * @param name Name of the library to load.
     * @param file Name of the actual library file.
     *
     * @return null if the library was successfully loaded, an error message
     *         otherwise.
     */
    private static String loadNativeLibrary( String name, String file )
    {
        try
        {
            System.loadLibrary( name );
            
            if ( m_debug )
            {
                m_outDebug.println( "  Loaded native library: " + file );
            }
            
            return null;
        }
        catch ( UnsatisfiedLinkError e )
        {
            if ( m_debug )
            {
                m_outDebug.println( "  Unable to load native library: " + file + "  Cause: " + e.getMessage() );
            }
            String error = e.getMessage();
            if ( error == null )
            {
                error = e.toString();
            }
            return error;
        }
        catch ( Throwable e )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Loading native library failed: " + file + "  Cause: " + e );
            }
            String error = e.toString();
            return error;
        }
    }
    
    /**
     * Java 1.5 and above supports the ability to register the WrapperManager
     *  MBean internally.
     */
    private static void registerMBean( Object mbean, String name )
    {
        Class classManagementFactory;
        try
        {
            classManagementFactory = Class.forName( "java.lang.management.ManagementFactory" );
        }
        catch ( ClassNotFoundException e )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Registering MBeans not supported by current JVM: " + name );
            }
            return;
        }
        
        try
        {
            // This code uses reflection so it combiles on older JVMs.
            // The original code is as follows:
            // javax.management.MBeanServer mbs =
            //     java.lang.management.ManagementFactory.getPlatformMBeanServer();
            // javax.management.ObjectName oName = new javax.management.ObjectName( name );
            // mbs.registerMBean( mbean, oName );
            
            // The version of the above code using reflection follows.
            Class classMBeanServer = Class.forName( "javax.management.MBeanServer" );
            Class classObjectName = Class.forName( "javax.management.ObjectName" );
            Method methodGetPlatformMBeanServer =
                classManagementFactory.getMethod( "getPlatformMBeanServer", null );
            Constructor constructorObjectName =
                classObjectName.getConstructor( new Class[] {String.class} );
            Method methodRegisterMBean = classMBeanServer.getMethod(
                "registerMBean", new Class[] {Object.class, classObjectName} );
            Object mbs = methodGetPlatformMBeanServer.invoke( null, null );
            Object oName = constructorObjectName.newInstance( new Object[] {name} );
            methodRegisterMBean.invoke( mbs, new Object[] {mbean, oName} );
            
            if ( m_debug )
            {
                m_outDebug.println( "Registered MBean with Platform MBean Server: " + name );
            }
        }
        catch ( Throwable t )
        {
            m_outError.println( "Unable to register the " + name + " MBean." );
            t.printStackTrace( m_outError );
        }
    }
    
    /**
     * Searches for a file on a path.
     *
     * @param file File to look for.
     * @param path Path to be searched.
     *
     * @return Reference to thr file object if found, otherwise null.
     */
    private static File locateFileOnPath( String file, String path )
    {
        // A library path exists but the library was not found on it.
        String pathSep = System.getProperty( "path.separator" );
        
        // Search for the file on the library path to verify that it does not
        //  exist, it could be some other problem
        StringTokenizer st = new StringTokenizer( path, pathSep );
        while( st.hasMoreTokens() )
        {
            File libFile = new File( new File( st.nextToken() ), file );
            if ( libFile.exists() )
            {
                return libFile;
            }
        }
        
        return null;
    }
    
    /**
     * Generates a detailed native library base name which is made up of the
     *  base name, the os name, architecture, and the bits of the current JVM,
     *  not the platform.
     *
     * @return A detailed native library base name.
     */
    private static String generateDetailedNativeLibraryBaseName( String baseName,
                                                                 int jvmBits )
    {
        // Generate an os name.  Most names are used as is, but some are modified.
        String os = System.getProperty( "os.name", "" ).toLowerCase();
        if ( os.startsWith( "windows" ) )
        {
            os = "windows";
        }
        else if ( os.equals( "sunos" ) )
        {
            os = "solaris";
        }
        else if ( os.equals( "hp-ux" ) || os.equals( "hp-ux64" ) )
        {
            os = "hpux";
        }
        else if ( os.equals( "mac os x" ) )
        {
            os = "macosx";
        }
        else if ( os.equals( "unix_sv" ) )
        {
            os = "unixware";
        }
        else if ( os.equals( "os/400" ) )
        {
            os = "os400";
        }
        
        // Generate an architecture name.
        String arch;
        if ( os.equals( "macosx" ) )
        {
            arch = "universal";
        }
        else
        {
            arch = System.getProperty( "os.arch", "" ).toLowerCase();
            if ( arch.equals( "amd64" ) || arch.equals( "athlon" ) || arch.equals( "x86_64" ) ||
                arch.equals( "i686" ) || arch.equals( "i586" ) || arch.equals( "i486" ) ||
                arch.equals( "i386" ) )
            {
                arch = "x86";
            }
            else if ( arch.startsWith( "ia32" ) || arch.startsWith( "ia64" ) )
            {
                arch = "ia";
            }
            else if ( arch.startsWith( "sparc" ) )
            {
                arch = "sparc";
            }
            else if ( arch.equals( "power" ) || arch.equals( "powerpc" ) || arch.equals( "ppc64" ) )
            {
                arch = "ppc";
            }
            else if ( arch.startsWith( "pa_risc" ) || arch.startsWith( "pa-risc" ) )
            {
                arch = "parisc";
            }
        }
        
        return baseName + "-" + os + "-" + arch + "-" + jvmBits;
    }
    
    /**
     * Searches for and then loads the native library.  This method will attempt
     *  locate the wrapper library using one of the following 3 naming 
     */
    private static void initializeNativeLibrary()
    {
        // Look for the base name of the library.
        String baseName = System.getProperty( "wrapper.native_library" );
        if ( baseName == null )
        {
            // This should only happen if an old version of the Wrapper binary is being used.
            m_outInfo.println( "WARNING - The wrapper.native_library system property was not" );
            m_outInfo.println( "          set. Using the default value, 'wrapper'." );
            baseName = "wrapper";
        }
        String[] detailedNames = new String[4];
        if ( m_jvmBits > 0 )
        {
            detailedNames[0] = generateDetailedNativeLibraryBaseName( baseName, m_jvmBits );
        }
        else
        {
            detailedNames[0] = generateDetailedNativeLibraryBaseName( baseName, 32 );
            detailedNames[1] = generateDetailedNativeLibraryBaseName( baseName, 64 );
        }
        
        // Construct brief and detailed native library file names.
        String file = System.mapLibraryName( baseName );
        String[] detailedFiles = new String[detailedNames.length];
        for ( int i = 0; i < detailedNames.length; i++ )
        {
            if ( detailedNames[i] != null )
            {
                detailedFiles[i] = System.mapLibraryName( detailedNames[i] );
            }
        }
        
        String[] detailedErrors = new String[detailedNames.length];
        String baseError = null;
        
        // Try loading the native library using the detailed name first.  If that fails, use
        //  the brief name.
        if ( m_debug )
        {
            m_outDebug.println( "Load native library.  One or more attempts may fail if platform "
                + "specific libraries do not exist.  This is NORMAL and is only a problem if they "
                + "all fail." ); 
        }
        m_libraryOK = false;
        for ( int i = 0; i < detailedNames.length; i++ )
        {
            if ( detailedNames[i] != null )
            {
                detailedErrors[i] = loadNativeLibrary( detailedNames[i], detailedFiles[i] );
                if ( detailedErrors[i] == null )
                {
                    m_libraryOK = true;
                    break;
                }
            }
        }
        if ( ( !m_libraryOK ) && ( ( baseError = loadNativeLibrary( baseName, file ) ) == null ) )
        {
            m_libraryOK = true;
        }
        if ( m_libraryOK )
        {
            // The library was loaded correctly, so initialize it.
            if ( m_debug )
            {
                m_outDebug.println( "Calling native initialization method." );
            }
            nativeInit( m_debug );
        }
        else
        {
            // The library could not be loaded, so we want to give the user a useful
            //  clue as to why not.
            String libPath = System.getProperty( "java.library.path" );
            m_outInfo.println();
            if ( libPath.equals( "" ) )
            {
                // No library path
                m_outInfo.println(
                    "WARNING - Unable to load the Wrapper's native library because the" );
                m_outInfo.println(
                    "          java.library.path was set to ''.  Please see the" );
                m_outInfo.println(
                    "          documentation for the wrapper.java.library.path " );
                m_outInfo.println(
                    "          configuration property.");
            }
            else
            {
                // Attempt to locate the actual files on the path.
                String error = null;
                File libFile = null;
                for ( int i = 0; i < detailedNames.length; i++ )
                {
                    if ( detailedFiles[i] != null )
                    {
                        libFile = locateFileOnPath( detailedFiles[i], libPath );
                        if ( libFile != null )
                        {
                            error = detailedErrors[i];
                            break;
                        }
                    }
                }
                if ( libFile == null )
                {
                    libFile = locateFileOnPath( file, libPath );
                    if ( libFile != null )
                    {
                        error = baseError;
                    }
                }
                if ( libFile == null )
                {
                    // The library could not be located on the library path.
                    m_outInfo.println(
                        "WARNING - Unable to load the Wrapper's native library because none of the" );
                    m_outInfo.println(
                        "          following files:" );
                    for ( int i = 0; i < detailedNames.length; i++ )
                    {
                        if ( detailedFiles[i] != null )
                        {
                            m_outInfo.println(
                                "            " + detailedFiles[i] );
                        }
                    }
                    m_outInfo.println(
                        "            " + file );
                    m_outInfo.println(
                        "          could be located on the following java.library.path:" );
                    
                    String pathSep = System.getProperty( "path.separator" );
                    StringTokenizer st = new StringTokenizer( libPath, pathSep );
                    while ( st.hasMoreTokens() )
                    {
                        File pathElement = new File( st.nextToken() );
                        m_outInfo.println( "            " + pathElement.getAbsolutePath() );
                    }
                    m_outInfo.println(
                        "          Please see the documentation for the "
                        +          "wrapper.java.library.path" );
                    m_outInfo.println(
                        "          configuration property." );
                }
                else
                {
                    // The library file was found but could not be loaded for some reason.
                    m_outInfo.println(
                        "WARNING - Unable to load the Wrapper's native library '" + libFile.getName() + "'." );
                    m_outInfo.println(
                        "          The file is located on the path at the following location but" );
                    m_outInfo.println(
                        "          could not be loaded:" );
                    m_outInfo.println(
                        "            " + libFile.getAbsolutePath() );
                    m_outInfo.println(
                        "          Please verify that the file is readable by the current user" );
                    m_outInfo.println(
                        "          and that the file has not been corrupted in any way." );
                    m_outInfo.println(
                        "          One common cause of this problem is running a 32-bit version" );
                    m_outInfo.println(
                        "          of the Wrapper with a 64-bit version of Java, or vica versa." );
                    if ( m_jvmBits > 0 )
                    {
                        m_outInfo.println(
                            "          This is a " + m_jvmBits + "-bit JVM." );
                    }
                    else
                    {
                        m_outInfo.println(
                            "          The bit depth of this JVM could not be determined." );
                    }
                    m_outInfo.println(
                        "          Reported cause:" );
                    m_outInfo.println(
                        "            " + error );
                }
            }
            m_outInfo.println( "          System signals will not be handled correctly." );
            m_outInfo.println();
        }
    }
    
    /**
     * Compares the version of the wrapper which launched this JVM with that of
     *  the jar.  If they differ then a Warning message will be displayed.  The
     *  Wrapper application will still be allowed to start.
     */
    private static void verifyWrapperVersion()
    {
        // If we are not being controlled by the wrapper then return.
        if ( !WrapperManager.isControlledByNativeWrapper() )
        {
            return;
        }
        
        // Lookup the version from the wrapper.  It should have been set as a property
        //  when the JVM was launched.
        String wrapperVersion = System.getProperty( "wrapper.version" );
        if ( wrapperVersion == null )
        {
            wrapperVersion = "unknown";
        }
        if ( wrapperVersion.endsWith( "-pro" ) )
        {
            wrapperVersion = wrapperVersion.substring( 0, wrapperVersion.length() - 4 );
        }
        else if ( wrapperVersion.endsWith( "-st" ) )
        {
            wrapperVersion = wrapperVersion.substring( 0, wrapperVersion.length() - 3 );
        }
        
        if ( !WrapperInfo.getVersion().equals( wrapperVersion ) )
        {
            m_outInfo.println(
                "WARNING - The Wrapper jar file currently in use is version \""
                + WrapperInfo.getVersion() + "\"" );
            m_outInfo.println(
                "          while the version of the Wrapper which launched this JVM is " );
            m_outInfo.println(
                "          \"" + wrapperVersion + "\"." );
            m_outInfo.println(
                "          The Wrapper may appear to work correctly but some features may" );
            m_outInfo.println(
                "          not function correctly.  This configuration has not been tested" );
            m_outInfo.println(
                "          and is not supported." );
            m_outInfo.println();
        }
    }
    
    /**
     * Compares the version of the native library with that of this jar.  If
     *  they differ then a Warning message will be displayed.  The Wrapper
     *  application will still be allowed to start.
     */
    private static void verifyNativeLibraryVersion()
    {
        // Request the version from the native library.  Be careful as the method
        //  will not exist if the library is old.
        String jniVersion;
        try
        {
            jniVersion = nativeGetLibraryVersion();
        }
        catch ( Throwable e )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Call to nativeGetLibraryVersion() failed: " + e );
            }
            jniVersion = "unknown";
        }
        
        String wrapperVersion = System.getProperty( "wrapper.version" );
        if ( wrapperVersion == null )
        {
            wrapperVersion = "unknown";
        }
        
        if ( !wrapperVersion.equals( jniVersion ) )
        {
            m_outInfo.println(
                "WARNING - The version of the Wrapper which launched this JVM is " );
            m_outInfo.println(
                "          \"" + wrapperVersion + "\" while the version of the native library " );
            m_outInfo.println(
                "          is \"" + jniVersion + "\"." );
            m_outInfo.println(
                "          The Wrapper may appear to work correctly but some features may" );
            m_outInfo.println(
                "          not function correctly.  This configuration has not been tested" );
            m_outInfo.println(
                "          and is not supported." );
            m_outInfo.println();
        }
    }
    
    /**
     * Obtain the current version of Wrapper.
     *
     * @return The version of the Wrapper.
     */
    public static String getVersion()
    {
        return WrapperInfo.getVersion();
    }
    
    /**
     * Obtain the build time of Wrapper.
     *
     * @return The time that the Wrapper was built.
     */
    public static String getBuildTime()
    {
        return WrapperInfo.getBuildTime();
    }
    
    /**
     * Returns the Id of the current JVM.  JVM Ids increment from 1 each time
     *  the wrapper restarts a new one.
     *
     * @return The Id of the current JVM.
     */
    public static int getJVMId()
    {
        return m_jvmId;
    }
    
    /**
     * Returns true if the current Wrapper edition has support for Professional
     *  Edition features.
     *
     * @return True if professional features are supported.
     */
    public static boolean isProfessionalEdition()
    {
        // Be careful as this will not exist in older versions
        if ( m_libraryOK )
        {
            try
            {
                return nativeIsProfessionalEdition();
            }
            catch ( Throwable e )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Call to nativeIsProfessionalEdition() failed: " + e );
                }
                return false;
            }
        }
        else
        {
            return false;
        }
    }
    
    /**
     * Returns true if the current Wrapper edition has support for Standard
     *  Edition features.
     *
     * @return True if standard features are supported.
     */
    public static boolean isStandardEdition()
    {
        // Be careful as this will not exist in older versions
        if ( m_libraryOK )
        {
            try
            {
                return nativeIsStandardEdition();
            }
            catch ( Throwable e )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Call to nativeIsStandardEdition() failed: " + e );
                }
                return false;
            }
        }
        else
        {
            return false;
        }
    }
    
    /**
     * Sets the title of the console in which the Wrapper is running.  This
     *  is currently only supported on Windows platforms.
     * <p>
     * As an alternative, it is also possible to set the console title from
     *  within the wrapper.conf file using the wrapper.console.title property.
     *
     * @param title The new title.  The specified string will be encoded
     *              to a byte array using the default encoding for the
     *              current platform.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("setConsoleTitle")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static void setConsoleTitle( String title )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "setConsoleTitle" ) );
        }
        
        if ( m_libraryOK )
        {
            // Convert the unicode string to a string of bytes using the default
            //  platform encoding.
            byte[] titleBytes = title.getBytes();
            
            // We need a null terminated string.
            byte[] nullTermBytes = new byte[titleBytes.length + 1];
            System.arraycopy( titleBytes, 0, nullTermBytes, 0, titleBytes.length );
            nullTermBytes[titleBytes.length] = 0;
            
            nativeSetConsoleTitle( nullTermBytes );
        }
    }
    
    /**
     * Returns a WrapperUser object which describes the user under which the
     *  Wrapper is currently running.  Additional platform specific information
     *  can be obtained by casting the object to a platform specific subclass.
     *  WrapperWin32User, for example.
     *
     * @param groups True if the user's groups should be returned as well.
     *               Requesting the groups that a user belongs to increases
     *               the CPU load required to complete the call.
     *
     * @return An object describing the current user.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("getUser") permission.
     *
     * @see WrapperPermission
     */
    public static WrapperUser getUser( boolean groups )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "getUser" ) );
        }
        
        WrapperUser user = null;
        if ( m_libraryOK )
        {
            user = nativeGetUser( groups );
        }
        return user;
    }
    
    /**
     * Returns a WrapperUser object which describes the interactive user whose
     *  desktop is being interacted with.  When a service running on a Windows
     *  platform has its interactive flag set, this method will return the user
     *  who is currently logged in.  Additional platform specific information
     *  can be obtained by casting the object to a platform specific subclass.
     *  WrapperWin32User, for example.
     * <p>
     * If a user is not currently logged on then this method will return null.
     *  User code can repeatedly call this method to detect when a user has
     *  logged in.  To detect when a user has logged out, there are two options.
     *  1) The user code can continue to call this method until it returns null.
     *  2) Or if the WrapperListener method is being implemented, the
     *     WrapperListener.controlEvent method will receive a WRAPPER_CTRL_LOGOFF_EVENT
     *     event when the user logs out.
     * <p>
     * On XP systems, it is possible to switch to another account rather than
     *  actually logging out.  In such a case, the interactive user will be
     *  the first user that logged in.  This will also be the only user with
     *  which the service will interact.  If other users are logged in when the
     *  interactive user logs out, the service will not automatically switch to
     *  another logged in user.  Rather, the next user to log in will become
     *  the new user which the service will interact with.
     * <p>
     * This method will always return NULL on versions of NT prior to Windows
     *  2000.  This can not be helped as some required functions were not added
     *  to the windows API until NT version 5.0, also known as Windows 2000.
     *
     * @param groups True if the user's groups should be returned as well.
     *               Requesting the groups that a user belongs to increases
     *               the CPU load required to complete the call.
     *
     * @return The current interactive user, or null.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("getInteractiveUser")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static WrapperUser getInteractiveUser( boolean groups )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "getInteractiveUser" ) );
        }
        
        WrapperUser user = null;
        if ( m_libraryOK )
        {
            user = nativeGetInteractiveUser( groups );
        }
        return user;
    }
    
    /**
     * Returns a Properties object containing expanded the contents of the
     *  configuration file used to launch the Wrapper.
     *
     * All properties are included so it is possible to define properties
     *  not used by the Wrapper in the configuration file and have then
     *  be available in this Properties object.
     *
     * @return The contents of the Wrapper configuration file.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("getProperties")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static Properties getProperties()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "getProperties" ) );
        }
        
        return m_properties;
    }
    
    /**
     * Returns the PID of the Wrapper process.
     *
     * A PID of 0 will be returned if the JVM was launched standalone.
     *
     * This value can also be obtained using the 'wrapper.pid' system property.
     *
     * @return The PID of the Wrpper process.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("getWrapperPID") permission.
     *
     * @see WrapperPermission
     */
    public static int getWrapperPID()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "getWrapperPID" ) );
        }
        
        return WrapperSystemPropertyUtil.getIntProperty( "wrapper.pid", 0 );
    }
    
    /**
     * Returns the PID of the Java process.
     *
     * A PID of 0 will be returned if the native library has not been initialized.
     *
     * This value can also be obtained using the 'wrapper.java.pid' system property.
     *
     * @return The PID of the Java process.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("getJavaPID") permission.
     *
     * @see WrapperPermission
     */
    public static int getJavaPID()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "getJavaPID" ) );
        }
        
        return WrapperSystemPropertyUtil.getIntProperty( "wrapper.java.pid", 0 );
    }
    
    /**
     * Requests that the current JVM process request a thread dump.  This is
     *  the same as pressing CTRL-BREAK (under Windows) or CTRL-\ (under Unix)
     *  in the the console in which Java is running.  This method does nothing
     *  if the native library is not loaded.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("requestThreadDump")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static void requestThreadDump()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "requestThreadDump" ) );
        }
        
        if ( m_libraryOK )
        {
            nativeRequestThreadDump();
        }
        else
        {
            m_outInfo.println( "  wrapper library not loaded." );
        }
    }
    
    /**
     * (Testing Method) Causes the WrapperManager to go into a state which makes the JVM appear
     *  to be hung when viewed from the native Wrapper code.  Does not have any effect when the
     *  JVM is not being controlled from the native Wrapper. Useful for testing the Wrapper 
     *  functions.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("test.appearHung") permission.
     *
     * @see WrapperPermission
     */
    public static void appearHung()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "test.appearHung" ) );
        }
        
        m_outInfo.println( "WARNING: Making JVM appear to be hung..." );
        m_appearHung = true;
    }
    
    /**
     * (Testing Method) Cause an access violation within the Java code.  Useful
     *  for testing the Wrapper functions.  This currently only crashes Sun
     *  JVMs and takes advantage of Bug #4369043 which does not exist in newer
     *  JVMs.  Use of the accessViolationNative() method is preferred.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("test.accessViolation")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static void accessViolation()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "test.accessViolation" ) );
        }
        
        m_outInfo.println( "WARNING: Attempting to cause an access violation..." );
        
        try
        {
            Class c = Class.forName( "java.lang.String" );
            java.lang.reflect.Method m = c.getDeclaredMethod( null, null );
        }
        catch( NoSuchMethodException ex )
        {
            // Correctly did not find method.  access_violation attempt failed.  Not Sun JVM?
        }
        catch( Exception ex )
        {
            if ( ex instanceof NoSuchFieldException )
            {
                // Can't catch this in a catch because the compiler doesn't think it is being
                //  thrown.  But it is thrown on IBM jvms at least
                // Correctly did not find method.  access_violation attempt failed.  Not Sun JVM?
            }
            else
            {
                // Shouldn't get here.
                ex.printStackTrace( m_outError );
            }
        }
        
        m_outInfo.println( "  Attempt to cause access violation failed.  JVM is still alive." );
    }

    /**
     * (Testing Method) Cause an access violation within native JNI code.
     *  Useful for testing the Wrapper functions. This currently causes the
     *  access violation by attempting to write to a null pointer.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("test.accessViolationNative")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static void accessViolationNative()
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "test.accessViolationNative" ) );
        }
        
        m_outInfo.println( "WARNING: Attempting to cause an access violation..." );
        if ( m_libraryOK )
        {
            accessViolationInner();
        
            m_outInfo.println( "  Attempt to cause access violation failed.  JVM is still alive." );
        }
        else
        {
            m_outInfo.println( "  wrapper library not loaded." );
        }
    }
        
    /**
     * Returns true if the JVM was launched by the Wrapper application.  False
     *  if the JVM was launched manually without the Wrapper controlling it.
     *
     * @return True if the current JVM was launched by the Wrapper.
     */
    public static boolean isControlledByNativeWrapper()
    {
        return m_key != null;
    }
    
    /**
     * Returns true if the Wrapper was launched as an NT service on Windows or
     *  as a daemon process on UNIX platforms.  False if launched as a console.
     *  This can be useful if you wish to display a user interface when in
     *  Console mode.  On UNIX platforms, this is not as useful because an
     *  X display may not be visible even if launched in a console.
     *
     * @return True if the Wrapper is running as an NT service or daemon
     *         process.
     */
    public static boolean isLaunchedAsService()
    {
        return m_service;
    }
    
    /**
     * Returns true if the JVM should ignore user logoff events.  Mainly used
     *  within WrapperListener.controlEvent() method implemenations.
     
     * @return True if user logoff events should be ignroed.
     */
    public static boolean isIgnoreUserLogoffs()
    {
        return m_ignoreUserLogoffs;
    }
    
    /**
     * Returns true if the wrapper.debug property, or any of the logging
     *  channels are set to DEBUG in the wrapper configuration file.  Useful
     *  for deciding whether or not to output certain information to the
     *  console.
     *
     * @return True if the Wrapper is logging any Debug level output.
     */
    public static boolean isDebugEnabled()
    {
        return m_debug;
    }
    
    /**
     * Start the Java side of the Wrapper code running.  This will make it
     *  possible for the native side of the Wrapper to detect that the Java
     *  Wrapper is up and running.
     * <p>
     * This method must be called on startup and then can only be called once
     *  so there is no reason for any security permission checks on this call.
     *
     * @param listener The WrapperListener instance which represents the
     *                 application being started.
     * @param args The argument list passed to the JVM when it was launched.
     */
    public static synchronized void start( final WrapperListener listener, final String[] args )
    {
        // As was done in the static initializer, we need to execute the following
        //  code in a privileged action so it is not necessary for the calling code
        //  to have the same privileges as the wrapper jar.
        // This is safe because this method can only be called once and that one call
        //  will presumably be made on JVM startup.
        AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    privilegedStart( listener, args );
                    return null;
                }
            }
        );
    }
    
    /**
     * Called by the start method within a PrivilegedAction.
     *
     * @param WrapperListener The WrapperListener instance which represents
     *                        the application being started.
     * @param args The argument list passed to the JVM when it was launched.
     */
    private static void privilegedStart( WrapperListener listener, String[] args )
    {
        // Check the SecurityManager here as it is possible that it was set before this call.
        checkSecurityManager();
        
        // Just in case the user failed to provide an argument list, recover by creating one
        //  here.  This will avoid possible problems down stream.
        if ( args == null )
        {
            args = new String[0];
        }
        
        if ( m_debug )
        {
            StringBuffer sb = new StringBuffer();
            sb.append( "args[" );
            for ( int i = 0; i < args.length; i++ )
            {
                if ( i > 0 )
                {
                    sb.append( ", " );
                }
                sb.append( "\"" );
                sb.append( args[i] );
                sb.append( "\"" );
            }
            sb.append( "]" );
            
            m_outDebug.println( "WrapperManager.start(" + listener + ", " + sb.toString() + ") "
                + "called by thread: " + Thread.currentThread().getName() );
        }
        
        // Make sure that the class has not already been disposed.
        if ( m_disposed)
        {
            throw new IllegalStateException( "WrapperManager has already been disposed." );
        }
        
        if ( m_listener != null )
        {
            throw new IllegalStateException(
                "WrapperManager has already been started with a WrapperListener." );
        }
        if ( listener == null )
        {
            throw new IllegalStateException( "A WrapperListener must be specified." );
        }
        m_listener = listener;
        
        m_args = args;
        
        startRunner();
        
        // If this JVM is being controlled by a native wrapper, then we want to
        //  wait for the command to start.  However, if this is a standalone
        //  JVM, then we want to start now.
        if ( !isControlledByNativeWrapper() )
        {
            startInner();
        }
    }
    
    /**
     * Tells the native wrapper that the JVM wants to restart, then informs
     *  all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * This method will not return.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("restart") permission.
     *
     * @see WrapperPermission
     */
    public static void restart()
        throws SecurityException
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "restart" ) );
        }
        
        if ( m_debug )
        {
            m_outDebug.println( "WrapperManager.restart() called by thread: "
                + Thread.currentThread().getName() );
        }
        
        restartInner();
    }
    
    /**
     * Tells the native wrapper that the JVM wants to restart, then informs
     *  all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * This method requests that the JVM be restarted but then returns.  This
     *  allows components to initiate a JVM exit and then continue, allowing
     *  a normal shutdown initiated by the JVM via shutdown hooks.  In
     *  applications which are designed to be shutdown when the user presses
     *  CTRL-C, this may result in a cleaner shutdown.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("restart") permission.
     *
     * @see WrapperPermission
     */
    public static void restartAndReturn()
        throws SecurityException
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "restart" ) );
        }
        
        synchronized( WrapperManager.class )
        {
            if ( m_stopping )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "WrapperManager.restartAndReturn() called by thread: "
                        + Thread.currentThread().getName() + " already stopping." );
                }
                return;
            }
            else
            {
                if ( m_debug )
                {
                    m_outDebug.println( "WrapperManager.restartAndReturn() called by thread: "
                        + Thread.currentThread().getName() );
                }
            }
        }
        
        
        // To make this possible, we have to create a new thread to actually do the shutdown.
        Thread restarter = new Thread( "Wrapper-Restarter" )
        {
            public void run()
            {
                restartInner();
            }
        };
        restarter.setDaemon( false );
        restarter.start();
    }
    
    /**
     * Common code used to restart the JVM.  It is assumed that the calling
     *  thread has has passed security checks before this is called.
     */
    private static void restartInner()
    {
        boolean stopping;
        synchronized( WrapperManager.class )
        {
            stopping = m_stopping;
            if ( !stopping )
            {
                m_stopping = true;
            }
        }
        
        if ( !stopping )
        {
            // I used to check to make sure the commRunner was started, but that could fail
            //  for a number of reasons.  If it is down then leave it alone.
            //if ( !m_commRunnerStarted )
            //{
            //   startRunner();
            //}
            
            // Always send the stop command
            sendCommand( WRAPPER_MSG_RESTART, "restart" );
        }
        
        // Give the Wrapper a chance to register the stop command before stopping.
        // This avoids any errors thrown by the Wrapper because the JVM died before
        //  it was expected to.
        try
        {
            Thread.sleep( 1000 );
        }
        catch ( InterruptedException e )
        {
        }
        
        // This is safe because we are already checking for the privilege to restart the JVM
        //  above.  If we get this far then we want the Wrapper to be able to do everything
        //  necessary to stop the JVM.
        AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    privilegedStopInner( 0 );
                    return null;
                }
            }
        );
    }
    
    /**
     * Tells the native wrapper that the JVM wants to shut down, then informs
     *  all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * This method will not return.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("stop") permission.
     *
     * @see WrapperPermission
     */
    public static void stop( final int exitCode )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "stop" ) );
        }
        
        if ( m_debug )
        {
            m_outDebug.println( "WrapperManager.stop(" + exitCode + ") called by thread: "
                + Thread.currentThread().getName() );
        }
        
        stopCommon( exitCode, 1000 );
        
        // This is safe because we are already checking for the privilege to stop the JVM
        //  above.  If we get this far then we want the Wrapper to be able to do everything
        //  necessary to stop the JVM.
        AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    privilegedStopInner( exitCode );
                    return null;
                }
            }
        );
    }
    
    /**
     * Tells the native wrapper that the JVM wants to shut down, then informs
     *  all listeners that the JVM is about to shutdown before killing the JVM.
     * <p>
     * This method requests that the JVM be shutdown but then returns.  This
     *  allows components to initiate a JVM exit and then continue, allowing
     *  a normal shutdown initiated by the JVM via shutdown hooks.  In
     *  applications which are designed to be shutdown when the user presses
     *  CTRL-C, this may result in a cleaner shutdown.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("stop") permission.
     *
     * @see WrapperPermission
     */
    public static void stopAndReturn( final int exitCode )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "stop" ) );
        }
        
        synchronized( WrapperManager.class )
        {
            if ( m_stopping )
            {
                if ( m_debug )
                {
                    m_outDebug.println(
                        "WrapperManager.stopAndReturn(" + exitCode + ") called by thread: "
                        + Thread.currentThread().getName() + " already stopping." );
                }
                return;
            }
            else
            {
                if ( m_debug )
                {
                    m_outDebug.println(
                        "WrapperManager.stopAndReturn(" + exitCode + ") called by thread: "
                        + Thread.currentThread().getName() );
                }
            }
        }
        
        // To make this possible, we have to create a new thread to actually do the shutdown.
        Thread stopper = new Thread( "Wrapper-Stopper" )
        {
            public void run()
            {
                stopCommon( exitCode, 1000 );
                
                // This is safe because we are already checking for the privilege to stop the JVM
                //  above.  If we get this far then we want the Wrapper to be able to do everything
                //  necessary to stop the JVM.
                AccessController.doPrivileged(
                    new PrivilegedAction() {
                        public Object run() {
                            privilegedStopInner( exitCode );
                            return null;
                        }
                    }
                );
            }
        };
        stopper.setDaemon( false );
        stopper.start();
    }

    /**
     * Tells the native wrapper that the JVM wants to shut down and then
     *  promptly halts.  Be careful when using this method as an application
     *  will not be given a chance to shutdown cleanly.
     *
     * @param exitCode The exit code that the Wrapper will return when it exits.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("stopImmediate") permission.
     *
     * @see WrapperPermission
     */
    public static void stopImmediate( final int exitCode )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "stopImmediate" ) );
        }
        
        if ( m_debug )
        {
            m_outDebug.println( "WrapperManager.stopImmediate(" + exitCode + ") called by thread: "
                + Thread.currentThread().getName() );
        }
        
        stopCommon( exitCode, 250 );
        
        signalStopped( exitCode );
        
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
            m_outError.println( "halt not supported by current JVM." );
            haltMethod = null;
        }
        
        if ( haltMethod != null )
        {
            Runtime runtime = Runtime.getRuntime();
            try
            {
                haltMethod.invoke( runtime, new Object[] { new Integer( exitCode ) } );
            }
            catch ( IllegalAccessException e )
            {
                m_outError.println( "Unable to call runtime.halt: " + e );
            }
            catch ( InvocationTargetException e )
            {
                Throwable t = e.getTargetException();
                if ( t == null )
                {
                    t = e;
                }
                
                m_outError.println( "Unable to call runtime.halt: " + t );
            }
        }
        else
        {
            // Shutdown normally
            
            // This is safe because we are already checking for the privilege to stop the JVM
            //  above.  If we get this far then we want the Wrapper to be able to do everything
            //  necessary to stop the JVM.
            AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        privilegedStopInner( exitCode );
                        return null;
                    }
                }
            );
        }
    }
    
    /**
     * Signal the native wrapper that the startup is progressing but that more
     *  time is needed.  The current startup timeout will be extended if
     *  necessary so it will be at least 'waitHint' milliseconds in the future.
     * <p>
     * This call will have no effect if the current startup timeout is already
     *  more than 'waitHint' milliseconds in the future.
     *
     * @param waitHint Time in milliseconds to allow for the startup to
     *                 complete.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("signalStarting") permission.
     *
     * @see WrapperPermission
     */
    public static void signalStarting( int waitHint )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "signalStarting" ) );
        }
        
        sendCommand( WRAPPER_MSG_START_PENDING, Integer.toString( waitHint ) );
    }

    /**
     * Signal the native wrapper that the shutdown is progressing but that more
     *  time is needed.  The current shutdown timeout will be extended if
     *  necessary so it will be at least 'waitHint' milliseconds in the future.
     * <p>
     * This call will have no effect if the current shutdown timeout is already
     *  more than 'waitHint' milliseconds in the future.
     *
     * @param waitHint Time in milliseconds to allow for the shutdown to
     *                 complete.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("signalStopping") permission.
     *
     * @see WrapperPermission
     */
    public static void signalStopping( int waitHint )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "signalStopping" ) );
        }
        
        m_stopping = true;
        sendCommand( WRAPPER_MSG_STOP_PENDING, Integer.toString( waitHint ) );
    }
    
    /**
     * This method should not normally be called by user code as it is called
     *  from within the stop and restart methods.  However certain applications
     *  which stop the JVM may need to call this method to let the wrapper code
     *  know that the shutdown was intentional.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("signalStopped") permission.
     *
     * @see WrapperPermission
     */
    public static void signalStopped( int exitCode )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "signalStopped" ) );
        }
        
        m_stopping = true;
        sendCommand( WRAPPER_MSG_STOPPED, Integer.toString( exitCode ) );
        
        // Give the socket time to actuall send the packet to the Wrapper
        //  as this call is often immediately followed by a halt command.
        try
        {
            Thread.sleep( 250 );
        }
        catch ( InterruptedException e )
        {
            // Ignore.
        }
    }
    
    /**
     * Returns true if the ShutdownHook for the JVM has already been triggered.
     *  Some code needs to know whether or not the system is shutting down.
     *
     * @return True if the ShutdownHook for the JVM has already been triggered.
     */
    public static boolean hasShutdownHookBeenTriggered()
    {
        return m_hookTriggered;
    }
    
    /**
     * Requests that the Wrapper log a message at the specified log level.
     *  If the JVM is not being managed by the Wrapper then calls to this
     *  method will be ignored.  This method has been optimized to ignore
     *  messages at a log level which will not be logged given the current
     *  log levels of the Wrapper.
     * <p>
     * Log messages will currently by trimmed by the Wrapper at 4k (4096 bytes).
     * <p>
     * Because of differences in the way console output is collected and
     *  messages logged via this method, it is expected that interspersed
     *  console and log messages will not be in the correct order in the
     *  resulting log file.
     * <p>
     * This method was added to allow simple logging to the wrapper.log
     *  file.  This is not meant to be a full featured log file and should
     *  not be used as such.  Please look into a logging package for most
     *  application logging.
     *
     * @param logLevel The level to log the message at can be one of
     *                 WRAPPER_LOG_LEVEL_DEBUG, WRAPPER_LOG_LEVEL_INFO,
     *                 WRAPPER_LOG_LEVEL_STATUS, WRAPPER_LOG_LEVEL_WARN,
     *                 WRAPPER_LOG_LEVEL_ERROR, or WRAPPER_LOG_LEVEL_FATAL.
     * @param message The message to be logged.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("log") permission.
     *
     * @see WrapperPermission
     */
    public static void log( int logLevel, String message )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "log" ) );
        }
        
        // Make sure that the logLevel is valid to avoid problems with the
        //  command sent to the server.
        
        if ( ( logLevel < WRAPPER_LOG_LEVEL_DEBUG ) || ( logLevel > WRAPPER_LOG_LEVEL_ADVICE ) )
        {
            throw new IllegalArgumentException( "The specified logLevel is not valid." );
        }
        if ( message == null )
        {
            throw new IllegalArgumentException( "The message parameter can not be null." );
        }
        
        if ( m_lowLogLevel <= logLevel )
        {
            sendCommand( (byte)( WRAPPER_MSG_LOG + logLevel ), message );
        }
    }
    
    /**
     * Returns an array of all registered services.  This method is only
     *  supported on Windows platforms which support services.  Calling this
     *  method on other platforms will result in null being returned.
     *
     * @return An array of services.
     *
     * @throws SecurityException If a SecurityManager has not been set in the
     *                           JVM or if the calling code has not been
     *                           granted the WrapperPermission "listServices"
     *                           permission.  A SecurityManager is required
     *                           for this operation because this method makes
     *                           it possible to learn a great deal about the
     *                           state of the system.
     *
     * @see WrapperPermission
     */
    public static WrapperWin32Service[] listServices()
        throws SecurityException
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm == null )
        {
            throw new SecurityException( "A SecurityManager has not yet been set." );
        }
        else
        {
            sm.checkPermission( new WrapperPermission( "listServices" ) );
        }
        
        if ( m_libraryOK )
        {
            return nativeListServices();
        }
        else
        {
            return null;
        }
    }
    
    /**
     * Sends a service control code to the specified service.  The state of the
     *  service should be tested on return.  If the service was not currently
     *  running then the control code will not be sent.
     * <p>
     * The control code sent can be one of the system control codes:
     *  WrapperManager.SERVICE_CONTROL_CODE_START,
     *  WrapperManager.SERVICE_CONTROL_CODE_STOP,
     *  WrapperManager.SERVICE_CONTROL_CODE_PAUSE,
     *  WrapperManager.SERVICE_CONTROL_CODE_CONTINUE, or
     *  WrapperManager.SERVICE_CONTROL_CODE_INTERROGATE.  In addition, user
     *  defined codes in the range 128-255 can also be sent.
     *
     * @param serviceName Name of the Windows service which will receive the
     *                    control code.
     * @param controlCode The actual control code to be sent.  User defined
     *                    control codes should be in the range 128-255.
     *
     * @return A WrapperWin32Service containing the last known status of the
     *         service after sending the control code.  This will be null if
     *         the currently platform is not a version of Windows which
     *         supports services.
     *
     * @throws WrapperServiceException If there are any problems accessing the
     *                                 specified service.
     * @throws SecurityException If a SecurityManager has not been set in the
     *                           JVM or if the calling code has not been
     *                           granted the WrapperServicePermission
     *                           permission for the specified service and
     *                           control code.  A SecurityManager is required
     *                           for this operation because this method makes
     *                           it possible to control any service on the
     *                           system, which is of course rather dangerous.
     *
     * @see WrapperServicePermission
     */
    public static WrapperWin32Service sendServiceControlCode( String serviceName, int controlCode )
        throws WrapperServiceException, SecurityException
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm == null )
        {
            throw new SecurityException( "A SecurityManager has not yet been set." );
        }
        else
        {
            String action;
            switch( controlCode )
            {
            case SERVICE_CONTROL_CODE_START:
                action = WrapperServicePermission.ACTION_START;
                break;
                
            case SERVICE_CONTROL_CODE_STOP:
                action = WrapperServicePermission.ACTION_STOP;
                break;
                
            case SERVICE_CONTROL_CODE_PAUSE:
                action = WrapperServicePermission.ACTION_PAUSE;
                break;
                
            case SERVICE_CONTROL_CODE_CONTINUE:
                action = WrapperServicePermission.ACTION_CONTINUE;
                break;
                
            case SERVICE_CONTROL_CODE_INTERROGATE:
                action = WrapperServicePermission.ACTION_INTERROGATE;
                break;
                
            default:
                action = WrapperServicePermission.ACTION_USER_CODE;
                break;
            }
            
            sm.checkPermission( new WrapperServicePermission( serviceName, action ) );
        }
        
        WrapperWin32Service service = null;
        if ( m_libraryOK )
        {
            service = nativeSendServiceControlCode( serviceName.getBytes(), controlCode );
        }
        return service;
    }
    
    /**
     * Adds a WrapperEventListener which will receive WrapperEvents.  The
     *  specific events can be controlled using the mask parameter.  This API
     *  was chosen to allow for additional events in the future.
     *
     * To avoid future compatibility problems, WrapperEventListeners should
     *  always test the class of an event before making use of it.  This will
     *  avoid problems caused by new event classes added in future versions
     *  of the Wrapper.
     *
     * This method should only be called once for a given WrapperEventListener.
     *  Build up a single mask to receive events of multiple types.
     *
     * @param listener WrapperEventListener to be start receiving events.
     * @param mask A mask specifying the event types that the listener is
     *             interrested in receiving.  See the WrapperEventListener
     *             class for a full list of flags.  A mask is created by
     *             combining multiple flags using the binary '|' OR operator.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the appropriate
     *                           WrapperEventPermission(...) permission.
     *
     * @see WrapperEventPermission
     */
    public static void addWrapperEventListener( WrapperEventListener listener, long mask )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            StringBuffer sb = new StringBuffer();
            boolean first = true;
            if ( ( mask & WrapperEventListener.EVENT_FLAG_SERVICE ) != 0 )
            {
                first = false;
                sb.append( WrapperEventPermission.EVENT_TYPE_SERVICE );
            }
            if ( ( mask & WrapperEventListener.EVENT_FLAG_CONTROL ) != 0 )
            {
                if ( first )
                {
                    first = false;
                }
                else
                {
                    sb.append( "," );
                }
                sb.append( WrapperEventPermission.EVENT_TYPE_CONTROL );
            }
            if ( ( mask & WrapperEventListener.EVENT_FLAG_CORE ) != 0 )
            {
                if ( first )
                {
                    first = false;
                }
                else
                {
                    sb.append( "," );
                }
                sb.append( WrapperEventPermission.EVENT_TYPE_CORE );
            }
            sm.checkPermission( new WrapperEventPermission( sb.toString() ) );
        }
        
        synchronized( WrapperManager.class )
        {
            WrapperEventListenerMask listenerMask = new WrapperEventListenerMask();
            listenerMask.m_listener = listener;
            listenerMask.m_mask = mask;
            
            m_wrapperEventListenerMaskList.add( listenerMask );
            m_wrapperEventListenerMasks = null;
        }
        
        updateWrapperEventListenerFlags();
    }
    
    /**
     * Removes a WrapperEventListener so it will not longer receive WrapperEvents.
     *
     * @param listener WrapperEventListener to be stop receiving events.
     *
     * @throws SecurityException If a SecurityManager is present and the
     *                           calling thread does not have the
     *                           WrapperPermission("removeWrapperEventListener")
     *                           permission.
     *
     * @see WrapperPermission
     */
    public static void removeWrapperEventListener( WrapperEventListener listener )
    {
        SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            sm.checkPermission( new WrapperPermission( "removeWrapperEventListener" ) );
        }
        
        synchronized( WrapperManager.class )
        {
            // Look for the first instance of a given listener in the list.
            for ( Iterator iter = m_wrapperEventListenerMaskList.iterator(); iter.hasNext(); )
            {
                WrapperEventListenerMask listenerMask = (WrapperEventListenerMask)iter.next();
                if ( listenerMask.m_listener == listener )
                {
                    iter.remove();
                    m_wrapperEventListenerMasks = null;
                    break;
                }
            }
        }
        
        updateWrapperEventListenerFlags();
    }
    
    /*---------------------------------------------------------------
     * Constructors
     *-------------------------------------------------------------*/
    /** 
     * This class can not be instantiated.
     */
    private WrapperManager()
    {
    }
    
    /*---------------------------------------------------------------
     * Private methods
     *-------------------------------------------------------------*/
    /**
     * Checks for the existence of a SecurityManager and then makes sure that
     *  the Wrapper jar has been granted AllPermissions.  If not then a warning
     *  will be displayed as this will most likely result in the Wrapper
     *  failing to function correctly.
     *
     * This method is called at various points in the startup as it is possible
     *  and in fact likely that any SecurityManager will be set by user code
     *  during or shortly after initialization.  Once a SecurityManager has
     *  been located and tested then this method will become a noop.
     */
    private static void checkSecurityManager()
    {
        if ( m_securityManagerChecked )
        {
            return;
        }
        
        SecurityManager securityManager = System.getSecurityManager();
        if ( securityManager != null )
        {
            if ( m_debug )
            {
                m_outDebug.println(
                    "Detected a SecurityManager: " + securityManager.getClass().getName() );
            }
            
            try
            {
                securityManager.checkPermission( new java.security.AllPermission() );
            }
            catch ( SecurityException e )
            {
                m_outDebug.println();
                m_outDebug.println(
                    "WARNING - Detected that a SecurityManager has been installed but the " );
                m_outDebug.println(
                    "          wrapper.jar has not been granted the java.security.AllPermission" );
                m_outDebug.println(
                    "          permission.  This will most likely result in SecurityExceptions" );
                m_outDebug.println(
                    "          being thrown by the Wrapper." );
                m_outDebug.println();
            }
            
            // Always set the flag.
            m_securityManagerChecked = true;
        }
    }
    
    /**
     * Returns an array of WrapperEventListenerMask instances which can
     *  be safely used outside of synchronization.
     *
     * @return An array of WrapperEventListenerMask instances.
     */
    private static WrapperEventListenerMask[] getWrapperEventListenerMasks()
    {
        WrapperEventListenerMask[] listenerMasks = m_wrapperEventListenerMasks;
        if ( listenerMasks == null )
        {
            synchronized( WrapperManager.class )
            {
                if ( listenerMasks == null )
                {
                    listenerMasks =
                        new WrapperEventListenerMask[m_wrapperEventListenerMaskList.size()];
                    m_wrapperEventListenerMaskList.toArray( listenerMasks );
                    m_wrapperEventListenerMasks = listenerMasks;
                }
            }
        }
        
        return listenerMasks;
    }
    
    /**
     * Updates the internal flags based on the WrapperEventListeners currently
     *  registered.
     */
    private static void updateWrapperEventListenerFlags()
    {
        boolean core = false;
        
        WrapperEventListenerMask[] listenerMasks = getWrapperEventListenerMasks();
        for ( int i = 0; i < listenerMasks.length; i++ )
        {
            long mask = listenerMasks[i].m_mask;
            
            // See whether particular event types are required.
            core = core | ( ( mask & WrapperEventListener.EVENT_FLAG_CORE ) != 0 );
        }
        
        m_produceCoreEvents = core;
    }
    
    /**
     * Notifies registered listeners that an event has been fired.
     *
     * @param event Event to notify the listeners of.
     */
    private static void fireWrapperEvent( WrapperEvent event )
    {
        long eventMask = event.getFlags();
        
        WrapperEventListenerMask[] listenerMasks = getWrapperEventListenerMasks();
        for ( int i = 0; i < listenerMasks.length; i++ )
        {
            long listenerMask = listenerMasks[i].m_mask;
            
            // See if the event should be passed to this listner.
            if ( ( listenerMask & eventMask ) != 0 )
            {
                // The listener wants the event.
                WrapperEventListener listener = listenerMasks[i].m_listener;
                try
                {
                    listener.fired( event );
                }
                catch ( Throwable t )
                {
                    m_outError.println( "Encountered an uncaught exception while notifying "
                        + "WrapperEventListener of an event:" );
                    t.printStackTrace( m_outError );
                }
            }
        }
    }
    
    /**
     * Executed code common to the stop and stopImmediate methods.
     */
    private static void stopCommon( int exitCode, int delay )
    {
        boolean stopping;
        synchronized( WrapperManager.class )
        {
            stopping = m_stopping;
            if ( !stopping )
            {
                m_stopping = true;
            }
        }
        
        if ( !stopping )
        {
            // I used to check to make sure the commRunner was started, but that could fail
            //  for a number of reasons.  If it is down then leave it alone.
            //if ( !m_commRunnerStarted )
            //{
            //    startRunner();
            //}
            
            // Always send the stop command
            sendCommand( WRAPPER_MSG_STOP, Integer.toString( exitCode ) );
            
            // Give the Wrapper a chance to register the stop command before stopping.
            // This avoids any errors thrown by the Wrapper because the JVM died before
            //  it was expected to.
            try
            {
                Thread.sleep( delay );
            }
            catch ( InterruptedException e )
            {
            }
        }
    }
    
    /**
     * Dispose of all resources used by the WrapperManager.  Closes the server
     *  socket which is used to listen for events from the 
     */
    private static void dispose()
    {
        synchronized( WrapperManager.class )
        {
            m_disposed = true;
            
            // Close the open socket if it exists.
            closeSocket();
            
            // Give the Connection Thread a chance to stop itself.
            try
            {
                Thread.sleep( 500 );
            }
            catch ( InterruptedException e )
            {
            }
        }
    }
    
    /**
     * Informs the listener that it should start.
     */
    private static void startInner()
    {
        // Set the thread priority back to normal so that any spawned threads
        //  will use the normal priority
        int oldPriority = Thread.currentThread().getPriority();
        Thread.currentThread().setPriority( Thread.NORM_PRIORITY );
        
        // This method can be called from the connection thread which must be a
        //  daemon thread by design.  We need to call the WrapperListener.start method
        //  from a non-daemon thread.  This means that if the current thread is a
        //  daemon we need to launch a new thread while we wait for the start method
        //  to return.
        if ( m_listener == null )
        {
            if ( m_debug )
            {
                m_outDebug.println( "No WrapperListener has been set.  Nothing to start." );
            }
        }
        else
        {
            if ( m_debug )
            {
                m_outDebug.println( "calling WrapperListener.start()" );
            }
            
            // These arrays aren't pretty, but we need final variables for the inline
            //  class and this makes it possible to get the values back.
            final Integer[] resultF = new Integer[1];
            final Throwable[] tF = new Throwable[1];
            
            if ( Thread.currentThread().isDaemon() )
            {
                // Start in a dedicated thread.
                Thread startRunner = new Thread( "WrapperListener_start_runner" )
                {
                    public void run()
                    {
                        if ( m_debug )
                        {
                           m_outDebug.println( "WrapperListener.start runner thread started." );
                        }
                        
                        try
                        {
                            // This is user code, so don't trust it.
                            try
                            {
                                resultF[0] = m_listener.start( m_args );
                            }
                            catch ( Throwable t )
                            {
                                tF[0] = t;
                            }
                        }
                        finally
                        {
                            if ( m_debug )
                            {
                               m_outDebug.println( "WrapperListener.start runner thread stopped." );
                            }
                        }
                    }
                };
                startRunner.setDaemon( false );
                startRunner.start();
                
                // Wait for the start runner to complete.
                if ( m_debug )
                {
                   m_outDebug.println(
                        "Waiting for WrapperListener.start runner thread to complete." );
                }
                while ( ( startRunner != null ) && ( startRunner.isAlive() ) )
                {
                    try
                    {
                        startRunner.join();
                        startRunner = null;
                    }
                    catch ( InterruptedException e )
                    {
                        // Ignore and keep waiting.
                    }
                }
            }
            else
            {
                // Start in line.
                // This is user code, so don't trust it.
                try
                {
                    resultF[0] = m_listener.start( m_args );
                }
                catch ( Throwable t )
                {
                    tF[0] = t;
                }
            }
            
            // Now that we are back in the main thread, handle the results.
            if ( tF[0] != null )
            {
                m_outError.println( "Error in WrapperListener.start callback.  " + tF[0] );
                tF[0].printStackTrace( m_outError );
                // Kill the JVM, but don't tell the wrapper that we want to stop.
                //  This may be a problem with this instantiation only.
                privilegedStopInner( 1 );
                // Won't make it here.
                return;
            }
            
            if ( m_debug )
            {
                m_outDebug.println( "returned from WrapperListener.start()" );
            }
            if ( resultF[0] != null )
            {
                int exitCode = resultF[0].intValue();
                if ( m_debug )
                {
                    m_outDebug.println(
                        "WrapperListener.start() returned an exit code of " + exitCode + "." );
                }
                
                // Signal the native code.
                stop( exitCode );
                // Won't make it here.
                return;
            }
        }
        m_startedTicks = getTicks();
        
        // Let the startup thread die since the application has been started.
        m_startupRunner = null;
        
        // Check the SecurityManager here as it is possible that it was set in the
        //  listener's start method.
        checkSecurityManager();
        
        // Crank the priority back up.
        Thread.currentThread().setPriority( oldPriority );
        
        // Signal that the application has started.
        signalStarted();
    }
    
    private static void shutdownJVM( int exitCode )
    {
        if ( m_debug )
        {
            m_outDebug.println(
                "shutdownJVM(" + exitCode + ") Thread:" + Thread.currentThread().getName() );
        }
        
        // Do not call System.exit if this is the ShutdownHook
        if ( Thread.currentThread() == m_hook )
        {
            // Signal that the application has stopped and the JVM is about to shutdown.
            signalStopped( exitCode );
            
            // Dispose the wrapper.  (If the hook runs, it will do this.)
            dispose();
            
            // This is the shutdown hook, so fall through because things are
            //  already shutting down.
            
            m_shutdownJVMComplete = true;
        }
        else
        {
            // Signal that the application has stopped and the JVM is about to shutdown.
            signalStopped( exitCode );
            
            // Dispose the wrapper.  (If the hook runs, it will do this.)
            dispose();
            
            if ( m_debug )
            {
                m_outDebug.println( "calling System.exit(" + exitCode + ")" );
            }
            m_shutdownJVMComplete = true;
            safeSystemExit( exitCode );
        }
    }
    
    /**
     * A user ran into a JVM bug where a call to System exit was causing an
     *  IllegalThreadStateException to be thrown.  Not sure how widespread
     *  this problem is.  But it is easy to avoid it causing serious problems
     *  for the wrapper.
     */
    private static void safeSystemExit( int exitCode )
    {
        try
        {
            System.exit( exitCode );
        }
        catch ( IllegalThreadStateException e )
        {
            m_outError.println( "Attempted System.exit(" + exitCode + ") call failed: " + e.toString() );
            m_outError.println( "   Trying Runtime.halt(" + exitCode + ")" );
            Runtime.getRuntime().halt( exitCode );
        }
    }
    
    /**
     * Informs the listener that the JVM will be shut down.
     *
     * This should only be called from within a PrivilegedAction or in a
     *  context that came from a PrivilegedAction.
     */
    private static void privilegedStopInner( int exitCode )
    {
        boolean block;
        synchronized( WrapperManager.class )
        {
            // Always set the stopping flag.
            m_stopping = true;
            
            // Only one thread can be allowed to continue.
            if ( m_stoppingThread == null )
            {
                m_stoppingThread = Thread.currentThread();
                block = false;
            }
            else
            {
                if ( Thread.currentThread() == m_stoppingThread )
                {
                    throw new IllegalStateException(
                        "WrapperManager.stop() can not be called recursively." );
                }
                
                block = true;
            }
        }
        
        if ( block )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Thread, " + Thread.currentThread().getName()
                    + ", waiting for the JVM to exit." );
                
                if ( Thread.currentThread() == m_hook )
                {
                    m_outDebug.println(
                        "System.exit appears to have been called from within the" );
                    m_outDebug.println(
                        "  WrapperListener.stop() method.  If possible the application" );
                    m_outDebug.println(
                        "  should be modified to avoid this behavior." );
                    m_outDebug.println(
                        "  To avoid a deadlock, this thread will only wait 5 seconds" );
                    m_outDebug.println(
                        "  for the application to shutdown.  This may result in the" );
                    m_outDebug.println(
                        "  application failing to shutdown completely before the JVM" );
                    m_outDebug.println(
                        "  exists.  Removing the offending System.exit call will" );
                    m_outDebug.println(
                        "  resolve this." );
                }
            }
            
            // This thread needs to be put into an infinite loop until the JVM exits.
            //  This thread can not be allowed to return to the caller, but another
            //  thread is already responsible for shutting down the JVM, so this
            //  one can do nothing but wait.
            int loops = 0;
            int wait = 50;
            while( true )
            {
                try
                {
                    Thread.sleep( wait );
                }
                catch ( InterruptedException e )
                {
                }
                
                // If this is the wrapper's shutdown hook then we only want to loop until
                //  the shutdownJVM method has completed.  We will only get into this state
                //  if user code calls System.exit from within the WrapperListener.stop
                //  method.  Failing to return here will cause the shutdown process to hang.
                // If the user code calls System.exit directly in the stop method then the
                //  m_shutdownJVMComplete flag will never be set.   Always time out after
                //  5 seconds so the JVM will not hang in such cases.
                if ( Thread.currentThread() == m_hook )
                {
                    if ( m_shutdownJVMComplete || ( loops > 5000 / wait ) )
                    {
                        if ( !m_shutdownJVMComplete )
                        {
                            if ( m_debug )
                            {
                                m_outDebug.println( "Thread, " + Thread.currentThread().getName()
                                    + ", continuing after 5 seconds." );
                            }
                        }
                        
                        // To keep the wrapper from showing a JVM exited unexpectedly message
                        //  on shutdown, tell the wrapper that we are ready to stop.
                        // If the WrapperListener.stop method is taking a long time, we will
                        //  also get here.  In that case, the Wrapper will still wait for
                        //  the configured exit timeout before killing the JVM process.
                        // In theory, the shutdown process of an application will only call
                        //  System.exit after the shutdown is complete so this should be Ok.
                        // Use the exit code from the thread which initiated the call rather
                        //  than this call as that one is the one we really want.
                        signalStopped( m_exitCode );
                        
                        return;
                    }
                }
                
                loops++;
            }
        }
        
        if ( m_debug )
        {
            m_outDebug.println( "Thread, " + Thread.currentThread().getName()
                + ", handling the shutdown process." );
        }
        m_exitCode = exitCode;
        
        // If appropriate, unregister the shutdown hook.  This must be done before the
        //  user stop code is called to avoid nested shutdowns.
        if ( Thread.currentThread() != m_hook )
        {
            // We do not want the ShutdownHook to execute, so unregister it before calling exit.
            //  It can't be unregistered if it has already fired however.  The only way that this
            //  could happen is if user code calls System.exit from within the listener stop
            //  method.
            if ( ( !m_hookTriggered ) && ( m_hook != null ) )
            {
                // Remove the shutdown hook using reflection.
                try
                {
                    m_removeShutdownHookMethod.invoke(
                        Runtime.getRuntime(), new Object[] { m_hook } );
                }
                catch ( IllegalAccessException e )
                {
                    m_outError.println( "Unable to unregister shutdown hook: " + e );
                }
                catch ( InvocationTargetException e )
                {
                    Throwable t = e.getTargetException();
                    if ( t == null )
                    {
                        t = e;
                    }
                    
                    m_outError.println( "Unable to unregister shutdown hook: " + t );
                }
            }
        }
        
        // Only stop the listener if the app has been started.
        int code = exitCode;
        if ( m_started )
        {
            // Set the thread priority back to normal so that any spawned threads
            //  will use the normal priority
            int oldPriority = Thread.currentThread().getPriority();
            Thread.currentThread().setPriority( Thread.NORM_PRIORITY );
            
            // This method can be called from the connection thread which must be a
            //  daemon thread by design.  We need to call the WrapperListener.stop method
            //  from a non-daemon thread.  This means that if the current thread is a
            //  daemon we need to launch a new thread while we wait for the stop method
            //  to return.
            if ( m_listener == null )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "No WrapperListener has been set.  Nothing to stop." );
                }
            }
            else
            {
                if ( m_debug )
                {
                    m_outDebug.println( "calling listener.stop()" );
                }
                
                if ( Thread.currentThread().isDaemon() )
                {
                    // This array isn't pretty, but we need final variables for the inline
                    //  class and this makes it possible to get the values back.
                    final Integer[] codeF = new Integer[] {new Integer(code)};
                    
                    // Start in a dedicated thread.
                    Thread stopRunner = new Thread( "WrapperListener_stop_runner" )
                    {
                        public void run()
                        {
                            if ( m_debug )
                            {
                               m_outDebug.println( "WrapperListener.stop runner thread started." );
                            }
                            
                            try
                            {
                                // This is user code, so don't trust it.
                                try
                                {
                                    codeF[0] = new Integer( m_listener.stop( codeF[0].intValue() ) );
                                }
                                catch ( Throwable t )
                                {
                                    m_outError.println(
                                        "Error in WrapperListener.stop callback." );
                                    t.printStackTrace( m_outError );
                                }
                            }
                            finally
                            {
                                if ( m_debug )
                                {
                                   m_outDebug.println(
                                        "WrapperListener.stop runner thread stopped." );
                                }
                            }
                        }
                    };
                    stopRunner.setDaemon( false );
                    stopRunner.start();
                    
                    // Wait for the start runner to complete.
                    if ( m_debug )
                    {
                       m_outDebug.println(
                            "Waiting for WrapperListener.stop runner thread to complete." );
                    }
                    while ( ( stopRunner != null ) && ( stopRunner.isAlive() ) )
                    {
                        try
                        {
                            stopRunner.join();
                            stopRunner = null;
                        }
                        catch ( InterruptedException e )
                        {
                            // Ignore and keep waiting.
                        }
                    }
                    
                    // Get the exit code back from the array.
                    code = codeF[0].intValue();
                }
                else
                {
                    // This is user code, so don't trust it.
                    try
                    {
                        code = m_listener.stop( code );
                    }
                    catch ( Throwable t )
                    {
                        m_outError.println( "Error in WrapperListener.stop callback." );
                        t.printStackTrace( m_outError );
                    }
                }
                if ( m_debug )
                {
                    m_outDebug.println( "returned from listener.stop() -> " + code );
                }
            }
            
            // Crank the priority back up.
            Thread.currentThread().setPriority( oldPriority );
        }

        shutdownJVM( code );
    }
    
    private static void signalStarted()
    {
        sendCommand( WRAPPER_MSG_STARTED, "" );
        m_started = true;
    }
    
    /**
     * Called by the native code when a control event is trapped by native code.
     * Can have the values: WRAPPER_CTRL_C_EVENT, WRAPPER_CTRL_CLOSE_EVENT, 
     *    WRAPPER_CTRL_LOGOFF_EVENT, WRAPPER_CTRL_SHUTDOWN_EVENT,
     *    WRAPPER_CTRL_TERM_EVENT, or WRAPPER_CTRL_HUP_EVENT.
     */
    private static void controlEvent( int event )
    {
        String eventName;
        boolean ignore;
        switch( event )
        {
        case WRAPPER_CTRL_C_EVENT:
            eventName = "WRAPPER_CTRL_C_EVENT";
            ignore = m_ignoreSignals;
            break;
        case WRAPPER_CTRL_CLOSE_EVENT:
            eventName = "WRAPPER_CTRL_CLOSE_EVENT";
            ignore = m_ignoreSignals;
            break;
        case WRAPPER_CTRL_LOGOFF_EVENT:
            eventName = "WRAPPER_CTRL_LOGOFF_EVENT";
            ignore = false;
            break;
        case WRAPPER_CTRL_SHUTDOWN_EVENT:
            eventName = "WRAPPER_CTRL_SHUTDOWN_EVENT";
            ignore = false;
            break;
        case WRAPPER_CTRL_TERM_EVENT:
            eventName = "WRAPPER_CTRL_TERM_EVENT";
            ignore = m_ignoreSignals;
            break;
        case WRAPPER_CTRL_HUP_EVENT:
            eventName = "WRAPPER_CTRL_HUP_EVENT";
            ignore = m_ignoreSignals;
            break;
        case WRAPPER_CTRL_USR1_EVENT:
            eventName = "WRAPPER_CTRL_USR1_EVENT";
            ignore = m_ignoreSignals;
            break;
        case WRAPPER_CTRL_USR2_EVENT:
            eventName = "WRAPPER_CTRL_USR2_EVENT";
            ignore = m_ignoreSignals;
            break;
        default:
            eventName = "Unexpected event: " + event;
            ignore = false;
            break;
        }
        
        WrapperControlEvent controlEvent = new WrapperControlEvent( event, eventName );
        if ( ignore )
        {
            // Preconsume the event if it is set to be ignored, but go ahead and fire it so
            //  user can can still have the oportunity to recognize it.
            controlEvent.consume();
        }
        fireWrapperEvent( controlEvent );
        
        if ( !controlEvent.isConsumed() )
        {
            if ( ignore )
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Ignoring control event(" + eventName + ")" );
                }
            }
            else
            {
                if ( m_debug )
                {
                    m_outDebug.println( "Processing control event(" + eventName + ")" );
                }
                
                // This is user code, so don't trust it.
                if ( m_listener != null )
                {
                    try
                    {
                        m_listener.controlEvent( event );
                    }
                    catch ( Throwable t )
                    {
                        m_outError.println( "Error in WrapperListener.controlEvent callback." );
                        t.printStackTrace( m_outError );
                    }
                }
                else
                {
                    // A listener was never registered.  Always respond by exiting.
                    //  This can happen if the user does not initialize things correctly.
                    stop( 0 );
                }
            }
        }
    }
    
    /**
     * Parses a long tab separated string of properties into an internal
     *  properties object.  Actual tabs are escaped by real tabs.
     */
    private static char PROPERTY_SEPARATOR = '\t';
    private static void readProperties( String rawProps )
    {
        WrapperProperties properties = new WrapperProperties();
        
        int len = rawProps.length();
        int first = 0;
        while ( first < len )
        {
            StringBuffer sb = new StringBuffer();
            boolean foundEnd = false;
            do
            {
                int pos = rawProps.indexOf( PROPERTY_SEPARATOR, first );
                if ( pos >= 0 )
                {
                    if ( pos > 0 )
                    {
                        sb.append( rawProps.substring( first, pos ) );
                    }
                    if ( pos < len - 1 )
                    {
                        if ( rawProps.charAt( pos + 1 ) == PROPERTY_SEPARATOR )
                        {
                            // Two separators in a row, it was escaped.
                            sb.append( PROPERTY_SEPARATOR );
                            first = pos + 2;
                        }
                        else
                        {
                            foundEnd = true;
                            first = pos + 1;
                        }
                    }
                    else
                    {
                        foundEnd = true;
                        first = pos + 1;
                    }
                }
                else
                {
                    // No more separators.  The rest is the last property.
                    sb.append( rawProps.substring( first ) );
                    foundEnd = true;
                    first = len;
                }
            }
            while ( !foundEnd );
            
            String property = sb.toString();
            
            // Parse the property.
            int pos = property.indexOf( '=' );
            if ( pos > 0 )
            {
                String key = property.substring( 0, pos );
                String value;
                if ( pos < property.length() - 1 )
                {
                    value = property.substring( pos + 1 );
                }
                else
                {
                    value = "";
                }
                
                properties.setProperty( key, value );
                
                // Process special properties
                if ( key.equals( "wrapper.ignore_user_logoffs" ) )
                {
                    m_ignoreUserLogoffs = value.equalsIgnoreCase( "true" );
                }
            }
        }
        
        // Lock the properties object and store it.
        properties.lock();
        
        m_properties = properties;
    }

    private static synchronized Socket openSocket()
    {
        if ( m_debug )
        {
            m_outDebug.println( "Open socket to wrapper..." + Thread.currentThread().getName() );
        }

        InetAddress iNetAddress;
        try
        {
            iNetAddress = InetAddress.getByName( "127.0.0.1" );
        }
        catch ( UnknownHostException e )
        {
            // This is pretty fatal.
            m_outError.println( "Unable to resolve localhost name: " + e );
            m_outError.println( "Exiting JVM..." );
            stop( 1 );
            return null; //please the compiler
        }
        
        // If the user has specified a specific port to use then we want to try that first.
        boolean connected = false;
        int tryPort;
        boolean fixedPort;
        if ( m_jvmPort > 0 )
        {
            tryPort = m_jvmPort;
            fixedPort = true;
        }
        else
        {
            tryPort = m_jvmPortMin;
            fixedPort = false;
        }
        
        // Loop until we find a port we can connect using.
        SocketException causeException = null;
        do
        {
            try
            {
                m_socket = new Socket( iNetAddress, m_port, iNetAddress, tryPort );
                if ( m_debug )
                {
                    m_outDebug.println( "Opened Socket from " + tryPort + " to " + m_port );
                }
                connected = true;
                break;
            }
            catch ( SocketException e )
            {
                String eMessage = e.getMessage();
                
                if ( e instanceof ConnectException )
                {
                    m_outError.println( "Failed to connect to the Wrapper at port " + m_port
                        + ".  Cause: " + e );
                    // This is fatal because there is nobody listening.
                    m_outError.println( "Exiting JVM..." );
                    stopImmediate( 1 );
                }
                else if ( ( e instanceof BindException ) ||
                    ( ( eMessage != null ) &&
                    ( ( eMessage.indexOf( "errno: 48" ) >= 0 ) || ( eMessage.indexOf( "Address already in use" ) >= 0 ) ) ) )
                {
                    // Most Java implementations throw a BindException when the port is in use,
                    //  but FreeBSD throws a SocketException with a specific message.
                    
                    // This happens if the local port is already in use.  In this case, we want
                    //  to loop and try again.
                    if ( m_debug )
                    {
                        m_outDebug.println( "Failed attempt to bind using local port " + tryPort );
                    }
                    
                    if ( fixedPort )
                    {
                        // The last port checked was the fixed port, switch to the dynamic range.
                        tryPort = m_jvmPortMin;
                        fixedPort = false;
                    }
                    else
                    {
                        tryPort++;
                    }
                    
                    // Keep this exception around in case we need to log it.
                    if ( causeException == null )
                    {
                        causeException = e;
                    }
                }
                else
                {
                    // Unexpected exception.
                    m_outError.println( "Unexpected exception opening backend socket: " + e );
                    m_socket = null;
                    return null;
                }
            }
            catch ( IOException e )
            {
                m_outError.println( "Unable to open backend socket: " + e );
                m_socket = null;
                return null;
            }
        }
        while ( tryPort <= m_jvmPortMax );
        
        if ( connected )
        {
            if ( ( m_jvmPort > 0 ) && ( m_jvmPort != tryPort ) )
            {
                m_outInfo.println(
                    "Port " + m_jvmPort + " already in use, using port " + tryPort + " instead." );
            }
        }
        else
        {
            if ( m_jvmPortMax > m_jvmPortMin )
            {
                m_outError.println( "Failed to connect to the Wrapper at port " + m_port
                    + " by binding to any ports in the range " + m_jvmPortMin + " to "
                    + m_jvmPortMax + ".  Cause: " + causeException );
            }
            else
            {
                m_outError.println( "Failed to connect to the Wrapper at port " + m_port
                    + " by binding to port " + m_jvmPortMin + ".  Cause: "
                    + causeException );
            }
            // This is fatal because there is nobody listening.
            m_outError.println( "Exiting JVM..." );
            stopImmediate( 1 );
        }
        
        // Now that we have a connected socket, continue on to configure it.
        try
        {
            // Turn on the TCP_NODELAY flag.  This is very important for speed!!
            m_socket.setTcpNoDelay( true );
            
            // Set the SO_TIMEOUT for the socket (max block time)
            if ( m_soTimeout > 0 )
            {
                m_socket.setSoTimeout( m_soTimeout );
            }
        }
        catch ( IOException e )
        {
            m_outError.println( e );
        }
        
        // Send the key back to the wrapper so that the wrapper can feel safe
        //  that it is talking to the correct JVM
        sendCommand( WRAPPER_MSG_KEY, m_key );
            
        return m_socket;
    }
    
    private static synchronized void closeSocket()
    {
        if ( m_socket != null )
        {
            if ( m_debug )
            {
                m_outDebug.println( "Closing socket." );
            }
            
            try
            {
                m_socket.close();
            }
            catch ( IOException e )
            {
            }
            finally
            {
                m_socket = null;
            }
        }
    }
    
    private static String getPacketCodeName(byte code)
    {
        String name;
    
        switch (code)
        {
        case WRAPPER_MSG_START:
            name ="START";
            break;
    
        case WRAPPER_MSG_STOP:
            name ="STOP";
            break;
    
        case WRAPPER_MSG_RESTART:
            name ="RESTART";
            break;
    
        case WRAPPER_MSG_PING:
            name ="PING";
            break;
    
        case WRAPPER_MSG_STOP_PENDING:
            name ="STOP_PENDING";
            break;
    
        case WRAPPER_MSG_START_PENDING:
            name ="START_PENDING";
            break;
    
        case WRAPPER_MSG_STARTED:
            name ="STARTED";
            break;
    
        case WRAPPER_MSG_STOPPED:
            name ="STOPPED";
            break;
    
        case WRAPPER_MSG_KEY:
            name ="KEY";
            break;
    
        case WRAPPER_MSG_BADKEY:
            name ="BADKEY";
            break;
    
        case WRAPPER_MSG_LOW_LOG_LEVEL:
            name ="LOW_LOG_LEVEL";
            break;
    
        case WRAPPER_MSG_PING_TIMEOUT:
            name ="PING_TIMEOUT";
            break;
    
        case WRAPPER_MSG_SERVICE_CONTROL_CODE:
            name ="SERVICE_CONTROL_CODE";
            break;
    
        case WRAPPER_MSG_PROPERTIES:
            name ="PROPERTIES";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_DEBUG:
            name ="LOG(DEBUG)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_INFO:
            name ="LOG(INFO)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_STATUS:
            name ="LOG(STATUS)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_WARN:
            name ="LOG(WARN)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_ERROR:
            name ="LOG(ERROR)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_FATAL:
            name ="LOG(FATAL)";
            break;
    
        case WRAPPER_MSG_LOG + WRAPPER_LOG_LEVEL_ADVICE:
            name ="LOG(ADVICE)";
            break;
    
        default:
            name = "UNKNOWN(" + code + ")";
            break;
        }
        return name;
    }
    
    private static synchronized void sendCommand( byte code, String message )
    {
        Socket socket = m_socket;
        if ( m_debug )
        {
            if ( ( code == WRAPPER_MSG_PING ) && ( message.equals( "silent" ) ) )
            {
                //m_outDebug.println( "Send silent ping packet." );
            }
            else if ( socket == null )
            {
                m_outDebug.println( "Backend socket not connected, not sending packet "
                    + getPacketCodeName( code ) + " : " + message );
            }
            else
            {
                m_outDebug.println(
                    "Send a packet " + getPacketCodeName( code ) + " : " + message );
            }
        }
        if ( m_appearHung )
        {
            // The WrapperManager is attempting to make the JVM appear hung, so do nothing
        }
        else
        {
            // Make a copy of the reference to make this more thread safe.
            if ( ( socket == null ) && isControlledByNativeWrapper() && ( !m_stopping ) )
            {
                // The socket is not currently open, try opening it.
                socket = openSocket();
            }
            
            if ( ( code == WRAPPER_MSG_START_PENDING ) || ( code == WRAPPER_MSG_STARTED ) )
            {
                // Set the last ping time so that the startup process does not time out
                //  thinking that the JVM has not received a Ping for too long.
                m_lastPingTicks = getTicks();
            }
            
            // If the socket is open, then send the command, otherwise just throw it away.
            if ( socket != null )
            {
                try
                {
                    // It is possible that a logged message is quite large.  Expand the size
                    // of the command buffer if necessary so that it can be included.  This
                    //  means that the command buffer will be the size of the largest message.
                    byte[] messageBytes = message.getBytes();
                    if ( m_commandBuffer.length < messageBytes.length + 2 )
                    {
                        m_commandBuffer = new byte[messageBytes.length + 2];
                    }
                    
                    // Writing the bytes one by one was sometimes causing the first byte to be lost.
                    // Try to work around this problem by creating a buffer and sending the whole lot
                    // at once.
                    m_commandBuffer[0] = code;
                    System.arraycopy( messageBytes, 0, m_commandBuffer, 1, messageBytes.length );
                    int len = messageBytes.length + 2;
                    m_commandBuffer[len - 1] = 0;
                    
                    OutputStream os = socket.getOutputStream();
                    os.write( m_commandBuffer, 0, len );
                    os.flush();
                }
                catch ( IOException e )
                {
                    m_outError.println( e );
                    e.printStackTrace( m_outError );
                    closeSocket();
                }
            }
        }
    }
    
    /**
     * Loop reading packets from the native side of the Wrapper until the 
     *  connection is closed or the WrapperManager class is disposed.
     *  Each packet consists of a packet code followed by a null terminated
     *  string up to 256 characters in length.  If the entire packet has not
     *  yet been received, then it must not be read until the complete packet
     *  has arived.
     */
    private static byte[] m_socketReadBuffer = new byte[256];
    private static void handleSocket()
    {
        WrapperPingEvent pingEvent = new WrapperPingEvent();
        try
        {
            if ( m_debug )
            {
                m_outDebug.println( "handleSocket(" + m_socket + ")" );
            }
            DataInputStream is = new DataInputStream( m_socket.getInputStream() );
            while ( !m_disposed )
            {
                try
                {
                    // A Packet code must exist.
                    byte code = is.readByte();
                    
                    // Always read from the buffer until a null '\0' is encountered.
                    byte b;
                    int i = 0;
                    do
                    {
                        b = is.readByte();
                        if ( b != 0 )
                        {
                            if ( i >= m_socketReadBuffer.length )
                            {
                                byte[] tmp = m_socketReadBuffer;
                                m_socketReadBuffer = new byte[tmp.length + 256];
                                System.arraycopy( tmp, 0, m_socketReadBuffer, 0, tmp.length );
                            }
                            m_socketReadBuffer[i] = b;
                            i++;
                        }
                    }
                    while ( b != 0 );
                    
                    String msg = new String( m_socketReadBuffer, 0, i );
                    
                    if ( m_appearHung )
                    {
                        // The WrapperManager is attempting to make the JVM appear hung,
                        //   so ignore all incoming requests
                    }
                    else
                    {
                        if ( m_debug )
                        {
                            String logMsg;
                            if ( code == WRAPPER_MSG_PROPERTIES )
                            {
                                // The property values are very large and distracting in the log.
                                //  Plus if any triggers are defined, then logging them will fire
                                //  the trigger.
                                logMsg = "(Property Values)";
                            }
                            else
                            {
                                logMsg = msg;
                            }
                            
                            // Don't log silent pings.
                            if ( ( code == WRAPPER_MSG_PING ) && ( msg.equals( "silent" ) ) )
                            {
                                //m_outDebug.println( "Received silent ping packet." );
                            }
                            else
                            {
                                m_outDebug.println( "Received a packet " + getPacketCodeName( code )
                                    + " : " + logMsg );
                            }
                        }
                        
                        // Ok, we got a packet.  Do something with it.
                        switch( code )
                        {
                        case WRAPPER_MSG_START:
                            startInner();
                            break;
                            
                        case WRAPPER_MSG_STOP:
                            // Don't do anything if we are already stopping
                            if ( !m_stopping )
                            {
                                privilegedStopInner( 0 );
                                // Should never get back here.
                            }
                            break;
                            
                        case WRAPPER_MSG_PING:
                            m_lastPingTicks = getTicks();
                            sendCommand( WRAPPER_MSG_PING, msg );
                            
                            if ( m_produceCoreEvents )
                            {
                                fireWrapperEvent( pingEvent );
                            }
                            
                            break;
                            
                        case WRAPPER_MSG_BADKEY:
                            // The key sent to the wrapper was incorrect.  We need to shutdown.
                            m_outError.println( "Authorization key rejected by Wrapper." );
                            m_outError.println( "Exiting JVM..." );
                            closeSocket();
                            privilegedStopInner( 1 );
                            break;
                            
                        case WRAPPER_MSG_LOW_LOG_LEVEL:
                            try
                            {
                                m_lowLogLevel = Integer.parseInt( msg );
                                m_debug = ( m_lowLogLevel <= WRAPPER_LOG_LEVEL_DEBUG );
                                if ( m_debug )
                                {
                                    m_outDebug.println(
                                        "LowLogLevel from Wrapper is " + m_lowLogLevel );
                                }
                            }
                            catch ( NumberFormatException e )
                            {
                                m_outError.println( "Encountered an Illegal LowLogLevel from the "
                                    + "Wrapper: " + msg );
                            }
                            break;
                            
                        case WRAPPER_MSG_PING_TIMEOUT:
                            try
                            {
                                m_pingTimeout = Integer.parseInt( msg ) * 1000;
                                if ( m_debug )
                                {
                                    m_outDebug.println(
                                        "PingTimeout from Wrapper is " + m_pingTimeout );
                                }
                            }
                            catch ( NumberFormatException e )
                            {
                                m_outError.println( "Encountered an Illegal PingTimeout from the "
                                    + "Wrapper: " + msg );
                            }
                            
                            // Make sure that the so timeout is longer than the ping timeout
                            if ( m_pingTimeout <= 0 )
                            {
                                m_socket.setSoTimeout( 0 );
                            }
                            else if ( m_soTimeout < m_pingTimeout )
                            {
                                m_socket.setSoTimeout( m_pingTimeout );
                            }
                            
                            break;
                            
                        case WRAPPER_MSG_SERVICE_CONTROL_CODE:
                            try
                            {
                                int serviceControlCode = Integer.parseInt( msg );
                                if ( m_debug )
                                {
                                    m_outDebug.println( "ServiceControlCode from Wrapper with code "
                                        + serviceControlCode );
                                }
                                WrapperServiceControlEvent event =
                                    new WrapperServiceControlEvent( serviceControlCode );
                                fireWrapperEvent( event );
                            }
                            catch ( NumberFormatException e )
                            {
                                m_outError.println( "Encountered an Illegal ServiceControlCode from "
                                    + "the Wrapper: " + msg );
                            }
                            break;
                            
                        case WRAPPER_MSG_PROPERTIES:
                            readProperties( msg );
                            break;
                            
                        default:
                            // Ignore unknown messages
                            m_outInfo.println( "Wrapper code received an unknown packet type: "
                                + code );
                            break;
                        }
                    }
                }
                catch ( InterruptedIOException e )
                {
                    int nowTicks = getTicks();
                    
                    // Unless the JVM is shutting dowm we want to show warning messages and maybe exit.
                    if ( ( m_started ) && ( !m_stopping ) )
                    {
                        if ( m_debug )
                        {
                            m_outDebug.println( "Read Timed out. (Last Ping was "
                                + getTickAge( m_lastPingTicks, nowTicks ) + " milliseconds ago)" );
                        }
                        
                        if ( !m_appearHung )
                        {
                            long lastPingAge = getTickAge( m_lastPingTicks, nowTicks );
                            long eventRunnerAge = getTickAge( m_eventRunnerTicks, nowTicks );
                            
                            // We may have timed out because the system was extremely busy or
                            //  suspended.  Only restart due to a lack of ping events if the
                            //  event thread has been running.
                            if ( eventRunnerAge < 10000 )
                            {
                                // Only perform ping timeout checks if ping timeouts are enabled.
                                if ( m_pingTimeout > 0 )
                                {
                                    // How long has it been since we received the last ping
                                    //  from the Wrapper?
                                    if ( lastPingAge > m_pingTimeout + 90000 )
                                    {
                                        // It has been more than the ping timeout + 90 seconds,
                                        //  so just give up and kill the JVM
                                        m_outInfo.println( "JVM did not exit.  Give up." );
                                        safeSystemExit(1);
                                    }
                                    else if ( lastPingAge > m_pingTimeout )
                                    {
                                        // It has been more than the ping timeout since the
                                        //  JVM was last pinged.  Ask to be stopped (and restarted).
                                        m_outInfo.println( "The Wrapper code did not ping the "
                                            + "JVM for " + (lastPingAge / 1000) + " seconds.  "
                                            + "Quit and let the Wrapper resynch.");
                                        
                                        // Don't do anything if we are already stopping
                                        if ( !m_stopping )
                                        {
                                            // Always send the stop command
                                            sendCommand( WRAPPER_MSG_RESTART, "restart" );
                                            
                                            // Give the Wrapper a chance to register the stop
                                            //  command before stopping.
                                            // This avoids any errors thrown by the Wrapper because
                                            //  the JVM died before it was expected to.
                                            try
                                            {
                                                Thread.sleep( 1000 );
                                            }
                                            catch ( InterruptedException e2 )
                                            {
                                            }
                                            
                                            privilegedStopInner( 1 );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;

        }
        catch ( SocketException e )
        {
            if ( m_debug )
            {
                if ( m_socket == null )
                {
                    // This error happens if the socket is closed while reading:
                    // java.net.SocketException: Descriptor not a socket: JVM_recv in socket
                    //                           input stream read
                }
                else
                {
                    m_outDebug.println( "Closed socket: " + e );
                }
            }
            return;
        }
        catch ( IOException e )
        {
            // This means that the connection was closed.  Allow this to return.
            //m_outInfo.println( e );
            //e.printStackTrace( m_outInfo );
            return;
        }
    }
    
    private static void startRunner()
    {
        if ( isControlledByNativeWrapper() )
        {
            if ( m_commRunner == null )
            {
                // Create and launch a new thread to manage this connection
                m_commRunner = new Thread( m_instance, WRAPPER_CONNECTION_THREAD_NAME );
                m_commRunner.setDaemon( true );
                m_commRunner.start();
            }
            
            // Wait to give the runner a chance to connect.
            synchronized( WrapperManager.class )
            {
                while ( !m_commRunnerStarted )
                {
                    try
                    {
                        WrapperManager.class.wait( 100 );
                    }
                    catch ( InterruptedException e )
                    {
                    }
                }
            }
        }
        else
        {
            // Immediately mark the runner as started as it will never be used.
            synchronized( WrapperManager.class )
            {
                m_commRunnerStarted = true;
                WrapperManager.class.notifyAll();
            }
        }
    }
    
    /*---------------------------------------------------------------
     * Runnable Methods
     *-------------------------------------------------------------*/
    public void run()
    {
        // Make sure that no other threads call this method.
        if ( Thread.currentThread() != m_commRunner )
        {
            throw new IllegalStateException(
                "Only the comm runner thread is allowed to call this method." );
        }
        
        if ( m_debug )
        {
            m_outDebug.println( "Communications runner thread started." );
        }
        
        // This thread needs to have a very high priority so that it never
        //  gets put behind other threads.
        Thread.currentThread().setPriority( Thread.MAX_PRIORITY );
        
        // Initialize the last ping tick count.
        m_lastPingTicks = getTicks();
        
        boolean gotPortOnce = false;
        while ( !m_disposed )
        {
            try
            {
                try
                {
                    openSocket();
                    
                    // After the socket has been opened the first time, mark the thread as
                    //  started.  This must be done here to make sure that exits work correctly
                    //  when called on startup.
                    if ( !m_commRunnerStarted )
                    {
                        synchronized( WrapperManager.class )
                        {
                            m_commRunnerStarted = true;
                            WrapperManager.class.notifyAll();
                        }
                    }
                    
                    if ( m_socket != null )
                    {
                        handleSocket();
                    }
                    else
                    {
                        // Failed, so wait for just a moment
                        try
                        {
                            Thread.sleep( 100 );
                        }
                        catch ( InterruptedException e )
                        {
                        }
                    }
                }
                finally
                {
                    // Always close the socket here.
                    closeSocket();
                }
            }
            catch ( ThreadDeath td )
            {
                m_outError.println( m_warning.format( "SERVER_DAEMON_KILLED" ) );
            }
            catch ( Throwable t )
            {
                if ( !m_shuttingDown )
                {
                    // Show a stack trace here because this is fairly critical
                    m_outError.println( m_error.format( "SERVER_DAEMON_DIED" ) );
                    t.printStackTrace( m_outError );
                }
            }
        }
        
        // Make sure that noone is ever left waiting for this thread to start.
        synchronized( WrapperManager.class )
        {
            if ( !m_commRunnerStarted )
            {
                m_commRunnerStarted = true;
                WrapperManager.class.notifyAll();
            }
        }
        
        if ( m_debug )
        {
            m_outDebug.println( m_info.format( "SERVER_DAEMON_SHUT_DOWN" ) );
        }
    }
    
    /*---------------------------------------------------------------
     * Inner Classes
     *-------------------------------------------------------------*/
    /**
     * Mapping between WrapperEventListeners and their registered masks.
     *  This is necessary to support the case where the same listener is
     *  registered more than once.   It also makes it possible to reference
     *  an array of these mappings without synchronization.
     */
    private static class WrapperEventListenerMask
    {
        private WrapperEventListener m_listener;
        private long m_mask;
    }
    
    private static class WrapperTickEventImpl
        extends WrapperTickEvent
    {
        private int m_ticks;
        private int m_tickOffset;
        
        /**
         * Returns the tick count at the point the event is fired.
         *
         * @return The tick count at the point the event is fired.
         */
        public int getTicks()
        {
            return m_ticks;
        }
        
        /**
         * Returns the offset between the tick count used by the Wrapper for time
         *  keeping and the tick count generated directly from the system time.
         *
         * This will be 0 in most cases.  But will be a positive value if the
         *  system time is ever set back for any reason.  It will be a negative
         *  value if the system time is set forward or if the system is under
         *  heavy load.  If the wrapper.use_system_time property is set to TRUE
         *  then the Wrapper will be using the system tick count for internal
         *  timing and this value will always be 0.
         *
         * @return The tick count offset.
         */
        public int getTickOffset()
        {
            return m_tickOffset;
        }
    }
}

