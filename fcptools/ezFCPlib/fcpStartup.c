/*
  This code is part of FCPTools - an FCP-based client library for Freenet

  CopyLeft (c) 2001 by David McNab

  Developers:
  - David McNab <david@rebirthing.co.nz>
  - Jay Oliveri <ilnero@gmx.net>
  
  Currently maintained by Jay Oliveri <ilnero@gmx.net>
  
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

#include "ezFCPlib.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>


extern char *_fcpHost;
extern char *_fcpTmpDir;

extern unsigned short _fcpPort;
extern int   _fcpHtl;
extern int   _fcpRawmode;
extern int   _fcpVerbosity;
extern FILE *_fcpLogStream;

extern int   _fcpRegress;
extern int   _fcpDeleteLocal;


/* I'm not sure it's a good idea to allow logging in fcpStartup */

int fcpStartup(void)
{
#ifdef WIN32
	/* define our Win32 specific vars here :) */

	WORD wVersionRequested;
	WSADATA wsaData;

	SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
	wVersionRequested = MAKEWORD(2, 0);

	if (WSAStartup(wVersionRequested, &wsaData) != 0)
		return -1;

	/* Get TEMP value from the Environment */
	_fcpTmpDir = strdup(getenv("TEMP"));

#else
	_fcpTmpDir = strdup("/tmp");

#endif

	/*** This section is for both platforms
			 Let's set some reasonable defaults:
	 ***/

	/* this is like so because of perhaps an undefined behaviour on Win with
		 realloc'ed null pointers */
	_fcpHost = strdup(EZFCP_DEFAULT_HOST);

	_fcpPort = EZFCP_DEFAULT_PORT;
  _fcpHtl = EZFCP_DEFAULT_HTL;
	_fcpRawmode = EZFCP_DEFAULT_RAWMODE;
	_fcpVerbosity = EZFCP_DEFAULT_VERBOSITY;
	_fcpLogStream = EZFCP_DEFAULT_LOGSTREAM;
	_fcpRegress = EZFCP_DEFAULT_REGRESS;
	_fcpDeleteLocal = EZFCP_DEFAULT_DELETELOCAL;

	return 0;
}


void fcpTerminate(void)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpTerminate()");

	free(_fcpTmpDir);
	free(_fcpHost);

	return;
}
