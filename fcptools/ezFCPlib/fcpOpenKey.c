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
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpOpenKey() - mode: %d", mode);

  /* Validate flags */
  if ((mode & FCP_MODE_O_READ) && (mode & FCP_MODE_O_WRITE))
    return -1; /* read/write access is impossible */
  
  if ((mode & (FCP_MODE_O_READ | FCP_MODE_O_WRITE)) == 0)
    return -1; /* neither selected - illegal */
  
  if (mode & FCP_MODE_RAW)
    hfcp->options->rawmode = 1;

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
  int    rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpOpenKeyRead()");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s", key_uri);

	/* function must get the key, then determine if it's a normal key or a manifest for a splitfile */

  hfcp->key->openmode = FCP_MODE_O_READ;

  /* store final key uri for later usage */
  if (fcpParseHURI(hfcp->key->target_uri, key_uri)) return -1;
	if (fcpParseHURI(hfcp->key->tmpblock->uri, key_uri)) return -1;

	/* if in normal mode, follow the redirects */
	if (hfcp->options->rawmode == 0) {
		
		_fcpLog(FCP_LOG_VERBOSE, "starting recursive retrieve");
		rc = get_follow_redirects(hfcp, key_uri);
	}
	else { /* RAWMODE */
		
		_fcpLog(FCP_LOG_VERBOSE, "start rawmode retrieve");
		rc = get_file(hfcp, key_uri);
	}
	
	if (rc) { /* bail after cleaning up */
		
		_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key: %s", hfcp->key->target_uri->uri_str);
		return -1;
	}

	/* unlink the tmpfiles, then relink them for subsequent calls to fcpReadKey() */
	tmpfile_unlink(hfcp->key);
	tmpfile_link(hfcp->key, O_RDONLY);
	
	_fcpLog(FCP_LOG_DEBUG, "retrieved key into tmpblocks: %s", hfcp->key->target_uri->uri_str);

  return 0;
}

static int fcpOpenKeyWrite(hFCP *hfcp, char *key_uri)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpOpenKeyWrte()");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s", key_uri);

  hfcp->key->openmode = FCP_MODE_O_WRITE;
  
  /* store final key uri for later usage */
  if (fcpParseHURI(hfcp->key->target_uri, key_uri)) return -1;
  if (fcpParseHURI(hfcp->key->tmpblock->uri, key_uri)) return -1;
	
	/* that's it for now */
  
  return 0;
}

