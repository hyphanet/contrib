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

static int fcpOpenKeyRead(hFCP *hfcp, char *key_uri);
static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri);


int fcpOpenKey(hFCP *hfcp, char *key_uri, int mode)
{
	_fcpLog(FCPT_LOG_DEBUG, "Entered fcpOpenKey() - mode: %d", mode);

  /* Validate flags */
  if ((mode & FCPT_MODE_O_READ) && (mode & FCPT_MODE_O_WRITE))
    return -1; /* read/write access is impossible */
  
  if ((mode & (FCPT_MODE_O_READ | FCPT_MODE_O_WRITE)) == 0)
    return -1; /* neither selected - illegal */
  
  if (mode & FCPT_MODE_RAW)
    hfcp->options->noredirect = 1;

  /* Now perform the read/write specific open */
  if (mode & FCPT_MODE_O_READ)
    return fcpOpenKeyRead(hfcp, key_uri);

  else if (mode & FCPT_MODE_O_WRITE)
    return fcpOpenKeyWrite(hfcp, key_uri);
  
  else { /* Who knows what's wrong here.. */
    _fcpLog(FCPT_LOG_DEBUG, "invalid file open mode specified: %d", mode);
    return -1;
  }
}

int fcpSetMimetype(hKey *key, char *mimetype)
{
	if (key->mimetype) free(key->mimetype);
	key->mimetype = strdup(mimetype);

	return 0;
}


static int fcpOpenKeyRead(hFCP *hfcp, char *key_uri)
{
  int rc;

	_fcpLog(FCPT_LOG_DEBUG, "Entered fcpOpenKeyRead()");
	_fcpLog(FCPT_LOG_DEBUG, "key_uri: %s", key_uri);

	/* function must get the key, then determine if it's a normal
	key or a manifest for a splitfile */

  hfcp->key->openmode = FCPT_MODE_O_READ;

  /* store final key uri for later usage */
  if (fcpParseHURI(hfcp->key->target_uri, key_uri)) return -1;
	if (fcpParseHURI(hfcp->key->tmpblock->uri, key_uri)) return -1;

	rc = _fcpGetKeyToFile(hfcp, key_uri, hfcp->key->tmpblock->filename, hfcp->key->metadata->tmpblock->filename);
	
	if (rc) { /* bail after cleaning up */
		_fcpLog(FCPT_LOG_VERBOSE, "Error retrieving key: %s", hfcp->key->target_uri->uri_str);
		return -1;
	}

	_fcpLog(FCPT_LOG_DEBUG, "retrieved key into tmpblocks: %s", hfcp->key->target_uri->uri_str);

	/* now link the files, so that next fcpReadKey() is primed */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_READ);
	_fcpBlockLink(hfcp->key->metadata->tmpblock, _FCP_READ);

  return 0;
}

static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri)
{
	_fcpLog(FCPT_LOG_DEBUG, "Entered fcpOpenKeyWrite()");
	_fcpLog(FCPT_LOG_DEBUG, "key_uri: %s", key_uri);

  hfcp->key->openmode = FCPT_MODE_O_WRITE;
  
  /* store final key uri for later usage */
  if (fcpParseHURI(hfcp->key->target_uri, key_uri)) return -1;
  /*if (fcpParseHURI(hfcp->key->tmpblock->uri, key_uri)) return -1;*/

	/* link the files */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_WRITE);
	_fcpBlockLink(hfcp->key->metadata->tmpblock, _FCP_WRITE);

	/* that's it for now */
  
  return 0;
}

