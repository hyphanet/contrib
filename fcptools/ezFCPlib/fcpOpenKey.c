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

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ez_sys.h"

static int    fcpOpenKeyRead(hFCP *hfcp, char *key_uri);
static int    fcpOpenKeyWrite(hFCP *hfcp, char *key_uri);


int fcpOpenKey(hFCP *hfcp, char *key_uri, int mode)
{
  /* Validate flags */
  if ((mode & FCP_O_READ) && (mode & FCP_O_WRITE))
    return -1;      /* read/write access is impossible */
  
  if ((mode & (FCP_O_READ | FCP_O_WRITE)) == 0)
    return -1;      /* neither selected - illegal */
  
  if (mode & FCP_O_RAW)
    hfcp->rawmode = 1;

  /* Now perform the read/write specific open */
  if (mode & FCP_O_READ)
    return fcpOpenKeyRead(hfcp, key_uri);

  else if (mode & FCP_O_WRITE)
    return fcpOpenKeyWrite(hfcp, key_uri);

	else { /* Who knows what's wrong here.. */
		_fcpLog(FCP_LOG_DEBUG, "invalid file open mode specified: %d", mode);
		return -1;
	}
}


static int fcpOpenKeyRead(hFCP *hfcp, char *key_uri)
{

	hfcp->key->openmode = FCP_O_READ;

	/* not yet implemented */
	key_uri = key_uri;

  return 1;
}


static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri)
{
	hfcp->key = _fcpCreateHKey();
	hfcp->key->openmode = FCP_O_WRITE;

	/* store final key uri for later usage */
	if (fcpParseURI(hfcp->key->target_uri, key_uri)) return -1;
	
	/* prepare the tmpblock for key data */
	hfcp->key->tmpblock = _fcpCreateHBlock();

	/* Tie it to a unique temporary file */
	hfcp->key->tmpblock->filename	= (char *)malloc(257);

	hfcp->key->tmpblock->fd = _fcpTmpfile(&hfcp->key->tmpblock->filename);
	if (hfcp->key->tmpblock->fd == -1)
		return -1;

	/* now prepare the tmpblock for key *meta* data */
	hfcp->key->metadata = _fcpCreateHMetadata();
	hfcp->key->metadata->tmpblock = _fcpCreateHBlock();

	hfcp->key->metadata->tmpblock->filename	= (char *)malloc(257);

	hfcp->key->metadata->tmpblock->fd =
		_fcpTmpfile(&hfcp->key->metadata->tmpblock->filename);

	if (hfcp->key->metadata->tmpblock->fd == -1)
		return -1;

	return 0;
}

