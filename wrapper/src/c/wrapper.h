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

#ifndef _WRAPPER_H
#define _WRAPPER_H

#include <sys/timeb.h>

#ifdef WIN32
#include <winsock.h>

#else /* UNIX */
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#ifndef MACOSX
#define u_short unsigned short
#endif /* MACOSX */

#endif

#ifndef DWORD
#define DWORD unsigned long
#endif

#include "property.h"

#define WRAPPER_TICK_MS 100 /* The number of ms that are represented by a single
                             *  tick.  Ticks are used as an alternative time
                             *  keeping method. See the wrapperGetTicks() and
                             *  wrapperGetTickAgeSeconds() functions for more information.
                             * Some code assumes that this number can be evenly
                             *  divided into 1000. */

#define WRAPPER_TIMER_FAST_THRESHOLD 2 * 24 * 3600 * 1000 / WRAPPER_TICK_MS /* Default to 2 days. */
#define WRAPPER_TIMER_SLOW_THRESHOLD 2 * 24 * 3600 * 1000 / WRAPPER_TICK_MS /* Default to 2 days. */

#define WRAPPER_WSTATE_STARTING  51 /* Wrapper is starting.  Remains in this state
                                     *  until the JVM enters the STARTED state or
                                     *  the wrapper jumps into the STOPPING state
                                     *  in response to the JVM application asking
                                     *  to shut down. */
#define WRAPPER_WSTATE_STARTED   52 /* The JVM has entered the STARTED state.
                                     *  The wrapper will remain in this state
                                     *  until the wrapper decides to shut down.
                                     *  This is true even when the JVM process
                                     *  is being restarted. */
#define WRAPPER_WSTATE_PAUSING   53 /* The wrapper enters this state when a PAUSE signal
                                     *  is received from the service control manager. */
#define WRAPPER_WSTATE_PAUSED    54 /* The wrapper enters this state when the Wrapper
                                     *  has actually paused. */
#define WRAPPER_WSTATE_CONTINUING 55 /* The wrapper enters this state when a CONTINUE signal
                                     *  is received from the service control manager. */
#define WRAPPER_WSTATE_STOPPING  56 /* The wrapper is shutting down.  Will be in
                                     *  this state until the JVM enters the DOWN
                                     *  state. */
#define WRAPPER_WSTATE_STOPPED   57 /* The wrapper enters this state just before
                                     *  it exits. */


#define WRAPPER_JSTATE_DOWN      71 /* JVM is confirmed to be down.  This is the 
                                     *  initial state and the state after the JVM
                                     *  process has gone away. */
#define WRAPPER_JSTATE_LAUNCH_DELAY 72 /* Set from the DOWN state to launch a JVM.  The
                                     *  timeout will be the time to actually launch
                                     *  the JVM after any required delay. */
#define WRAPPER_JSTATE_RESTART   73 /* JVM is about to be restarted. No timeout. */
#define WRAPPER_JSTATE_LAUNCH    74 /* JVM is about to launch a JVM. No timeout. */
#define WRAPPER_JSTATE_LAUNCHING 75 /* JVM was launched, but has not yet responded.
                                     *  Must enter the LAUNCHED state before <t>
                                     *  or the JVM will be killed. */
#define WRAPPER_JSTATE_LAUNCHED  76 /* JVM was launched, and responed to a ping. */
#define WRAPPER_JSTATE_STARTING  77 /* JVM has been asked to start.  Must enter the
                                     *  STARTED state before <t> or the JVM will be
                                     *  killed. */
#define WRAPPER_JSTATE_STARTED   78 /* JVM has responded that it is running.  Must
                                     *  respond to a ping by <t> or the JVM will
                                     *  be killed. */
#define WRAPPER_JSTATE_STOP      79 /* JVM is about to be sent a stop command to shutdown
                                     *  cleanly. */
#define WRAPPER_JSTATE_STOPPING  80 /* JVM was sent a stop command, but has not yet
                                     *  responded.  Must enter the STOPPED state
                                     *  and exit before <t> or the JVM will be killed. */
#define WRAPPER_JSTATE_STOPPED   81 /* JVM has responed that it is stopped. */
#define WRAPPER_JSTATE_KILLING   82 /* The Wrapper is about ready to kill the JVM
                                     *  process but it must wait a few moments before
                                     *  actually doing so.  After <t> has expired, the
                                     *  JVM will be killed and we will enter the STOPPED
                                     *  state. */
#define WRAPPER_JSTATE_KILL      83 /* The Wrapper is about ready to kill the JVM process. */

#define FILTER_ACTION_NONE       90
#define FILTER_ACTION_RESTART    91
#define FILTER_ACTION_SHUTDOWN   92


/* Because of the way time is converted to ticks, the largest possible timeout that
 *  can be specified without causing 32-bit overflows is (2^31 / 1000) - 5 = 2147478
 *  Which is a little over 24 days.  To make the interface nice, round this down to
 *  20 days.  Or 1728000. */
#define WRAPPER_TIMEOUT_MAX      1728000

/* Type definitions */
typedef struct WrapperConfig WrapperConfig;
struct WrapperConfig {
    char*   argCommand;             /* The command used to launch the wrapper. */
    char*   argCommandArg;          /* The argument to the command used to launch the wrapper. */
    char*   argConfFile;            /* The name of the config file from the command line. */
    int     argConfFileDefault;     /* True if the config file was not specified. */
    int     argConfFileFound;       /* True if the config file was found. */
    int     argCount;               /* The total argument count. */
    char**  argValues;              /* Argument values. */
    
    int     configured;             /* TRUE if loadConfiguration has been called. */
    int     useSystemTime;          /* TRUE if the wrapper should use the system clock for timing, FALSE if a tick counter should be used. */
    int     timerFastThreshold;     /* If the difference between the system time based tick count and the timer tick count ever falls by more than this value then a warning will be displayed. */
    int     timerSlowThreshold;     /* If the difference between the system time based tick count and the timer tick count ever grows by more than this value then a warning will be displayed. */

    int     port;                   /* Port number which the Wrapper is configured to be listening on */
    int     portMin;                /* Minimum port to use when looking for a port to bind to. */
    int     portMax;                /* Maximum port to use when looking for a port to bind to. */
    int     actualPort;             /* Port number which the Wrapper is actually listening on */
    int     jvmPort;                /* The port which the JVM should bind to when connecting back to the wrapper. */
    int     jvmPortMin;             /* Minimum port which the JVM should bind to when connecting back to the wrapper. */
    int     jvmPortMax;             /* Maximum port which the JVM should bind to when connecting back to the wrapper. */
    int     sock;                   /* Socket number. if open. */
    char    *originalWorkingDir;    /* Original Wrapper working directory. */
    char    *workingDir;            /* Configured working directory. */
    char    *configFile;            /* Name of the configuration file */
    int     commandLogLevel;        /* The log level to use when logging the java command. */
#ifdef WIN32
    char    *jvmCommand;            /* Command used to launch the JVM */
#else /* UNIX */
    char    **jvmCommand;           /* Command used to launch the JVM */
#endif
    int     debugJVM;               /* True if the JVM is being launched with a debugger enabled. */
    int     debugJVMTimeoutNotified;/* True if the JVM is being launched with a debugger enabled and the user has already been notified of a timeout. */
    char    key[17];                /* Key which the JVM uses to authorize connections. (16 digits + \0) */
    int     isConsole;              /* TRUE if the wrapper was launched as a console. */
    int     cpuTimeout;             /* Number of seconds without CPU before the JVM will issue a warning and extend timeouts */
    int     startupTimeout;         /* Number of seconds the wrapper will wait for a JVM to startup */
    int     pingTimeout;            /* Number of seconds the wrapper will wait for a JVM to reply to a ping */
    int     pingInterval;           /* Number of seconds between pinging the JVM */
    int     pingIntervalLogged;     /* Number of seconds between pings which can be logged to debug output. */
    int     shutdownTimeout;        /* Number of seconds the wrapper will wait for a JVM to shutdown */
    int     jvmExitTimeout;         /* Number of seconds the wrapper will wait for a JVM to process to terminate */

#ifdef WIN32
    int     ignoreUserLogoffs;      /* If TRUE, the Wrapper will ignore logoff events when run in the background as an in console mode. */
    DWORD   wrapperPID;             /* PID of the Wrapper process. */
    DWORD   javaPID;                /* PID of the Java process. */
    HANDLE  wrapperProcess;         /* Handle of the Wrapper process. */
    HANDLE  javaProcess;            /* Handle of the Java process. */
#else
    pid_t   wrapperPID;             /* PID of the Wrapper process. */
    pid_t   javaPID;                /* PID of the Java process. */
#endif
    int     wState;                 /* The current state of the wrapper */
    int     jState;                 /* The current state of the jvm */
    DWORD   jStateTimeoutTicks;     /* Tick count until which the current jState is valid */
    int     jStateTimeoutTicksSet;  /* 1 if the current jStateTimeoutTicks is set. */
    DWORD   lastPingTicks;          /* Time that the last ping was sent */
    DWORD   lastLoggedPingTicks;    /* Time that the last logged ping was sent */

    int     isDebugging;            /* TRUE if set in the configuration file */
    int     isAdviserEnabled;       /* TRUE if advice messages should be output. */
    const char *nativeLibrary;      /* The base name of the native library loaded by the WrapperManager. */
    int     libraryPathAppendPath;  /* TRUE if the PATH environment variable should be appended to the java library path. */
    int     isStateOutputEnabled;   /* TRUE if set in the configuration file.  Shows output on the state of the state engine. */
    int     isTickOutputEnabled;    /* TRUE if detailed tick timer output should be included in debug output. */
    int     isLoopOutputEnabled;    /* TRUE if very detailed output from the main loop should be output. */
    int     isSleepOutputEnabled;   /* TRUE if detailed sleep output should be included in debug output. */
    int     isMemoryOutputEnabled;  /* TRUE if detailed memory output should be included in status output. */
    int     memoryOutputInterval;   /* Interval in seconds at which memory usage is logged. */
    DWORD   memoryOutputTimeoutTicks; /* Tick count at which memory will next be logged. */
    int     isCPUOutputEnabled;     /* TRUE if detailed CPU output should be included in status output. */
    int     cpuOutputInterval;      /* Interval in seconds at which CPU usage is logged. */
    DWORD   cpuOutputTimeoutTicks;  /* Tick count at which CPU will next be logged. */
    int     logfileInactivityTimeout; /* The number of seconds of inactivity before the logfile will be closed. */
    DWORD   logfileInactivityTimeoutTicks; /* Tick count at which the logfile will be considered inactive and closed. */
    int     isShutdownHookDisabled; /* TRUE if set in the configuration file */
    int     startupDelayConsole;    /* Delay in seconds before starting the first JVM in console mode. */
    int     startupDelayService;    /* Delay in seconds before starting the first JVM in service mode. */
    int     exitCode;               /* Code which the wrapper will exit with */
    int     exitRequested;          /* TRUE if the current JVM should be shutdown. */
    int     restartRequested;       /* TRUE if the another JVM should be launched after the current JVM is shutdown. Only set if exitRequested is set. */
    int     jvmRestarts;            /* Number of times that a JVM has been launched since the wrapper was started. */
    int     restartDelay;           /* Delay in seconds before restarting a new JVM. */
    int     restartReloadConf;      /* TRUE if the configuration should be reloaded before a JVM restart. */
    int     isRestartDisabled;      /* TRUE if restarts should be disabled. */
    int     requestThreadDumpOnFailedJVMExit; /* TRUE if the JVM should be asked to dump its state when it fails to halt on request. */
    DWORD   jvmLaunchTicks;         /* The tick count at which the previous or current JVM was launched. */
    int     failedInvocationCount;  /* The number of times that the JVM exited in less than successfulInvocationTime in a row. */
    int     successfulInvocationTime;/* Amount of time that a new JVM must be running so that the invocation will be considered to have been a success, leading to a reset of the restart count. */
    int     maxFailedInvocations;   /* Maximum number of failed invocations in a row before the Wrapper will give up and exit. */
    int     outputFilterCount;      /* Number of registered output filters. */
    char**  outputFilters;          /* Array of output filters. */
    int*    outputFilterActions;    /* Array of output filter actions. */
    char    *pidFilename;           /* Name of file to store wrapper pid in */
    char    *lockFilename;          /* Name of file to store wrapper lock in */
    char    *javaPidFilename;       /* Name of file to store jvm pid in */
    char    *javaIdFilename;        /* Name of file to store jvm id in */
    char    *statusFilename;        /* Name of file to store wrapper status in */
    char    *javaStatusFilename;    /* Name of file to store jvm status in */
    char    *commandFilename;       /* Name of a command file used to send commands to the Wrapper. */
    int     commandPollInterval;    /* Interval in seconds at which the existence of the command file is polled. */
    DWORD   commandTimeoutTicks;    /* Tick count at which the command file will be checked next. */
    char    *anchorFilename;        /* Name of an anchor file used to control when the Wrapper should quit. */
    int     anchorPollInterval;     /* Interval in seconds at which the existence of the anchor file is polled. */
    DWORD   anchorTimeoutTicks;     /* Tick count at which the anchor file will be checked next. */
    int     umask;                  /* Default umask for all files. */
    int     javaUmask;              /* Default umask for the java process. */
    int     pidFileUmask;           /* Umask to use when creating the pid file. */
    int     lockFileUmask;          /* Umask to use when creating the lock file. */
    int     javaPidFileUmask;       /* Umask to use when creating the java pid file. */
    int     javaIdFileUmask;        /* Umask to use when creating the java id file. */
    int     statusFileUmask;        /* Umask to use when creating the status file. */
    int     javaStatusFileUmask;    /* Umask to use when creating the java status file. */
    int     anchorFileUmask;        /* Umask to use when creating the anchor file. */
    int     ignoreSignals;          /* True if the Wrapper should ignore any catchable system signals and inform its JVM to do the same. */
    char    *consoleTitle;          /* Text to set the console title to. */
    char    *serviceName;           /* Name of the service. */
    char    *serviceDisplayName;    /* Display name of the service. */
    char    *serviceDescription;    /* Description for service. */

#ifdef WIN32
    int     isSingleInvocation;     /* TRUE if only a single invocation of an application should be allowed to launch. */
    char    *ntServiceLoadOrderGroup; /* Load order group name. */
    char    *ntServiceDependencies; /* List of Dependencies */
    int     ntServiceStartType;     /* Mode in which the Service is installed. 
                                     * {SERVICE_AUTO_START | SERVICE_DEMAND_START} */
    DWORD   ntServicePriorityClass; /* Priority at which the Wrapper and its JVMS will run.
                                     * {HIGH_PRIORITY_CLASS | IDLE_PRIORITY_CLASS | NORMAL_PRIORITY_CLASS | REALTIME_PRIORITY_CLASS} */
    char    *ntServiceAccount;      /* Account name to use when running as a service.  NULL to use the LocalSystem account. */
    char    *ntServicePassword;     /* Password to use when running as a service.  NULL means no password. */
    int     ntServicePasswordPrompt; /* If true then the user will be prompted for a password when installing as a service. */
    int     ntServicePasswordPromptMask; /* If true then the password will be masked as it is input. */
    int     ntServiceInteractive;   /* Should the service be allowed to interact with the desktop? */
    int     ntServicePausable;      /* Should the service be allowed to be paused? */
    int     ntServicePausableStopJVM; /* Should the JVM be stopped when the service is paused? */
    int     ntHideJVMConsole;       /* Should the JVMs Console window be hidden when run as a service.  True by default but GUIs will not be visible for JVMs prior to 1.4.0. */
    int     ntHideWrapperConsole;   /* Should the Wrapper Console window be hidden when run as a service. */
    HWND    wrapperConsoleHandle;   /* Pointer to the Wrapper Console handle if it exists.  This will only be set if the console was allocated then hidden. */
    int     ntAllocConsole;         /* True if a console should be allocated for the Service. */
    int     threadDumpControlCode;  /* Control code which can be used to trigger a thread dump. */
#else /* UNIX */
    int     daemonize;              /* TRUE if the process  should be spawned as a daemon process on launch. */
    int     signalHUPMode;          /* Controls what happens when the Wrapper receives a HUP signal. */
    int     signalUSR1Mode;         /* Controls what happens when the Wrapper receives a USR1 signal. */
    int     signalUSR2Mode;         /* Controls what happens when the Wrapper receives a USR2 signal. */
    int     jvmStopped;             /* Flag which remembers the the stopped state of the JVM process. */
#endif
};

#define WRAPPER_SIGNAL_MODE_IGNORE   (char)100
#define WRAPPER_SIGNAL_MODE_RESTART  (char)101
#define WRAPPER_SIGNAL_MODE_SHUTDOWN (char)102
#define WRAPPER_SIGNAL_MODE_FORWARD  (char)103

#define WRAPPER_MSG_START         (char)100
#define WRAPPER_MSG_STOP          (char)101
#define WRAPPER_MSG_RESTART       (char)102
#define WRAPPER_MSG_PING          (char)103
#define WRAPPER_MSG_STOP_PENDING  (char)104
#define WRAPPER_MSG_START_PENDING (char)105
#define WRAPPER_MSG_STARTED       (char)106
#define WRAPPER_MSG_STOPPED       (char)107
#define WRAPPER_MSG_KEY           (char)110
#define WRAPPER_MSG_BADKEY        (char)111
#define WRAPPER_MSG_LOW_LOG_LEVEL (char)112
#define WRAPPER_MSG_PING_TIMEOUT  (char)113
#define WRAPPER_MSG_SERVICE_CONTROL_CODE (char)114
#define WRAPPER_MSG_PROPERTIES    (char)115

/** Log commands are actually 116 + the LOG LEVEL. */
#define WRAPPER_MSG_LOG           (char)116

#define WRAPPER_PROCESS_DOWN      200
#define WRAPPER_PROCESS_UP        201

extern WrapperConfig *wrapperData;
extern Properties    *properties;

extern char wrapperClasspathSeparator;

/* Protocol Functions */
/**
 * Build the java command line.
 *
 * @return TRUE if there were any problems.
 */
extern void wrapperProtocolClose();
extern int wrapperProtocolFunction(int useLoggerQueue, char function, const char *message);

/**
 * Checks the status of the server socket.
 *
 * The socket will be initialized if the JVM is in a state where it should
 *  be up, otherwise the socket will be left alone.
 *
 * If the forceOpen flag is set then an attempt will be made to initialize
 *  the socket regardless of the JVM state.
 *
 * Returns TRUE if the socket is open and ready on return, FALSE if not.
 */
extern int wrapperCheckServerSocket(int forceOpen);

/**
 * Read any data sent from the JVM.  This function will loop and read as many
 *  packets are available.  The loop will only be allowed to go for 250ms to
 *  ensure that other functions are handled correctly.
 *
 * Returns 0 if all available data has been read, 1 if more data is waiting.
 */
extern int wrapperProtocolRead();

/******************************************************************************
 * Utility Functions
 *****************************************************************************/
extern void wrapperAddDefaultProperties();

extern int wrapperLoadConfigurationProperties();

extern void wrapperGetCurrentTime(struct timeb *timeBuffer);

#ifdef WIN32
extern char** wrapperGetSystemPath();
extern int wrapperGetJavaHomeFromWindowsRegistry(char *javaHome);
#endif

extern int wrapperCheckRestartTimeOK();

/**
 * command is a pointer to a pointer of an array of character strings.
 * length is the number of strings in the above array.
 */
extern int wrapperBuildJavaCommandArray(char ***strings, int *length, int addQuotes);
extern void wrapperFreeJavaCommandArray(char **strings, int length);

extern int wrapperInitialize();
extern void wrapperDispose();

/**
 * Returns the file name base as a newly malloced char *.  The resulting
 *  base file name will have any path and extension stripped.
 *
 * baseName should be long enough to always contain the base name.
 *  (strlen(fileName) + 1) is safe.
 */
extern void wrapperGetFileBase(const char *fileName, char *baseName);

/**
 * Output the version.
 */
extern void wrapperVersionBanner();

/**
 * Output the application usage.
 */
extern void wrapperUsage(char *appName);

/**
 * Parse the main arguments.
 *
 * Returns FALSE if the application should exit with an error.  A message will
 *  already have been logged.
 */
extern int wrapperParseArguments(int argc, char **argv);

/**
 * Called when the Wrapper detects that the JVM process has exited.
 *  Contains code common to all platforms.
 */
extern void wrapperJVMProcessExited(int useLoggerQueue, DWORD nowTicks, int exitCode);

/**
 * Logs a single line of child output allowing any filtering
 *  to be done in a common location.
 */
extern void wrapperLogChildOutput(const char* log);

/**
 * Changes the current Wrapper state.
 *
 * useLoggerQueue - True if the log entries should be queued.
 * wState - The new Wrapper state.
 */
extern void wrapperSetWrapperState(int useLoggerQueue, int wState);

/**
 * Updates the current state time out.
 *
 * nowTicks - The current tick count at the time of the call, may be -1 if
 *            delay is negative.
 * delay - The delay in seconds, added to the nowTicks after which the state
 *         will time out, if negative will never time out.
 */
extern void wrapperUpdateJavaStateTimeout(DWORD nowTicks, int delay);

/**
 * Changes the current Java state.
 *
 * useLoggerQueue - True if the log entries should be queued.
 * jState - The new Java state.
 * nowTicks - The current tick count at the time of the call, may be -1 if
 *            delay is negative.
 * delay - The delay in seconds, added to the nowTicks after which the state
 *         will time out, if negative will never time out.
 */
extern void wrapperSetJavaState(int useLoggerQueue, int jState, DWORD nowTicks, int delay);

/******************************************************************************
 * Platform specific methods
 *****************************************************************************/
#ifdef WIN32
extern int exceptionFilterFunction(PEXCEPTION_POINTERS exceptionPointers);
#endif

/**
 * Gets the error code for the last operation that failed.
 */
extern int wrapperGetLastError();

/**
 * Execute initialization code to get the wrapper set up.
 */
extern int wrapperInitializeRun();

/**
 * Cause the current thread to sleep for the specified number of milliseconds.
 *  Sleeps over one second are not allowed.
 */
extern void wrapperSleep(int useLoggerQueue, int ms);

/**
 * Reports the status of the wrapper to the service manager
 * Possible status values:
 *   WRAPPER_WSTATE_STARTING
 *   WRAPPER_WSTATE_STARTED
 *   WRAPPER_WSTATE_STOPPING
 *   WRAPPER_WSTATE_STOPPED
 */
extern void wrapperReportStatus(int useLoggerQueue, int status, int errorCode, int waitHint);

/**
 * Read and process any output from the child JVM Process.
 * Most output should be logged to the wrapper log file.
 */
extern int wrapperReadChildOutput();

/**
 * Checks on the status of the JVM Process.
 * Returns WRAPPER_PROCESS_UP or WRAPPER_PROCESS_DOWN
 */
extern int wrapperGetProcessStatus(int useLoggerQueue, DWORD nowTicks, int sigChild);

/**
 * Pauses before launching a new JVM if necessary.
 */
extern void wrapperPauseBeforeExecute();

/**
 * Launches a JVM process and store it internally
 */
extern void wrapperExecute();

/**
 * Returns a tick count that can be used in combination with the
 *  wrapperGetTickAgeSeconds() function to perform time keeping.
 */
extern DWORD wrapperGetTicks();

/**
 * Outputs a a log entry describing what the memory dump columns are.
 */
extern void wrapperDumpMemoryBanner();

/**
 * Outputs a log entry at regular intervals to track the memory usage of the
 *  Wrapper and its JVM.
 */
extern void wrapperDumpMemory();

/**
 * Outputs a log entry at regular intervals to track the CPU usage over each
 *  interval for the Wrapper and its JVM.
 */
extern void wrapperDumpCPUUsage();

/******************************************************************************
 * Wrapper inner methods.
 *****************************************************************************/
/**
 * Immediately kill the JVM process and set the JVM state to
 *  WRAPPER_JSTATE_DOWN.
 */
extern void wrapperKillProcessNow();

/**
 * Puts the Wrapper into a state where the JVM will be killed at the soonest
 *  possible opportunity.  It is necessary to wait a moment if a final thread
 *  dump is to be requested.  This call wll always set the JVM state to
 *  WRAPPER_JSTATE_KILLING.
 */
extern void wrapperKillProcess(int useLoggerQueue);

/**
 * Launch the wrapper as a console application.
 */
extern int wrapperRunConsole();

/**
 * Launch the wrapper as a service application.
 */
extern int wrapperRunService();

#ifdef WIN32
/**
 * Used to ask the state engine to pause the JVM and Wrapper
 */
extern void wrapperPauseProcess(int useLoggerQueue);

/**
 * Used to ask the state engine to resume the JVM and Wrapper
 */
extern void wrapperContinueProcess(int useLoggerQueue);
#endif

/**
 * Used to ask the state engine to shut down the JVM and Wrapper
 */
extern void wrapperStopProcess(int useLoggerQueue, int exitCode);

/**
 * Used to ask the state engine to shut down the JVM.
 */
extern void wrapperRestartProcess(int useLoggerQueue);

/**
 * Loops over and strips all double quotes from prop and places the
 *  stripped version into propStripped.
 *
 * The exception is double quotes that are preceeded by a backslash
 *  in this case the backslash is stripped.
 *
 * If two backslashes are found in a row, then the first escapes the
 *  second and the second is removed.
 */
extern void wrapperStripQuotes(const char *prop, char *propStripped);

/**
 * Adds quotes around the specified string in such a way that everything is
 *  escaped correctly.  If the bufferSize is not large enough then the
 *  required size will be returned.  0 is returned if successful.
 */
extern size_t wrapperQuoteValue(const char* value, char *buffer, size_t bufferSize);

/**
 * Checks the quotes in the value and displays an error if there are any problems.
 * This can be useful to help users debug quote problems.
 */
extern int wrapperCheckQuotes(const char *value, const char *propName);

/**
 * The main event loop for the wrapper.  Handles all state changes and events.
 */
extern void wrapperEventLoop();

extern void wrapperBuildKey();

/**
 * Send a signal to the JVM process asking it to dump its JVM state.
 */
extern void wrapperRequestDumpJVMState(int useLoggerQueue);

/**
 * Build the java command line.
 *
 * @return TRUE if there were any problems.
 */
extern int wrapperBuildJavaCommand();

/**
 * Calculates a tick count using the system time.
 */
extern DWORD wrapperGetSystemTicks();

/**
 * Returns difference in seconds between the start and end ticks.  This function
 *  handles cases where the tick counter has wrapped between when the start
 *  and end tick counts were taken.  See the wrapperGetTicks() function.
 */
extern int wrapperGetTickAgeSeconds(DWORD start, DWORD end);

/**
 * Returns difference in ticks between the start and end ticks.  This function
 *  handles cases where the tick counter has wrapped between when the start
 *  and end tick counts were taken.  See the wrapperGetTicks() function.
 *
 * This can be done safely in 32 bits
 */
extern int wrapperGetTickAgeTicks(DWORD start, DWORD end);

/**
 * Returns TRUE if the specified tick timeout has expired relative to the
 *  specified tick count.
 */
extern int wrapperTickExpired(DWORD nowTicks, DWORD timeoutTicks);

/**
 * Returns a tick count that is the specified number of seconds later than
 *  the base tick count.
 */
extern DWORD wrapperAddToTicks(DWORD start, int seconds);

/**
 * Sets the working directory of the Wrapper to the specified directory.
 *  The directory can be relative or absolute.
 * If there are any problems then a non-zero value will be returned.
 */
extern int wrapperSetWorkingDir(const char* dir);

/******************************************************************************
 * Protocol callback functions
 *****************************************************************************/
extern void wrapperLogSignalled(int logLevel, char *msg);
extern void wrapperKeyRegistered(char *key);
extern void wrapperPingResponded();
extern void wrapperStopRequested(int exitCode);
extern void wrapperRestartRequested();
extern void wrapperStopPendingSignalled(int waitHint);
extern void wrapperStoppedSignalled();
extern void wrapperStartPendingSignalled(int waitHint);
extern void wrapperStartedSignalled();

#endif
