/*
	This code is part of FCPTools - an FCP-based client library for Freenet.
	Copyright (C) 2001 by David McNab

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

/*
	I'd like to name all these functions with the prefix "cr" to imply
	they are "compatibility routines", that work on both *nix and Win32
	making use of #ifdef's.
*/


/* TODO: make this shit work on windows */
#include <sys/types.h>
#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <stdio.h>
#include <unistd.h>
#include <pthread.h>
#include <errno.h>

#include <string.h>

#include "ezFCPlib.h"


int   crThreadLaunch(void (*f)(void *), void *parms);
void  crThreadQuit(char *s);
int   crThreadSleep(unsigned int seconds, unsigned int nanoseconds);
int   crSockConnect(hFCP *hfcp);
void  crSockDisconnect(hFCP *hfcp);
char *crTmpFilename(void);


int crThreadLaunch(void (*f)(void *), void *parms)
{
#ifndef WINDOWS
  pthread_t pth;
  pthread_attr_t attr;
  
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  return pthread_create(&pth, &attr, (void *(*)(void *))f, parms);

#else
  return _beginthread(f, 0, parms) == -1 ? -1 : 0;

#endif
}


void crThreadQuit(char *s)
{
#ifndef WINDOWS
  pthread_exit(s);

#else
  _endthread();

#endif
}


int crThreadSleep(unsigned int seconds, unsigned int nanoseconds)
{
#ifndef WINDOWS
  struct timespec delay;
  struct timespec remain;

  delay.tv_sec = seconds;
  delay.tv_nsec = nanoseconds;

  return nanosleep( &delay, &remain );

#else
  Sleep(seconds * 1000);
  return 0;

#endif
}


void crSockDisconnect(hFCP *hfcp)
{
  if (hfcp->socket < 0) return;

#ifndef WINDOWS
	close(hfcp->socket);
#else
	/* This one does extra cleanup within Winsock apparantly */
	closesocket(hfcp->socket);
#endif

	hfcp->socket = -1;
	_fcpNumOpenSockets--;
}


char *crTmpFilename(void)
{
	int search = 1;
  char *filename = 0;

	struct stat st;
  time_t seedseconds;

	filename = (char *)malloc(4097);
  
	time(&seedseconds);
	srand((unsigned int)seedseconds);

	while (search) {
		snprintf(filename, 4096, "%s/eztmp_%x", _fcpTmpDir, (unsigned int)rand());
		_fcpLog(FCP_LOG_DEBUG, "trying to stat: %s", filename);

		if (stat(filename, &st) == -1)
			if (errno == ENOENT) search = 0;
	}

	realloc(filename, strlen(filename) + 1);
	_fcpLog(FCP_LOG_DEBUG, "returning tmpfile: %s", filename);

	return filename;
}

