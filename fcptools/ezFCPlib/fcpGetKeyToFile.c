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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ez_sys.h"


/*
	fcpGetKeyToFile()
*/
int fcpGetKeyToFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	int   rc;
	
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpGetKeyToFile()");
	_fcpLog(FCP_LOG_DEBUG, "Function parameters:");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s, key_filename: %s, meta_filename: %s",
					key_uri,
					key_filename,
					meta_filename
					);
	
	/* function must get the key, then determine if it's a normal key or a manifest for a splitfile */

	/* new fcpCreation.c routines create everything in fcpCreateHFCP() */

	fcpParseURI(hfcp->key->target_uri, key_uri);
	fcpParseURI(hfcp->key->tmpblock->uri, key_uri);

	/* if in normal mode, follow the redirects */
	if (hfcp->rawmode == 0) {
		
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
	
	/* Here, the key and meta data is within the tmpblocks */

	fcpParseURI(hfcp->key->uri, hfcp->key->tmpblock->uri->uri_str);
	tmpfile_unlink(hfcp->key);
	
	/* TODO: check metadata to detect splitfiles */

	_fcpLog(FCP_LOG_VERBOSE, "Copying tmp files");
	
	if (copy_file(key_filename, hfcp->key->tmpblock->filename) < 0)
		return -1;
	
	if (copy_file(meta_filename, hfcp->key->metadata->tmpblock->filename) < 0)
		return -1;

	_fcpLog(FCP_LOG_VERBOSE, "Retrieved key: %s", hfcp->key->target_uri->uri_str);

	return 0;
}

