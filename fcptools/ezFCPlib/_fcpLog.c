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
#include <stdarg.h>

extern int   _fcpVerbosity;
extern FILE *_fcpLogStream;


/* extern int vsnprintf(char *str, size_t size, const char *format, va_list ap); */

/*
  Function:    _fcpLog()
	
  Arguments:   as for printf
	
  Description:
*/
void _fcpLog(int level, char *format, ...)
{
	char buf[FCP_LOG_MESSAGE_SIZE + 1];

	/* thanks mjr for the idea */
	va_list ap;

	/* exit if the message wishes to be ignored */
	if (level > _fcpVerbosity) return;
	
	va_start(ap, format);
	vsnprintf(buf, FCP_LOG_MESSAGE_SIZE, format, ap);
	va_end(ap);

	switch (level) {
	case FCP_LOG_CRITICAL: /*1*/
		fprintf(_fcpLogStream, "CRITICAL: %s\n", buf);
		break;
		
	case FCP_LOG_NORMAL: /*2*/
		fprintf(_fcpLogStream, "%s\n", buf);
		break;
		
	case FCP_LOG_VERBOSE: /*3*/
		fprintf(_fcpLogStream, "%s\n", buf);
		break;
		
	case FCP_LOG_DEBUG: /*4*/
		fprintf(_fcpLogStream, "dbg: %s\n", buf);
		break;
	}
}


/*
  Function: fcpSetLogVerbosity(int verbosity)
*/
void fcpSetLogVerbosity(int verbosity)
{
	if ((verbosity >= 0) && (verbosity <= 4)) {
		_fcpVerbosity = verbosity;
		_fcpLog(FCP_LOG_DEBUG, "Log verbosity changed to %d", _fcpVerbosity);
	}

	return;
}

/*
  Function: fcpSetLog(char *s)

	is stream==NULL, then tie the log to STDOUT
*/
int fcpSetLogStream(FILE *stream)
{
	int ifile = 0;
	
	/* If we have an address, use it as a file */
	if (stream) {

/* Ayyyy Windoze !!! */

/* Must add logic HERE to create file in users home directory */

#ifdef WIN32
		ifile = creat("fcptools.log", O_CREAT | O_APPEND);
#else
		ifile = creat("/tmp/fcptools.log",  O_CREAT | O_APPEND | S_IRUSR | S_IWUSR);
#endif
		
	}
	else {
		_fcpLogStream = stdout;
	}

	return ifile;
}

