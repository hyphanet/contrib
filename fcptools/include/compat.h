/*
  QM Compatibility Routines
  Copyright 2002 QM LLC.

  GPL Declaration to follow..
*/

#ifndef _COMPAT_H
#define _COMPAT_H

/* Place generic statements here if you must ;) */

/* Create Function Pointer type */
typedef void (*FP) (void *);

#ifdef WINDOWS

/* Kludge until someone finds a better way
   Normally this would be 0.4.3 or something similar */
#define VERSION "WINDOWS"

#define write _write
#define open _open
#define read _read
#define close _close
#define mkstemp _mkstemp
#define snprintf _snprintf
#define vsnprintf _vsnprintf

#include <malloc.h>
#include <process.h>
#include <winsock.h>
#include <io.h>

#else

/* UNIX includes that do not correspond on WINDOWS go here */
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <stdint.h>
#include <unistd.h>
#include <pthread.h>

#endif


/* Function Definitions */

#ifdef __cplusplus
#define _C_ "C"
#else
#define _C_
#endif

extern _C_ int   LaunchThread(FP, void *);
extern _C_ void  QuitThread(char *);

extern _C_ int  _fcpSleep(unsigned int, unsigned int);

#endif /* End of COMPAT_H */
