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

#include "ez_sys.h"

#ifdef DMALLOC
int _fcpDMALLOC;
#endif

/* I'm not sure it's a good idea to allow logging in fcpStartup */

int fcpStartup(FILE *logstream, int verbosity)
{

#ifdef WIN32
	{
		WORD wVersionRequested;
		WSADATA wsaData;

		SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
		wVersionRequested = MAKEWORD(2, 0);
		
		if (WSAStartup(wVersionRequested, &wsaData) != 0)
			return -1;
	}
#endif

	_fcpOpenLog(logstream, verbosity);
	_fcpLog(FCPT_LOG_DEBUG, "Exiting fcpStartup() successfully");

	return 0;
}

void fcpTerminate(void)
{

	_fcpLog(FCPT_LOG_DEBUG, "Entered fcpTerminate()");
	_fcpCloseLog();

#ifdef WIN32
	{
		WSACleanup();
	}
#endif
}

