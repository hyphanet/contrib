/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include <sys/stat.h>
#include <fcntl.h>

/*#include <unistd.h>*/
#include <stdlib.h>

#include "ezFCPlib.h"

/*
  Function:    fcpGetKeyToFile()

  Arguments:   hfcp
  
  Description:
*/

int fcpGetKeyToFile(HFCP *hfcp, char *key, char *file, char **pMetadata)
{
  char buf[1024];
  int count;
  int fd;

  /* try to get the key open */
  if (fcpOpenKey(hfcp, key, (_FCP_O_READ | (hfcp->raw ? _FCP_O_RAW : 0))) != 0)
	 return -1;
  
  *pMetadata = 0;

  /* nuke file if it exists */
  unlink(file);

  /* open a file to write the key to */
  if ((fd = open(file, OPEN_MODE_WRITE | O_CREAT, OPEN_PERMS)) < 0)
    return -1;

  /* suck all of key's data into this file */
  while ((count = fcpReadKey(hfcp, buf, 1024)) > 0)
	 write(fd, buf, count);
  
  close(fd);

  /* all done */
  fcpCloseKey(hfcp);
  
  return 0;
}
