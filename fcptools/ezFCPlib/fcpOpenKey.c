/*
  This code is part of FreeWeb - an FCP-based client for Freenet
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net
  
  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include <sys/types.h>
#include <sys/stat.h>

#ifndef WINDOWS
#include <sys/socket.h>
#endif

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ezFCPlib.h"


extern int   snprintf(char *str, size_t size, const char *format, ...);

extern int    crSockConnect(hFCP *hfcp);
extern void   crSockDisconnect(hFCP *hfcp);
extern char  *crTmpFilename(void);


static int    fcpOpenKeyRead(hFCP *hfcp, char *key);
static int    fcpOpenKeyWrite(hFCP *hfcp, char *key);


/*
	Perhaps handle a mode mask that creates basic splitfiles, and another for
	creating FEC encoded splitfiles (?)
*/

int fcpOpenKey(hFCP *hfcp, char *key, int mode)
{
  /* Validate flags */
  if ((mode & _FCP_O_READ) && (mode & _FCP_O_WRITE))
    return -1;      /* read/write access is impossible */
  
  if ((mode & (_FCP_O_READ | _FCP_O_WRITE)) == 0)
    return -1;      /* neither selected - illegal */
  
  if (mode & _FCP_O_RAW)
    hfcp->rawmode = 1;

  /* Now perform the read/write specific open */
  if (mode & _FCP_O_READ)
    return fcpOpenKeyRead(hfcp, key);

  else if (mode & _FCP_O_WRITE)
    return fcpOpenKeyWrite(hfcp, key);

	else return -2; /* Who knows what's wrong here.. */
}


static int fcpOpenKeyRead(hFCP *hfcp, char *key)
{
	hfcp->key->openmode = _FCP_O_READ;
  return 0;
}


static int fcpOpenKeyWrite(hFCP *hfcp, char *key)
{
	hfcp->key = _fcpCreateHKey();
	hfcp->key->openmode = _FCP_O_WRITE;
	
	/* Bomb out if the key cannot be parsed into a valid URI */
	/* (uri is allocated in fcpCreation.c) */
	if (_fcpParseURI(hfcp->key->uri, key))
		return -1;

	hfcp->key->tmpblock = _fcpCreateHBlock();

	/* Tie it to a unique temporary file */
	hfcp->key->tmpblock->filename = crTmpFilename();
	if (!(hfcp->key->tmpblock->file = fopen(hfcp->key->tmpblock->filename, "wb")))
		return -1;

	hfcp->key->tmpblock->fd = fileno(hfcp->key->tmpblock->file);

	_fcpLog(FCP_LOG_DEBUG, "successfully opened key for writing");

	/* Successful in opening temporary chunk bucket thing */
	return 0;
}

