/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
  CopyLeft (c) 2001 by David McNab
	
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

#include <string.h>

int fcpStartup(void)
{
#ifdef WIN32
	/* define our Win32 specific vars here :) */

	WORD wVersionRequested;
	WSADATA wsaData;
#endif

	_fcpLog(FCP_LOG_VERBOSE, "Entered fcpStartup()");

#ifdef WIN32
	SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
	wVersionRequested = MAKEWORD(2, 0);

	if (WSAStartup(wVersionRequested, &wsaData) != 0) {

		_fcpLog(FCP_LOG_VERBOSE, "Could not initialize the Winsock subsystem");
		return -1;
	}

	_fcpLog(FCP_LOG_VERBOSE, "Initialized Winsock subsystem");

	/* @@@ TODO: implement Win32 API call to properly retrieve the TEMP dir. */
	/* something like libvar = getenv( "LIB" ) */
	_fcpTmpDir = "c:/temp";

#else
	_fcpTmpDir = "/tmp";

#endif

	/* this is like so because of perhaps an undefined behaviour on Win with
		 realloc'ed null pointers */
	_fcpHost = strdup(EZFCP_DEFAULT_HOST);

	return 0;
}


void fcpTerminate(void)
{
	_fcpLog(FCP_LOG_VERBOSE, "Entered fcpTerminate()");

	return;
}

