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
  int rc;
  
  hfcp->key = _fcpCreateHKey();
  hfcp->key->openmode = FCP_O_READ;
  
  /* store final key uri for later usage */
  if (fcpParseURI(hfcp->key->target_uri, key_uri)) return -1;
  
  /* prepare the tmpblock for key data */
  hfcp->key->tmpblock = _fcpCreateHBlock();
  
  /* Tie it to a unique temporary file */
  hfcp->key->tmpblock->filename	= (char *)malloc(257);
  
  hfcp->key->tmpblock->fd = _fcpTmpfile(&hfcp->key->tmpblock->filename);
  if (hfcp->key->tmpblock->fd == -1) return -1;
  
  /* now prepare the tmpblock for key *meta* data */
  hfcp->key->metadata = _fcpCreateHMetadata();
  hfcp->key->metadata->tmpblock = _fcpCreateHBlock();
  
  hfcp->key->metadata->tmpblock->filename	= (char *)malloc(257);
  
  hfcp->key->metadata->tmpblock->fd = _fcpTmpfile(&hfcp->key->metadata->tmpblock->filename);
  if (hfcp->key->metadata->tmpblock->fd == -1)
    return -1;
  
  unlink_key(hfcp->key);
  
  /* key and meta tmp files are closed, but exist and are ready.. call get_file() */
  if ((rc = get_file(hfcp, key_uri,
		     hfcp->key->tmpblock->filename,
		     hfcp->key->metadata->tmpblock->filename)) != 0) {
    
    _fcpLog(FCP_LOG_DEBUG, "could not get file from freenet");
    return -1;
  }
  
  _fcpLog(FCP_LOG_DEBUG, "transferred file into temp directory");
  
  /* now open the key and meta files, preparing them for subsequent
     calls to fcpReadKey() */

  if ((hfcp->key->tmpblock->fd = open(hfcp->key->tmpblock->filename, O_RDONLY)) == -1) {
    _fcpLog(FCP_LOG_DEBUG, "could not create temp key file");
    return -1;
  }
	hfcp->key->size = file_size(hfcp->key->tmpblock->filename);
  
  if ((hfcp->key->metadata->tmpblock->fd = open(hfcp->key->metadata->tmpblock->filename, O_RDONLY)) == -1) {
    _fcpLog(FCP_LOG_DEBUG, "could not create temp metadata file");
    return -1;
  }
	hfcp->key->metadata->size = file_size(hfcp->key->metadata->tmpblock->filename);
  
  hfcp->key->tmpblock->index = 0;
  hfcp->key->metadata->tmpblock->index = 0;
  
  return 0;
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
  if (hfcp->key->tmpblock->fd == -1) return -1;
  
  /* now prepare the tmpblock for key *meta* data */
  hfcp->key->metadata = _fcpCreateHMetadata();
  hfcp->key->metadata->tmpblock = _fcpCreateHBlock();
  
  hfcp->key->metadata->tmpblock->filename	= (char *)malloc(257);
  
  hfcp->key->metadata->tmpblock->fd =	_fcpTmpfile(&hfcp->key->metadata->tmpblock->filename);
  if (hfcp->key->metadata->tmpblock->fd == -1)
    return -1;
  
  return 0;
}

