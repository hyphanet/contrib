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
	int rc;
	
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpGetKeyToFile()");
	_fcpLog(FCP_LOG_DEBUG, "Function parameters:");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s, key_filename: %s, meta_filename: %s",
					key_uri,
					key_filename,
					meta_filename
					);
	
	/* function must get the key, then determine if it's a normal
	key or a manifest for a splitfile */

	fcpParseHURI(hfcp->key->target_uri, key_uri);

	/* TODO: implement splitfile logic */

	/* if in normal mode, follow the redirects */
	if (hfcp->options->rawmode == 0) {
		
		_fcpLog(FCP_LOG_VERBOSE, "Starting recursive retrieve (will follow redirects)");
		rc = _fcpGetFollowRedirects(hfcp, key_uri);
	}
	else { /* RAWMODE */
		
		_fcpLog(FCP_LOG_VERBOSE, "Starting basic retrieve (rawmode)");
		rc = _fcpGetBLock(hfcp,
											hfcp->key->tmpblock,
											hfcp->key->metadata->tmpblock,
											key_uri);
	}

	if (rc) { /* bail after cleaning up */
		_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key");
		rc = -1;
		goto cleanup;
	}
	
	/* Here, the key and meta data is within the tmpblocks */

	fcpParseHURI(hfcp->key->uri, hfcp->key->tmpblock->uri->uri_str);
	
	/* TODO: check metadata to detect splitfiles */

	_fcpLog(FCP_LOG_DEBUG, "copying tmp files");

	if (key_filename) 
		if (hfcp->key->size)

			if (_fcpCopyFile(key_filename, hfcp->key->tmpblock->filename) < 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not copy internal tempfile key %s", key_filename);
				
				rc = -1;
				goto cleanup;
			}

	if (meta_filename)
		if (hfcp->key->metadata->size)

			if (_fcpCopyFile(meta_filename, hfcp->key->metadata->tmpblock->filename) < 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not copy internal tempfile metadata %s", meta_filename);
				
				rc = -1;
				goto cleanup;
			}
	
	_fcpLog(FCP_LOG_DEBUG, "Retrieved key: %s", hfcp->key->target_uri->uri_str);
	
	/* delete temp files before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpGetKeyToFile()");

	return 0;

cleanup:

	/* delete temp files before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpGetKeyToFile()");

	return rc;
}

