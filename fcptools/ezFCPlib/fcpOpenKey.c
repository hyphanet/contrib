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

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>


extern int   _fcpSockConnect(hFCP *hfcp);
extern void  _fcpSockDisconnect(hFCP *hfcp);
extern char *_fcpTmpFilename(void);

static int    fcpOpenKeyRead(hFCP *hfcp, char *key_uri);
static int    fcpOpenKeyWrite(hFCP *hfcp, char *key_uri);


int fcpOpenKey(hFCP *hfcp, char *key_uri, int mode)
{
	/* clear the error field */
	if (hfcp->error) {
		free(hfcp->error);
		hfcp->error = 0;
	}
	
  /* Validate flags */
  if ((mode & _FCP_O_READ) && (mode & _FCP_O_WRITE))
    return -1;      /* read/write access is impossible */
  
  if ((mode & (_FCP_O_READ | _FCP_O_WRITE)) == 0)
    return -1;      /* neither selected - illegal */
  
  if (mode & _FCP_O_RAW)
    hfcp->rawmode = 1;

  /* Now perform the read/write specific open */
  if (mode & _FCP_O_READ)
    return fcpOpenKeyRead(hfcp, key_uri);

  else if (mode & _FCP_O_WRITE)
    return fcpOpenKeyWrite(hfcp, key_uri);

	else { /* Who knows what's wrong here.. */
		hfcp->error = strdup("invalid mode specified");
		return -1;
	}
}


static int fcpOpenKeyRead(hFCP *hfcp, char *key_uri)
{
	_fcpLog(FCP_LOG_VERBOSE, "Entered fcpOpenKeyRead()");

	hfcp->key->openmode = _FCP_O_READ;

	/* not yet implemented */
  return 1;
}


static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri)
{
	_fcpLog(FCP_LOG_VERBOSE, "Entered fcpOpenKeyWrite()");

	hfcp->key = _fcpCreateHKey();
	hfcp->key->openmode = _FCP_O_WRITE;

	/* store final key uri for later usage */
	if (_fcpParseURI(hfcp->key->target_uri, key_uri)) {
		hfcp->error = strdup("invalid freenet uri");
		return -1;
	}
	
	/* prepare the tmpblock for key data */
	hfcp->key->tmpblock = _fcpCreateHBlock();

	/* Tie it to a unique temporary file */
	hfcp->key->tmpblock->filename = _fcpTmpFilename();
	if (!(hfcp->key->tmpblock->file = fopen(hfcp->key->tmpblock->filename, "wb")))
		return -1;

	hfcp->key->tmpblock->fd = fileno(hfcp->key->tmpblock->file);

	/* now prepare the tmpblock for key *meta* data */
	hfcp->key->metadata = _fcpCreateHMetadata();
	hfcp->key->metadata->tmpblock = _fcpCreateHBlock();

	hfcp->key->metadata->tmpblock->filename = _fcpTmpFilename();
	if (!(hfcp->key->metadata->tmpblock->file = fopen(hfcp->key->metadata->tmpblock->filename, "wb")))
		return -1;

	hfcp->key->metadata->tmpblock->fd = fileno(hfcp->key->metadata->tmpblock->file);

	_fcpLog(FCP_LOG_DEBUG, "successfully opened key for writing");

	return 0;
}

