/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"

#include <stdio.h>
#include <stdarg.h>

extern int fcpLogCallback(int level, char *buf);


/*
  Function:    _fcpLog()

  Arguments:   as for printf

  Description: creates a log message, then passes this to the client callback
  function fcpLogCallback()
*/
void _fcpLog(int level, char *format, ...)
{
    char buf[FCP_LOG_MESSAGE_SIZE];

    /* thanks mjr for the idea */
    va_list ap;
    va_start(ap, format);

    vsnprintf(buf, FCP_LOG_MESSAGE_SIZE, format, ap);

    va_end(ap);
    fcpLogCallback(level, buf);
}
