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
  if ((mode & FCP_MODE_O_READ) && (mode & FCP_MODE_O_WRITE))
    return -1; /* read/write access is impossible */
  
  if ((mode & (FCP_MODE_O_READ | FCP_MODE_O_WRITE)) == 0)
    return -1; /* neither selected - illegal */
  
  if (mode & FCP_MODE_RAW)
    hfcp->rawmode = 1;

  /* Now perform the read/write specific open */
  if (mode & FCP_MODE_O_READ)
    return fcpOpenKeyRead(hfcp, key_uri);

  else if (mode & FCP_MODE_O_WRITE)
    return fcpOpenKeyWrite(hfcp, key_uri);
  
  else { /* Who knows what's wrong here.. */
    _fcpLog(FCP_LOG_DEBUG, "invalid file open mode specified: %d", mode);
    return -1;
  }
}

static int fcpOpenKeyRead(hFCP *hfcp, char *key_uri)
{
	hFCP  *tmp_hfcp;
  int    rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpOpenKeyRead()");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s", key_uri);
	
	/* function must get the key, then determine if it's a normal key or a manifest for a splitfile */

	tmp_hfcp = fcpInheritHFCP(hfcp);
	
	tmp_hfcp->key = _fcpCreateHKey();
  tmp_hfcp->key->openmode = FCP_MODE_O_READ;
	tmp_hfcp->key->tmpblock = _fcpCreateHBlock();

  /* store final key uri for later usage */
  if (fcpParseURI(tmp_hfcp->key->target_uri, key_uri)) return -1;

	tmp_hfcp->key->metadata = _fcpCreateHMetadata();
	tmp_hfcp->key->metadata->tmpblock = _fcpCreateHBlock();

	tmp_hfcp->key->tmpblock->fd = _fcpTmpfile(tmp_hfcp->key->tmpblock->filename);	
	tmp_hfcp->key->metadata->tmpblock->fd = _fcpTmpfile(tmp_hfcp->key->metadata->tmpblock->filename);	

	fcpParseURI(tmp_hfcp->key->target_uri, key_uri);

	/* close the tmp files */
	unlink_key(tmp_hfcp->key);

	/* if in normal mode, follow the redirects */
	if (tmp_hfcp->rawmode == 0) {
		
		_fcpLog(FCP_LOG_VERBOSE, "starting recursive retrieve");
		rc = get_follow_redirects(tmp_hfcp, key_uri,
															tmp_hfcp->key->tmpblock->filename,
															tmp_hfcp->key->metadata->tmpblock->filename);
	}
	else { /* RAWMODE */
		
		_fcpLog(FCP_LOG_VERBOSE, "start rawmode retrieve");
		rc = get_file(tmp_hfcp, key_uri,
									tmp_hfcp->key->tmpblock->filename,
									tmp_hfcp->key->metadata->tmpblock->filename);
	}
	
	if (rc) { /* bail after cleaning up */
		
		_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key: %s", tmp_hfcp->key->target_uri->uri_str);
		return -1;
	}

	hfcp->key = tmp_hfcp->key;
	
	tmp_hfcp->key = 0;
	fcpDestroyHFCP(tmp_hfcp);

	/* now re-open the tmpblocks in preparation for calls to fcpReadKey() */

	hfcp->key->tmpblock->fd = open(hfcp->key->tmpblock->filename, O_RDONLY);
	hfcp->key->metadata->tmpblock->fd = open(hfcp->key->metadata->tmpblock->filename, O_RDONLY);
	
	_fcpLog(FCP_LOG_VERBOSE, "Retrieved key: %s", hfcp->key->target_uri->uri_str);
	
  return 0;
}

static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri)
{
  hfcp->key = _fcpCreateHKey();
  hfcp->key->openmode = FCP_MODE_O_WRITE;
  
  /* store final key uri for later usage */
  if (fcpParseURI(hfcp->key->target_uri, key_uri)) return -1;
  
  /* prepare the tmpblock for key data */
  hfcp->key->tmpblock = _fcpCreateHBlock();
  
  hfcp->key->tmpblock->fd = _fcpTmpfile(hfcp->key->tmpblock->filename);
  if (hfcp->key->tmpblock->fd == -1) return -1;
  
  /* now prepare the tmpblock for key *meta* data */
  hfcp->key->metadata = _fcpCreateHMetadata();
  hfcp->key->metadata->tmpblock = _fcpCreateHBlock();
  
  hfcp->key->metadata->tmpblock->fd =	_fcpTmpfile(hfcp->key->metadata->tmpblock->filename);
  if (hfcp->key->metadata->tmpblock->fd == -1)
    return -1;
  
  return 0;
}

