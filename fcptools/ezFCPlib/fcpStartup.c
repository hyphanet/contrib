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


extern int   _fcpVerbosity;
extern FILE *_fcpLogStream;

extern char *_fcpTmpDir;
extern char *_fcpHomeDir;


/* I'm not sure it's a good idea to allow logging in fcpStartup */

int fcpStartup(char *logfile, int log_verbosity)
{
	/* pass a bum value here and it's set to SILENT */
	_fcpVerbosity = (((log_verbosity >= 0) && (log_verbosity <= 4)) ? log_verbosity : FCP_LOG_SILENT);

#ifdef WIN32
	{
		WORD wVersionRequested;
		WSADATA wsaData;

		SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
		wVersionRequested = MAKEWORD(2, 0);

		if (WSAStartup(wVersionRequested, &wsaData) != 0)
			return -1;
	}

	_fcpTmpDir = strdup(getenv("TEMP"));

	/* Maybe this needs to be re-thought */
	_fcpHomeDir = getenv("USERPROFILE");

#else

	_fcpTmpDir = strdup("/tmp");
	_fcpHomeDir = getenv("HOME");

#endif

	/* now finish initialization of the logfile, after the settings above */
		if (logfile) {
		if (!(_fcpLogStream = fopen(logfile, "a")))
			return -1;
	}

	return 0;
}

void fcpTerminate(void)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpTerminate()");

	if (_fcpTmpDir) free(_fcpTmpDir);
	if (_fcpHomeDir) free(_fcpHomeDir);

	if (_fcpLogStream) {
		fclose(_fcpLogStream);
		_fcpLogStream = 0;
	}
	
	return;
}
