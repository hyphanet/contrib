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

#ifndef _LOGGER_H
#define _LOGGER_H

#ifdef WIN32
#include <windows.h>
#endif

#ifndef DWORD
#define DWORD unsigned long
#endif

/* * * Log source constants * * */

#define WRAPPER_SOURCE_WRAPPER -1
#define WRAPPER_SOURCE_PROTOCOL -2

/* * * Log thread constants * * */
/* These are indexes in an array so they must be sequential, start
 *  with zero and be one less than the final WRAPPER_THREAD_COUNT */
#define WRAPPER_THREAD_CURRENT  -1
#define WRAPPER_THREAD_SIGNAL   0
#define WRAPPER_THREAD_MAIN     1
#define WRAPPER_THREAD_SRVMAIN  2
#define WRAPPER_THREAD_TIMER    3
#define WRAPPER_THREAD_COUNT    4

#define MAX_LOG_SIZE 4096

/* * * Log level constants * * */

/* No logging at all. */
#define LEVEL_NONE   8

/* Advisor messages which should always be displayed.  These never go to the syslog. */
#define LEVEL_ADVICE 7

/* Too many restarts, unable to start etc. Case when the Wrapper is forced to exit. */
#define LEVEL_FATAL  6

/* JVM died, hung messages */
#define LEVEL_ERROR  5

/* Warning messages. */
#define LEVEL_WARN   4

/* Started, Stopped, Restarted messages. */
#define LEVEL_STATUS 3

/* Copyright message. and logged console output. */
#define LEVEL_INFO   2

/* Current debug messages */
#define LEVEL_DEBUG  1

/* Unknown level */
#define LEVEL_UNKNOWN  0

/* * * Log file roll mode constants * * */
#define ROLL_MODE_UNKNOWN         0
#define ROLL_MODE_NONE            1
#define ROLL_MODE_SIZE            2
#define ROLL_MODE_WRAPPER         4
#define ROLL_MODE_JVM             8
#define ROLL_MODE_SIZE_OR_WRAPPER ROLL_MODE_SIZE + ROLL_MODE_WRAPPER
#define ROLL_MODE_SIZE_OR_JVM     ROLL_MODE_SIZE + ROLL_MODE_JVM
#define ROLL_MODE_DATE            16

#define ROLL_MODE_DATE_TOKEN      "YYYYMMDD"

#ifdef WIN32
extern void setConsoleStdoutHandle( HANDLE stdoutHandle );
#endif

/* * * Function predeclaration * * */
#ifdef WIN32
#define strcmpIgnoreCase(str1, str2) _stricmp(str1, str2)
#else
#define strcmpIgnoreCase(str1, str2) strcasecmp(str1, str2)
#endif

extern void outOfMemory(const char *context, int id);

/* * Logfile functions * */
extern void setLogfilePath( const char *log_file_path );
extern const char *getLogfilePath();
extern int getLogfileRollModeForName( const char *logfileRollName );
extern void setLogfileRollMode( int log_file_roll_mode );
extern int getLogfileRollMode();
extern void setLogfileUmask( int log_file_umask );
extern void setLogfileFormat( const char *log_file_format );
extern void setLogfileLevelInt( int log_file_level );
extern int getLogfileLevelInt();
extern void setLogfileLevel( const char *log_file_level );
extern void setLogfileMaxFileSize( const char *max_file_size );
extern void setLogfileMaxFileSizeInt( int max_file_size );
extern void setLogfileMaxLogFiles( int max_log_files );
extern DWORD getLogfileActivity();
extern void closeLogfile();
extern void setLogfileAutoClose(int autoClose);
extern void flushLogfile();

/* * Console functions * */
extern void setConsoleLogFormat( const char *console_log_format );
extern void setConsoleLogLevelInt( int console_log_level );
extern int getConsoleLogLevelInt();
extern void setConsoleLogLevel( const char *console_log_level );
extern void setConsoleFlush( int flush );

/* * Syslog/eventlog functions * */
extern void setSyslogLevelInt( int loginfo_level );
extern int getSyslogLevelInt();
extern void setSyslogLevel( const char *loginfo_level );
#ifndef WIN32
extern void setSyslogFacility( const char *loginfo_level );
#endif
extern void setSyslogEventSourceName( const char *event_source_name );
extern int registerSyslogMessageFile( );
extern int unregisterSyslogMessageFile( );

extern int getLowLogLevel();

/* * General log functions * */
extern int initLogging();
extern int disposeLogging();
extern void rollLogs();
extern int getLogLevelForName( const char *logLevelName );
#ifndef WIN32
extern int getLogFacilityForName( const char *logFacilityName );
#endif
extern void logRegisterThread( int thread_id );

/**
 * The log_printf function logs a message to the configured log targets.
 *
 * This method can be used safely in most cases.  See the log_printf_queue
 *  funtion for the exceptions.
 */
extern void log_printf( int source_id, int level, const char *lpszFmt, ... );

/**
 * The log_printf_queue function is less efficient than the log_printf
 *  function and will cause logged messages to be logged out of order from
 *  those logged with log_printf because the messages are queued and then
 *  logged from another thread.
 *
 * Use of this function is required in cases where the thread may possibly
 *  be a signal callback.  In these cases, it is possible for the original
 *  thread to have been suspended within a log_printf call.  If the signal
 *  thread then attempted to call log_printf, it would result in a deadlock.
 */
extern void log_printf_queue( int useQueue, int source_id, int level, const char *lpszFmt, ... );

extern char* getLastErrorText();
extern int getLastError();
extern void maintainLogger();

#endif
