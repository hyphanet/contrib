/*
  This code is part of FCPTools - an FCP-based client library for Freenet.

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/


#include "ezFCPlib.h"


/**********************************************************************/
/*
  xtoi()

  Convert a hexadecimal number string into an int
  this is the hex version of atoi
*/
long xtoi(char *s)
{
  long val = 0;
  
  if (!s) return 0L;
  
  for (; *s != '\0'; s++)
    if (*s >= '0' && *s <= '9')
      val = val * 16 + *s - '0';
    else if (*s >= 'a' && *s <= 'f')
      val = val * 16 + (*s - 'a' + 10);
    else if (*s >= 'A' && *s <= 'F')
      val = val * 16 + (*s - 'A' + 10);
    else
      break;
  
  return val;
}


/*
long timeLastMidnight()
{
  time_t timenow;

  time(&timenow);
  timenow -= timenow % 86400;
  return timenow;
}
*/
