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

#include <stdlib.h>
#include <string.h>


/*
  function xtoi()

  Convert a hexadecimal string into an int. This is the hex version of
	atoi.
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


long file_size(char *filename)
{
	struct stat fstat;

	if (!filename) return -1;

	if (stat(filename, &fstat))
		return -1;
	else
		return fstat.st_size;
}


char *str_reset(char *dest, char *src)
{
	if (dest) free(dest);

	dest = (char*)malloc(strlen(src) + 1);
	strcpy(dest, src);

	/* on function exit, dest *may* not point to the same location it did when
		 the function was originally called.  Use the returned value instead. */
	return dest;
}

