
/*
  QM Compatibility Routines
  by Jay Oliveri
  Copyright 2002 QM LLC.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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

#define OPEN_MODE_READ  (_O_RDONLY | _O_BINARY)
#define OPEN_MODE_WRITE 0

#include <malloc.h>
#include <process.h>
#include <winsock.h>
#include <io.h>

#else

/* UNIX includes that do not correspond on WINDOWS go here */
#define OPEN_MODE_READ  0
#define OPEN_MODE_WRITE 0

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

extern _C_ int   crLaunchThread(FP, void *);
extern _C_ void  crQuitThread(char *);
extern _C_ int   crSleep(unsigned int, unsigned int);

#endif /* End of COMPAT_H */
