
/*

*/

#ifndef _COMPAT_H
#define _COMPAT_H

/* Place generic statements here if you must ;) */

/* Create Function Pointer type */
typedef void (*FP) (void *);

#ifdef WINDOWS

/* Kludge until someone finds a better way */
#define VERSION "0.4.3"

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

/* Not sure if this is necessary */
/* Change what <windows.h> sets */
#define WIN32_LEAN_AND_MEAN
#define NOGDI
#include <windows.h>

#else /* !WINDOWS */

/* UNIX includes that do not correspond on WINDOWS go here */
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <stdint.h>
#include <unistd.h>
#include <pthread.h>

#endif

/*********************************************************************
 Place all function definitions here
*********************************************************************/

static int LaunchThread(FP f, void *parms)
{
#ifdef WINDOWS
  return _beginthread(f, 0, parms) == -1 ? -1 : 0;

#else
  pthread_t pth;
  pthread_attr_t attr;
  
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  return pthread_create(&pth, &attr, (void *)f, parms);
#endif
}

static void QuitThread()
{
#ifdef WINDOWS
	_endthread();

#else
	pthread_exit("");
#endif
}

static int _fcpSleep(unsigned int seconds, unsigned int nanoseconds)
{
#ifdef WINDOWS
  Sleep(seconds * 1000);
	return 0;

#else
  struct timespec delay;
  struct timespec remain;

  delay.tv_sec = seconds;
  delay.tv_nsec = nanoseconds;

  return nanosleep( &delay, &remain );
#endif

}

#endif /* End of COMPAT_H */
