/*
  compat.h
  Copyright 2002 by Jay Oliveri

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

#ifndef WINDOWS
#include <pthread.h>
#endif

typedef void (*FP) (void *);


/**************************************************************************
  Function definitions
**************************************************************************/
static int crLaunchThread(void (*f)(void *), void *parms)
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

static void crQuitThread(char *s)
{
#ifdef WINDOWS
  _endthread();

#else
  pthread_exit(s);
#endif
}

static int crSleep(unsigned int seconds, unsigned int nanoseconds)
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
