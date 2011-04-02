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

/**
 * Author:
 *   Leif Mortenson <leif@tanukisoftware.com>
 */

#ifdef WIN32

#include <direct.h>
#include <io.h>
#include <math.h>
#include <process.h>
#include <stdio.h>
#include <stdlib.h>
#include <windows.h>
#include <sys/timeb.h>
#include <conio.h>
#include "psapi.h"

#include "resource.h"
#include "wrapper.h"
#include "wrapperinfo.h"
#include "property.h"
#include "logger.h"

/*****************************************************************************
 * Win32 specific variables and procedures                                   *
 *****************************************************************************/
SERVICE_STATUS          ssStatus;       
SERVICE_STATUS_HANDLE   sshStatusHandle;

#define SYSTEM_PATH_MAX_LEN 256
static char *systemPath[SYSTEM_PATH_MAX_LEN];
static HANDLE wrapperChildStdoutWr = INVALID_HANDLE_VALUE;
static HANDLE wrapperChildStdoutRd = INVALID_HANDLE_VALUE;

/* Each time wrapperReadChildOutput() is called, there is a chance the we are
 *  forced to log a line of output before receiving the LF.  This flag remembers
 *  when that happens so we can avoid logging that extra LF when it is read. */
static int    wrapperChildStdoutRdLastLF = 0;

/* The block size that data is peeked from the JVM pipe in wrapperReadChildOutput()
 *  If this is too large then we waste time when reading in lots of short lines.
 *  But if it is too short then we have to read in many blocks for each line.
 *  This value assumes that really long lines are relatively rare. */
#define READ_BUFFER_BLOCK_SIZE 100

/* The buffer used to store piped output lines from the JVM.  This buffer will
 *  grow as needed to store the largest line output by the application. */
char *wrapperChildStdoutRdBuffer = NULL;
int wrapperChildStdoutRdBufferSize = 0;

char wrapperClasspathSeparator = ';';

HANDLE timerThreadHandle;
DWORD timerThreadId;
/* Initialize the timerTicks to a very high value.  This means that we will
 *  always encounter the first rollover (256 * WRAPPER_MS / 1000) seconds
 *  after the Wrapper the starts, which means the rollover will be well
 *  tested. */
DWORD timerTicks = 0xffffff00;

/** Flag which keeps track of whether or not the CTRL-C key has been pressed. */
int ctrlCTrapped = FALSE;

/** Flag which keeps track of whether or not PID files should be deleted on shutdown. */
int cleanUpPIDFilesOnExit = FALSE;

char* getExceptionName(DWORD exCode);

/* Dynamically loaded functions. */
FARPROC OptionalGetProcessTimes = NULL;
FARPROC OptionalGetProcessMemoryInfo = NULL;

/******************************************************************************
 * Windows specific code
 ******************************************************************************/
void loadDLLProcs() {
    HMODULE kernel32Mod;
    HMODULE psapiMod;

    /* The PSAPI module was added in NT 3.5. */
    if ((kernel32Mod = GetModuleHandle("KERNEL32.DLL")) == NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
            "The KERNEL32.DLL was not found.  Some functions will be disabled.");
    } else {
        if ((OptionalGetProcessTimes = GetProcAddress(kernel32Mod, "GetProcessTimes")) == NULL) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "The GetProcessTimes is not available in this KERNEL32.DLL version.  Some functions will be disabled.");
        }
    }

    /* The PSAPI module was added in NT 4.0. */
    if ((psapiMod = LoadLibrary("PSAPI.DLL")) == NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
            "The PSAPI.DLL was not found.  Some functions will be disabled.");
    } else {
        if ((OptionalGetProcessMemoryInfo = GetProcAddress(psapiMod, "GetProcessMemoryInfo")) == NULL) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "The GetProcessMemoryInfo is not available in this PSAPI.DLL version.  Some functions will be disabled.");
        }
    }
}

/**
 * Builds an array in memory of the system path.
 */
int buildSystemPath() {
    char *envBuffer;
    size_t len, i;
    char *c, *lc;

    /* Get the length of the PATH environment variable. */
    len = GetEnvironmentVariable("PATH", NULL, 0);
    if (len == 0) {
        /* PATH not set on this system.  Not an error. */
        systemPath[0] = NULL;
        return 0;
    }

    /* Allocate the memory to hold the PATH */
    envBuffer = malloc(sizeof(char) * len);
    if (!envBuffer) {
        outOfMemory("BSP", 1);
        return 1;
    }
    GetEnvironmentVariable("PATH", envBuffer, (DWORD)len);

#ifdef _DEBUG
    printf("Getting the system path: %s\n", envBuffer);
#endif

    /* Build an array of the path elements.  To make it easy, just
     *  assume there won't be more than 255 path elements. Verified
     *  in the loop. */
    i = 0;
    lc = envBuffer;
    /* Get the elements ending in a ';' */
    while (((c = strchr(lc, ';')) != NULL) && (i < SYSTEM_PATH_MAX_LEN - 2))
    {
        len = (int)(c - lc);
        systemPath[i] = malloc(sizeof(char) * (len + 1));
        if (!systemPath[i]) {
            outOfMemory("BSP", 2);
            return 1;
        }

        memcpy(systemPath[i], lc, len);
        systemPath[i][len] = '\0';
#ifdef _DEBUG
        printf("PATH[%d]=%s\n", i, systemPath[i]);
#endif
        lc = c + 1;
        i++;
    }
    /* There should be one more value after the last ';' */
    len = strlen(lc);
    systemPath[i] = malloc(sizeof(char) * (len + 1));
    if (!systemPath[i]) {
        outOfMemory("BSP", 3);
        return 1;
    }
    strcpy(systemPath[i], lc);
#ifdef _DEBUG
    printf("PATH[%d]=%s\n", i, systemPath[i]);
#endif
    i++;
    /* NULL terminate the array. */
    systemPath[i] = NULL;
#ifdef _DEBUG
    printf("PATH[%d]=<null>\n", i);
#endif
    i++;

    /* Release the environment variable memory. */
    free(envBuffer);

    return 0;
}
char** wrapperGetSystemPath() {
    return systemPath;
}

/**
 * Initializes the invocation mutex.  Returns 1 if the mutex already exists
 *  or can not be created.  0 if this is the first instance.
 */
HANDLE invocationMutexHandle = NULL;
int initInvocationMutex() {
    char *mutexName;
    if (wrapperData->isSingleInvocation) {
        mutexName = malloc(sizeof(char) * (23 + strlen(wrapperData->serviceName) + 1));
        if (!mutexName) {
            outOfMemory("IIM", 1);
            return 1;
        }
        sprintf(mutexName, "Java Service Wrapper - %s", wrapperData->serviceName);
        
        if (!(invocationMutexHandle = CreateMutex(NULL, FALSE, mutexName))) {
            free(mutexName);
            
            if (GetLastError() == ERROR_ACCESS_DENIED) {
                /* Most likely the app is running as a service and we tried to run it as a console. */
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                    "ERROR: Another instance of the %s application is already running.",
                    wrapperData->serviceName);
                return 1;
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                    "ERROR: Unable to create the single invation mutex. %s",
                    getLastErrorText());
                return 1;
            }
        } else {
            free(mutexName);
        }
        
        if (GetLastError() == ERROR_ALREADY_EXISTS) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "ERROR: Another instance of the %s application is already running.",
                wrapperData->serviceName);
            return 1;
        }
    }
    
    return 0;
}

/**
 * exits the application after running shutdown code.
 */
void appExit(int exitCode) {
    /* We only want to delete the pid files if we created them. Some Wrapper
     *  invocations are meant to run in parallel with Wrapper instances
     *  controlling a JVM. */
    if (cleanUpPIDFilesOnExit) {
        /* Remove pid file.  It may no longer exist. */
        if (wrapperData->pidFilename) {
            _unlink(wrapperData->pidFilename);
        }
        
        /* Remove lock file.  It may no longer exist. */
        if (wrapperData->lockFilename) {
            _unlink(wrapperData->lockFilename);
        }
        
        /* Remove status file.  It may no longer exist. */
        if (wrapperData->statusFilename) {
            _unlink(wrapperData->statusFilename);
        }
        
        /* Remove java status file if it was registered and created by this process. */
        if (wrapperData->javaStatusFilename) {
            _unlink(wrapperData->javaStatusFilename);
        }
        
        /* Remove java id file if it was registered and created by this process. */
        if (wrapperData->javaIdFilename) {
            _unlink(wrapperData->javaIdFilename);
        }
        
        /* Remove anchor file.  It may no longer exist. */
        if (wrapperData->anchorFilename) {
            _unlink(wrapperData->anchorFilename);
        }
    }
    
    /* Close the invocation mutex if we created or looked it up. */
    if (invocationMutexHandle) {
        CloseHandle(invocationMutexHandle);
        invocationMutexHandle = NULL;
    }
    
    /* Common wrapper cleanup code. */
    wrapperDispose();
    
    /* Do this here to unregister the syslog resources on exit.*/
    /*unregisterSyslogMessageFile(); */
    exit(exitCode);
}

/**
 * Gets the error code for the last operation that failed.
 */
int wrapperGetLastError() {
    return WSAGetLastError();
}

/**
 * Writes a PID to disk.
 *
 * filename: File to write to.
 * pid: pid to write in the file.
 */
int writePidFile(const char *filename, DWORD pid, int newUmask) {
    FILE *pid_fp = NULL;
    int old_umask;

    old_umask = _umask(newUmask);
    pid_fp = fopen(filename, "w");
    _umask(old_umask);
    
    if (pid_fp != NULL) {
        fprintf(pid_fp, "%d\n", pid);
        fclose(pid_fp);
    } else {
        return 1;
    }
    return 0;
}

/**
 * Initialize the pipe which will be used to capture the output from the child
 * process.
 */
int wrapperInitChildPipe() {
    SECURITY_ATTRIBUTES saAttr;
    HANDLE childStdoutRd = INVALID_HANDLE_VALUE;

    /* Set the bInheritHandle flag so pipe handles are inherited. */
    saAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
    saAttr.lpSecurityDescriptor = NULL;
    saAttr.bInheritHandle = TRUE;

    /* Create a pipe for the child process's STDOUT. */
    if (!CreatePipe(&childStdoutRd, &wrapperChildStdoutWr, &saAttr, 0)) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Stdout pipe creation failed  Err(%ld : %s)",
            GetLastError(), getLastErrorText());
        return -1;
    }

    /* Create a noninheritable read handle and close the inheritable read handle. */
    if (!DuplicateHandle(GetCurrentProcess(), childStdoutRd, GetCurrentProcess(), &wrapperChildStdoutRd, 0, FALSE, DUPLICATE_SAME_ACCESS)) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "DuplicateHandle failed");
        return -1;
    }
    CloseHandle(childStdoutRd);

    return 0;
}

/**
 * Handler to take care of the case where the user hits CTRL-C when the wrapper 
 * is being run as a console.
 */
int wrapperConsoleHandler(int key) {
    int quit = FALSE;
    int halt = FALSE;
    
    /* Immediately register this thread with the logger. */
    logRegisterThread(WRAPPER_THREAD_SIGNAL);

    /* Enclose the contents of this call in a try catch block so we can
     *  display and log useful information should the need arise. */
    __try {
        switch (key) {
        case CTRL_C_EVENT:
        case CTRL_CLOSE_EVENT:
            /* The user hit CTRL-C.  Can only happen when run as a console. */
            if (wrapperData->ignoreSignals) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "CTRL-C trapped, but ignored.");
            } else {
                /*  Always quit.  If the user has pressed CTRL-C previously then we want to force
                 *   an immediate shutdown. */
                if (ctrlCTrapped) {
                    /* Pressed CTRL-C more than once. */
                    log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "CTRL-C trapped.  Forcing immediate shutdown.");
                    halt = TRUE;
                } else {
                    log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "CTRL-C trapped.  Shutting down.");
                    ctrlCTrapped = TRUE;
                }
                quit = TRUE;
            }
            break;
    
        case CTRL_BREAK_EVENT:
            /* The user hit CTRL-BREAK */
            log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                "CTRL-BREAK/PAUSE trapped.  Asking the JVM to dump its state.");
    
            /* If the java process was launched using the same console, ie where
             *  processflags=CREATE_NEW_PROCESS_GROUP; then the java process will
             *  also get this message, so it can be ignored here. */
            /*
            wrapperRequestDumpJVMState(TRUE);
            */
    
            quit = FALSE;
            break;
    
        case CTRL_LOGOFF_EVENT:
            /* Happens when the user logs off.  We should quit when run as a */
            /*  console, but stay up when run as a service. */
            if ((wrapperData->isConsole) && (!wrapperData->ignoreUserLogoffs)) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "User logged out.  Shutting down.");
                quit = TRUE;
            } else {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_INFO,
                    "User logged out.  Ignored.");
                quit = FALSE;
            }
            break;
        case CTRL_SHUTDOWN_EVENT:
            /* Happens when the machine is shutdown or rebooted.  Always quit. */
            log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                "Machine is shutting down.");
            quit = TRUE;
            break;
        default:
            /* Unknown.  Don't quit here. */
            log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Trapped unexpected console signal (%d).  Ignored.", key);
            quit = FALSE;
        }
    
        if (quit) {
            if (halt) {
                /* Disable the thread dump on exit feature if it is set because it
                 *  should not be displayed when the user requested the immediate exit. */
                wrapperData->requestThreadDumpOnFailedJVMExit = FALSE;
                wrapperKillProcess(TRUE);
            } else {
                wrapperStopProcess(TRUE, 0);
            }
            /* Don't actually kill the process here.  Let the application shut itself down */
    
            /* To make sure that the JVM will not be restarted for any reason,
             *  start the Wrapper shutdown process as well. */
            if ((wrapperData->wState == WRAPPER_WSTATE_STOPPING) ||
                (wrapperData->wState == WRAPPER_WSTATE_STOPPED)) {
                /* Already stopping. */
            } else {
                wrapperSetWrapperState(TRUE, WRAPPER_WSTATE_STOPPING);
            }
        }
        
    } __except (exceptionFilterFunction(GetExceptionInformation())) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
            "<-- Wrapper Stopping due to error in console handler.");
        appExit(1);
    }

    return TRUE; /* We handled the event. */
}

/**
 * Used to ask the state engine to pause the JVM and Wrapper
 */
void wrapperPauseProcess(int useLoggerQueue) {
    if ((wrapperData->wState == WRAPPER_WSTATE_STOPPING) ||
        (wrapperData->wState == WRAPPER_WSTATE_STOPPED)) {
        /* If we are already shutting down, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperPauseProcess() called while stopping.  (IGNORED)");
        }
    } else if (wrapperData->wState == WRAPPER_WSTATE_PAUSING) {
        /* If we are currently being paused, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperPauseProcess() called while pausing.  (IGNORED)");
        }
    } else if (wrapperData->wState == WRAPPER_WSTATE_PAUSED) {
        /* If we are currently paused, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperPauseProcess() called while paused.  (IGNORED)");
        }
    } else {
        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperPauseProcess() called.");
        }

        wrapperSetWrapperState(useLoggerQueue, WRAPPER_WSTATE_PAUSING);
    }
}

/**
 * Used to ask the state engine to continue the JVM and Wrapper
 */
void wrapperContinueProcess(int useLoggerQueue) {
    if ((wrapperData->wState == WRAPPER_WSTATE_STOPPING) ||
        (wrapperData->wState == WRAPPER_WSTATE_STOPPED)) {
        /* If we are already shutting down, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperContinueProcess() called while stopping.  (IGNORED)");
        }
    } else if (wrapperData->wState == WRAPPER_WSTATE_STARTING) {
        /* If we are currently being started, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperContinueProcess() called while starting.  (IGNORED)");
        }
    } else if (wrapperData->wState == WRAPPER_WSTATE_STARTED) {
        /* If we are currently started, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperContinueProcess() called while started.  (IGNORED)");
        }
    } else if (wrapperData->wState == WRAPPER_WSTATE_CONTINUING) {
        /* If we are currently being continued, then ignore and continue to do so. */

        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperContinueProcess() called while continuing.  (IGNORED)");
        }
    } else {
        if (wrapperData->isDebugging) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "wrapperContinueProcess() called.");
        }

        /* If we were configured to stop the JVM then we want to reset its failed
         *  invocation count as the current stoppage was expected. */
        if (wrapperData->ntServicePausableStopJVM) {
            wrapperData->failedInvocationCount = 0;
        }

        wrapperSetWrapperState(useLoggerQueue, WRAPPER_WSTATE_CONTINUING);
    }
}

/******************************************************************************
 * Platform specific methods
 *****************************************************************************/

/**
 * Send a signal to the JVM process asking it to dump its JVM state.
 */
void wrapperRequestDumpJVMState(int useLoggerQueue) {
    if (wrapperData->javaProcess != NULL) {
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
            "Dumping JVM state.");
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
            "Sending BREAK event to process group %ld.", wrapperData->javaPID);
        if ( GenerateConsoleCtrlEvent( CTRL_BREAK_EVENT, wrapperData->javaPID ) == 0 ) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Unable to send BREAK event to JVM process.  Err(%ld : %s)",
                GetLastError(), getLastErrorText());
        }
    }
}

/**
 * Build the java command line.
 *
 * @return TRUE if there were any problems.
 */
int wrapperBuildJavaCommand() {
    size_t commandLen;
    char **strings;
    int length, i;

    /* If this is not the first time through, then dispose the old command */
    if (wrapperData->jvmCommand) {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Clearing up old command line");
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Old Command Line \"%s\"", wrapperData->jvmCommand);
#endif
        free(wrapperData->jvmCommand);
        wrapperData->jvmCommand = NULL;
    }

    /* Build the Java Command Strings */
    strings = NULL;
    length = 0;
    if (wrapperBuildJavaCommandArray(&strings, &length, TRUE)) {
        /* Failed. */
        return TRUE;
    }
    
#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "JVM Command Line Parameters");
    for (i = 0; i < length; i++) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "%d : %s", i, strings[i]);
    }
#endif

    /* Build a single string from the array */
    /* Calculate the length */
    commandLen = 0;
    for (i = 0; i < length; i++) {
        if (i > 0) {
            commandLen++; /* Space */
        }
        commandLen += strlen(strings[i]);
    }
    commandLen++; /* '\0' */

    /* Build the actual command */
    wrapperData->jvmCommand = malloc(sizeof(char) * commandLen);
    if (!wrapperData->jvmCommand) {
        outOfMemory("WBJC", 1);
        return TRUE;
    }
    commandLen = 0;
    for (i = 0; i < length; i++) {
        if (i > 0) {
            wrapperData->jvmCommand[commandLen++] = ' ';
        }
        sprintf((char *)(&(wrapperData->jvmCommand[commandLen])), "%s", strings[i]);
        commandLen += strlen(strings[i]);
    }
    wrapperData->jvmCommand[commandLen++] = '\0';

    /* Free up the temporary command array */
    wrapperFreeJavaCommandArray(strings, length);

    return FALSE;
}

void hideConsoleWindow(HWND consoleHandle) {
    WINDOWPLACEMENT consolePlacement;
    
    if (GetWindowPlacement(consoleHandle, &consolePlacement)) {
        /* Hide the Window. */
        consolePlacement.showCmd = SW_HIDE;

        /* If we hide the window too soon after it is shown, it sometimes sticks, so wait a moment. */
        wrapperSleep(FALSE, 10);

        if (!SetWindowPlacement(consoleHandle, &consolePlacement)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                "Unable to set window placement information: %s", getLastErrorText());
        }
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
            "Unable to obtain window placement information: %s", getLastErrorText());
    }
}

HWND findAndHideConsoleWindow( char *title ) {
    HWND consoleHandle;
    int i = 0;

    /* Allow up to 2 seconds for the window to show up, but don't hang
     *  up if it doesn't */
    consoleHandle = NULL;
    while ((!consoleHandle) && (i < 200)) {
        wrapperSleep(FALSE, 10);
        consoleHandle = FindWindow("ConsoleWindowClass", title);
        i++;
    }
    if (consoleHandle != NULL) {
        hideConsoleWindow(consoleHandle);
    }
    
    return consoleHandle;
}

void showConsoleWindow(HWND consoleHandle) {
    WINDOWPLACEMENT consolePlacement;
    
    if (GetWindowPlacement(consoleHandle, &consolePlacement)) {
        /* Show the Window. */
        consolePlacement.showCmd = SW_SHOW;

        if (!SetWindowPlacement(consoleHandle, &consolePlacement)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                "Unable to set window placement information: %s", getLastErrorText());
        }
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
            "Unable to obtain window placement information: %s", getLastErrorText());
    }
}

/**
 * The main entry point for the timer thread which is started by
 *  initializeTimer().  Once started, this thread will run for the
 *  life of the process.
 *
 * This thread will only be started if we are configured NOT to
 *  use the system time as a base for the tick counter.
 */
DWORD WINAPI timerRunner(LPVOID parameter) {
    DWORD sysTicks;
    DWORD lastTickOffset = 0;
    DWORD tickOffset;
    int offsetDiff;
    int first = 1;

    /* In case there are ever any problems in this thread, enclose it in a try catch block. */
    __try {
        /* Immediately register this thread with the logger. */
        logRegisterThread(WRAPPER_THREAD_TIMER);

        if (wrapperData->isTickOutputEnabled) {
            log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Timer thread started.");
        }

        while(TRUE) {
            wrapperSleep(TRUE, WRAPPER_TICK_MS);

            /* Get the tick count based on the system time. */
            sysTicks = wrapperGetSystemTicks();

            /* Advance the timer tick count. */
            timerTicks++;

            /* Calculate the offset between the two tick counts. This will always work due to overflow. */
            tickOffset = sysTicks - timerTicks;

            /* The number we really want is the difference between this tickOffset and the previous one. */
            offsetDiff = (int)(tickOffset - lastTickOffset);

            if (first) {
                first = 0;
            } else {
                if (offsetDiff > wrapperData->timerSlowThreshold) {
                    log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "The timer fell behind the system clock by %dms.", (int)(offsetDiff * WRAPPER_TICK_MS));
                } else if (offsetDiff < -1 * wrapperData->timerFastThreshold) {
                    log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "The system clock fell behind the timer by %dms.", (int)(-1 * offsetDiff * WRAPPER_TICK_MS));
                }

                if (wrapperData->isTickOutputEnabled) {
                    log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "    Timer: ticks=%lu, system ticks=%lu, offset=%lu, offsetDiff=%ld",
                        timerTicks, sysTicks, tickOffset, offsetDiff);
                }
            }

            /* Store this tick offset for the next time through the loop. */
            lastTickOffset = tickOffset;
        }
    } __except (exceptionFilterFunction(GetExceptionInformation())) {
        /* This call is not queued to make sure it makes it to the log prior to a shutdown. */
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Fatal error in the Timer thread.");
        appExit(1);
        return 1; /* For the compiler, we will never get here. */
    }
}

/**
 * Creates a process whose job is to loop and simply increment a ticks
 *  counter.  The tick counter can then be used as a clock as an alternative
 *  to using the system clock.
 */
int initializeTimer() {
    if (wrapperData->isTickOutputEnabled) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Launching Timer thread.");
    }

    timerThreadHandle = CreateThread(
        NULL, /* No security attributes as there will not be any child processes of the thread. */
        0,    /* Use the default stack size. */
        timerRunner,
        NULL, /* No parameters need to passed to the thread. */
        0,    /* Start the thread running immediately. */
        &timerThreadId
        );
    if (!timerThreadHandle) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
            "Unable to create a timer thread: %s", getLastErrorText());
        return 1;
    } else {
        return 0;
    }
}

int initializeWinSock() {
    WORD ws_version=MAKEWORD(1, 1);
    WSADATA ws_data;
    int res;
    
    /* Initialize Winsock */
    if ((res = WSAStartup(ws_version, &ws_data)) != 0) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Cannot initialize Windows socket DLLs.");
        return res;
    }
    
    return 0;
}

/**
 * Execute initialization code to get the wrapper set up.
 */
int wrapperInitializeRun() {
    HANDLE hStdout;
    int res;
    char titleBuffer[80];

    /* Set the process priority. */
    HANDLE process = GetCurrentProcess();
    if (!SetPriorityClass(process, wrapperData->ntServicePriorityClass)) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
            "Unable to set the process priority:  %s", getLastErrorText());
    }

    /* Initialize the pipe to capture the child process output */
    if ((res = wrapperInitChildPipe()) != 0) {
        return res;
    }

    /* Initialize the Wrapper console handle to null */
    wrapperData->wrapperConsoleHandle = NULL;
    
    /* The Wrapper will not have its own console when running as a service.  We need
     *  to create one here. */
    if ((!wrapperData->isConsole) && (wrapperData->ntAllocConsole)) {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Allocating a console for the service.");
#endif

        if (!AllocConsole()) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "ERROR: Unable to allocate a console for the service: %s", getLastErrorText());
            return 1;
        }

        hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
        if (hStdout == INVALID_HANDLE_VALUE) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "ERROR: Unable to get the new stdout handle: %s", getLastErrorText());
           return 1;
        }
        setConsoleStdoutHandle( hStdout );

        if (wrapperData->ntHideWrapperConsole) {
            /* A console needed to be allocated for the process but it should be hidden. */
#ifdef _DEBUG
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Hiding the console.");
#endif

            /* Generate a unique time for the console so we can look for it below. */
            sprintf(titleBuffer, "Wrapper Console ID %d%d (Do not close)", rand(), rand());

            SetConsoleTitle( titleBuffer );

            wrapperData->wrapperConsoleHandle = findAndHideConsoleWindow( titleBuffer );
        }
    }

    /* Attempt to set the console title if it exists and is accessable. */
    if (wrapperData->consoleTitle) {
        if (wrapperData->isConsole || (wrapperData->ntServiceInteractive && !wrapperData->ntHideWrapperConsole)) {
            /* The console should be visible. */
            if (!SetConsoleTitle(wrapperData->consoleTitle)) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                    "Attempt to set the console title failed: %s", getLastErrorText());
            }
        }
    }

    /* Set the handler to trap console signals.  This must be done after the console
     *  is created or it will not be applied to that console. */
    SetConsoleCtrlHandler((PHANDLER_ROUTINE)wrapperConsoleHandler, TRUE);

    if (wrapperData->useSystemTime) {
        /* We are going to be using system time so there is no reason to start up a timer thread. */
        timerThreadHandle = NULL;
        timerThreadId = 0;
    } else {
        /* Create and initialize a timer thread. */
        if ((res = initializeTimer()) != 0) {
            return res;
        }
    }

    return 0;
}

/**
 * Cause the current thread to sleep for the specified number of milliseconds.
 *  Sleeps over one second are not allowed.
 */
void wrapperSleep(int useLoggerQueue, int ms) {
    if (wrapperData->isSleepOutputEnabled) {
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
            "    Sleep: sleep %dms", ms);
    }
    
    Sleep(ms);
    
    if (wrapperData->isSleepOutputEnabled) {
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "    Sleep: awake");
    }
}

/**
 * Reports the status of the wrapper to the service manager
 * Possible status values:
 *   WRAPPER_WSTATE_STARTING
 *   WRAPPER_WSTATE_STARTED
 *   WRAPPER_WSTATE_PAUSING
 *   WRAPPER_WSTATE_PAUSED
 *   WRAPPER_WSTATE_CONTINUING
 *   WRAPPER_WSTATE_STOPPING
 *   WRAPPER_WSTATE_STOPPED
 */
void wrapperReportStatus(int useLoggerQueue, int status, int errorCode, int waitHint) {
    int natState;
    char *natStateName;
    static DWORD dwCheckPoint = 1;
    BOOL bResult = TRUE;

    /*
    log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
        "wrapperReportStatus(%d, %d, %d, %d)", useLoggerQueue, status, errorCode, waitHint);
    */

    switch (status) {
    case WRAPPER_WSTATE_STARTING:
        natState = SERVICE_START_PENDING;
        natStateName = "SERVICE_START_PENDING";
        break;
    case WRAPPER_WSTATE_STARTED:
        natState = SERVICE_RUNNING;
        natStateName = "SERVICE_RUNNING";
        break;
    case WRAPPER_WSTATE_PAUSING:
        natState = SERVICE_PAUSE_PENDING;
        natStateName = "SERVICE_PAUSE_PENDING";
        break;
    case WRAPPER_WSTATE_PAUSED:
        natState = SERVICE_PAUSED;
        natStateName = "SERVICE_PAUSED";
        break;
    case WRAPPER_WSTATE_CONTINUING:
        natState = SERVICE_CONTINUE_PENDING;
        natStateName = "SERVICE_CONTINUE_PENDING";
        break;
    case WRAPPER_WSTATE_STOPPING:
        natState = SERVICE_STOP_PENDING;
        natStateName = "SERVICE_STOP_PENDING";
        break;
    case WRAPPER_WSTATE_STOPPED:
        natState = SERVICE_STOPPED;
        natStateName = "SERVICE_STOPPED";
        break;
    default:
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unknown status: %d", status);
        return;
    }

    if (!wrapperData->isConsole) {
        if (natState == SERVICE_START_PENDING) {
            ssStatus.dwControlsAccepted = 0;
        } else {
            ssStatus.dwControlsAccepted = SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN;
            if (wrapperData->ntServicePausable) {
                ssStatus.dwControlsAccepted |= SERVICE_ACCEPT_PAUSE_CONTINUE;
            }
        }

        ssStatus.dwCurrentState = natState;
        if (errorCode == 0) {
            ssStatus.dwWin32ExitCode = NO_ERROR;
            ssStatus.dwServiceSpecificExitCode = 0;
        } else {
            ssStatus.dwWin32ExitCode = ERROR_SERVICE_SPECIFIC_ERROR;
            ssStatus.dwServiceSpecificExitCode = errorCode;
        }
        ssStatus.dwWaitHint = waitHint;

        if ((natState == SERVICE_RUNNING ) || (natState == SERVICE_STOPPED) || (natState == SERVICE_PAUSED)) {
            ssStatus.dwCheckPoint = 0;
        } else {
            ssStatus.dwCheckPoint = dwCheckPoint++;
        }

        if (wrapperData->isStateOutputEnabled) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                "calling SetServiceStatus with status=%s, waitHint=%d, checkPoint=%u, errorCode=%d",
                natStateName, waitHint, dwCheckPoint, errorCode);
        }

        if (!(bResult = SetServiceStatus(sshStatusHandle, &ssStatus))) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "SetServiceStatus failed");
        }
    }
}

/**
 * Read and process any output from the child JVM Process.
 * Most output should be logged to the wrapper log file.
 *
 * This function will only be allowed to run for 250ms before returning.  This is to
 *  make sure that the main loop gets CPU.  If there is more data in the pipe, then
 *  the function returns -1, otherwise 0.  This is a hint to the mail loop not to
 *  sleep.
 */
int wrapperReadChildOutput() {
    DWORD dwRead;
    char *bufferP;
    char *cCR;
    char *cLF;
    char *newBuffer;
    DWORD lineLength;
    DWORD maxRead;
    DWORD keepCnt;
    int thisLF;
    struct timeb timeBuffer;
    time_t startTime;
    int startTimeMillis;
    time_t now;
    int nowMillis;
    time_t durr;


    if (!wrapperChildStdoutRdBuffer) {
        /* Initialize the wrapperChildStdoutRdBuffer.  Set its initial size to the block size + 1.
         *  This is so that we can always add a \0 to the end of it. */
        wrapperChildStdoutRdBuffer = malloc(sizeof(CHAR) * (READ_BUFFER_BLOCK_SIZE + 1));
        if (!wrapperChildStdoutRdBuffer) {
            outOfMemory("WRCO", 1);
            return 0;
        }
        wrapperChildStdoutRdBufferSize = READ_BUFFER_BLOCK_SIZE + 1;
    }

    wrapperGetCurrentTime(&timeBuffer);
    startTime = now = timeBuffer.time;
    startTimeMillis = nowMillis = timeBuffer.millitm;

    /*
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "now=%ld, nowMillis=%d", now, nowMillis);
    */
    
    bufferP = wrapperChildStdoutRdBuffer;
    lineLength = 0;

    /* Loop and read as much input as is available.  When a large amount of output is
     *  being piped from the JVM this can lead to the main event loop not getting any
     *  CPU for an extended period of time.  To avoid that problem, this loop is only
     *  allowed to cycle for 250ms before returning.   Allow a full second if an
     *  incomplete line is being read.  This makes it much less likely that we will
     *  accidentally break a line of output. */
    while((durr = (now - startTime) * 1000 + (nowMillis - startTimeMillis)) < (lineLength > 0 ? 1000 : 250)) {
        /*
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "durr=%ld", durr);
        */
        
        /* Decide how much we are able to read.   We can read up to the end of the
         *  full buffer, but not more than the READ_BUFFER_BLOCK_SIZE at a time.
         *  Always peeking the max will just waste CPU as most lines will be much
         *  shorter. */
        maxRead = wrapperChildStdoutRdBufferSize - (int)(bufferP - wrapperChildStdoutRdBuffer) - 1;
        if (maxRead <= 0 ) {
            /* We are out of buffer space.  The buffer needs to be expanded. */
            /*
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG,
                "Expanding wrapperChildStdoutRdBuffer size from %d to %d bytes.",
                wrapperChildStdoutRdBufferSize, wrapperChildStdoutRdBufferSize + READ_BUFFER_BLOCK_SIZE);
            */
            newBuffer = malloc(sizeof(char) * (wrapperChildStdoutRdBufferSize + READ_BUFFER_BLOCK_SIZE));
            if (!newBuffer) {
                outOfMemory("WRCO", 2);
                return 0;
            }
            strcpy(newBuffer, wrapperChildStdoutRdBuffer);
            bufferP = newBuffer + (bufferP - wrapperChildStdoutRdBuffer);
            free(wrapperChildStdoutRdBuffer);
            wrapperChildStdoutRdBuffer = newBuffer;
            wrapperChildStdoutRdBufferSize += READ_BUFFER_BLOCK_SIZE;
            maxRead = READ_BUFFER_BLOCK_SIZE;
        }
        if (maxRead > READ_BUFFER_BLOCK_SIZE) {
            maxRead = READ_BUFFER_BLOCK_SIZE;
        }
        
        /* Peek at a block of data from the JVM then look for a CR+LF or LF before
         *  actually reading the bytes that make up the line. */
        if (!PeekNamedPipe(wrapperChildStdoutRd, bufferP, maxRead, &dwRead, NULL, NULL)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Failed to peek at output from the JVM: %s", getLastErrorText());
            return 0;
        }

        /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "dwRead=%d", dwRead);*/
        thisLF = 0;
        if (dwRead > 0) {
            /* Additional data was peeked. Terminate it. */
            bufferP[dwRead] = '\0';

            /* Look for a CR and LF in the data.  Normally on Windows, all lines
             *  will end with CR+LF.  Thread dumps and other JVM messages are the
             *  exception.  They end only with LF.  We have to be careful about
             *  how we check blocks of text becase of cases like
             *  "<text>LF<text>CRLF" */
            cCR = strchr(bufferP, (char)0x0d);
            cLF = strchr(bufferP, (char)0x0a);

            if ((cCR != NULL) && ((cLF == NULL) || (cLF > cCR))) {
                /* CR was found.  If both were found then the CR was first. */
                keepCnt = (int)(cCR - bufferP) + 1;
                if (cCR[1] == (char)0x0a) {
                    /* CR+LF found. Read count should include it as well. */
                    keepCnt++;
                    thisLF = 1;
                   /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  CR+LF");*/
                } else if (cCR[1] == '\0') {
                    /* End of buffer, the LF is probably coming later. */
                   /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  CR !");*/
                } else {
                    /* Only found a CR.  Is this possible? */
                    thisLF = 1;
                   /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  CR");*/
                }

                /* Terminate the buffer to just before the CR. */
                cCR[0] = '\0';
                lineLength = (int)(cCR - wrapperChildStdoutRdBuffer);

            } else if (cLF != NULL) {
                /* LF found. */
                keepCnt = (int)(cLF - bufferP) + 1;

                /* Terminate the buffer to just before the LF. */
                cLF[0] = '\0';
                lineLength = (int)(cLF - wrapperChildStdoutRdBuffer);
                thisLF = 1;
              /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  LF");*/
            } else {
                /* Neither CR+LF or LF was found so we need to read another
                 *  block and keep looking. */
                keepCnt = dwRead;
                lineLength += dwRead;
              /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  No LF");*/
            }

            /* Now that we know how much of this block is wanted, actually read it in. */
            if (!ReadFile(wrapperChildStdoutRd, bufferP, keepCnt, &dwRead, NULL)) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                    "Failed to read output from the JVM: %s", getLastErrorText());
                return 0;
            }
            if (dwRead != keepCnt) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                    "Read %d bytes rather than requested %d bytes from JVM output.",
                    dwRead, keepCnt);
                return 0;
            }

            /* Reterminate the string as we have read the LF back in. */
            wrapperChildStdoutRdBuffer[lineLength] = '\0';
            /*
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "lineLength=%d, keepCnt=%d, thisLF=%d", lineLength, keepCnt, thisLF);
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "buffer='%s'", wrapperChildStdoutRdBuffer);
            */
        } else {
            /* Nothing was read, but there is no more data available. */
            if (lineLength > 0) {
                /* We never found the LF, but log the output and remember that fact. */
                wrapperLogChildOutput(wrapperChildStdoutRdBuffer);
                wrapperChildStdoutRdLastLF = 0;
            }
            return 0;
        }

        if (thisLF) {
            /* The line feed was found. */
            if ((lineLength == 0) && (!wrapperChildStdoutRdLastLF)) {
                /* This is just an unread LF from a previous call, so skip it. */
            } else {
                /* Log the line. */
              /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "log '%s'", wrapperChildStdoutRdBuffer);*/
                wrapperLogChildOutput(wrapperChildStdoutRdBuffer);
            }

            /* Reset things to read the next line. */
            bufferP = wrapperChildStdoutRdBuffer;
            lineLength = 0;

            /* Remember that a LF was found. */
            wrapperChildStdoutRdLastLF = 1;
        } else {
            /* Not at the end of the line yet, so prepare to read another block. */
            bufferP = wrapperChildStdoutRdBuffer + lineLength;
        }

        /* Get the time again */
        wrapperGetCurrentTime(&timeBuffer);
        now = timeBuffer.time;
        nowMillis = timeBuffer.millitm;
    }

    /* If we get here then we timed out. */
    if (lineLength > 0) {
        /* We had a partially read line. */
        wrapperLogChildOutput(wrapperChildStdoutRdBuffer);
    }
    return 1;
}

/**
 * Checks on the status of the JVM Process.
 * Returns WRAPPER_PROCESS_UP or WRAPPER_PROCESS_DOWN
 */
int wrapperGetProcessStatus(int useLoggerQueue, DWORD nowTicks, int sigChild) {
    int res;
    DWORD exitCode;
    char *exName;

    switch (WaitForSingleObject(wrapperData->javaProcess, 0)) {
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
        res = WRAPPER_PROCESS_DOWN;

        /* Get the exit code of the process. */
        if (!GetExitCodeProcess(wrapperData->javaProcess, &exitCode)) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                "Critical error: unable to obtain the exit code of the JVM process: %s", getLastErrorText());
            appExit(1);
        }
        
        if (exitCode == STILL_ACTIVE) {
            /* Should never happen, but check for it. */
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                "The JVM returned JVM exit code was STILL_ACTIVE." );
        }
        
        /* If the JVM crashed then GetExitCodeProcess could have returned an uncaught exception. */
        exName = getExceptionName(exitCode);
        if (exName != NULL) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "The JVM process terminated due to an uncaught exception: %s (0x%08x)", exName, exitCode);
            
            /* Reset the exit code as the exeption value will confuse users. */
            exitCode = 1;
        }
        
        wrapperJVMProcessExited(useLoggerQueue, nowTicks, exitCode);
        
        if (!CloseHandle(wrapperData->javaProcess)) {
            log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Failed to close the Java process handle: %s", getLastErrorText());
        }
        wrapperData->javaProcess = NULL;
        wrapperData->javaPID = 0;

        break;

    case WAIT_TIMEOUT:
        res = WRAPPER_PROCESS_UP;
        break;

    default:
        log_printf_queue(useLoggerQueue, WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
            "Critical error: wait for JVM process failed");
        appExit(1);
    }

    return res;
}

/**
 * Launches a JVM process and store it internally
 */
void wrapperExecute() {
    SECURITY_ATTRIBUTES process_attributes;
    STARTUPINFO startup_info; 
    PROCESS_INFORMATION process_info;
    int ret;
    /* Do not show another console for the new process */
    /*int processflags=CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS; */

    /* Create a new process group as part of this console so that signals can */
    /*  be sent to the JVM. */
    DWORD processflags=CREATE_NEW_PROCESS_GROUP;

    /* Do not show another console for the new process, but show its output in the current console. */
    /*int processflags=CREATE_NEW_PROCESS_GROUP; */

    /* Show a console for the new process */
    /*int processflags=CREATE_NEW_PROCESS_GROUP | CREATE_NEW_CONSOLE; */

    char *commandline=NULL;
    char *environment=NULL;
    char *binparam=NULL;
    int char_block_size = 8196;
    int string_size = 0;
    int temp_int = 0;
    char szPath[512];
    char *c;
    char titleBuffer[80];
    int hideConsole;
    int old_umask;

    FILE *pid_fp = NULL;

    /* Reset the exit code when we launch a new JVM. */
    wrapperData->exitCode = 0;

    /* Add the priority class of the new process to the processflags */
    processflags = processflags | wrapperData->ntServicePriorityClass;

    /* Setup the command line */
    commandline = wrapperData->jvmCommand;
    if (wrapperData->commandLogLevel != LEVEL_NONE) {
        log_printf(WRAPPER_SOURCE_WRAPPER, wrapperData->commandLogLevel,
            "command: %s", commandline);
    }
                           
    /* Setup environment. Use parent's for now */
    environment = NULL;

    /* Initialize a SECURITY_ATTRIBUTES for the process attributes of the new process. */
    process_attributes.nLength = sizeof(SECURITY_ATTRIBUTES);
    process_attributes.lpSecurityDescriptor = NULL;
    process_attributes.bInheritHandle = TRUE;

    /* Generate a unique time for the console so we can look for it below. */
    sprintf(titleBuffer, "Wrapper Controlled JVM Console ID %d%d (Do not close)", rand(), rand());

    /* Initialize a STARTUPINFO structure to use for the new process. */
    startup_info.cb=sizeof(STARTUPINFO);
    startup_info.lpReserved=NULL;
    startup_info.lpDesktop=NULL;
    startup_info.lpTitle=titleBuffer;
    startup_info.dwX=0;
    startup_info.dwY=0;
    startup_info.dwXSize=0;
    startup_info.dwYSize=0;
    startup_info.dwXCountChars=0;
    startup_info.dwYCountChars=0;
    startup_info.dwFillAttribute=0;
    
    /* Set the default flags which will not hide any windows opened by the JVM. */
    startup_info.dwFlags=STARTF_USESTDHANDLES;
    startup_info.wShowWindow=0;
    hideConsole = FALSE;
    if (wrapperData->isConsole) {
        /* We are running as a console so no special console handling needs to be done. */
    } else {
        /* Running as a service. */
        if (wrapperData->ntAllocConsole) {
            /* A console was allocated when the service was started so the JVM will not create
             *  its own. */
            if (wrapperData->wrapperConsoleHandle) {
                /* The console exists but is currently hidden. */
                if (!wrapperData->ntHideJVMConsole) {
                    /* In order to support older JVMs we need to show the console when the
                     *  JVM is launched.  We need to remember to hide it below. */
                    showConsoleWindow(wrapperData->wrapperConsoleHandle);
                    hideConsole = TRUE;
                }
            }
        } else {
            /* A console does not yet exist so the JVM will create and display one itself. */
            if (wrapperData->ntHideJVMConsole) {
                /* The console that the JVM creates should be surpressed and never shown.
                 *  JVMs of version 1.4.0 and above will still display a GUI.  But older JVMs
                 *  will not. */
                startup_info.dwFlags=STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES;
                startup_info.wShowWindow=SW_HIDE;
            } else {
                /* The new JVM console should be allowed to be displayed.  But we need to
                 *  remember to hide it below. */
                hideConsole = TRUE;
            }
        }
    }
    
    startup_info.cbReserved2=0;
    startup_info.lpReserved2=NULL;
    startup_info.hStdInput=GetStdHandle(STD_INPUT_HANDLE);
    startup_info.hStdOutput=wrapperChildStdoutWr;
    startup_info.hStdError=wrapperChildStdoutWr;

    /* Initialize a PROCESS_INFORMATION structure to use for the new process */ 
    process_info.hProcess=NULL;
    process_info.hThread=NULL;
    process_info.dwProcessId=0;
    process_info.dwThreadId=0;

    /* Need the directory that this program exists in.  Not the current directory. */
    /*    Note, the current directory when run as an NT service is the windows system directory. */
    /* Get the full path and filename of this program */
    if (GetModuleFileName(NULL, szPath, 512) == 0){
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to launch %s -%s",
                     wrapperData->serviceDisplayName, getLastErrorText());
        wrapperData->javaProcess = NULL;
        return;
    }
    c = strrchr(szPath, '\\');
    if (c == NULL) {
        szPath[0] = '\0';
    } else {
        c[1] = '\0'; /* terminate after the slash */
    }
    
    /* Make sure the log file is closed before the Java process is created.  Failure to do
     *  so will give the Java process a copy of the open file.  This means that this process
     *  will not be able to rename the file even after closing it because it will still be
     *  open in the Java process.  Also set the auto close flag to make sure that other
     *  threads do not reopen the log file as the new process is being created. */
    setLogfileAutoClose(TRUE);
    closeLogfile();

    /* Set the umask of the JVM */
    old_umask = _umask(wrapperData->javaUmask);
    
    /* Create the new process */
    ret=CreateProcess(NULL, 
                      commandline,    /* the command line to start */
                      NULL,           /* process security attributes */
                      NULL,           /* primary thread security attributes */
                      TRUE,           /* handles are inherited */
                      processflags,   /* we specify new process group */
                      environment,    /* use parent's environment */
                      NULL,           /* use the Wrapper's current working directory */
                      &startup_info,  /* STARTUPINFO pointer */
                      &process_info); /* PROCESS_INFORMATION pointer */
    
    /* Restore the umask. */
    _umask(old_umask);
    
    /* As soon as the new process is created, restore the auto close flag. */
    setLogfileAutoClose(wrapperData->logfileInactivityTimeout <= 0);

    /* Check if virtual machine started */
    if (ret==FALSE) {
        int err=GetLastError();
        /* Make sure the process was launched correctly. */
        if (err!=NO_ERROR) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                "Unable to execute Java command.  %s", getLastErrorText());
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "    %s", commandline);
            wrapperData->javaProcess = NULL;
            
            if (err == ERROR_ACCESS_DENIED) {
                if (wrapperData->isAdviserEnabled) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE, "" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "------------------------------------------------------------------------" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "Advice:" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "Access denied errors when attempting to launch the Java process are" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "usually caused by strict access permissions assigned to the directory" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "in which Java is installed." );
                    if (!wrapperData->isConsole) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                            "Unless you have configured the Wrapper to run as a different user with" );
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                            "wrapper.ntservice.account property, the Wrapper and its JVM will be" );
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                            "as the SYSTEM user by default when run as a service." );
                    }
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "------------------------------------------------------------------------" );
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE, "" );
                }
            }
            
            return;
        }
    }

    /* Now check if we have a process handle again for the Swedish WinNT bug */
    if (process_info.hProcess==NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "can not execute \"%s\"", commandline);
        wrapperData->javaProcess = NULL;
        return;
    }

    if (hideConsole) {
        /* Now that the JVM has been launched we need to hide the console that it
         *  is using. */
        if (wrapperData->wrapperConsoleHandle) {
            /* The wrapper's console needs to be hidden. */
            hideConsoleWindow(wrapperData->wrapperConsoleHandle);
        } else {
            /* We need to locate the console that was created by the JVM on launch
             *  and hide it. */
            findAndHideConsoleWindow(titleBuffer);
        }
    }

    if (wrapperData->isDebugging) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "JVM started (PID=%d)", process_info.dwProcessId);
    }

    /* We keep a reference to the process handle, but need to close the thread handle. */
    wrapperData->javaProcess = process_info.hProcess;
    wrapperData->javaPID = process_info.dwProcessId;
    CloseHandle(process_info.hThread);

    /* If a java pid filename is specified then write the pid of the java process. */
    if (wrapperData->javaPidFilename) {
        if (writePidFile(wrapperData->javaPidFilename, wrapperData->javaPID, wrapperData->javaPidFileUmask)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                "Unable to write the Java PID file: %s", wrapperData->javaPidFilename);
        }
    }

    /* If a java id filename is specified then write the id of the java process. */
    if (wrapperData->javaIdFilename) {
        if (writePidFile(wrapperData->javaIdFilename, wrapperData->jvmRestarts, wrapperData->javaIdFileUmask)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_WARN,
                "Unable to write the Java ID file: %s", wrapperData->javaIdFilename);
        }
    }
}

/**
 * Returns a tick count that can be used in combination with the
 *  wrapperGetTickAgeSeconds() function to perform time keeping.
 */
DWORD wrapperGetTicks() {
    if (wrapperData->useSystemTime) {
        /* We want to return a tick count that is based on the current system time. */
        return wrapperGetSystemTicks();

    } else {
        /* Return a snapshot of the current tick count. */
        return timerTicks;
    }
}

/**
 * Outputs a a log entry describing what the memory dump columns are.
 */
void wrapperDumpMemoryBanner() {
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
        "Wrapper memory: PageFaultcount, WorkingSetSize (Peak), QuotaPagePoolUsage (Peak), QuotaNonPagedPoolUsage (Peak), PageFileUsage (Peak)  Java memory: PageFaultcount, WorkingSetSize (Peak), QuotaPagePoolUsage (Peak), QuotaNonPagedPoolUsage (Peak), PageFileUsage (Peak)  System memory: MemoryLoad, Available/PhysicalSize (%%), Available/PageFileSize (%%), Available/VirtualSize (%%), ExtendedVirtualSize");
}

/**
 * Outputs a log entry at regular intervals to track the memory usage of the
 *  Wrapper and its JVM.
 */
void wrapperDumpMemory() {
    PROCESS_MEMORY_COUNTERS wCounters;
    PROCESS_MEMORY_COUNTERS jCounters;
    MEMORYSTATUSEX statex;
    
    if (OptionalGetProcessMemoryInfo) {
        /* Start with the Wrapper process. */
        if (OptionalGetProcessMemoryInfo(wrapperData->wrapperProcess, &wCounters, sizeof(wCounters)) == 0) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Call to GetProcessMemoryInfo failed for Wrapper process %08x: %s",
                wrapperData->wrapperPID, getLastErrorText());
            return;
        }
        
        if (wrapperData->javaProcess != NULL) {
            /* Next the Java process. */
            if (OptionalGetProcessMemoryInfo(wrapperData->javaProcess, &jCounters, sizeof(jCounters)) == 0) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                    "Call to GetProcessMemoryInfo failed for Java process %08x: %s",
                    wrapperData->javaPID, getLastErrorText());
                return;
            }
        } else {
            memset(&jCounters, 0, sizeof(jCounters));
        }

        statex.dwLength = sizeof (statex);
        GlobalMemoryStatusEx(&statex);
        
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
            "Wrapper memory: %lu, %lu (%lu), %lu (%lu), %lu (%lu), %lu (%lu)  Java memory: %lu, %lu (%lu), %lu (%lu), %lu (%lu), %lu (%lu)  System memory: %lu%%, %I64u/%I64u (%u%%), %I64u/%I64u (%u%%), %I64u/%I64u (%u%%), %I64u",
            wCounters.PageFaultCount,
            wCounters.WorkingSetSize, wCounters.PeakWorkingSetSize,
            wCounters.QuotaPagedPoolUsage, wCounters.QuotaPeakPagedPoolUsage,
            wCounters.QuotaNonPagedPoolUsage, wCounters.QuotaPeakNonPagedPoolUsage,
            wCounters.PagefileUsage, wCounters.PeakPagefileUsage,
            jCounters.PageFaultCount,
            jCounters.WorkingSetSize, jCounters.PeakWorkingSetSize,
            jCounters.QuotaPagedPoolUsage, jCounters.QuotaPeakPagedPoolUsage,
            jCounters.QuotaNonPagedPoolUsage, jCounters.QuotaPeakNonPagedPoolUsage,
            jCounters.PagefileUsage, jCounters.PeakPagefileUsage,
            statex.dwMemoryLoad,
            statex.ullAvailPhys,
            statex.ullTotalPhys,
            (int)(100 * statex.ullAvailPhys / statex.ullTotalPhys),
            statex.ullAvailPageFile,
            statex.ullTotalPageFile,
            (int)(100 * statex.ullAvailPageFile / statex.ullTotalPageFile),
            statex.ullAvailVirtual,
            statex.ullTotalVirtual,
            (int)(100 * statex.ullAvailVirtual / statex.ullTotalVirtual),
            statex.ullAvailExtendedVirtual);
    }
}

DWORD filetimeToMS(FILETIME* filetime) {
    LARGE_INTEGER li;
    
    memcpy(&li, filetime, sizeof(li));
    li.QuadPart /= 10000;
    
    return li.LowPart;
}

/**
 * Outputs a log entry at regular intervals to track the CPU usage over each
 *  interval for the Wrapper and its JVM.
 *
 * In order to make sense of the timing values, it is also necessary to see how
 *  far the system performance counter has progressed.  By carefully comparing
 *  these values, it is possible to very accurately calculate the CPU usage over
 *  any period of time.
 */
LONGLONG lastPerformanceCount = 0;
LONGLONG lastWrapperKernelTime = 0;
LONGLONG lastWrapperUserTime = 0;
LONGLONG lastJavaKernelTime = 0;
LONGLONG lastJavaUserTime = 0;
LONGLONG lastIdleKernelTime = 0;
LONGLONG lastIdleUserTime = 0;
void wrapperDumpCPUUsage() {
    LARGE_INTEGER count;
    LARGE_INTEGER frequency;
    LARGE_INTEGER li;
    LONGLONG performanceCount;
    
    FILETIME creationTime;
    FILETIME exitTime;
    FILETIME wKernelTime;
    FILETIME wUserTime;
    FILETIME jKernelTime;
    FILETIME jUserTime;
    
    DWORD wKernelTimeMs; /* Will overflow in 49 days of usage. */
    DWORD wUserTimeMs;
    DWORD wTimeMs;
    DWORD jKernelTimeMs;
    DWORD jUserTimeMs;
    DWORD jTimeMs;
    
    double age;
    double wKernelPercent;
    double wUserPercent;
    double wPercent;
    double jKernelPercent;
    double jUserPercent;
    double jPercent;
    
    if (OptionalGetProcessTimes) {
        if (!QueryPerformanceCounter(&count)) {
            /* no high-resolution performance counter support. */
            return;
        }
        if (!QueryPerformanceFrequency(&frequency)) {
        }
        
        performanceCount = count.QuadPart;
        
        /* Start with the Wrapper process. */
        if (!OptionalGetProcessTimes(wrapperData->wrapperProcess, &creationTime, &exitTime, &wKernelTime, &wUserTime)) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Call to GetProcessTimes failed for Wrapper process %08x: %s",
                wrapperData->wrapperPID, getLastErrorText());
            return;
        }
        
        if (wrapperData->javaProcess != NULL) {
            /* Next the Java process. */
            if (!OptionalGetProcessTimes(wrapperData->javaProcess, &creationTime, &exitTime, &jKernelTime, &jUserTime)) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                    "Call to GetProcessTimes failed for Java process %08x: %s",
                    wrapperData->javaPID, getLastErrorText());
                return;
            }
        } else {
            memset(&jKernelTime, 0, sizeof(jKernelTime));
            memset(&jUserTime, 0, sizeof(jUserTime));
            lastJavaKernelTime = 0;
            lastJavaUserTime = 0;
        }
        
        
        /* Convert the times to ms. */
        wKernelTimeMs = filetimeToMS(&wKernelTime);
        wUserTimeMs = filetimeToMS(&wUserTime);
        wTimeMs = wKernelTimeMs + wUserTimeMs;
        jKernelTimeMs = filetimeToMS(&jKernelTime);
        jUserTimeMs = filetimeToMS(&jUserTime);
        jTimeMs = jKernelTimeMs + jUserTimeMs;
        
        /* Calculate the number of seconds since the last call. */
        age = (double)(performanceCount - lastPerformanceCount) / frequency.QuadPart;
        
        /* Calculate usage percentages. */
        memcpy(&li, &wKernelTime, sizeof(li));
        wKernelPercent = 100.0 * ((li.QuadPart - lastWrapperKernelTime) / 10000000.0) / age;
        lastWrapperKernelTime = li.QuadPart;
        
        memcpy(&li, &wUserTime, sizeof(li));
        wUserPercent = 100.0 * ((li.QuadPart - lastWrapperUserTime) / 10000000.0) / age;
        lastWrapperUserTime = li.QuadPart;
        
        wPercent = wKernelPercent + wUserPercent;
        
        memcpy(&li, &jKernelTime, sizeof(li));
        jKernelPercent = 100.0 * ((li.QuadPart - lastJavaKernelTime) / 10000000.0) / age;
        lastJavaKernelTime = li.QuadPart;
        
        memcpy(&li, &jUserTime, sizeof(li));
        jUserPercent = 100.0 * ((li.QuadPart - lastJavaUserTime) / 10000000.0) / age;
        lastJavaUserTime = li.QuadPart;
        
        jPercent = jKernelPercent + jUserPercent;
        
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
            "Wrapper CPU: kernel %ldms (%5.2f%%), user %ldms (%5.2f%%), total %ldms (%5.2f%%)  Java CPU: kernel %ldms (%5.2f%%), user %ldms (%5.2f%%), total %ldms (%5.2f%%)",
            wKernelTimeMs, wKernelPercent, wUserTimeMs, wUserPercent, wTimeMs, wPercent,
            jKernelTimeMs, jKernelPercent, jUserTimeMs, jUserPercent, jTimeMs, jPercent);
        
        lastPerformanceCount = performanceCount;
    }
}

/******************************************************************************
 * NT Service Methods
 *****************************************************************************/

/**
 * The service control handler is called by the service manager when there are
 *    events for the service.  registered using a call to 
 *    RegisterServiceCtrlHandler in wrapperServiceMain.
 */
VOID WINAPI wrapperServiceControlHandler(DWORD dwCtrlCode) {
    /* Allow for a large integer + \0 */
    char buffer[11];
    
    /* Enclose the contents of this call in a try catch block so we can
     *  display and log useful information should the need arise. */
    __try {
        if (wrapperData->isDebugging) {
            log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "ServiceControlHandler(%d)", dwCtrlCode);
        }
    
        /* This thread appears to always be the same as the main thread.
         *  Just to be safe reregister it. */
        logRegisterThread(WRAPPER_THREAD_MAIN);
        
        /* Forward the control code off to the JVM. */
        sprintf(buffer, "%d", dwCtrlCode);
        wrapperProtocolFunction(TRUE, WRAPPER_MSG_SERVICE_CONTROL_CODE, buffer);
    
        switch(dwCtrlCode) {
        case SERVICE_CONTROL_PAUSE:
            if (wrapperData->isDebugging) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  SERVICE_CONTROL_PAUSE");
            }
    
            /* Tell the wrapper to pause */
            wrapperPauseProcess(TRUE);

            break;
            
        case SERVICE_CONTROL_CONTINUE:
            if (wrapperData->isDebugging) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  SERVICE_CONTROL_CONTINUE");
            }
    
            /* Tell the wrapper to continue */
            wrapperContinueProcess(TRUE);

            break;
            
        case SERVICE_CONTROL_STOP:
            if (wrapperData->isDebugging) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  SERVICE_CONTROL_STOP");
            }
    
            /* Tell the wrapper to shutdown normally */
            wrapperStopProcess(TRUE, 0);
    
            /* To make sure that the JVM will not be restarted for any reason,
             *  start the Wrapper shutdown process as well.
             *  In this case we do not want to allow any exit filters to be used
             *  so setting this here will force the shutdown. */
            if ((wrapperData->wState == WRAPPER_WSTATE_STOPPING) ||
                (wrapperData->wState == WRAPPER_WSTATE_STOPPED)) {
                /* Already stopping. */
            } else {
                wrapperSetWrapperState(TRUE, WRAPPER_WSTATE_STOPPING);
            }
            break;

        case SERVICE_CONTROL_INTERROGATE:
            if (wrapperData->isDebugging) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  SERVICE_CONTROL_INTERROGATE");
            }
            
            /* This case MUST be processed, even though we are not */
            /* obligated to do anything substantial in the process. */
            break;
    
        case SERVICE_CONTROL_SHUTDOWN:
            if (wrapperData->isDebugging) {
                log_printf_queue(TRUE, WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "  SERVICE_CONTROL_SHUTDOWN");
            }
    
            /* Tell the wrapper to shutdown normally */
            wrapperStopProcess(TRUE, 0);
    
            /* To make sure that the JVM will not be restarted for any reason,
             *  start the Wrapper shutdown process as well. */
            if ((wrapperData->wState == WRAPPER_WSTATE_STOPPING) ||
                (wrapperData->wState == WRAPPER_WSTATE_STOPPED)) {
                /* Already stopping. */
            } else {
                wrapperSetWrapperState(TRUE, WRAPPER_WSTATE_STOPPING);
            }
    
            break;
    
        default:
            if ((wrapperData->threadDumpControlCode > 0) && (dwCtrlCode == wrapperData->threadDumpControlCode)) {
                wrapperRequestDumpJVMState(TRUE);
            } else {
                /* Any other cases... */
            }
            break;
        }
    
        /* After invocation of this function, we MUST call the SetServiceStatus */
        /* function, which is accomplished through our ReportStatus function. We */
        /* must do this even if the current status has not changed. */
        wrapperReportStatus(TRUE, wrapperData->wState, 0, 0);
        
    } __except (exceptionFilterFunction(GetExceptionInformation())) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
            "<-- Wrapper Stopping due to error in service control handler.");
        appExit(1);
    }
}

/**
 * The wrapperServiceMain function is the entry point for the NT service.
 *    It is called by the service manager.
 */
void WINAPI wrapperServiceMain(DWORD dwArgc, LPTSTR *lpszArgv) {
    int timeout;

    /* Enclose the contents of this call in a try catch block so we can
     *  display and log useful information should the need arise. */
    __try {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "wrapperServiceMain()");
#endif
    
        /* Immediately register this thread with the logger. */
        logRegisterThread(WRAPPER_THREAD_SRVMAIN);
    
        /* Call RegisterServiceCtrlHandler immediately to register a service control */
        /* handler function. The returned SERVICE_STATUS_HANDLE is saved with global */
        /* scope, and used as a service id in calls to SetServiceStatus. */
        if (!(sshStatusHandle = RegisterServiceCtrlHandler(wrapperData->serviceName, wrapperServiceControlHandler))) {
            goto finally;
        }
    
        /* The global ssStatus SERVICE_STATUS structure contains information about the */
        /* service, and is used throughout the program in calls made to SetStatus through */
        /* the ReportStatus function. */
        ssStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS; 
        ssStatus.dwServiceSpecificExitCode = 0;
    
    
        /* If we could guarantee that all initialization would occur in less than one */
        /* second, we would not have to report our status to the service control manager. */
        /* For good measure, we will assign SERVICE_START_PENDING to the current service */
        /* state and inform the service control manager through our ReportStatus function. */
        if (wrapperData->startupTimeout > 0 ) {
            timeout = wrapperData->startupTimeout * 1000;
        } else {
            timeout = 86400000; // Set infinity at 1 day.
        }
        wrapperReportStatus(FALSE, WRAPPER_WSTATE_STARTING, 0, timeout);
    
        /* Now actually start the service */
        wrapperRunService();
    
 finally:
    
        /* Report that the service has stopped and set the correct exit code. */
        wrapperReportStatus(FALSE, WRAPPER_WSTATE_STOPPED, wrapperData->exitCode, 1000);
        
#ifdef _DEBUG
        /* The following message will not always appear on the screen if the STOPPED
         *  status was set above.  But the code in the appExit function below always
         *  appears to be getting executed.  Looks like some kind of a timing issue. */
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Exiting service process.");
#endif
        
        /* Actually exit the process, returning the current exit code. */
        appExit(wrapperData->exitCode);
        
    } __except (exceptionFilterFunction(GetExceptionInformation())) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
            "<-- Wrapper Stopping due to error in service main.");
        appExit(1);
    }
}

/**
 * Reads a password from the console and then returns it as a malloced string.
 *  This is only called once so the memory can leak.
 */
char *readPassword() {
    char *buffer;
    char c;
    int cnt = 0;
    
    buffer = malloc(sizeof(char) * 65);
    if (!buffer) {
        outOfMemory("RP", 1);
        appExit(0);
        return NULL;
    }
    buffer[0] = 0;
    
    do {
        c = _getch();
        switch (c) {
        case 0x03: /* Ctrl-C */
            printf( "\n" );
            appExit(0);
            break;
            
        case 0x08: /* Backspace */
            if (cnt > 0) {
                printf("%c %c", 0x08, 0x08);
                cnt--;
                buffer[cnt] = 0;
            }
            break;
            
        case 0xffffffe0: /* Arrow key. */
            /* Skip the next character as well. */
            _getch();
            break;
            
        case 0x0d: /* CR */
        case 0x0a: /* LF */
            /* Done */
            break;
            
        default:
            if (cnt < 64) {
                /* For now, ignore any non-standard ascii characters. */
                if ((c >= 0x20) && (c < 0x7f)) {
                    if (wrapperData->ntServicePasswordPromptMask) {
                        printf("*");
                    } else {
                        printf("%c", c);
                    }
                    buffer[cnt] = c;
                    buffer[cnt + 1] = 0;
                    cnt++;
                }
            }
            break;
        }
        /*printf( "(%02x)", c );*/
    } while ((c != 0x0d) && (c != 0x0a));
    printf("\n");
    
    return buffer;
}

/**
 * Install the Wrapper as an NT Service using the information and service
 *  name in the current configuration file.
 *
 * Stores the parameters with the service name so that the wrapper.conf file
 *  can be located at runtime.
 */
int wrapperInstall() {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    DWORD       serviceType;

    char szPath[512];
    char binaryPath[4096];
    int i;
    int result = 0;
    HKEY hKey;
    char regPath[ 1024 ];
    char *ntServicePassword;

    /* Get the full path and filename of this program */
    if (GetModuleFileName(NULL, szPath, 512) == 0){
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to install %s -%s",
                     wrapperData->serviceDisplayName, getLastErrorText());
        return 1;
    }
    
    /* Build a new command line with all of the parameters. */
    binaryPath[0] = '\0';
    
    /* Always start with the full path to the binary. */
    /* If the szPath contains spaces, it needs to be quoted */
    if (strchr(szPath, ' ') == NULL) {
        strcat(binaryPath, szPath);
    } else {
        strcat(binaryPath, "\"");
        strcat(binaryPath, szPath);
        strcat(binaryPath, "\"");
    }

    /* Next write the command to start the service. */
    strcat(binaryPath, " ");
    strcat(binaryPath, "-s");

    /* Third, the configuration file. */
    /* If the wrapperData->configFile contains spaces, it needs to be quoted */
    strcat(binaryPath, " ");
    if (strchr(wrapperData->configFile, ' ') == NULL) {
        strcat(binaryPath, wrapperData->configFile);
    } else {
        strcat(binaryPath, "\"");
        strcat(binaryPath, wrapperData->configFile);
        strcat(binaryPath, "\"");
    }

    /* All other arguments need to be appended as is. */
    for (i = 0; i < wrapperData->argCount; i++) {
        /* For security reasons, skip the wrapper.ntservice.account and
         *  wrapper.ntservice.password properties if they are declared on the
         *  command line.  They will not be needed  once the service is
         *  installed.  Having them in the registry would be an obvious
         *  security leak. */
        if ((strstr(wrapperData->argValues[i], "wrapper.ntservice.account") == NULL) &&
            (strstr(wrapperData->argValues[i], "wrapper.ntservice.password") == NULL)) {
            strcat(binaryPath, " ");

            /* If the argument contains spaces, it needs to be quoted */
            if (strchr(wrapperData->argValues[i], ' ') == NULL) {
                strcat(binaryPath, wrapperData->argValues[i]);
            } else {
                strcat(binaryPath, "\"");
                strcat(binaryPath, wrapperData->argValues[i]);
                strcat(binaryPath, "\"");
            }
        }
    }

    if (wrapperData->isDebugging) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Service command: %s", binaryPath);
    }
    
    if (wrapperData->ntServiceAccount && wrapperData->ntServicePasswordPrompt) {
        /* Prompt the user for a password. */
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Prompting for account password...");
        printf("Please input the password for account '%s': ", wrapperData->ntServiceAccount);
        wrapperData->ntServicePassword = readPassword();
#ifdef _DEBUG
        printf("Password=[%s]\n", wrapperData->ntServicePassword);
#endif
    }

    /* Decide on the service type */
    if ( wrapperData->ntServiceInteractive ) {
        serviceType = SERVICE_WIN32_OWN_PROCESS | SERVICE_INTERACTIVE_PROCESS;
    } else {
        serviceType = SERVICE_WIN32_OWN_PROCESS;
    }

    /* Next, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    
    if (schSCManager) {
        /* Make sure that an empty length password is null. */
        ntServicePassword = wrapperData->ntServicePassword;
        if ((ntServicePassword != NULL) && (strlen(ntServicePassword) <= 0)) {
            ntServicePassword = NULL;
        }
        
        schService = CreateService(
                                   schSCManager,                       /* SCManager database */
                                   wrapperData->serviceName,           /* name of service */
                                   wrapperData->serviceDisplayName,    /* name to display */
                                   SERVICE_ALL_ACCESS,                 /* desired access */
                                   serviceType,                        /* service type */
                                   wrapperData->ntServiceStartType,    /* start type */
                                   SERVICE_ERROR_NORMAL,               /* error control type */
                                   binaryPath,                         /* service's binary */
                                   wrapperData->ntServiceLoadOrderGroup, /* load ordering group */
                                   NULL,                               /* tag identifier not used because they are used for driver level services. */
                                   wrapperData->ntServiceDependencies, /* dependencies */
                                   wrapperData->ntServiceAccount,      /* LocalSystem account if NULL */
                                   ntServicePassword );                /* NULL or empty for no password */
        
        if (schService) {
            /* Have the service, add a description to the registry. */
            sprintf(regPath, "SYSTEM\\CurrentControlSet\\Services\\%s", wrapperData->serviceName);
            if ((wrapperData->serviceDescription != NULL && strlen(wrapperData->serviceDescription) > 0)
                && (RegOpenKeyEx(HKEY_LOCAL_MACHINE, regPath, 0, KEY_WRITE, (PHKEY) &hKey) == ERROR_SUCCESS)) {
                
                /* Set Description key in registry */
                RegSetValueEx(hKey, "Description", (DWORD) 0, (DWORD) REG_SZ,
                    (const unsigned char *)wrapperData->serviceDescription,
                    (int)(strlen(wrapperData->serviceDescription) + 1));
                RegCloseKey(hKey);
            }
            
            /* Service was installed. */
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s installed.",
                wrapperData->serviceDisplayName);

            /* Close the handle to this service object */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "CreateService failed - %s",
                getLastErrorText());
            result = 1;
        }

        /* Close the handle to the service control manager database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    return result;
}

/**
 * Sets any environment variables stored in the system registry to the current
 *  environment.  The NT service environment only has access to the environment
 *  variables set when the machine was last rebooted.  This makes it possible
 *  to access the latest values in registry without a reboot.
 *
 * Note that this function is always called before the configuration file has
 *  been loaded this means that any logging that takes place will be sent to
 *  the default log file which may be difficult for the user to locate.
 *
 * Return TRUE if there were any problems.
 */
int wrapperLoadEnvFromRegistryInner(HKEY baseHKey, const char *regPath, int appendPath) {
    int envCount = 0;
    int ret;
    HKEY hKey;
    DWORD dwIndex;
    DWORD valueCount;
    DWORD maxValueNameLength;
    DWORD maxValueLength;
    char *valueName;
    char *value;
    LONG err;
    DWORD thisValueNameLength;
    DWORD thisValueLength;
    DWORD thisValueType;
    const char *oldVal;
    char *newVal;
    BOOL expanded;

    /* NOTE - Any log output here will be placed in the default log file as it happens
     *        before the wrapper.conf is loaded. */

    /* Open the registry entry where the current environment variables are stored. */
    if (RegOpenKeyEx(baseHKey, regPath, 0, KEY_ENUMERATE_SUB_KEYS | KEY_QUERY_VALUE, (PHKEY) &hKey) == ERROR_SUCCESS) {
        /* Read in each of the environment variables and set them into the environment.
         *  These values will be set as is without doing any environment variable
         *  expansion.  In order for the ExpandEnvironmentStrings function to work all
         *  of the environment variables to be replaced must already be set.  To handle
         *  this, after we set the values as is from the registry, we need to go back
         *  through all the ones we set and Expand them if necessary. */

        /* Query the registry to find out how many values there are as well as info about how
         *  large the values names and data are. */
        err = RegQueryInfoKey(hKey, NULL, NULL, NULL, NULL, NULL, NULL, &valueCount, &maxValueNameLength, &maxValueLength, NULL, NULL);
        if ( err != ERROR_SUCCESS) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to query the registry to get the environment: %d : %s", err, getLastErrorText());
            RegCloseKey(hKey);
            return TRUE;
        }

#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Registry contains %d variables.  Longest name=%d, longest value=%d", valueCount, maxValueNameLength, maxValueLength);
#endif
        /* Add space for the null. */
        maxValueNameLength++;
        maxValueLength++;

        /* Allocate buffers to get the value names and values from the registry.  These can
         *  be reused because we are using the setEnv function to store the values into the
         *  environment.  setEnv allocates the memory required by the environment. */
        valueName = malloc(maxValueNameLength);
        if (!valueName) {
            outOfMemory("WLEFRI", 1);
            RegCloseKey(hKey);
            return TRUE;
        }
        value = malloc(maxValueLength);
        if (!valueName) {
            outOfMemory("WLEFRI", 2);
            RegCloseKey(hKey);
            return TRUE;
        }

        /* Loop over the values and load each of them into the local environment as is. */
        dwIndex = 0;
        do {
            thisValueNameLength = maxValueNameLength;
            thisValueLength = maxValueLength;

            err = RegEnumValue(hKey, dwIndex, valueName, &thisValueNameLength, NULL, &thisValueType, value, &thisValueLength);
            if (err == ERROR_SUCCESS) {
                if ((thisValueType == REG_SZ) || (thisValueType = REG_EXPAND_SZ))  {
                    /* Got a value. */
#ifdef _DEBUG
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Loaded var name=\"%s\", value=\"%s\"", valueName, value);
#endif
                    if (appendPath && (strcmpIgnoreCase("path", valueName) == 0)) {
                        /* The PATH variable is special, it needs to be appended to the existing value. */
                        oldVal = getenv("PATH");
                        if (oldVal) {
                            newVal = malloc(strlen(oldVal) + 1 + strlen(value) + 1);
                            if (!newVal) {
                                outOfMemory("WLEFRI", 3);
                                RegCloseKey(hKey);
                                return TRUE;
                            }
                            sprintf(newVal, "%s;%s", oldVal, value);
                            if (setEnv(valueName, newVal)) {
                                /* Already reported. */
                                free(newVal);
                                RegCloseKey(hKey);
                                return TRUE;
                            }
#ifdef _DEBUG
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "    Appended to existing value: %s=%s", valueName, newVal);
#endif
                            free(newVal);
                        } else {
                            /* Did not exist, set normally. */
                            if (setEnv(valueName, value)) {
                                /* Already reported. */
                                RegCloseKey(hKey);
                                return TRUE;
                            }
                        }
                    } else {
                        if (setEnv(valueName, value)) {
                            /* Already reported. */
                            RegCloseKey(hKey);
                            return TRUE;
                        }
                    }
#ifdef _DEBUG
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Set to local environment.");
#endif
                } else {
#ifdef _DEBUG
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Loaded var name=\"%s\" but type is invalid: %d, skipping.", valueName, thisValueType);
#endif
                }
            } else if (err = ERROR_NO_MORE_ITEMS) {
                /* This means we are at the end.  Fall through. */
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to query the registry to get environment variable #%d: %d : %s", dwIndex, err, getLastErrorText());
                RegCloseKey(hKey);
                return TRUE;
            }

            dwIndex++;
        } while (err != ERROR_NO_MORE_ITEMS);

#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "All environment variables loaded.  Loop back over them to evaluate any nested variables.");
#endif
        /* Go back and loop over the environment variables we just set and expand any
         *  variables which contain % characters. Loop until we make a pass which does
         *  not perform any replacements. */
        do {
            expanded = FALSE;

            dwIndex = 0;
            do {
                thisValueNameLength = maxValueNameLength;
                err = RegEnumValue(hKey, dwIndex, valueName, &thisValueNameLength, NULL, &thisValueType, NULL, NULL);
                if (err == ERROR_SUCCESS) {
                    /* Found an environment variable in the registry.  Variables that contain references have a different type. */
                    if (thisValueType = REG_EXPAND_SZ)  {
#ifdef _DEBUG
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Get the current local value of variable \"%s\"", valueName);
#endif
                        oldVal = getenv(valueName);
                        if (oldVal == NULL) {
#ifdef _DEBUG
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  The current local value of variable \"%s\" is null, meaning it was not in the registry.  Skipping.", valueName);
#endif
                        } else {
#ifdef _DEBUG
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "     \"%s\"=\"%s\"", valueName, oldVal);
#endif
                            if (strchr(oldVal, '%')) {
                                /* This variable contains tokens which need to be expanded. */
                                /* Find out how much space is required to store the expanded value. */
                                ret = ExpandEnvironmentStrings(oldVal, NULL, 0);
                                if (ret == 0) {
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to expand \"%s\": %s", valueName, getLastErrorText());
                                    RegCloseKey(hKey);
                                    return TRUE;
                                }

                                /* Allocate a buffer to hold to the expanded value. */
                                newVal = malloc(ret + 2);
                                if (!newVal) {
                                    outOfMemory("WLEFRI", 4);
                                    RegCloseKey(hKey);
                                    return TRUE;
                                }

                                /* Actually expand the variable. */
                                ret = ExpandEnvironmentStrings(oldVal, newVal, ret);
                                if (ret == 0) {
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to expand \"%s\" (2): %s", valueName, getLastErrorText());
                                    free(newVal);
                                    RegCloseKey(hKey);
                                    return TRUE;
                                }

                                /* Was anything changed? */
                                if (strcmp(oldVal, newVal) == 0) {
#ifdef _DEBUG
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "       Value unchanged.  Referenced environment variable not set.");
#endif
                                } else {
                                    /* Set the expanded environment variable */
                                    expanded = TRUE;
#ifdef _DEBUG
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Update local environment variable.  \"%s\"=\"%s\"", valueName, newVal);
#endif

                                    /* Update the environment. */
                                    if (setEnv(valueName, newVal)) {
                                        /* Already reported. */
                                        free(newVal);
                                        RegCloseKey(hKey);
                                        return TRUE;
                                    }
                                }

                                free(newVal);
                            }
                        }
                    }
                } else if (err == ERROR_NO_MORE_ITEMS) {
                    /* No more environment variables. */
                } else {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to read registry - %s", getLastErrorText());
                    RegCloseKey(hKey);
                    return TRUE;
                }
                dwIndex++;
            } while (err != ERROR_NO_MORE_ITEMS);

#ifdef _DEBUG
            if (expanded) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Rescan environment variables to varify that there are no more expansions necessary.");
            }
#endif
        } while (expanded);

#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Done loading environment variables.");
#endif

        /* Close the registry entry */
        RegCloseKey(hKey);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to access registry to obtain environment variables - %s", getLastErrorText());
        return TRUE;
    }

    return FALSE;
}

int wrapperLoadEnvFromRegistry() {
    /* Always load in the system wide variables. */
#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Loading System environment variables from Registry:");
#endif

    if (wrapperLoadEnvFromRegistryInner(HKEY_LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment\\", FALSE))
    {
        return 1;
    }

    /* Only load in the user specific variables if the USERNAME environment variable is set. */
    if (getenv("USERNAME")) {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Loading Account environment variables from Registry:");
#endif

        if (wrapperLoadEnvFromRegistryInner(HKEY_CURRENT_USER, "Environment\\", TRUE))
        {
            return 1;
        }
    }

    return 0;
}

/**
 * Gets the JavaHome absolute path from the windows registry
 */
int wrapperGetJavaHomeFromWindowsRegistry(char *javaHome) {
    const char *prop;
    char *c;
    char subKey[512];       /* Registry subkey that jvm creates when is installed */
    char *valueKey;
    char jreversion[10];    /* Will receive a registry value that has jvm version */
    HKEY baseHKey;
    HKEY openHKey = NULL; /* Will receive the handle to the opened registry key */
    DWORD valueType;
    DWORD valueSize;
    char *value;

    prop = getStringProperty(properties, "wrapper.registry.java_home", NULL);
    if (prop) {
        /* A registry location was specified. */
        if (strstr(prop, "HKEY_CLASSES_ROOT\\") == prop) {
            baseHKey = HKEY_CLASSES_ROOT;
            strcpy(subKey, prop + 18);
        } else if (strstr(prop, "HKEY_CURRENT_CONFIG\\") == prop) {
            baseHKey = HKEY_CURRENT_USER;
            strcpy(subKey, prop + 20);
        } else if (strstr(prop, "HKEY_CURRENT_USER\\") == prop) {
            baseHKey = HKEY_CURRENT_USER;
            strcpy(subKey, prop + 18);
        } else if (strstr(prop, "HKEY_LOCAL_MACHINE\\") == prop) {
            baseHKey = HKEY_LOCAL_MACHINE;
            strcpy(subKey, prop + 19);
        } else if (strstr(prop, "HKEY_USERS\\") == prop) {
            baseHKey = HKEY_USERS;
            strcpy(subKey, prop + 11);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "wrapper.registry.java_home does not begin with a known root key: %s", prop);
            return 0;
        }

        /* log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "subKey=%s", subKey); */

        /* We need to split the value from the key.  Find the last \ */
        c = strrchr(subKey, '\\');
        if (!c) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "wrapper.registry.java_home is an invalid key: %s", prop);
            return 0;
        }
        valueKey = c + 1;
        /* Truncate the subKey. */
        *c = '\0';

        /*log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "subKey=%s valueKey=%s", subKey, valueKey); */

        /*
         * Opens the Registry Key needed to query the jvm version
         */
        if (RegOpenKeyEx(baseHKey, subKey, 0, KEY_QUERY_VALUE, &openHKey) != ERROR_SUCCESS) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Unable to access configured registry location for JAVA_HOME: %s - (%d)", subKey, errno);
            return 0;
        }

        if (RegQueryValueEx(openHKey, valueKey, NULL, &valueType, NULL, &valueSize) != ERROR_SUCCESS) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Unable to access configured registry location for JAVA_HOME: %s - (%d)", prop, errno);
            RegCloseKey(openHKey);
            return 0;
        }
        if (valueType != REG_SZ) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Configured JAVA_HOME registry location is not of type REG_SZ: %s", prop);
            RegCloseKey(openHKey);
            return 0;
        }
        value = malloc(valueSize);
        if (!value) {
            outOfMemory("WGJFWR", 1);
            RegCloseKey(openHKey);
            return 0;
        }
        if (RegQueryValueEx(openHKey, valueKey, NULL, &valueType, value, &valueSize) != ERROR_SUCCESS) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Unable to access configured registry location %s - (%d)", prop, errno);
            free(value);
            RegCloseKey(openHKey);
            return 0;
        }

        RegCloseKey(openHKey);

        /* Returns the JavaHome path */
        strcpy(javaHome, value);

        free(value);
        return 1;
    } else {
        /* Look for the java_home in the default location. */

        /* SubKey containing the jvm version */
        strcpy(subKey, "SOFTWARE\\JavaSoft\\Java Runtime Environment");

        /*
         * Opens the Registry Key needed to query the jvm version
         */
        if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey, 0, KEY_QUERY_VALUE, &openHKey) != ERROR_SUCCESS) {
            return 0;
        }

        /*
         * Queries for the jvm version
         */

        valueSize = sizeof(jreversion);
        if (RegQueryValueEx(openHKey, "CurrentVersion", NULL, &valueType, jreversion, &valueSize) != ERROR_SUCCESS) {
            RegCloseKey(openHKey);
            return 0;
        }

        RegCloseKey(openHKey);


        /* adds the jvm version to the subkey */
        strcat(subKey, "\\");
        strcat(subKey, jreversion);

        /*
         * Opens the Registry Key needed to query the JavaHome
         */
        if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey, 0, KEY_QUERY_VALUE, &openHKey) != ERROR_SUCCESS) {
            return 0;
        }

        /*
         * Queries for the JavaHome
         */
        if (RegQueryValueEx(openHKey, "JavaHome", NULL, &valueType, NULL, &valueSize) != ERROR_SUCCESS) {
            RegCloseKey(openHKey);
            return 0;
        }
        value = malloc(valueSize);
        if (!value) {
            outOfMemory("WGJFWR", 2);
            RegCloseKey(openHKey);
            return 0;
        }
        if (RegQueryValueEx(openHKey, "JavaHome", NULL, &valueType, value, &valueSize) != ERROR_SUCCESS) {
            RegCloseKey(openHKey);
            return 0;
        }

        RegCloseKey(openHKey);

        /* Returns the JavaHome path */
        strcpy(javaHome, value);
        free(value);

        return 1;
    }
}

char *getNTServiceStatusName(int status)
{
    char *name;
    switch(status) {
    case SERVICE_STOPPED:
        name = "STOPPED";
        break;
    case SERVICE_START_PENDING:
        name = "START_PENDING";
        break;
    case SERVICE_STOP_PENDING:
        name = "STOP_PENDING";
        break;
    case SERVICE_RUNNING:
        name = "RUNNING";
        break;
    case SERVICE_CONTINUE_PENDING:
        name = "CONTINUE_PENDING";
        break;
    case SERVICE_PAUSE_PENDING:
        name = "PAUSE_PENDING";
        break;
    case SERVICE_PAUSED:
        name = "PAUSED";
        break;
    default:
        name = "UNKNOWN";
        break;
    }
    return name;
}

/** Starts a Wrapper instance running as an NT Service. */
int wrapperStartService() {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;

    char *status;
    int msgCntr;
    int stopping;
    int result = 0;

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager){
        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService){
            /* Make sure that the service is not already running. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    /* The service is stopped, so try starting it. */
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Starting the %s service...",
                        wrapperData->serviceDisplayName);
                    if (StartService( schService, 0, NULL )) {
                        /* We will get here immediately if the service process was launched.
                         *  We still need to wait for it to actually start. */
                        msgCntr = 0;
                        stopping = FALSE;
                        do {
                            if ( QueryServiceStatus(schService, &serviceStatus)) {
                                if (serviceStatus.dwCurrentState == SERVICE_STOP_PENDING) {
                                    if (!stopping) {
                                        stopping = TRUE;
                                        msgCntr = 5; /* Trigger a message */
                                    }
                                    if (msgCntr >= 5) {
                                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "Stopping...");
                                        msgCntr = 0;
                                    }
                                } else {
                                    if (msgCntr >= 5) {
                                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "Waiting to start...");
                                        msgCntr = 0;
                                    }
                                }
                                wrapperSleep(FALSE, 1000);
                                msgCntr++;
                            } else {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                                    "Unable to query the status of the %s service - %s",
                                    wrapperData->serviceDisplayName, getLastErrorText());
                                result = 1;
                                break;
                            }
                        } while ((serviceStatus.dwCurrentState != SERVICE_STOPPED)
                            && (serviceStatus.dwCurrentState != SERVICE_RUNNING)
                            && (serviceStatus.dwCurrentState != SERVICE_PAUSED));

                        /* Was the service started? */
                        if (serviceStatus.dwCurrentState == SERVICE_RUNNING) {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s started.", wrapperData->serviceDisplayName);
                        } else if (serviceStatus.dwCurrentState == SERVICE_PAUSED) {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s started but immediately paused..", wrapperData->serviceDisplayName);
                        } else {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service was launched, but failed to start.",
                                wrapperData->serviceDisplayName);
                            result = 1;
                        }
                    } else {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to start the service - %s",
                            getLastErrorText());
                        result = 1;
                    }
                } else {
                    status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is already running with status: %s",
                        wrapperData->serviceDisplayName, status);
                    result = 1;
                }
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    return result;
}

/** Stops a Wrapper instance running as an NT Service. */
int wrapperStopService(int command) {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;

    char *status;
    int msgCntr;
    int result = 0;

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager){

        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService){
            /* Find out what the current status of the service is so we can decide what to do. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    if (command) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "The %s service was not running.",
                            wrapperData->serviceDisplayName);
                    }
                } else {
                    if (serviceStatus.dwCurrentState == SERVICE_STOP_PENDING) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                            "The %s service was already in the process of stopping.",
                            wrapperData->serviceDisplayName);
                    } else {
                        /* Stop the service. */
                        if (ControlService( schService, SERVICE_CONTROL_STOP, &serviceStatus)){
                            if ( command ) {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Stopping the %s service...",
                                    wrapperData->serviceDisplayName);
                            } else {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Service is running.  Stopping it...");
                            }
                        } else {
                            if (serviceStatus.dwCurrentState == SERVICE_START_PENDING) {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                    "The %s service was in the process of starting.  Stopping it...",
                                    wrapperData->serviceDisplayName);
                            } else {
                                status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                                    "Attempt to stop the %s service failed.  Status: %s",
                                    wrapperData->serviceDisplayName, status);
                                result = 1;
                            }
                        }
                    }
                    if (result == 0) {
                        /* Wait for the service to stop. */
                        msgCntr = 0;
                        do {
                            if ( QueryServiceStatus(schService, &serviceStatus)) {
                                if (msgCntr >= 5) {
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "Waiting to stop...");
                                    msgCntr = 0;
                                }
                                wrapperSleep(FALSE, 1000);
                                msgCntr++;
                            } else {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                                    "Unable to query the status of the %s service - %s",
                                    wrapperData->serviceDisplayName, getLastErrorText());
                                result = 1;
                                break;
                            }
                        } while (serviceStatus.dwCurrentState != SERVICE_STOPPED);

                        if ( serviceStatus.dwCurrentState == SERVICE_STOPPED ) {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s stopped.", wrapperData->serviceDisplayName);
                        } else {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "%s failed to stop.", wrapperData->serviceDisplayName);
                            result = 1;
                        }
                    }
                }
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    return result;
}

/** Pauses a Wrapper instance running as an NT Service. */
int wrapperPauseService() {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;

    char *status;
    int msgCntr;
    int result = 0;

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager) {
        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService) {
            /* Make sure that the service is in a state that can be paused. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "The %s service was not running.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_STOP_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of stopping.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_PAUSED) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was already paused.",
                        wrapperData->serviceDisplayName);
                } else if (serviceStatus.dwCurrentState == SERVICE_PAUSE_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of being paused.",
                        wrapperData->serviceDisplayName);
                } else {
                    /* The service is started, starting, or continuing, so try pausing it. */
                    if (ControlService( schService, SERVICE_CONTROL_PAUSE, &serviceStatus)){
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Pausing the %s service...",
                            wrapperData->serviceDisplayName);
                    } else {
                        status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                            "Attempt to pause the %s service failed.  Status: %s",
                            wrapperData->serviceDisplayName, status);
                        result = 1;
                    }
                }
                if (result == 0) {
                    /* Wait for the service to pause. */
                    msgCntr = 0;
                    do {
                        if ( QueryServiceStatus(schService, &serviceStatus)) {
                            if (msgCntr >= 5) {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "Waiting to pause...");
                                msgCntr = 0;
                            }
                            wrapperSleep(FALSE, 1000);
                            msgCntr++;
                        } else {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                                "Unable to query the status of the %s service - %s",
                                wrapperData->serviceDisplayName, getLastErrorText());
                            result = 1;
                            break;
                        }
                    } while (!((serviceStatus.dwCurrentState == SERVICE_PAUSED) || (serviceStatus.dwCurrentState == SERVICE_STOPPED)));

                    if ( serviceStatus.dwCurrentState == SERVICE_PAUSED ) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s paused.", wrapperData->serviceDisplayName);
                    } else {
                        status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                            "%s failed to pause.  Status: %s",
                            wrapperData->serviceDisplayName, status);
                        result = 1;
                    }
                }
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    return result;
}

/** Resume a Wrapper instance running as an NT Service. */
int wrapperContinueService() {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;

    char *status;
    int msgCntr;
    int result = 0;

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager) {
        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService) {
            /* Make sure that the service is in a state that can be resumed. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "The %s service was not running.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_STOP_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of stopping.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_PAUSE_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of being paused.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_CONTINUE_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of being resumed.",
                        wrapperData->serviceDisplayName);
                } else if (serviceStatus.dwCurrentState == SERVICE_RUNNING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was already started.",
                        wrapperData->serviceDisplayName);
                } else {
                    /* The service is paused, so try resuming it. */
                    if (ControlService( schService, SERVICE_CONTROL_CONTINUE, &serviceStatus)){
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Resuming the %s service...",
                            wrapperData->serviceDisplayName);
                    } else {
                        status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                            "Attempt to resume the %s service failed.  Status: %s",
                            wrapperData->serviceDisplayName, status);
                        result = 1;
                    }
                }
                if (result == 0) {
                    /* Wait for the service to resume. */
                    msgCntr = 0;
                    do {
                        if ( QueryServiceStatus(schService, &serviceStatus)) {
                            if (msgCntr >= 5) {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_INFO, "Waiting to resume...");
                                msgCntr = 0;
                            }
                            wrapperSleep(FALSE, 1000);
                            msgCntr++;
                        } else {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                                "Unable to query the status of the %s service - %s",
                                wrapperData->serviceDisplayName, getLastErrorText());
                            result = 1;
                            break;
                        }
                    } while (!((serviceStatus.dwCurrentState == SERVICE_RUNNING) || (serviceStatus.dwCurrentState == SERVICE_STOPPED)));

                    if ( serviceStatus.dwCurrentState == SERVICE_RUNNING ) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s resumed.", wrapperData->serviceDisplayName);
                    } else {
                        status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                            "%s failed to resume.  Status: %s",
                            wrapperData->serviceDisplayName, status);
                        result = 1;
                    }
                }
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    return result;
}

int sendServiceControlCodeInner(int controlCode) {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;
    char *status;
    int result = 0;

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager) {
        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService) {
            /* Make sure that the service is in a state that can be resumed. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "The %s service was not running.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_STOP_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of stopping.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_PAUSED) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was currently paused.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_PAUSE_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of being paused.",
                        wrapperData->serviceDisplayName);
                    result = 1;
                } else if (serviceStatus.dwCurrentState == SERVICE_CONTINUE_PENDING) {
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                        "The %s service was in the process of being resumed.",
                        wrapperData->serviceDisplayName);
                } else {
                    /* The service is running, so try sending the code. */
                    if (ControlService( schService, controlCode, &serviceStatus)){
                        result = 0;
                    } else {
                        status = getNTServiceStatusName(serviceStatus.dwCurrentState);
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                            "Attempt to send the %s service control code %d failed.  Status: %s",
                            wrapperData->serviceDisplayName, controlCode, status);
                        result = 1;
                    }
                }
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }
    
    return result;
}

/** Sends a service control code to a running as an NT Service. */
int wrapperSendServiceControlCode(char **argv, char *controlCodeS) {
    int controlCode;
    int result;
    
    /* Make sure the control code is valid. */
    if (controlCodeS == NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Control code to send is missing.");
        wrapperUsage(argv[0]);
        return 1;
    }
    controlCode = atoi(controlCodeS);
    if ((controlCode < 128) || (controlCode > 255)) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The service control code must be in the range 128-255.");
        return 1;
    }
    
    result = sendServiceControlCodeInner(controlCode);
    if (!result) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Sent the %s service control code %d.",
            wrapperData->serviceDisplayName, controlCode);
    }

    return result;
}

/**
 * Requests that the Wrapper perform a thread dump.
 */
int wrapperRequestThreadDump() {
    int result;
    
    if (wrapperData->threadDumpControlCode <= 0) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The thread dump control code is disabled.");
        return 1;
    }
    
    result = sendServiceControlCodeInner(wrapperData->threadDumpControlCode);
    if (!result) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Requested that the %s service perform a thread dump.",
            wrapperData->serviceDisplayName);
    }

    return result;
}

/**
 * Obtains the current service status.
 * The returned result becomes the exitCode.  The exitCode is made up of
 *  a series of status bits:
 *
 * Bits:
 * 0: Service Installed. (1)
 * 1: Service Running. (2)
 * 2: Service Interactive. (4)
 * 3: Startup Mode: Auto. (8)
 * 4: Startup Mode: Manual. (16)
 * 5: Startup Mode: Disabled. (32)
 * 6: Service Running but Paused. (64)
 */
int wrapperServiceStatus(int consoleOutput) {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;
    SERVICE_STATUS serviceStatus;
    QUERY_SERVICE_CONFIG *pQueryServiceConfig;
    DWORD reqSize;

    int result = 0;
    
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager){

        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService){
            /* Service is installed, so set that bit. */
            if (consoleOutput) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "The %s Service is installed.", wrapperData->serviceDisplayName);
            }
            result |= 1;
            
            /* Get the service configuration. */
            QueryServiceConfig(schService, NULL, 0, &reqSize);
            pQueryServiceConfig = malloc(reqSize);
            if (!pQueryServiceConfig) {
                outOfMemory("WSS", 1);
                CloseServiceHandle(schSCManager);
                return 0;
            }
            if (QueryServiceConfig(schService, pQueryServiceConfig, reqSize, &reqSize)) {
                switch (pQueryServiceConfig->dwStartType) {
                case SERVICE_BOOT_START:   /* Possible? */
                case SERVICE_SYSTEM_START: /* Possible? */
                case SERVICE_AUTO_START:
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Start Type: Automatic");
                    }
                    result |= 8;
                    break;
                    
                case SERVICE_DEMAND_START:
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Start Type: Manual");
                    }
                    result |= 16;
                    break;
                    
                case SERVICE_DISABLED:
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Start Type: Disabled");
                    }
                    result |= 32;
                    break;
                    
                default:
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "  Start Type: Unknown");
                    }
                    break;
                }
                
                if (pQueryServiceConfig->dwServiceType & SERVICE_INTERACTIVE_PROCESS) {
                    /* This is an interactive service, so set that bit. */
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Interactive: Yes");
                    }
                    result |= 4;
                } else {
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Interactive: No");
                    }
                }
                
                free(pQueryServiceConfig);
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the configuration of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
            }
            
            /* Find out what the current status of the service is so we can decide what to do. */
            if (QueryServiceStatus(schService, &serviceStatus)) {
                if (serviceStatus.dwCurrentState == SERVICE_STOPPED) {
                    /* The service is stopped. */
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Running: No");
                    }
                } else {
                    /* Any other state, it is running. Set that bit. */
                    if (consoleOutput) {
                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Running: Yes");
                    }
                    result |= 2;
                    
                    if (serviceStatus.dwCurrentState == SERVICE_PAUSED) {
                        if (consoleOutput) {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  Paused: Yes");
                        }
                        result |= 64;
                    }
                }
                
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to query the status of the %s service - %s",
                    wrapperData->serviceDisplayName, getLastErrorText());
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            /* Could not open the service.  This means that it is not installed. */
            if (consoleOutput) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "The %s Service is not installed.", wrapperData->serviceDisplayName);
            }
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
    }

    return result;
}

/**
 * Uninstall the service and clean up
 */
int wrapperRemove() {
    SC_HANDLE   schService;
    SC_HANDLE   schSCManager;

    int result = 0;

    /* First attempt to stop the service if it is already running. */
    result = wrapperStopService( FALSE );
    if ( result )
    {
        /* There was a problem stopping the service. */
        return result;
    }

    /* First, get a handle to the service control manager */
    schSCManager = OpenSCManager(
                                 NULL,                   
                                 NULL,                   
                                 SC_MANAGER_ALL_ACCESS   
                                 );
    if (schSCManager){

        /* Next get the handle to this service... */
        schService = OpenService(schSCManager, wrapperData->serviceName, SERVICE_ALL_ACCESS);

        if (schService){
            /* Now try to remove the service... */
            if (DeleteService(schService)) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "%s removed.", wrapperData->serviceDisplayName);
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "DeleteService failed - %s", getLastErrorText());
                result = 1;
            }
            
            /* Close this service object's handle to the service control manager */
            CloseServiceHandle(schService);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "The %s service is not installed - %s",
                wrapperData->serviceName, getLastErrorText());
            result = 1;
        }
        
        /* Finally, close the handle to the service control manager's database */
        CloseServiceHandle(schSCManager);
    } else {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "OpenSCManager failed - %s", getLastErrorText());
        result = 1;
    }

    /* Remove message file registration on service remove */
    if( result == 0 ) {
        /* Do this here to unregister the syslog on uninstall of a resource. */
        /* unregisterSyslogMessageFile( ); */
    }

    return result;
}

/**
 * Sets the working directory to that of the current executable
 */
int setWorkingDir() {
    int size = 128;
    char* szPath = NULL;
    int result;
    char* pos;
   
    /* How large a buffer is needed? The GetModuleFileName function doesn't tell us how much
     *  is needed, only if it is too short. */
    do {
        szPath = malloc(sizeof(TCHAR) * size);
        if (!szPath) {
            outOfMemory("SWD", 1);
            return 1;
        }
        result = GetModuleFileName(NULL, szPath, size);
        if (result <= 0) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to get the path-%s", getLastErrorText());
            return 1;
        } else if (result >= size) {
            /* Too small. */
            size += 128;
            free(szPath);
            szPath = NULL;
        }
    } while (!szPath);

    /* The wrapperData->isDebugging flag will never be set here, so we can't really use it. */
#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_DEBUG, "Executable Name: %s", szPath);
#endif

    /* To get the path, strip everything off after the last '\' */
    pos = strrchr(szPath, '\\');
    if (pos == NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Unable to extract path from: %s", szPath);
        free(szPath);
        return 1;
    } else {
        /* Clip the path at the position of the last backslash */
        pos[0] = (char)0;
    }

    /* Set a variable to the location of the binary. */
    setEnv("WRAPPER_BIN_DIR", szPath);

    result = wrapperSetWorkingDir(szPath);

    free(szPath);

    return result;
}

/******************************************************************************
 * Main function
 *****************************************************************************/

/** Attempts to resolve the name of an exception.  Returns null if it is unknown. */
char* getExceptionName(DWORD exCode) {
    char *exName;
    
    switch (exCode) {
    case EXCEPTION_ACCESS_VIOLATION:
        exName = "EXCEPTION_ACCESS_VIOLATION";
        break;
    case EXCEPTION_ARRAY_BOUNDS_EXCEEDED:
        exName = "EXCEPTION_ARRAY_BOUNDS_EXCEEDED";
        break;
    case EXCEPTION_BREAKPOINT:
        exName = "EXCEPTION_BREAKPOINT";
        break;
    case EXCEPTION_DATATYPE_MISALIGNMENT:
        exName = "EXCEPTION_DATATYPE_MISALIGNMENT";
        break;
    case EXCEPTION_FLT_DENORMAL_OPERAND:
        exName = "EXCEPTION_FLT_DENORMAL_OPERAND";
        break;
    case EXCEPTION_FLT_DIVIDE_BY_ZERO:
        exName = "EXCEPTION_FLT_DIVIDE_BY_ZERO";
        break;
    case EXCEPTION_FLT_INEXACT_RESULT:
        exName = "EXCEPTION_FLT_INEXACT_RESULT";
        break;
    case EXCEPTION_FLT_INVALID_OPERATION:
        exName = "EXCEPTION_FLT_INVALID_OPERATION";
        break;
    case EXCEPTION_FLT_OVERFLOW:
        exName = "EXCEPTION_FLT_OVERFLOW";
        break;
    case EXCEPTION_FLT_STACK_CHECK:
        exName = "EXCEPTION_FLT_STACK_CHECK";
        break;
    case EXCEPTION_FLT_UNDERFLOW:
        exName = "EXCEPTION_FLT_UNDERFLOW";
        break;
    case EXCEPTION_ILLEGAL_INSTRUCTION:
        exName = "EXCEPTION_ILLEGAL_INSTRUCTION";
        break;
    case EXCEPTION_IN_PAGE_ERROR:
        exName = "EXCEPTION_IN_PAGE_ERROR";
        break;
    case EXCEPTION_INT_DIVIDE_BY_ZERO:
        exName = "EXCEPTION_INT_DIVIDE_BY_ZERO";
        break;
    case EXCEPTION_INT_OVERFLOW:
        exName = "EXCEPTION_INT_OVERFLOW";
        break;
    case EXCEPTION_INVALID_DISPOSITION:
        exName = "EXCEPTION_INVALID_DISPOSITION";
        break;
    case EXCEPTION_NONCONTINUABLE_EXCEPTION:
        exName = "EXCEPTION_NONCONTINUABLE_EXCEPTION";
        break;
    case EXCEPTION_PRIV_INSTRUCTION:
        exName = "EXCEPTION_PRIV_INSTRUCTION";
        break;
    case EXCEPTION_SINGLE_STEP:
        exName = "EXCEPTION_SINGLE_STEP";
        break;
    case EXCEPTION_STACK_OVERFLOW:
        exName = "EXCEPTION_STACK_OVERFLOW";
        break;
    default:
        exName = NULL;
        break;
    }
    
    return exName;
}

int exceptionFilterFunction(PEXCEPTION_POINTERS exceptionPointers) {
    DWORD exCode;
    char *exName;
    int i;

    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "encountered a fatal error in Wrapper");
    exCode = exceptionPointers->ExceptionRecord->ExceptionCode;
    exName = getExceptionName(exCode);
    if (exName == NULL) {
        exName = malloc(sizeof(char) * 64);  /* Let this leak.  It only happens once before shutdown. */
        if (exName) {
            sprintf(exName, "Unknown Exception (%ld)", exCode);
        }
    }

    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  exceptionCode    = %s", exName);
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  exceptionFlag    = %s", 
        (exceptionPointers->ExceptionRecord->ExceptionFlags == EXCEPTION_NONCONTINUABLE ? "EXCEPTION_NONCONTINUABLE" : "EXCEPTION_NONCONTINUABLE_EXCEPTION"));
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  exceptionAddress = %p", exceptionPointers->ExceptionRecord->ExceptionAddress);
    if (exCode == EXCEPTION_ACCESS_VIOLATION) {
        if (exceptionPointers->ExceptionRecord->ExceptionInformation[0] == 0) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  Read access exception from %p", 
                exceptionPointers->ExceptionRecord->ExceptionInformation[1]);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  Write access exception to %p", 
                exceptionPointers->ExceptionRecord->ExceptionInformation[1]);
        }
    } else {
        for (i = 0; i < (int)exceptionPointers->ExceptionRecord->NumberParameters; i++) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "  exceptionInformation[%d] = %ld", i,
                exceptionPointers->ExceptionRecord->ExceptionInformation[i]);
        }
    }

    return EXCEPTION_EXECUTE_HANDLER;
}

void main(int argc, char **argv) {
    int result;
#ifdef _DEBUG
    int i;
#endif

    /* The StartServiceCtrlDispatcher requires this table to specify
     * the ServiceMain function to run in the calling process. The first
     * member in this example is actually ignored, since we will install
     * our service as a SERVICE_WIN32_OWN_PROCESS service type. The NULL
     * members of the last entry are necessary to indicate the end of 
     * the table; */
    SERVICE_TABLE_ENTRY serviceTable[2];

    if (buildSystemPath()) {
        appExit(1);
        return; /* For clarity. */
    }

    if (wrapperInitialize()) {
        appExit(1);
        return; /* For clarity. */
    }

    /* Immediately register this thread with the logger. */
    logRegisterThread(WRAPPER_THREAD_MAIN);

    /* Enclose the rest of the program in a try catch block so we can
     *  display and log useful information should the need arise.  This
     *  must be done after logging has been initialized as the catch
     *  block makes use of the logger. */
    __try {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Wrapper DEBUG build!");
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Logging initialized.");
#endif
        
        if (initializeWinSock()) {
            appExit(1);
            return; /* For clarity. */
        }

        if (setWorkingDir()) {
            appExit(1);
            return; /* For clarity. */
        }
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Working directory set.");
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Arguments:");
        for ( i = 0; i < argc; i++ ) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  argv[%d]=%s", i, argv[i]);
        }
#endif

        /* Parse the command and configuration file from the command line. */
        if (!wrapperParseArguments(argc, argv)) {
            appExit(1);
            return; /* For clarity. */
        }
        
        /* At this point, we have a command, confFile, and possibly additional arguments. */
        if (!strcmpIgnoreCase(wrapperData->argCommand,"?") || !strcmpIgnoreCase(wrapperData->argCommand,"-help")) {
            /* User asked for the usage. */
            wrapperUsage(argv[0]);
            appExit(0);
            return; /* For clarity. */
        } else if (!strcmpIgnoreCase(wrapperData->argCommand,"v") || !strcmpIgnoreCase(wrapperData->argCommand,"-version")) {
            /* User asked for version. */
            wrapperVersionBanner();
            appExit(0);
            return; /* For clarity. */
        }

        /* All 4 valid commands use the configuration file.  It is loaded here to
         *  reduce duplicate code.  But before loading the parameters, in the case
         *  of an NT service. the environment variables must first be loaded from
         *  the registry. */
        if (!strcmpIgnoreCase(wrapperData->argCommand,"s") || !strcmpIgnoreCase(wrapperData->argCommand,"-service")) {
            if (wrapperLoadEnvFromRegistry())
            {
                appExit(1);
                return; /* For clarity. */
            }
        }
        
        /* Load the properties. */
        if (wrapperLoadConfigurationProperties()) {
            /* Unable to load the configuration.  Any errors will have already
             *  been reported. */
            if (wrapperData->argConfFileDefault && !wrapperData->argConfFileFound) {
                /* The config file that was being looked for was default and
                 *  it did not exist.  Show the usage. */
                wrapperUsage(argv[0]);
            }
            appExit(1);
            return; /* For clarity. */
        }

        /* Change the working directory if configured to do so. */
        if (wrapperData->workingDir && wrapperSetWorkingDir(wrapperData->workingDir)) {
            appExit(1);
            return; /* For clarity. */
        }
        
        /* Set the default umask of the Wrapper process. */
        _umask(wrapperData->umask);
        
        /* Perform the specified command */
        if(!strcmpIgnoreCase(wrapperData->argCommand,"i") || !strcmpIgnoreCase(wrapperData->argCommand,"-install")) {
            /* Install an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperInstall());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"it") || !strcmpIgnoreCase(wrapperData->argCommand,"-installstart")) {
            /* Install and Start an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            result = wrapperInstall();
            if (!result) {
                result = wrapperStartService();
            }
            appExit(result);
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"r") || !strcmpIgnoreCase(wrapperData->argCommand,"-remove")) {
            /* Remove an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperRemove());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"t") || !strcmpIgnoreCase(wrapperData->argCommand,"-start")) {
            /* Start an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperStartService());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"a") || !strcmpIgnoreCase(wrapperData->argCommand,"-pause")) {
            /* Pause a started NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperPauseService());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"e") || !strcmpIgnoreCase(wrapperData->argCommand,"-resume")) {
            /* Resume a paused NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperContinueService());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"p") || !strcmpIgnoreCase(wrapperData->argCommand,"-stop")) {
            /* Stop an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperStopService(TRUE));
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"l") || !strcmpIgnoreCase(wrapperData->argCommand,"-controlcode")) {
            /* Send a control code to an NT service */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperSendServiceControlCode(argv, wrapperData->argCommandArg));
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"d") || !strcmpIgnoreCase(wrapperData->argCommand,"-dump")) {
            /* Request a thread dump */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperRequestThreadDump(argv));
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"q") || !strcmpIgnoreCase(wrapperData->argCommand,"-query")) {
            /* Return service status with console output. */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperServiceStatus(TRUE));
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"qs") || !strcmpIgnoreCase(wrapperData->argCommand,"-querysilent")) {
            /* Return service status without console output. */
            /* Always auto close the log file to keep the output in synch. */
            setLogfileAutoClose(TRUE);
            appExit(wrapperServiceStatus(FALSE));
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"c") || !strcmpIgnoreCase(wrapperData->argCommand,"-console")) {
            /* Run as a console application */
            
            /* Load any dynamic functions. */
            loadDLLProcs();
            
            /* Initialize the invocation mutex as necessary, exit if it already exists. */
            if (initInvocationMutex()) {
                appExit(1);
                return; /* For clarity. */
            }
            
            /* Get the current process. */
            wrapperData->wrapperProcess = GetCurrentProcess();
            wrapperData->wrapperPID = GetCurrentProcessId();
            
            /* See if the logs should be rolled on Wrapper startup. */
            if ((getLogfileRollMode() & ROLL_MODE_WRAPPER) ||
                (getLogfileRollMode() & ROLL_MODE_JVM)) {
                rollLogs();
            }
            
            /* Write pid and anchor files as requested.  If they are the same file the file is
             *  simply overwritten. */
            cleanUpPIDFilesOnExit = TRUE;
            if (wrapperData->anchorFilename) {
                if (writePidFile(wrapperData->anchorFilename, wrapperData->wrapperPID, wrapperData->anchorFileUmask)) {
                    log_printf
                        (WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                         "ERROR: Could not write anchor file %s: %s",
                         wrapperData->anchorFilename, getLastErrorText());
                    appExit(1);
                    return; /* For clarity. */
                }
            }
            if (wrapperData->pidFilename) {
                if (writePidFile(wrapperData->pidFilename, wrapperData->wrapperPID, wrapperData->pidFileUmask)) {
                    log_printf
                        (WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                         "ERROR: Could not write pid file %s: %s",
                         wrapperData->pidFilename, getLastErrorText());
                    appExit(1);
                    return; /* For clarity. */
                }
            }
            if (wrapperData->lockFilename) {
                if (writePidFile(wrapperData->lockFilename, wrapperData->wrapperPID, wrapperData->lockFileUmask)) {
                    log_printf
                        (WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                         "ERROR: Could not write lock file %s: %s",
                         wrapperData->lockFilename, getLastErrorText());
                    appExit(1);
                    return; /* For clarity. */
                }
            }

            appExit(wrapperRunConsole());
            return; /* For clarity. */
        } else if(!strcmpIgnoreCase(wrapperData->argCommand,"s") || !strcmpIgnoreCase(wrapperData->argCommand,"-service")) {
            /* Run as a service */
            
            /* Load any dynamic functions. */
            loadDLLProcs();
            
            /* Initialize the invocation mutex as necessary, exit if it already exists. */
            if (initInvocationMutex()) {
                appExit(1);
                return; /* For clarity. */
            }
            
            /* Get the current process. */
            wrapperData->wrapperProcess = GetCurrentProcess();
            wrapperData->wrapperPID = GetCurrentProcessId();
            
            /* See if the logs should be rolled on Wrapper startup. */
            if ((getLogfileRollMode() & ROLL_MODE_WRAPPER) ||
                (getLogfileRollMode() & ROLL_MODE_JVM)) {
                rollLogs();
            }
            
            /* Write pid and anchor files as requested.  If they are the same file the file is
             *  simply overwritten. */
            cleanUpPIDFilesOnExit = TRUE;
            if (wrapperData->anchorFilename) {
                if (writePidFile(wrapperData->anchorFilename, wrapperData->wrapperPID, wrapperData->anchorFileUmask)) {
                    log_printf
                        (WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                         "ERROR: Could not write anchor file %s: %s",
                         wrapperData->anchorFilename, getLastErrorText());
                    appExit(1);
                    return; /* For clarity. */
                }
            }
            if (wrapperData->pidFilename) {
                if (writePidFile(wrapperData->pidFilename, wrapperData->wrapperPID, wrapperData->pidFileUmask)) {
                    log_printf
                        (WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                         "ERROR: Could not write pid file %s: %s",
                         wrapperData->pidFilename, getLastErrorText());
                    appExit(1);
                    return; /* For clarity. */
                }
            }

            /* Prepare the service table */
            serviceTable[0].lpServiceName = wrapperData->serviceName;
            serviceTable[0].lpServiceProc = (LPSERVICE_MAIN_FUNCTION)wrapperServiceMain;
            serviceTable[1].lpServiceName = NULL;
            serviceTable[1].lpServiceProc = NULL;

            printf("Attempting to start %s as an NT service.\n", wrapperData->serviceDisplayName);
            printf("\nCalling StartServiceCtrlDispatcher...please wait.\n");

            /* Start the service control dispatcher.  This will not return
             *  if the service is started without problems.
             *  The ServiceControlDispatcher will call the wrapperServiceMain method. */
            if (!StartServiceCtrlDispatcher(serviceTable)){
                printf("\nStartServiceControlDispatcher failed!\n");
                printf("\nFor help, type\n\n%s /?\n\n", argv[0]);
                appExit(1);
                return; /* For clarity. */
            }
            appExit(0);
            return; /* For clarity. */
        } else {
            printf("\nUnrecognized option: -%s\n", wrapperData->argCommand);
            wrapperUsage(argv[0]);
            appExit(1);
            return; /* For clarity. */
        }
    } __except (exceptionFilterFunction(GetExceptionInformation())) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "<-- Wrapper Stopping due to error");
        appExit(1);
        return; /* For clarity. */
    }
}

#endif /* ifdef WIN32 */

