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

static int toUnix(char *path);

/* I'm not sure it's a good idea to allow logging in fcpStartup */

int fcpStartup(char *logfile, int retry, int log_verbosity)
{
	char buf[513];

	/* pass a bum value here and it's set to SILENT */
	_fcpVerbosity = (((log_verbosity >= 0) && (log_verbosity <= 4)) ? log_verbosity : FCP_LOG_SILENT);
	_fcpRetry     = (retry >= 0 ? retry : 0);

#ifdef WIN32
	{
		WORD wVersionRequested;
		WSADATA wsaData;

		SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
		wVersionRequested = MAKEWORD(2, 0);

		if (WSAStartup(wVersionRequested, &wsaData) != 0)
			return -1;
	}

	snprintf(buf, 512, "%s", getenv("USERPROFILE"));
	_fcpHomeDir = strdup(buf);

	snprintf(buf, 512, "%s\\local settings\\temp", _fcpHomeDir);
	_fcpTmpDir = strdup(buf);

	/* both of these paths must have their \'s converted to /'s */
	toUnix(_fcpTmpDir);
	toUnix(_fcpHomeDir);

#else

	_fcpTmpDir = strdup("/tmp");
	_fcpHomeDir = strdup(getenv("HOME"));

#endif
	
	/* now finish initialization of the logfile, after the settings above */
	if (logfile) {
		if (!(_fcpLogStream = fopen(logfile, "a"))) {

			_fcpLog(FCP_LOG_VERBOSE, "Could not open logfile \"%s\"", logfile);
			return -1;
		}
	}

	_fcpLog(FCP_LOG_DEBUG, "Home Directory: %s", _fcpHomeDir);
	_fcpLog(FCP_LOG_DEBUG, "Temp Directory: %s", _fcpTmpDir);

	_fcpSplitblock = L_BLOCK_SIZE;

	return 0;
}


void fcpTerminate(void)
{

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpTerminate()");

#ifdef WIN32
	{
	/* on Win32, we gotta call the winsock exit function */
	}
#endif

	if (_fcpTmpDir) free(_fcpTmpDir);
	if (_fcpHomeDir) free(_fcpHomeDir);

	if (_fcpLogStream) {
		fclose(_fcpLogStream);
		_fcpLogStream = 0;
	}
	
	return;
}

static int toUnix(char *path)
{
	int i;

	for (i=0; path[i]; i++)
		if (path[i] == '\\') path[i] = '/';

	return 0;
}

