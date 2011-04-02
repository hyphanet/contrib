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
 *   Johan Sorlin   <Johan.Sorlin@Paregos.se>
 *   Leif Mortenson <leif@tanukisoftware.com>
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <time.h>
#include <sys/stat.h>
#include <string.h>
#include <errno.h>

#ifdef WIN32
#include <io.h>
#include <windows.h>
#include <tchar.h>
#include <conio.h>
#include <sys/timeb.h>
#include "messages.h"

/* MS Visual Studio 8 went and deprecated the POXIX names for functions.
 *  Fixing them all would be a big headache for UNIX versions. */
#pragma warning(disable : 4996)

/* Defines for MS Visual Studio 6 */
#ifndef _INTPTR_T_DEFINED
typedef long intptr_t;
#define _INTPTR_T_DEFINED
#endif

#else
#include <glob.h>
#include <syslog.h>
#include <strings.h>
#include <pthread.h>
#include <sys/time.h>
#endif

#include "logger.h"

#ifndef TRUE
#define TRUE -1
#endif

#ifndef FALSE
#define FALSE 0
#endif

/* Global data for logger */

/* Initialize all log levels to unknown until they are set */
int currentConsoleLevel = LEVEL_UNKNOWN;
int currentLogfileLevel = LEVEL_UNKNOWN;
int currentLoginfoLevel = LEVEL_UNKNOWN;

#ifndef WIN32
/* Default syslog facility is LOG_USER */
int currentLogfacilityLevel = LOG_USER;
#endif

char *logFilePath;
char *currentLogFileName;
char *workLogFileName;
int logFileRollMode = ROLL_MODE_SIZE;
int logFileUmask = 0022;
char *logLevelNames[] = { "NONE  ", "DEBUG ", "INFO  ", "STATUS", "WARN  ", "ERROR ", "FATAL ", "ADVICE" };
char loginfoSourceName[ 1024 ];
int  logFileMaxSize = -1;
int  logFileMaxLogFiles = -1;

char logFileLastNowDate[9];

/* Defualt formats (Must be 4 chars) */
char consoleFormat[32];
char logfileFormat[32];

/* Flag to keep track of whether the console output should be flushed or not. */
int consoleFlush = FALSE;

/* Internal function declaration */
void sendEventlogMessage( int source_id, int level, const char *szBuff );
void sendLoginfoMessage( int source_id, int level, const char *szBuff );
#ifdef WIN32
void writeToConsole( char *lpszFmt, ... );
#endif
void checkAndRollLogs(const char *nowDate);

/* Any log messages generated within signal handlers must be stored until we
 *  have left the signal handler to avoid deadlocks in the logging code.
 *  Messages are stored in a round robin buffer of log messages until
 *  maintainLogger is next called.
 * When we are inside of a signal, and thus when calling log_printf_queue,
 *  we know that it is safe to modify the queue as needed.  But it is possible
 *  that a signal could be fired while we are in maintainLogger, so case is
 *  taken to make sure that volatile changes are only made in log_printf_queue.
 */
#define QUEUE_SIZE 100
int queueWrapped[WRAPPER_THREAD_COUNT];
int queueWriteIndex[WRAPPER_THREAD_COUNT];
int queueReadIndex[WRAPPER_THREAD_COUNT];
char *queueMessages[WRAPPER_THREAD_COUNT][QUEUE_SIZE];
int queueSourceIds[WRAPPER_THREAD_COUNT][QUEUE_SIZE];
int queueLevels[WRAPPER_THREAD_COUNT][QUEUE_SIZE];

/* Thread specific work buffers. */
#ifdef WIN32
DWORD threadIds[WRAPPER_THREAD_COUNT];
#else
pthread_t threadIds[WRAPPER_THREAD_COUNT];
#endif
char *threadMessageBuffer = NULL;
size_t threadMessageBufferSize = 0;
char *threadPrintBuffer = NULL;
size_t threadPrintBufferSize = 0;

/* Logger file pointer.  It is kept open under high log loads but closed whenever it has been idle. */
FILE *logfileFP = NULL;

/** Flag which controls whether or not the logfile is auto closed after each line. */
int autoCloseLogfile = 0;

/* The number of lines sent to the log file since the getLogfileActivity method was last called. */
DWORD logfileActivityCount;

/* Mutex for syncronization of the log_printf function. */
#ifdef WIN32
HANDLE log_printfMutexHandle = NULL;
#else
pthread_mutex_t log_printfMutex = PTHREAD_MUTEX_INITIALIZER;
#endif

#ifdef WIN32
HANDLE consoleStdoutHandle = NULL;
void setConsoleStdoutHandle( HANDLE stdoutHandle ) {
    consoleStdoutHandle = stdoutHandle;
}
#endif

void outOfMemory(const char *context, int id) {
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, "Out of memory (%s%02d). %s",
        context, id, getLastErrorText());
}

/**
 * Replaces one token with another.  The length of the new token must be equal
 *  to or less than that of the old token.
 *
 * newToken may be null, implying "".
 */
char *replaceStringLongWithShort(char *string, const char *oldToken, const char *newToken) {
    size_t oldLen = strlen(oldToken);
    size_t newLen;
    char *in = string;
    char *out = string;
    
    if (newToken) {
        newLen = strlen(newToken);
    } else {
        newLen = 0;
    }
    
    /* Assertion check. */
    if (newLen > oldLen) {
        return string;
    }
    
    while (in[0] != '\0') {
        if (memcmp(in, oldToken, oldLen) == 0) {
            /* Found the oldToken.  Replace it with the new. */
            if (newLen > 0) {
                memcpy(out, newToken, newLen);
            }
            in += oldLen;
            out += newLen;
        }
        else
        {
            out[0] = in[0];
            in++;
            out++;
        }
    }
    out[0] = '\0';
    
    return string;
}

/**
 * Initializes the logger.  Returns 0 if the operation was successful.
 */
int initLogging() {
    int threadId, i;

#ifdef WIN32
    if (!(log_printfMutexHandle = CreateMutex(NULL, FALSE, NULL))) {
        printf("Failed to create logging mutex. %s\n", getLastErrorText());
        return 1;
    }
#endif
    
    logFileLastNowDate[0] = '\0';

    for ( threadId = 0; threadId < WRAPPER_THREAD_COUNT; threadId++ ) {
        threadIds[threadId] = 0;
    
        for ( i = 0; i < QUEUE_SIZE; i++ )
        {
            queueWrapped[threadId] = 0;
            queueWriteIndex[threadId] = 0;
            queueReadIndex[threadId] = -1; /* Start here so equality checks work. */
            queueMessages[threadId][i] = NULL;
            queueSourceIds[threadId][i] = 0;
            queueLevels[threadId][i] = 0;
        }
    }

    return 0;
}

/**
 * Disposes of any logging resouces prior to shutdown.
 */
int disposeLogging() {
#ifdef WIN32
    if (log_printfMutexHandle) {
        if (!CloseHandle(log_printfMutexHandle))
        {
            printf("Unable to close Logging Mutex handle. %s\n", getLastErrorText());
            return 1;
        }
    }
#endif
    
    return 0;
}

/** Registers the calling thread so it can be recognized when it calls
 *  again later. */
void logRegisterThread( int thread_id ) {
#ifdef WIN32
    DWORD threadId;
    threadId = GetCurrentThreadId();
#else
    pthread_t threadId;
    threadId = pthread_self();
#endif

    if ( thread_id >= 0 && thread_id < WRAPPER_THREAD_COUNT )
    {
        threadIds[thread_id] = threadId;
    }
}

int getThreadId() {
    int i;
#ifdef WIN32
    DWORD threadId;
    threadId = GetCurrentThreadId();
#else
    pthread_t threadId;
    threadId = pthread_self();
#endif
    /* printf( "threadId=%lu\n", threadId ); */

    for ( i = 0; i < WRAPPER_THREAD_COUNT; i++ ) {
        if ( threadIds[i] == threadId ) {
            return i;
        }
    }
    
    printf( "WARNING - Encountered an unknown thread %ld in getThreadId().\n", (long int)threadId );
    return 0; /* WRAPPER_THREAD_SIGNAL */
}

int getLogfileRollModeForName( const char *logfileRollName ) {
    if (strcmpIgnoreCase(logfileRollName, "NONE") == 0) {
        return ROLL_MODE_NONE;
    } else if (strcmpIgnoreCase(logfileRollName, "SIZE") == 0) {
        return ROLL_MODE_SIZE;
    } else if (strcmpIgnoreCase(logfileRollName, "WRAPPER") == 0) {
        return ROLL_MODE_WRAPPER;
    } else if (strcmpIgnoreCase(logfileRollName, "JVM") == 0) {
        return ROLL_MODE_JVM;
    } else if (strcmpIgnoreCase(logfileRollName, "SIZE_OR_WRAPPER") == 0) {
        return ROLL_MODE_SIZE_OR_WRAPPER;
    } else if (strcmpIgnoreCase(logfileRollName, "SIZE_OR_JVM") == 0) {
        return ROLL_MODE_SIZE_OR_JVM;
    } else if (strcmpIgnoreCase(logfileRollName, "DATE") == 0) {
        return ROLL_MODE_DATE;
    } else {
        return ROLL_MODE_UNKNOWN;
    }
}

int getLogLevelForName( const char *logLevelName ) {
    if (strcmpIgnoreCase(logLevelName, "NONE") == 0) {
        return LEVEL_NONE;
    } else if (strcmpIgnoreCase(logLevelName, "ADVICE") == 0) {
        return LEVEL_ADVICE;
    } else if (strcmpIgnoreCase(logLevelName, "FATAL") == 0) {
        return LEVEL_FATAL;
    } else if (strcmpIgnoreCase(logLevelName, "ERROR") == 0) {
        return LEVEL_ERROR;
    } else if (strcmpIgnoreCase(logLevelName, "WARN") == 0) {
        return LEVEL_WARN;
    } else if (strcmpIgnoreCase(logLevelName, "STATUS") == 0) {
        return LEVEL_STATUS;
    } else if (strcmpIgnoreCase(logLevelName, "INFO") == 0) {
        return LEVEL_INFO;
    } else if (strcmpIgnoreCase(logLevelName, "DEBUG") == 0) {
        return LEVEL_DEBUG;
    } else {
        return LEVEL_UNKNOWN;
    }
}

#ifndef WIN32
int getLogFacilityForName( const char *logFacilityName ) {
    if (strcmpIgnoreCase(logFacilityName, "USER") == 0) {
      return LOG_USER;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL0") == 0) {
      return LOG_LOCAL0;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL1") == 0) {
      return LOG_LOCAL1;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL2") == 0) {
      return LOG_LOCAL2;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL3") == 0) {
      return LOG_LOCAL3;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL4") == 0) {
      return LOG_LOCAL4;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL5") == 0) {
      return LOG_LOCAL5;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL6") == 0) {
      return LOG_LOCAL6;
    } else if (strcmpIgnoreCase(logFacilityName, "LOCAL7") == 0) {
      return LOG_LOCAL7;
    } else {
      return LOG_USER;
    }
}
#endif

/* Logfile functions */

void setLogfilePath( const char *log_file_path ) {
    size_t len = strlen(log_file_path);
#ifdef WIN32
    char *c;
#endif
    
    if (logFilePath) {
        free(logFilePath);
        free(currentLogFileName);
        free(workLogFileName);
    }
    logFilePath = NULL;
    currentLogFileName = NULL;
    workLogFileName = NULL;

    logFilePath = malloc(sizeof(char) * (len + 1));
    if (!logFilePath) {
        outOfMemory("SLP", 1);
        return;
    }
    strcpy(logFilePath, log_file_path);

    currentLogFileName = malloc(sizeof(char) * (len + 10 + 1));
    if (!currentLogFileName) {
        outOfMemory("SLP", 2);
        free(logFilePath);
        logFilePath = NULL;
        return;
    }
    currentLogFileName[0] = '\0';
    workLogFileName = malloc(sizeof(char) * (len + 10 + 1));
    if (!workLogFileName) {
        outOfMemory("SLP", 3);
        free(logFilePath);
        logFilePath = NULL;
        free(currentLogFileName);
        currentLogFileName = NULL;
        return;
    }
    workLogFileName[0] = '\0';
    
#ifdef WIN32    
    /* To avoid problems on some windows systems, the '/' characters must
     *  be replaced by '\' characters in the specified path. */
    c = (char *)logFilePath;
    while((c = strchr(c, '/')) != NULL) {
        c[0] = '\\';
    }
#endif
}

const char *getLogfilePath()
{
    return logFilePath;
}

void setLogfileRollMode( int log_file_roll_mode ) {
    logFileRollMode = log_file_roll_mode;
}

int getLogfileRollMode() {
    return logFileRollMode;
}

void setLogfileUmask( int log_file_umask ) {
    logFileUmask = log_file_umask;
}

void setLogfileFormat( const char *log_file_format ) {
    if( log_file_format != NULL )
        strcpy( logfileFormat, log_file_format );
}

void setLogfileLevelInt( int log_file_level ) {
    currentLogfileLevel = log_file_level;
}

int getLogfileLevelInt() {
    return currentLogfileLevel;
}

void setLogfileLevel( const char *log_file_level ) {
    setLogfileLevelInt(getLogLevelForName(log_file_level));
}

void setLogfileMaxFileSize( const char *max_file_size ) {
    int multiple, i, newLength;
    char *tmpFileSizeBuff;
    char chr;

    if( max_file_size != NULL ) {
        /* Allocate buffer */
        tmpFileSizeBuff = (char *) malloc(sizeof(char) * (strlen( max_file_size ) + 1));
        if (!tmpFileSizeBuff) {
            outOfMemory("SLMFS", 1);
            return;
        }

        /* Generate multiple and remove unwanted chars */
        multiple = 1;
        newLength = 0;
        for( i=0; i<(int) strlen(max_file_size); i++ ) {
            chr = max_file_size[i];

            switch( chr ) {
                case 'k': /* Kilobytes */
                case 'K':
                    multiple = 1024;
                break;

                case 'M': /* Megabytes */
                case 'm':
                    multiple = 1048576;
                break;
            }

            if( (chr >= '0' && chr <= '9') || (chr == '-') )
                tmpFileSizeBuff[newLength++] = max_file_size[i];
        }
        tmpFileSizeBuff[newLength] = '\0';/* Crop string */

        logFileMaxSize = atoi( tmpFileSizeBuff );
        if( logFileMaxSize > 0 )
            logFileMaxSize *= multiple;

        /* Free memory */
        free( tmpFileSizeBuff );
        tmpFileSizeBuff = NULL;
    }
}

void setLogfileMaxFileSizeInt( int max_file_size ) {
    logFileMaxSize = max_file_size;
}

void setLogfileMaxLogFiles( int max_log_files ) {
    logFileMaxLogFiles = max_log_files;
}

/** Returns the number of lines of log file activity since the last call. */
DWORD getLogfileActivity() {
    DWORD logfileLines;
    
    /* Don't worry about synchronization here.  Any errors are not critical the way this is used. */
    logfileLines = logfileActivityCount;
    logfileActivityCount = 0;
    
    return logfileLines;
}

/** Obtains a lock on the logging mutex. */
int lockLoggingMutex() {
#ifdef WIN32
    switch (WaitForSingleObject(log_printfMutexHandle, INFINITE)) {
    case WAIT_ABANDONED:
        printf("Logging mutex was abandoned.\n");
        return -1;
    case WAIT_FAILED:
        printf("Logging mutex wait failed.\n");
        return -1;
    case WAIT_TIMEOUT:
        printf("Logging mutex wait timed out.\n");
        return -1;
    default:
        /* Ok */
        break;
    }
#else
    if (pthread_mutex_lock(&log_printfMutex)) {
        printf("Failed to lock the Logging mutex. %s\n", getLastErrorText());
        return -1;
    }
#endif
    
    return 0;
}

/** Releases a lock on the logging mutex. */
int releaseLoggingMutex() {
#ifdef WIN32
    if (!ReleaseMutex(log_printfMutexHandle)) {
        printf( "Failed to release logging mutex. %s\n", getLastErrorText());
        return -1;
    }
#else
    if (pthread_mutex_unlock(&log_printfMutex)) {
        printf("Failed to unlock the Logging mutex. %s\n", getLastErrorText());
        return -1;
    }
#endif
    return 0;
}

/** Closes the logfile if it is open. */
void closeLogfile() {
    /* We need to be very careful that only one thread is allowed in here
     *  at a time.  On Windows this is done using a Mutex object that is
     *  initialized in the initLogging function. */
    if (lockLoggingMutex()) {
        return;
    }
    
    if (logfileFP != NULL) {
#ifdef _DEBUG
        printf("Closing logfile by request...\n");
#endif
        
        fclose(logfileFP);
        logfileFP = NULL;
        if (currentLogFileName) {
            currentLogFileName[0] = '\0';
        }
    }

    /* Release the lock we have on this function so that other threads can get in. */
    if (releaseLoggingMutex()) {
        return;
    }
}

/** Sets the auto close log file flag. */
void setLogfileAutoClose(int autoClose) {
    autoCloseLogfile = autoClose;
}

/** Flushes any buffered logfile output to the disk. */
void flushLogfile() {
    /* We need to be very careful that only one thread is allowed in here
     *  at a time.  On Windows this is done using a Mutex object that is
     *  initialized in the initLogging function. */
    if (lockLoggingMutex()) {
        return;
    }

    if (logfileFP != NULL) {
#ifdef _DEBUG
        printf("Flushing logfile by request...\n");
#endif

        fflush(logfileFP);
    }

    /* Release the lock we have on this function so that other threads can get in. */
    if (releaseLoggingMutex()) {
        return;
    }
}

/* Console functions */
void setConsoleLogFormat( const char *console_log_format ) {
    if( console_log_format != NULL )
        strcpy( consoleFormat, console_log_format );
}

void setConsoleLogLevelInt( int console_log_level ) {
    currentConsoleLevel = console_log_level;
}

int getConsoleLogLevelInt() {
    return currentConsoleLevel;
}

void setConsoleLogLevel( const char *console_log_level ) {
    setConsoleLogLevelInt(getLogLevelForName(console_log_level));
}

void setConsoleFlush( int flush ) {
    consoleFlush = flush;
}


/* Syslog/eventlog functions */
void setSyslogLevelInt( int loginfo_level ) {
    currentLoginfoLevel = loginfo_level;
}

int getSyslogLevelInt() {
    return currentLoginfoLevel;
}

void setSyslogLevel( const char *loginfo_level ) {
    setSyslogLevelInt(getLogLevelForName(loginfo_level));
}

#ifndef WIN32
void setSyslogFacilityInt( int logfacility_level ) {
    currentLogfacilityLevel = logfacility_level;
}

void setSyslogFacility( const char *loginfo_level ) {
    setSyslogFacilityInt(getLogFacilityForName(loginfo_level));
}
#endif

void setSyslogEventSourceName( const char *event_source_name ) {
    if( event_source_name != NULL )
        strcpy( loginfoSourceName, event_source_name );
}

int getLowLogLevel() {
    int lowLogLevel = (currentLogfileLevel < currentConsoleLevel ? currentLogfileLevel : currentConsoleLevel);
    lowLogLevel =  (currentLoginfoLevel < lowLogLevel ? currentLoginfoLevel : lowLogLevel);
    return lowLogLevel;
}

/* Writes to and then returns a buffer that is reused by the current thread.
 *  It should not be released. */
char* buildPrintBuffer( int source_id, int level, int threadId, int queued, struct tm *nowTM, int nowMillis, const char *format, const char *message ) {
    int       i;
    size_t    reqSize;
    int       numColumns;
    char      *pos;
    int       currentColumn;
    int       handledFormat;
    
    /* Decide the number of columns and come up with a required length for the printBuffer. */
    reqSize = 0;
    for( i = 0, numColumns = 0; i < (int)strlen( format ); i++ ) {
        switch( format[i] ) {
        case 'P':
            reqSize += 8 + 3;
            numColumns++;
            break;

        case 'L':
            reqSize += 6 + 3;
            numColumns++;
            break;

        case 'D':
            reqSize += 7 + 3;
            numColumns++;
            break;

        case 'Q':
            reqSize += 1 + 3;
            numColumns++;
            break;

        case 'T':
            reqSize += 19 + 3;
            numColumns++;
            break;

        case 'Z':
            reqSize += 23 + 3;
            numColumns++;
            break;

        case 'M':
            reqSize += strlen( message ) + 3;
            numColumns++;
            break;
        }
    }

    /* Always add room for the null. */
    reqSize += 1;

    if ( threadPrintBuffer == NULL ) {
        threadPrintBuffer = (char *)malloc( reqSize * sizeof( char ) );
        if (!threadPrintBuffer) {
            printf("Out of memory is logging code (BPB1)\n");
            threadPrintBufferSize = 0;
            return NULL;
        }
        threadPrintBufferSize = reqSize;
    } else if ( threadPrintBufferSize < reqSize ) {
        free( threadPrintBuffer );
        threadPrintBuffer = (char *)malloc( reqSize * sizeof( char ) );
        if (!threadPrintBuffer) {
            printf("Out of memory is logging code (BPB2)\n");
            threadPrintBufferSize = 0;
            return NULL;
        }
        threadPrintBufferSize = reqSize;
    }

    /* Always start with a null terminated string in case there are no formats specified. */
    threadPrintBuffer[0] = '\0';
    
    /* Create a pointer to the beginning of the print buffer, it will be advanced
     *  as the formatted message is build up. */
    pos = threadPrintBuffer;

    /* We now have a buffer large enough to store the entire formatted message. */
    for( i = 0, currentColumn = 0; i < (int)strlen( format ); i++ ) {
        handledFormat = 1;

        switch( format[i] ) {
        case 'P':
            switch ( source_id ) {
            case WRAPPER_SOURCE_WRAPPER:
                pos += sprintf( pos, "wrapper " );
                break;

            case WRAPPER_SOURCE_PROTOCOL:
                pos += sprintf( pos, "wrapperp" );
                break;

            default:
                pos += sprintf( pos, "jvm %-4d", source_id );
                break;
            }
            currentColumn++;
            break;

        case 'L':
            pos += sprintf( pos, "%s", logLevelNames[ level ] );
            currentColumn++;
            break;

        case 'D':
            switch ( threadId )
            {
            case WRAPPER_THREAD_SIGNAL:
                pos += sprintf( pos, "signal " );
                break;

            case WRAPPER_THREAD_MAIN:
                pos += sprintf( pos, "main   " );
                break;

            case WRAPPER_THREAD_SRVMAIN:
                pos += sprintf( pos, "srvmain" );
                break;

            case WRAPPER_THREAD_TIMER:
                pos += sprintf( pos, "timer  " );
                break;

            default:
                pos += sprintf( pos, "unknown" );
                break;
            }
            currentColumn++;
            break;

        case 'Q':
            pos += sprintf( pos, "%c", ( queued ? 'Q' : ' ' ) );
            currentColumn++;
            break;

        case 'T':
            pos += sprintf( pos, "%04d/%02d/%02d %02d:%02d:%02d",
                nowTM->tm_year + 1900, nowTM->tm_mon + 1, nowTM->tm_mday, 
                nowTM->tm_hour, nowTM->tm_min, nowTM->tm_sec );
            currentColumn++;
            break;

        case 'Z':
            pos += sprintf( pos, "%04d/%02d/%02d %02d:%02d:%02d.%03d",
                nowTM->tm_year + 1900, nowTM->tm_mon + 1, nowTM->tm_mday, 
                nowTM->tm_hour, nowTM->tm_min, nowTM->tm_sec, nowMillis );
            currentColumn++;
            break;

        case 'M':
            pos += sprintf( pos, "%s", message );
            currentColumn++;
            break;

        default:
            handledFormat = 0;
        }

        /* Add separator chars */
        if ( handledFormat && ( currentColumn != numColumns ) ) {
            pos += sprintf( pos, " | " );
        }
    }

    /* Return the print buffer to the caller. */
    return threadPrintBuffer;
}

void forceFlush(FILE *fp) {
    int lastError;
    
    fflush(fp);
    lastError = getLastError();
}

/**
 * Generates a log file name given.
 *
 * buffer - Buffer into which to sprintf the generated name.
 * template - Template from which the name is generated.
 * nowDate - Optional date used to replace any YYYYMMDD tokens.
 * rollNum - Optional roll number used to replace any ROLLNUM tokens.
 */
void generateLogFileName(char *buffer, const char *template, const char *nowDate, const char *rollNum ) {
    /* Copy the template to the buffer to get started. */
    sprintf(buffer, template);
    
    /* Handle the date token. */
    if (strstr(buffer, "YYYYMMDD")) {
        if (nowDate == NULL) {
            /* The token needs to be removed. */
            replaceStringLongWithShort(buffer, "-YYYYMMDD", NULL);
            replaceStringLongWithShort(buffer, "_YYYYMMDD", NULL);
            replaceStringLongWithShort(buffer, ".YYYYMMDD", NULL);
            replaceStringLongWithShort(buffer, "YYYYMMDD", NULL);
        } else {
            /* The token needs to be replaced. */
            replaceStringLongWithShort(buffer, "YYYYMMDD", nowDate);
        }
    }
    
    /* Handle the roll number token. */
    if (strstr(buffer, "ROLLNUM")) {
        if (rollNum == NULL ) {
            /* The token needs to be removed. */
            replaceStringLongWithShort(buffer, "-ROLLNUM", NULL);
            replaceStringLongWithShort(buffer, "_ROLLNUM", NULL);
            replaceStringLongWithShort(buffer, ".ROLLNUM", NULL);
            replaceStringLongWithShort(buffer, "ROLLNUM", NULL);
        } else {
            /* The token needs to be replaced. */
            replaceStringLongWithShort(buffer, "ROLLNUM", rollNum);
        }
    } else {
        /* The name did not contain a ROLLNUM token. */
        if (rollNum != NULL ) {
            /* Generate the name as if ".ROLLNUM" was appended to the template. */
            sprintf(buffer + strlen(buffer), ".%s", rollNum);
        }
    }
}

/**
 * Prints the contents of a buffer to all configured targets.
 *
 * Must be called while locked.
 */
void log_printf_message( int source_id, int level, int threadId, int queued, const char *message ) {
    char        *printBuffer;
    int         old_umask;
    char        nowDate[9];
#ifdef WIN32
    struct _timeb timebNow;
#else
    struct timeval timevalNow;
#endif
    time_t      now;
    int         nowMillis;
    struct tm   *nowTM;

    /* Build a timestamp */
#ifdef WIN32
    _ftime( &timebNow );
    now = (time_t)timebNow.time;
    nowMillis = timebNow.millitm;
#else
    gettimeofday( &timevalNow, NULL );
    now = (time_t)timevalNow.tv_sec;
    nowMillis = timevalNow.tv_usec / 1000;
#endif
    nowTM = localtime( &now );

    if ( threadId < 0 )
    {
        threadId = getThreadId();
    }
    
    /* Console output by format */
    if( level >= currentConsoleLevel ) {
        /* Build up the printBuffer. */
        printBuffer = buildPrintBuffer( source_id, level, threadId, queued, nowTM, nowMillis, consoleFormat, message );
        if (printBuffer) {
            /* Write the print buffer to the console. */
#ifdef WIN32
            if ( consoleStdoutHandle != NULL ) {
                writeToConsole( "%s\n", printBuffer );
            } else {
#endif
                fprintf( stdout, "%s\n", printBuffer );
                if ( consoleFlush ) {
                    fflush( stdout );
                }
#ifdef WIN32
            }
#endif
        }
    }

    /* Logfile output by format */
    
    /* Log the message to the log file */
    if (level >= currentLogfileLevel) {
        /* If the log file was set to a blank value then it will not be used. */
        if ( logFilePath && ( strlen( logFilePath ) > 0 ) )
        {
            /* If this the roll mode is date then we need a nowDate for this log entry. */
            if (logFileRollMode & ROLL_MODE_DATE) {
                sprintf(nowDate, "%04d%02d%02d", nowTM->tm_year + 1900, nowTM->tm_mon + 1, nowTM->tm_mday );
            } else {
                nowDate[0] = '\0';
            }
            
            /* Make sure that the log file does not need to be rolled. */
            checkAndRollLogs(nowDate);
            
            /* If the file needs to be opened then do so. */
            if (logfileFP == NULL) {
                /* Generate the log file name. */
                if (logFileRollMode & ROLL_MODE_DATE) {
                    generateLogFileName(currentLogFileName, logFilePath, nowDate, NULL);
                } else {
                    generateLogFileName(currentLogFileName, logFilePath, NULL, NULL);
                }
                
                old_umask = umask( logFileUmask );
                logfileFP = fopen( currentLogFileName, "a" );
                if (logfileFP == NULL) {
                    /* The log file could not be opened.  Try the default file location. */
                    sprintf(currentLogFileName, "wrapper.log");
                    logfileFP = fopen( "wrapper.log", "a" );
                }
                umask(old_umask);
                
#ifdef _DEBUG                
                if (logfileFP != NULL) {
                    printf("Opened logfile\n");
                }
#endif
            }
            
            if (logfileFP == NULL) {
                currentLogFileName[0] = '\0';
                printf("Unable to open logfile %s: %s\n", logFilePath, getLastErrorText());
            } else {
                /* We need to store the date the file was opened for. */
                strcpy(logFileLastNowDate, nowDate);
                
                /* Build up the printBuffer. */
                printBuffer = buildPrintBuffer( source_id, level, threadId, queued, nowTM, nowMillis, logfileFormat, message );
                if (printBuffer) {
                    fprintf( logfileFP, "%s\n", printBuffer );
                    
                    /* Increment the activity counter. */
                    logfileActivityCount++;
                    
                    /* Only close the file if autoClose is set.  Otherwise it will be closed later
                     *  after an appropriate period of inactivity. */
                    if (autoCloseLogfile) {
#ifdef _DEBUG
                        printf("Closing logfile immediately...\n");
#endif
                        
                        fclose(logfileFP);
                        logfileFP = NULL;
                        currentLogFileName[0] = '\0';
                    }
                    
                    /* Leave the file open.  It will be closed later after a period of inactivity. */
                }
            }
        }
    }

    /* Loginfo/Eventlog if levels match (not by format timecodes/status allready exists in evenlog) */
    switch ( level ) {
    case LEVEL_ADVICE:
        /* Advice level messages are special in that they never get logged to the
         *  EventLog / SysLog. */
        break;

    default:
        if ( level >= currentLoginfoLevel ) {
            sendEventlogMessage( source_id, level, message );
            sendLoginfoMessage( source_id, level, message );
        }
    }
}


/* General log functions */
void log_printf( int source_id, int level, const char *lpszFmt, ... ) {
    va_list     vargs;
    int         count;
    int         threadId;

    /* We need to be very careful that only one thread is allowed in here
     *  at a time.  On Windows this is done using a Mutex object that is
     *  initialized in the initLogging function. */
    if (lockLoggingMutex()) {
        return;
    }

    threadId = getThreadId();

    /* Loop until the buffer is large enough that we are able to successfully
     *  print into it. Once the buffer has grown to the largest message size,
     *  smaller messages will pass through this code without looping. */
    do {
        if ( threadMessageBufferSize == 0 )
        {
            /* No buffer yet. Allocate one to get started. */
            threadMessageBufferSize = 100;
            threadMessageBuffer = (char*)malloc( threadMessageBufferSize * sizeof(char) );
            if (!threadMessageBuffer) {
                printf("Out of memory is logging code (P1)\n");
                threadMessageBufferSize = 0;
                return;
            }
        }

        /* Try writing to the buffer. */
        va_start( vargs, lpszFmt );
#ifdef WIN32
        count = _vsnprintf( threadMessageBuffer, threadMessageBufferSize, lpszFmt, vargs );
#else
        count = vsnprintf( threadMessageBuffer, threadMessageBufferSize, lpszFmt, vargs );
#endif
        va_end( vargs );
        /*
        printf( " vsnprintf->%d, size=%d\n", count, threadMessageBufferSize );
        */
        if ( ( count < 0 ) || ( count >= (int)threadMessageBufferSize ) ) {
            /* If the count is exactly equal to the buffer size then a null char was not written.
             *  It must be larger.
             * Windows will return -1 if the buffer is too small. If the number is
             *  exact however, we still need to expand it to have room for the null.
             * UNIX will return the required size. */

            /* Free the old buffer for starters. */
            free( threadMessageBuffer );

            /* Decide on a new buffer size. */
            if ( count <= (int)threadMessageBufferSize ) {
                threadMessageBufferSize += 100;
            } else if ( count + 1 <= (int)threadMessageBufferSize + 100 ) {
                threadMessageBufferSize += 100;
            } else {
                threadMessageBufferSize = count + 1;
            }

            threadMessageBuffer = (char*)malloc( threadMessageBufferSize * sizeof(char) );
            if (!threadMessageBuffer) {
                printf("Out of memory is logging code (P2)\n");
                threadMessageBufferSize = 0;
                return;
            }

            /* Always set the count to -1 so we will loop again. */
            count = -1;
        }
    } while ( count < 0 );

    log_printf_message( source_id, level, threadId, FALSE, threadMessageBuffer );

    /* Release the lock we have on this function so that other threads can get in. */
    if (releaseLoggingMutex()) {
        return;
    }
}

/* Internal functions */

/**
 * Create an error message from GetLastError() using the
 *  FormatMessage API Call...
 */
#ifdef WIN32
TCHAR lastErrBuf[1024];
char* getLastErrorText() {
    DWORD dwRet;
    LPTSTR lpszTemp = NULL;

    dwRet = FormatMessage( FORMAT_MESSAGE_ALLOCATE_BUFFER | 
                           FORMAT_MESSAGE_FROM_SYSTEM |FORMAT_MESSAGE_ARGUMENT_ARRAY,
                           NULL,
                           GetLastError(),
                           LANG_NEUTRAL,
                           (LPTSTR)&lpszTemp,
                           0,
                           NULL);

    /* supplied buffer is not long enough */
    if (!dwRet || ((long)1023 < (long)dwRet+14)) {
        lastErrBuf[0] = TEXT('\0');
    } else {
        lpszTemp[lstrlen(lpszTemp)-2] = TEXT('\0');  /*remove cr and newline character */
        _stprintf( lastErrBuf, TEXT("%s (0x%x)"), lpszTemp, GetLastError());
    }

    if (lpszTemp) {
        GlobalFree((HGLOBAL) lpszTemp);
    }

    return lastErrBuf;
}
int getLastError() {
    return GetLastError();
}
#else
char* getLastErrorText() {
    return strerror(errno);
}
int getLastError() {
    return errno;
}
#endif

int registerSyslogMessageFile( ) {
#ifdef WIN32
    char buffer[ 1024 ];
    char regPath[ 1024 ];
    HKEY hKey;
    DWORD categoryCount, typesSupported;

    /* Get absolute path to service manager */
    if( GetModuleFileName( NULL, buffer, _MAX_PATH ) ) {
        sprintf( regPath, "SYSTEM\\CurrentControlSet\\Services\\Eventlog\\Application\\%s", loginfoSourceName );

        if( RegCreateKey( HKEY_LOCAL_MACHINE, regPath, (PHKEY) &hKey ) == ERROR_SUCCESS ) {
            RegCloseKey( hKey );

            if( RegOpenKeyEx( HKEY_LOCAL_MACHINE, regPath, 0, KEY_WRITE, (PHKEY) &hKey ) == ERROR_SUCCESS ) {
                /* Set EventMessageFile */
                if( RegSetValueEx( hKey, "EventMessageFile", (DWORD) 0, (DWORD) REG_SZ, (const unsigned char *) buffer, (DWORD)(strlen(buffer) + 1) ) != ERROR_SUCCESS ) {
                    RegCloseKey( hKey );
                    return -1;
                }

                /* Set CategoryMessageFile */
                if( RegSetValueEx( hKey, "CategoryMessageFile", (DWORD) 0, (DWORD) REG_SZ, (const unsigned char *) buffer, (DWORD)(strlen(buffer) + 1) ) != ERROR_SUCCESS ) {
                    RegCloseKey( hKey );
                    return -1;
                }

                /* Set CategoryCount */
                categoryCount = 12;
                if( RegSetValueEx( hKey, "CategoryCount", (DWORD) 0, (DWORD) REG_DWORD, (LPBYTE) &categoryCount, sizeof(DWORD) ) != ERROR_SUCCESS ) {
                    RegCloseKey( hKey );
                    return -1;
                }

                /* Set TypesSupported */
                typesSupported = 7;
                if( RegSetValueEx( hKey, "TypesSupported", (DWORD) 0, (DWORD) REG_DWORD, (LPBYTE) &typesSupported, sizeof(DWORD) ) != ERROR_SUCCESS ) {
                    RegCloseKey( hKey );
                    return -1;
                }

                RegCloseKey( hKey );
                return 0;
            }
        }
    }

    return -1; /* Failure */
#else
    return 0;
#endif
}

int unregisterSyslogMessageFile( ) {
#ifdef WIN32
    /* If we deregister this application, then the event viewer will not work when the program is not running. */
    /* Don't want to clutter up the Registry, but is there another way?  */
    char regPath[ 1024 ];

    /* Get absolute path to service manager */
    sprintf( regPath, "SYSTEM\\CurrentControlSet\\Services\\Eventlog\\Application\\%s", loginfoSourceName );

    if( RegDeleteKey( HKEY_LOCAL_MACHINE, regPath ) == ERROR_SUCCESS )
        return 0;

    return -1; /* Failure */
#else
    return 0;
#endif
}

void sendEventlogMessage( int source_id, int level, const char *szBuff ) {
#ifdef WIN32
    char   header[16];
    const char   **strings;
    WORD   eventType;
    HANDLE handle;
    WORD   eventID, categoryID;
    int    result;

    strings = (char **) malloc( 3 * sizeof( char * ) );
    if (!strings) {
        printf("Out of memory is logging code (SEM1)\n");
        return;
    }

    /* Build the source header */
    switch( source_id ) {
    case WRAPPER_SOURCE_WRAPPER:
        sprintf( header, "wrapper" );
    break;

    case WRAPPER_SOURCE_PROTOCOL:
        sprintf( header, "wrapperp" );
    break;

    default:
        sprintf( header, "jvm %d", source_id );
    break;
    }

    /* Build event type by level */
    switch( level ) {
    case LEVEL_ADVICE: /* Will not get in here. */
    case LEVEL_FATAL:
        eventType = EVENTLOG_ERROR_TYPE;
    break;

    case LEVEL_ERROR:
    case LEVEL_WARN:
        eventType = EVENTLOG_WARNING_TYPE;
    break;

    case LEVEL_STATUS:
    case LEVEL_INFO:
    case LEVEL_DEBUG:
        eventType = EVENTLOG_INFORMATION_TYPE;
    break;
    }

    /* Set the category id to the appropriate resource id. */
    if ( source_id == WRAPPER_SOURCE_WRAPPER ) {
        categoryID = MSG_EVENT_LOG_CATEGORY_WRAPPER;
    } else if ( source_id == WRAPPER_SOURCE_PROTOCOL ) {
        categoryID = MSG_EVENT_LOG_CATEGORY_PROTOCOL;
    } else {
        /* Source is a JVM. */
        switch ( source_id ) {
        case 1:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM1;
            break;

        case 2:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM2;
            break;

        case 3:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM3;
            break;

        case 4:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM4;
            break;

        case 5:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM5;
            break;

        case 6:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM6;
            break;

        case 7:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM7;
            break;

        case 8:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM8;
            break;

        case 9:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVM9;
            break;

        default:
            categoryID = MSG_EVENT_LOG_CATEGORY_JVMXX;
            break;
        }
    }

    /* Place event in eventlog */
    strings[0] = header;
    strings[1] = szBuff;
    strings[2] = 0;
    eventID = level;

    handle = RegisterEventSource( NULL, loginfoSourceName );
    if( !handle )
        return;

    result = ReportEvent(
        handle,                   /* handle to event log */
        eventType,                /* event type */
        categoryID,                  /* event category */
        MSG_EVENT_LOG_MESSAGE,    /* event identifier */
        NULL,                     /* user security identifier */
        2,                        /* number of strings to merge */
        0,                        /* size of binary data */
        (const char **) strings,  /* array of strings to merge */
        NULL                      /* binary data buffer */
    );
    if (result == 0) {
        /* If there are any errors accessing the event log, like it is full, then disable its output. */
        setSyslogLevelInt(LEVEL_NONE);

        /* Recurse so this error gets set in the log file and console.  The syslog
         *  output has been disabled so we will not get back here. */
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Unable to write to the EventLog due to: %s", getLastErrorText());
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR, "Internally setting wrapper.syslog.loglevel=NONE to prevent further messages.");
    }

    DeregisterEventSource( handle );

    free( (void *) strings );
    strings = NULL;
#endif
}

void sendLoginfoMessage( int source_id, int level, const char *szBuff ) {
#ifndef WIN32 /* If UNIX */
    int eventType;

    /* Build event type by level */
    switch( level ) {
        case LEVEL_FATAL:
            eventType = LOG_CRIT;
        break;

        case LEVEL_ERROR:
            eventType = LOG_ERR;
        break;

        case LEVEL_WARN:
        case LEVEL_STATUS:
            eventType = LOG_NOTICE;
        break;

        case LEVEL_INFO:
            eventType = LOG_INFO;
        break;

        case LEVEL_DEBUG:
            eventType = LOG_DEBUG;
        break;

        default:
            eventType = LOG_DEBUG;
    }

    openlog( loginfoSourceName, LOG_PID | LOG_NDELAY, currentLogfacilityLevel );
    syslog( eventType, "%s", szBuff );
    closelog( );
#endif
}

#ifdef WIN32
int vWriteToConsoleBufferSize = 100;
char *vWriteToConsoleBuffer = NULL;
void vWriteToConsole( char *lpszFmt, va_list vargs ) {
    int cnt;
    DWORD wrote;

    /* This should only be called if consoleStdoutHandle is set. */
    if ( consoleStdoutHandle == NULL ) {
        return;
    }

    if ( vWriteToConsoleBuffer == NULL ) {
        vWriteToConsoleBuffer = (char *)malloc( vWriteToConsoleBufferSize * sizeof(char) );
        if (!vWriteToConsoleBuffer) {
            printf("Out of memory is logging code (WTC1)\n");
            return;
        }
    }

    /* The only way I could figure out how to write to the console
     *  returned by AllocConsole when running as a service was to
     *  do all of this special casing and use the handle to the new
     *  console's stdout and the WriteConsole function.  If anyone
     *  puzzling over this code knows a better way of doing this
     *  let me know.
     * WriteConsole takes a fixed buffer and does not do any expansions
     *  We need to prepare the string to be displayed ahead of time.
     *  This means storing the message into a temporary buffer.  The
     *  following loop will expand the global buffer to hold the current
     *  message.  It will grow as needed to handle any arbitrarily large
     *  user message.  The buffer needs to be able to hold all available
     *  characters + a null char. */
    while ( ( cnt = _vsnprintf( vWriteToConsoleBuffer, vWriteToConsoleBufferSize - 1, lpszFmt, vargs ) ) < 0 ) {
        /* Expand the size of the buffer */
        free( vWriteToConsoleBuffer );
        vWriteToConsoleBufferSize += 100;
        vWriteToConsoleBuffer = (char *)malloc( vWriteToConsoleBufferSize * sizeof(char) );
        if (!vWriteToConsoleBuffer) {
            printf("Out of memory is logging code (WTC2)\n");
            return;
        }
    }

    /* We can now write the message. */
    WriteConsole(consoleStdoutHandle, vWriteToConsoleBuffer, (DWORD)strlen( vWriteToConsoleBuffer ), &wrote, NULL);
}
void writeToConsole( char *lpszFmt, ... ) {
    va_list        vargs;

    va_start( vargs, lpszFmt );
    vWriteToConsole( lpszFmt, vargs );
    va_end( vargs );
}
#endif

/**
 * Rolls log files using the ROLLNUM system.
 */
void rollLogs() {
    int i;
    char rollNum[11];
    struct stat fileStat;
    int result;

    if (!logFilePath) {
        return;
    }
    
    /* If the log file is currently open, it needs to be closed. */
    if (logfileFP != NULL) {
#ifdef _DEBUG
        printf("Closing logfile so it can be rolled...\n");
#endif

        fclose(logfileFP);
        logfileFP = NULL;
        currentLogFileName[0] = '\0';
    }
    
#ifdef _DEBUG
    printf("Rolling log files...\n");
#endif
    
    /* We don't know how many log files need to be rotated yet, so look. */
    i = 0;
    do {
        i++;
        sprintf(rollNum, "%d", i);
        generateLogFileName(workLogFileName, logFilePath, NULL, rollNum);
        result = stat(workLogFileName, &fileStat);
#ifdef _DEBUG
        if (result == 0) {
            printf("Rolled log file %s exists.\n", workLogFileName);
        }
#endif
    } while((result == 0) && ((logFileMaxLogFiles <= 0) || (i < logFileMaxLogFiles)));
    
    /* Remove the file with the highest index if it exists */
    if (remove(workLogFileName)) {
        if (getLastError() == 2) {
            /* The file did not exist. */
        } else if (getLastError() == 3) {
            /* The path did not exist. */
        } else {
            printf("Unable to delete old log file: %s (%s)\n", workLogFileName, getLastErrorText());
        }
    }
    
    /* Now, starting at the highest file rename them up by one index. */
    for (; i > 1; i--) {
        strcpy(currentLogFileName, workLogFileName);
        sprintf(rollNum, "%d", i - 1);
        generateLogFileName(workLogFileName, logFilePath, NULL, rollNum);

        if (rename(workLogFileName, currentLogFileName) != 0) {
            if (errno == 13) {
                /* Don't log this as with other errors as that would cause recursion. */
                printf("Unable to rename log file %s to %s.  File is in use by another application.\n",
                    workLogFileName, currentLogFileName);
            } else {
                /* Don't log this as with other errors as that would cause recursion. */
                printf("Unable to rename log file %s to %s. (%s)\n",
                    workLogFileName, currentLogFileName, getLastErrorText());
            }
            return;
        }
#ifdef _DEBUG
        else {
            printf("Renamed %s to %s\n", workLogFileName, currentLogFileName);
        }
#endif
    }

    /* Rename the current file to the #1 index position */
    generateLogFileName(currentLogFileName, logFilePath, NULL, NULL);
    if (rename(currentLogFileName, workLogFileName) != 0) {
        if (getLastError() == 2) {
            /* File does not yet exist. */
        } else if (getLastError() == 3) {
            /* Path does not yet exist. */
        } else if (errno == 13) {
            /* Don't log this as with other errors as that would cause recursion. */
            printf("Unable to rename log file %s to %s.  File is in use by another application.\n",
                currentLogFileName, workLogFileName);
        } else {
            /* Don't log this as with other errors as that would cause recursion. */
            printf("Unable to rename log file %s to %s. (%s)\n",
                currentLogFileName, workLogFileName, getLastErrorText());
        }
        return;
    }
#ifdef _DEBUG
    else {
        printf("Renamed %s to %s\n", currentLogFileName, workLogFileName);
    }
#endif
}

void limitLogFileCountHandleFile(const char *currentFile, const char *testFile, char **latestFiles, int count) {
    int i, j;
    int result;

#ifdef _DEBUG
    printf("  limitLogFileCountHandleFile(%s, %s, latestFiles, %d)\n", currentFile, testFile, count);
#endif

    if (strcmp(testFile, currentFile) > 0) {
        /* Newer than the current file.  Ignore it. */
#ifdef _DEBUG
        printf("    Newer Ignore\n");
#endif
        return;
    }
    
    /* Decide where in the array of files the file should be located. */
    for (i = 0; i < count; i++) {
#ifdef _DEBUG
        printf("    i=%d\n", i);
#endif
        if (latestFiles[i] == NULL) {
#ifdef _DEBUG
            printf("    Store at index  %d\n", i);
#endif
            latestFiles[i] = malloc(sizeof(char) * (strlen(testFile) + 1));
            if (!latestFiles[i]) {
                printf("Out of memory is logging code (LLFCHF1)\n");
                return;
            }
            strcpy(latestFiles[i], testFile);
            return;
        } else {
            result = strcmp(latestFiles[i], testFile);
            if (result == 0) {
                /* Ignore. */
#ifdef _DEBUG
                printf("    Duplicate at index  %d\n", i);
#endif
                return;
            } else if (result < 0) {
                /* test file is newer than the one in the list, shift all files up in the array. */
                for (j = count - 1; j >= i; j--) {
                    if (latestFiles[j] != NULL) {
                        if (j < count - 1) {
                            /* Move the file up. */
                            latestFiles[j + 1] = latestFiles[j];
                            latestFiles[j] = NULL;
                        } else {
                            /* File needs to be deleted as it can't be moved up. */
#ifdef _DEBUG
                            printf("    Delete old %s\n", latestFiles[j]);
#endif
                            if (remove(latestFiles[j])) {
                                printf("Unable to delete old log file: %s (%s)\n",
                                    latestFiles[j], getLastErrorText());
                            }
                            free(latestFiles[j]);
                            latestFiles[j] = NULL;
                        }
                    }
                }

#ifdef _DEBUG
                printf("    Insert at index  %d\n", i);
#endif
                latestFiles[i] = malloc(sizeof(char) * (strlen(testFile) + 1));
                if (!latestFiles[i]) {
                    printf("Out of memory is logging code (LLFCHF2)\n");
                    return;
                }
                strcpy(latestFiles[i], testFile);
                return;
            }
        }
    }
    
    /* File could not be added to the list because it was too old.  Delete. */
#ifdef _DEBUG
    printf("    Delete %s\n", testFile);
#endif
    if (remove(testFile)) {
        printf("Unable to delete old log file: %s (%s)\n",
            testFile, getLastErrorText());
    }
}

/**
 * Does a search for all files matching the specified pattern and deletes all
 *  but the most recent 'count' files.  The files are sorted by their names.
 */
void limitLogFileCount(const char *current, const char *pattern, int count) {
    char** latestFiles;
    int i;
#ifdef WIN32
    char* path;
    char* c;
    intptr_t handle;
    struct _finddata_t fblock;
    char* testFile;
#else
    glob_t g;
#endif

#ifdef _DEBUG
    printf("limitLogFileCount(%s, %d)\n", pattern, count);
#endif

    latestFiles = malloc(sizeof(char *) * count);
    if (!latestFiles) {
        printf("Out of memory is logging code (LLFC1)\n");
        return;
    }
    for (i = 0; i < count; i++) {
        latestFiles[i] = NULL;
    }
    /* Set the first element to the current file so things will work correctly if it already
     *  exists. */
    latestFiles[0] = malloc(sizeof(char) * (strlen(current) + 1));
    if (!latestFiles[0]) {
        printf("Out of memory is logging code (LLFC2)\n");
        return;
    }
    strcpy(latestFiles[0], current);

#ifdef WIN32
    path = malloc(sizeof(char) * (strlen(pattern) + 1));
    if (!path) {
        printf("Out of memory is logging code (LLFC3)\n");
        return;
    }

    /* Extract any path information from the beginning of the file */
    strcpy(path, pattern);
    c = max(strrchr(path, '\\'), strrchr(path, '/'));
    if (c == NULL) {
        path[0] = '\0';
    } else {
        c[1] = '\0'; /* terminate after the slash */
    }

    if ((handle = _findfirst(pattern, &fblock)) <= 0) {
        if (errno == ENOENT) {
            /* No matching files found. */
        } else {
            /* Encountered an error of some kind. */
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ERROR,
                "Error in findfirst for log file purge: %s", pattern);
        }
    } else {
        testFile = malloc(sizeof(char) * (strlen(path) + strlen(fblock.name) + 1));
        if (!testFile) {
            printf("Out of memory is logging code (LLFC4)\n");
            return;
        }
        sprintf(testFile, "%s%s", path, fblock.name);
        limitLogFileCountHandleFile(current, testFile, latestFiles, count);
        free(testFile);
        testFile = NULL;

        /* Look for additional entries */
        while (_findnext(handle, &fblock) == 0) {
            testFile = malloc(sizeof(char) * (strlen(path) + strlen(fblock.name) + 1));
            if (!testFile) {
                printf("Out of memory is logging code (LLFC5)\n");
                return;
            }
            sprintf(testFile, "%s%s", path, fblock.name);
            limitLogFileCountHandleFile(current, testFile, latestFiles, count);
            free(testFile);
            testFile = NULL;
        }

        /* Close the file search */
        _findclose(handle);
    }

    free(path);
#else
    /* Wildcard support for unix */
    glob(pattern, GLOB_MARK | GLOB_NOSORT, NULL, &g);

    if (g.gl_pathc > 0) {
        for (i = 0; i < g.gl_pathc; i++) {
            limitLogFileCountHandleFile(current, g.gl_pathv[i], latestFiles, count);
        }
    } else {
        /* No matching files. */
    }

    globfree(&g);
#endif

#ifdef _DEBUG
    printf("  Sorted file list:\n");
#endif
    for (i = 0; i < count; i++) {
        if (latestFiles[i]) {
#ifdef _DEBUG
            printf("    latestFiles[%d]=%s\n", i, latestFiles[i]);
#endif
            free(latestFiles[i]);
        } else {
#ifdef _DEBUG
            printf("    latestFiles[%d]=NULL\n", i);
#endif
        }
    }
    free(latestFiles);
}

/**
 * Check to see whether or not the log file needs to be rolled.
 *  This is only called when synchronized.
 */
void checkAndRollLogs(const char *nowDate) {
    long position;
    struct stat fileStat;
    
    /* Depending on the roll mode, decide how to roll the log file. */
    if (logFileRollMode & ROLL_MODE_SIZE) {
        /* Roll based on the size of the file. */
        if (logFileMaxSize <= 0)
            return;
        
        /* Find out the current size of the file.  If the file is currently open then we need to
         *  use ftell to make sure that the buffered data is also included. */
        if (logfileFP != NULL) {
            /* File is open */
            if ((position = ftell(logfileFP)) < 0) {
                printf("Unable to get the current logfile size with ftell: %s\n", getLastErrorText());
                return;
            }
        } else {
            /* File is not open */
            if (stat(logFilePath, &fileStat) != 0) {
                if (getLastError() == 2) {
                    /* File does not yet exist. */
                    position = 0;
                } else if (getLastError() == 3) {
                    /* Path does not exist. */
                    position = 0;
                } else {
                    printf("Unable to get the current logfile size with stat: %s\n", getLastErrorText());
                    return;
                }
            } else {
                position = fileStat.st_size;
            }
        }
        
        /* Does the log file need to rotated? */
        if (position >= logFileMaxSize) {
            rollLogs();
        }
    } else if (logFileRollMode & ROLL_MODE_DATE) {
        /* Roll based on the date of the log entry. */
        if (strcmp(nowDate, logFileLastNowDate) != 0) {
            /* The date has changed.  Close the file. */
            if (logfileFP != NULL) {
#ifdef _DEBUG
                printf("Closing logfile because the date changed...\n");
#endif
        
                fclose(logfileFP);
                logfileFP = NULL;
                currentLogFileName[0] = '\0';
            }
            
            /* This will happen just before a new log file is created.
             *  Check the maximum file count. */
            if (logFileMaxLogFiles > 0) {
                generateLogFileName(currentLogFileName, logFilePath, nowDate, NULL);
                generateLogFileName(workLogFileName, logFilePath, "????????", NULL);
                
                limitLogFileCount(currentLogFileName, workLogFileName, logFileMaxLogFiles + 1);
                
                currentLogFileName[0] = '\0';
                workLogFileName[0] = '\0';
            }
        }
    }
}

/*
 * Because of synchronization issues, it is not safe to immediately log messages
 *  that are logged from within signal handlers.  This is because it is possible
 *  that the signal was thrown while we were logging another message.
 *
 * To work around this, it is nessary to store such messages into a queue and
 *  then log them later when it is safe.
 *
 * Messages logged from signal handlers are relatively rare so this does not
 *  need to be all that efficient.
 */
void log_printf_queueInner( int source_id, int level, char *buffer ) {
    int threadId;
    int localWriteIndex;
    int localReadIndex;

#ifdef _DEBUG
    printf( "LOG ENQUEUE[%d]: %s\n", queueWriteIndex, buffer );
#endif

    /* Get the thread id here to keep the time below to a minimum. */
    threadId = getThreadId();

    /* NOTE - This function is not synchronized.  So be very careful and think
     *        about what would happen if the queueWrapped and or queueWriteIndex
     *        values were to be changed by another thread.  We need to be sure
     *        that such changes would not result in a crash of any kind. */
    localWriteIndex = queueWriteIndex[threadId];
    localReadIndex = queueReadIndex[threadId];
    if ((localWriteIndex == localReadIndex - 1) || ((localWriteIndex == QUEUE_SIZE - 1) && (localReadIndex == 0))) {
        printf("WARNING log queue overflow for thread[%d]:%d:%d dropping entry. %s\n", threadId, localWriteIndex, localReadIndex, buffer);
        return;
    }

    /* Clear any old buffer, only necessary starting on the second time through the queue buffers. */
    if ( queueWrapped[threadId] ) {
        free( queueMessages[threadId][queueWriteIndex[threadId]] );
        queueMessages[threadId][queueWriteIndex[threadId]] = NULL;
    }

    /* Store a reference to the buffer in the queue.  It will be freed later. */
    queueMessages[threadId][queueWriteIndex[threadId]] = buffer;

    /* Store additional information about the call. */
    queueSourceIds[threadId][queueWriteIndex[threadId]] = source_id;
    queueLevels[threadId][queueWriteIndex[threadId]] = level;

    /* Lastly increment and wrap the write index. */
    queueWriteIndex[threadId]++;
    if ( queueWriteIndex[threadId] >= QUEUE_SIZE ) {
        queueWriteIndex[threadId] = 0;
        queueWrapped[threadId] = 1;
    }
}

void log_printf_queue( int useQueue, int source_id, int level, const char *lpszFmt, ... ) {
    va_list     vargs;
    int         count;
    char        *buffer;
    int         bufferSize = 100;
    int         itFit = 0;

    /* Start by processing any arguments so that we can store a simple string. */

    /* This is a pain to do efficiently without using a static buffer.  But
     *  this call is only used in cases where we can not safely use such buffers.
     *  We do not know how big a buffer we need, so loop, growing it until we get
     *  a size that works.  The initial size will be big enough for most messages
     *  that this function is called with, but not so big as to be any less
     *  efficient than necessary. */
    do {
        buffer = malloc( sizeof( char ) * bufferSize );
        if (!buffer) {
            printf("Out of memory is logging code (PQ1)\n");
            return;
        }

        /* Before we can store the string, we need to know how much space is required to do so. */
        va_start( vargs, lpszFmt );
#ifdef WIN32
        count = _vsnprintf( buffer, bufferSize, lpszFmt, vargs );
#else
        count = vsnprintf( buffer, bufferSize, lpszFmt, vargs );
#endif
        va_end( vargs );

        /*
        printf( "count: %d bufferSize=%d\n", count, bufferSize );
        */

        /* On UNIX, the required size will be returned if it is too small.
         *  On Windows however, we always get -1.  Even worse, if the size
         *  is exactly correct then the buffer will not be null terminated.
         *  In either case, resize the buffer as best we can and retry. */
        if ( count < 0 ) {
            /* Not big enough, expand the buffer size and try again. */
            bufferSize += 100;
        } else if ( count >= bufferSize ) {
            /* Not big enough, but we know how big it will need to be. */
            bufferSize = count + 1;
        } else {
            itFit = 1;
        }

        if ( !itFit ) {
            /* Will need to try again, so free the buffer. */
            free( buffer );
            buffer = NULL;
        }
    } while ( !itFit );

    /* Now decide what to actually do with the message. */
    if ( useQueue ) {
        /* There is a risk here due to synchronization problems.  But these queued messages
         *  should only be called rarely and then only from signal threads so it should be
         *  ok.  If more than one thread gets into the following function at the same time
         *  it would not be good.  But the problem is that we can not do synchronization
         *  here as that could lead to deadlocks.  The contents of this function tries to
         *  be as careful as possible about checking its values to make sure that any
         *  synchronization issues result only in a malformed message and not a crash. */
        log_printf_queueInner( source_id, level, buffer );
        /* The buffer will be freed by the queue at a later point. */
    } else {
        /* Use the normal logging function.  There is some extra overhead
         *  here because the message is expanded twice, but this greatly
         *  simplifies the code over other options and makes it much less
         *  error prone. */
        log_printf( source_id, level, "%s", buffer );
        free( buffer );
    }
}

/**
 * Perform any required logger maintenance at regular intervals.
 *
 * One operation is to log any queued messages.  This must be done very
 *  carefully as it is possible that a signal handler could be thrown at
 *  any time as this function is being executed.
 */
void maintainLogger() {
    int localWriteIndex;
    int source_id;
    int level;
    int threadId;
    char *buffer;

    for ( threadId = 0; threadId < WRAPPER_THREAD_COUNT; threadId++ ) {
        /* NOTE - The queue variables are not synchronized so we need to access them
         *        carefully and assume that data could possibly be corrupted. */
        localWriteIndex = queueWriteIndex[threadId]; /* Snapshot the value to maintain a constant reference. */
        if ( queueReadIndex[threadId] != localWriteIndex ) {
            /* Lock the logging mutex. */
            if (lockLoggingMutex()) {
                return;
            }
    
            /* Empty the queue of any logged messages. */
            localWriteIndex = queueWriteIndex[threadId]; /* Snapshot the value to maintain a constant reference. */
            while ( queueReadIndex[threadId] != localWriteIndex ) {
                /* Snapshot the values in the queue at that index. */
                source_id = queueSourceIds[threadId][queueReadIndex[threadId]];
                level = queueLevels[threadId][queueReadIndex[threadId]];
                buffer = queueMessages[threadId][queueReadIndex[threadId]];
    
                /* Now we have safe values.  Everything below this is thread safe. */
    
                if (buffer) {
                    /* non null, assume it is valid. */
    
#ifdef _DEBUG
                    printf( "LOG QUEUED[%d]: %s\n", queueReadIndex[threadId], buffer );
#endif

                    log_printf_message( source_id, level, threadId, TRUE, buffer );
                    /*
                    printf("  Queue lw=%d, qw=%d, qr=%d\n", localWriteIndex, queueWriteIndex[threadId], queueReadIndex[threadId]);
                    */
                } else {
#ifdef _DEBUG
                    printf( "LOG QUEUED[%d]: <NULL> SYNCHRONIZATION CONFLICT!\n", queueReadIndex[threadId] );
#endif
                }
                queueReadIndex[threadId]++;
                if ( queueReadIndex[threadId] >= QUEUE_SIZE ) {
                    queueReadIndex[threadId] = 0;
                }
            }
    
            /* Release the lock we have on the logging mutex so that other threads can get in. */
            if (releaseLoggingMutex()) {
                return;
            }
        }
    }
}

